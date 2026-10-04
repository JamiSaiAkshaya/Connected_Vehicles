package com.project.digitaltwin.communication;

/**
 * A command the backend pushes DOWN to a vehicle over MQTT -- the reverse
 * direction of TelemetryMessage. First use case: REROUTE, triggered when a
 * high-severity event (accident / road block) is detected on another vehicle
 * nearby, per the adaptive-synchronization / event-severity sections of the
 * project brief (FR-18, FR-19).
 */
public class CommandMessage {

    public String schemaVersion = "1.0";
    public String commandId;
    public String commandType; // "REROUTE" for now; more types later
    public String sourceVehicleId; // which vehicle's event triggered this
    public long timestamp;
    public String reason; // human-readable, e.g. "ACCIDENT reported by BUS-01"
    public Position blockedLocation;

    public static class Position {
        public double x;
        public double y;
    }
}
