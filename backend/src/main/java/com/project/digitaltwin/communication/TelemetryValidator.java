package com.project.digitaltwin.communication;

import com.project.digitaltwin.repository.VehicleStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Validates incoming telemetry per FR-08 / STATE VALIDATION section of the brief.
 * Phase 0 scope: timestamp sanity, duplicate detection, plausibility bounds,
 * required-field presence. Out-of-order/sequence-number validation and
 * historical-consistency checks are deferred to Phase 2 (reliability engine).
 */
@Component
public class TelemetryValidator {

    private final VehicleStateRepository vehicleStateRepository;
    private final double maxPlausibleSpeedMps;

    public TelemetryValidator(VehicleStateRepository vehicleStateRepository,
                               @Value("${digitaltwin.validation.max-plausible-speed-mps}") double maxPlausibleSpeedMps) {
        this.vehicleStateRepository = vehicleStateRepository;
        this.maxPlausibleSpeedMps = maxPlausibleSpeedMps;
    }

    public ValidationResult validate(TelemetryMessage msg) {
        if (msg.vehicleId == null || msg.vehicleId.isBlank()) {
            return ValidationResult.reject("missing vehicleId");
        }
        if (msg.messageId == null || msg.messageId.isBlank()) {
            return ValidationResult.reject("missing messageId");
        }
        if (msg.timestamp <= 0) {
            return ValidationResult.reject("missing or invalid timestamp");
        }
        if (msg.position == null) {
            return ValidationResult.reject("missing position");
        }

        Instant msgTime = Instant.ofEpochMilli(msg.timestamp);
        if (msgTime.isAfter(Instant.now().plusSeconds(5))) {
            return ValidationResult.reject("timestamp is in the future");
        }

        if (msg.speed < 0 || msg.speed > maxPlausibleSpeedMps) {
            return ValidationResult.reject("implausible speed: " + msg.speed + " m/s");
        }

        if (vehicleStateRepository.existsByMessageId(msg.messageId)) {
            return ValidationResult.reject("duplicate messageId");
        }

        Optional<com.project.digitaltwin.vehicle.VehicleState> last =
                vehicleStateRepository.findTopByVehicleIdAndValidTrueOrderByMessageTimestampDesc(msg.vehicleId);
        if (last.isPresent() && msgTime.isBefore(last.get().getMessageTimestamp())) {
            return ValidationResult.reject("out-of-order message (older than last accepted state)");
        }

        return ValidationResult.accept();
    }

    public record ValidationResult(boolean valid, String rejectionReason) {
        public static ValidationResult accept() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
