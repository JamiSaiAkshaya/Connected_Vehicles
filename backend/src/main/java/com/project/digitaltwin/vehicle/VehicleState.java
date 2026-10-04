package com.project.digitaltwin.vehicle;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single historical telemetry reading as received from the communication layer.
 * This is the PHYSICAL/SIMULATED state record (raw, immutable, append-only).
 */
@Entity
@Table(name = "vehicle_state", indexes = {
        @Index(name = "idx_vehicle_state_vehicle_ts", columnList = "vehicleId, messageTimestamp")
})
public class VehicleState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;
    private String vehicleId;
    private Instant messageTimestamp;
    private Instant receivedAt;

    private double positionX;
    private double positionY;
    private double speedMps;
    private double headingDegrees;
    private String routeId;

    private String eventType;
    private Integer eventSeverity;

    private boolean valid;
    private String rejectionReason;

    protected VehicleState() {
        // JPA
    }

    public VehicleState(String messageId, String vehicleId, Instant messageTimestamp, Instant receivedAt,
                         double positionX, double positionY, double speedMps, double headingDegrees,
                         String routeId, String eventType, Integer eventSeverity,
                         boolean valid, String rejectionReason) {
        this.messageId = messageId;
        this.vehicleId = vehicleId;
        this.messageTimestamp = messageTimestamp;
        this.receivedAt = receivedAt;
        this.positionX = positionX;
        this.positionY = positionY;
        this.speedMps = speedMps;
        this.headingDegrees = headingDegrees;
        this.routeId = routeId;
        this.eventType = eventType;
        this.eventSeverity = eventSeverity;
        this.valid = valid;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public Instant getMessageTimestamp() {
        return messageTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public double getSpeedMps() {
        return speedMps;
    }

    public double getHeadingDegrees() {
        return headingDegrees;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getEventSeverity() {
        return eventSeverity;
    }

    public boolean isValid() {
        return valid;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
