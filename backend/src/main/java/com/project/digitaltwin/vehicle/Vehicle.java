package com.project.digitaltwin.vehicle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a physical/simulated vehicle (e.g. a bus in CARLA).
 * Identity only — dynamic state lives in {@link VehicleState} and the twin.
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private String vehicleId;

    private String displayName;

    protected Vehicle() {
        // JPA
    }

    public Vehicle(String vehicleId, String displayName) {
        this.vehicleId = vehicleId;
        this.displayName = displayName;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
