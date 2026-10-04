package com.project.digitaltwin.twin;

import com.project.digitaltwin.communication.CommandMessage;
import com.project.digitaltwin.communication.CommandPublisher;
import com.project.digitaltwin.communication.TelemetryMessage;
import com.project.digitaltwin.communication.TelemetryValidator;
import com.project.digitaltwin.freshness.FreshnessCalculator;
import com.project.digitaltwin.repository.DigitalTwinStateRepository;
import com.project.digitaltwin.repository.VehicleRepository;
import com.project.digitaltwin.repository.VehicleStateRepository;
import com.project.digitaltwin.vehicle.Vehicle;
import com.project.digitaltwin.vehicle.VehicleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DigitalTwinService {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinService.class);

    // Event types that trigger an immediate reroute command to other vehicles,
    // per the accident/road-block demo scenario. Kept as a fixed set for now;
    // move to configuration if more event types are added later.
    private static final Set<String> REROUTE_TRIGGER_EVENT_TYPES = Set.of("ACCIDENT", "ROAD_BLOCKED");

    private final TelemetryValidator validator;
    private final FreshnessCalculator freshnessCalculator;
    private final VehicleRepository vehicleRepository;
    private final VehicleStateRepository vehicleStateRepository;
    private final DigitalTwinStateRepository twinStateRepository;
    private final CommandPublisher commandPublisher;
    private final long staleThresholdSeconds;
    private final int rerouteSeverityThreshold;

    public DigitalTwinService(TelemetryValidator validator,
                               FreshnessCalculator freshnessCalculator,
                               VehicleRepository vehicleRepository,
                               VehicleStateRepository vehicleStateRepository,
                               DigitalTwinStateRepository twinStateRepository,
                               CommandPublisher commandPublisher,
                               @Value("${digitaltwin.validation.stale-threshold-seconds}") long staleThresholdSeconds,
                               @Value("${digitaltwin.events.reroute-severity-threshold}") int rerouteSeverityThreshold) {
        this.validator = validator;
        this.freshnessCalculator = freshnessCalculator;
        this.vehicleRepository = vehicleRepository;
        this.vehicleStateRepository = vehicleStateRepository;
        this.twinStateRepository = twinStateRepository;
        this.commandPublisher = commandPublisher;
        this.staleThresholdSeconds = staleThresholdSeconds;
        this.rerouteSeverityThreshold = rerouteSeverityThreshold;
    }

    @Transactional
    public void ingest(TelemetryMessage msg) {
        var result = validator.validate(msg);
        Instant now = Instant.now();
        Instant msgTime = (msg.timestamp > 0) ? Instant.ofEpochMilli(msg.timestamp) : now;

        vehicleRepository.findById(safeVehicleId(msg)).orElseGet(() -> {
            log.info("Registering previously unseen vehicle: {}", safeVehicleId(msg));
            return vehicleRepository.save(new Vehicle(safeVehicleId(msg), safeVehicleId(msg)));
        });

        String eventType = msg.event != null ? msg.event.type : null;
        Integer eventSeverity = msg.event != null ? msg.event.severity : null;

        VehicleState record = new VehicleState(
                msg.messageId, msg.vehicleId, msgTime, now,
                result.valid() ? msg.position.x : 0,
                result.valid() ? msg.position.y : 0,
                result.valid() ? msg.speed : 0,
                result.valid() ? msg.heading : 0,
                msg.routeId, eventType, eventSeverity,
                result.valid(), result.rejectionReason()
        );
        vehicleStateRepository.save(record);

        if (!result.valid()) {
            log.warn("Rejected telemetry from {}: {}", msg.vehicleId, result.rejectionReason());
            touchTwinAgeOnly(msg.vehicleId, now);
            return;
        }

        double freshness = freshnessCalculator.score(msgTime, now);
        String syncStatus = classifySyncStatus(msgTime, now);

        DigitalTwinState twin = twinStateRepository.findById(msg.vehicleId)
                .orElseGet(() -> new DigitalTwinState(msg.vehicleId));
        twin.applyValidUpdate(msg.position.x, msg.position.y, msg.speed, msg.heading,
                msgTime, now, eventType, eventSeverity, freshness, syncStatus);
        twinStateRepository.save(twin);

        log.info("Twin updated: vehicle={} freshness={} syncStatus={}", msg.vehicleId, freshness, syncStatus);

        maybeTriggerReroute(msg, eventType, eventSeverity);
    }

    /**
     * FR-18/FR-19: high-severity events must propagate to other vehicles
     * immediately, not wait for their next routine sync. This is the
     * decision point for the accident -> reroute demo scenario.
     */
    private void maybeTriggerReroute(TelemetryMessage sourceMsg, String eventType, Integer eventSeverity) {
        if (eventType == null || !REROUTE_TRIGGER_EVENT_TYPES.contains(eventType)) {
            return;
        }
        if (eventSeverity == null || eventSeverity < rerouteSeverityThreshold) {
            return;
        }

        List<Vehicle> allVehicles = vehicleRepository.findAll();
        for (Vehicle vehicle : allVehicles) {
            if (vehicle.getVehicleId().equals(sourceMsg.vehicleId)) {
                continue; // don't reroute the vehicle that reported the event
            }
            CommandMessage command = new CommandMessage();
            command.commandId = "CMD-" + UUID.randomUUID();
            command.commandType = "REROUTE";
            command.sourceVehicleId = sourceMsg.vehicleId;
            command.timestamp = System.currentTimeMillis();
            command.reason = eventType + " reported by " + sourceMsg.vehicleId + " (severity " + eventSeverity + ")";
            command.blockedLocation = new CommandMessage.Position();
            command.blockedLocation.x = sourceMsg.position.x;
            command.blockedLocation.y = sourceMsg.position.y;

            commandPublisher.publishCommand(vehicle.getVehicleId(), command);
        }
    }

    private void touchTwinAgeOnly(String vehicleId, Instant now) {
        twinStateRepository.findById(vehicleId).ifPresent(twin -> {
            if (twin.getLastValidTimestamp() == null) {
                return;
            }
            double freshness = freshnessCalculator.score(twin.getLastValidTimestamp(), now);
            String status = classifySyncStatus(twin.getLastValidTimestamp(), now);
            twin.refreshAgeOnly(now, freshness, status);
            twinStateRepository.save(twin);
        });
    }

    private String classifySyncStatus(Instant lastValidTimestamp, Instant now) {
        long ageSeconds = java.time.Duration.between(lastValidTimestamp, now).toSeconds();
        return ageSeconds > staleThresholdSeconds ? "STALE" : "SYNCED";
    }

    private String safeVehicleId(TelemetryMessage msg) {
        return msg.vehicleId != null ? msg.vehicleId : "UNKNOWN";
    }

    public List<DigitalTwinState> getAllTwins() {
        return twinStateRepository.findAll();
    }

    public DigitalTwinState getTwin(String vehicleId) {
        return twinStateRepository.findById(vehicleId)
                .orElseThrow(() -> new java.util.NoSuchElementException("No twin found for vehicle: " + vehicleId));
    }
}
