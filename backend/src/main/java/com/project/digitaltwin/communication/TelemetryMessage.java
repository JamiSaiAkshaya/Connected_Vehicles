package com.project.digitaltwin.communication;

/**
 * Wire format for telemetry received over MQTT.
 * Mirrors the standardized schema defined in the project brief (FR-05).
 * Kept schema-versioned so CARLA integration (Phase 1) is a drop-in payload change.
 */
public class TelemetryMessage {

    public String schemaVersion = "1.0";
    public String messageId;
    public String vehicleId;
    public long timestamp; // epoch millis
    public Position position;
    public double speed;
    public double heading;
    public String routeId;
    public Event event; // nullable

    public static class Position {
        public double x;
        public double y;
    }

    public static class Event {
        public String type;
        public int severity;
    }
}
