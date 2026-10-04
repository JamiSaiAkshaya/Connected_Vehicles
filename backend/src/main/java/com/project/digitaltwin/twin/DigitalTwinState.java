package com.project.digitaltwin.twin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The VIRTUAL state of a vehicle, as maintained by the Digital Twin — distinct
 * from the raw physical/simulated telemetry history in {@code vehicle_state}.
 * One row per vehicle: this is the twin's "current belief" about the vehicle.
 */
@Entity
@Table(name = "digital_twin_state")
public class DigitalTwinState {

    @Id
    private String vehicleId;

    private double lastValidPositionX;
    private double lastValidPositionY;
    private double lastValidSpeedMps;
    private double lastValidHeadingDegrees;
    private Instant lastValidTimestamp;
    private Instant lastUpdatedAt;

    private double freshnessScore;
    private long ageMillis;

    private String currentEventType;
    private Integer currentEventSeverity;

    private String syncStatus; // e.g. SYNCED, STALE, NO_DATA

    protected DigitalTwinState() {
        // JPA
    }

    public DigitalTwinState(String vehicleId) {
        this.vehicleId = vehicleId;
        this.syncStatus = "NO_DATA";
        this.freshnessScore = 0.0;
    }

    public void applyValidUpdate(double x, double y, double speed, double heading,
                                  Instant messageTimestamp, Instant now,
                                  String eventType, Integer eventSeverity,
                                  double freshnessScore, String syncStatus) {
        this.lastValidPositionX = x;
        this.lastValidPositionY = y;
        this.lastValidSpeedMps = speed;
        this.lastValidHeadingDegrees = heading;
        this.lastValidTimestamp = messageTimestamp;
        this.lastUpdatedAt = now;
        if (eventType != null) {
    this.currentEventType = eventType;
    this.currentEventSeverity = eventSeverity;
}
        // this.currentEventType = eventType;
        //this.currentEventSeverity = eventSeverity;
        this.freshnessScore = freshnessScore;
        this.ageMillis = java.time.Duration.between(messageTimestamp, now).toMillis();
        this.syncStatus = syncStatus;
    }

    public void refreshAgeOnly(Instant now, double freshnessScore, String syncStatus) {
        if (this.lastValidTimestamp != null) {
            this.ageMillis = java.time.Duration.between(this.lastValidTimestamp, now).toMillis();
        }
        this.freshnessScore = freshnessScore;
        this.syncStatus = syncStatus;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getLastValidPositionX() {
        return lastValidPositionX;
    }

    public double getLastValidPositionY() {
        return lastValidPositionY;
    }

    public double getLastValidSpeedMps() {
        return lastValidSpeedMps;
    }

    public double getLastValidHeadingDegrees() {
        return lastValidHeadingDegrees;
    }

    public Instant getLastValidTimestamp() {
        return lastValidTimestamp;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public double getFreshnessScore() {
        return freshnessScore;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public String getCurrentEventType() {
        return currentEventType;
    }

    public Integer getCurrentEventSeverity() {
        return currentEventSeverity;
    }

    public String getSyncStatus() {
        return syncStatus;
    }
}
