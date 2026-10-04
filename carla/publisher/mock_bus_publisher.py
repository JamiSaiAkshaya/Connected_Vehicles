"""
Phase 0 mock telemetry publisher.

This script stands in for the real CARLA + Python adapter (Phase 1).
It publishes the SAME JSON schema that the CARLA adapter will eventually
produce, over the SAME MQTT topics, so the Java backend, Postgres schema,
and freshness/reliability logic can all be built and verified end-to-end
before the CARLA install (which needs a GPU + Unreal Engine components)
is tackled separately.

Usage:
    python mock_bus_publisher.py

Requires: paho-mqtt (pip install paho-mqtt)
"""

import json
import math
import time
import uuid

import paho.mqtt.client as mqtt

BROKER_HOST = "localhost"
BROKER_PORT = 1883
PUBLISH_INTERVAL_SECONDS = 1.0

BUSES = ["BUS-01", "BUS-02"]


def build_telemetry(vehicle_id: str, tick: int) -> dict:
    """Generate a plausible, slowly-moving circular path per bus."""
    phase_offset = 0.0 if vehicle_id == "BUS-01" else math.pi  # opposite sides of the loop
    angle = (tick * 0.05) + phase_offset
    radius = 120.0

    x = radius * math.cos(angle)
    y = radius * math.sin(angle)
    heading = (math.degrees(angle) + 90) % 360
    speed = 8.0 + (2.0 * math.sin(tick * 0.1))  # ~8 m/s cruising speed, gentle variation

    event = None
    # Inject a simple heavy-traffic event periodically to exercise the event pipeline.
    if tick % 40 == 20 and vehicle_id == "BUS-01":
        event = {"type": "HEAVY_TRAFFIC", "severity": 2}

    return {
        "schemaVersion": "1.0",
        "messageId": f"MSG-{uuid.uuid4()}",
        "vehicleId": vehicle_id,
        "timestamp": int(time.time() * 1000),
        "position": {"x": round(x, 2), "y": round(y, 2)},
        "speed": round(speed, 2),
        "heading": round(heading, 2),
        "routeId": "REC-01",
        "event": event,
    }


def main():
    client = mqtt.Client(client_id=f"mock-publisher-{uuid.uuid4()}")
    client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
    client.loop_start()

    print(f"Connected to MQTT broker at {BROKER_HOST}:{BROKER_PORT}")
    print(f"Publishing mock telemetry for: {', '.join(BUSES)}")
    print("Press Ctrl+C to stop.\n")

    tick = 0
    try:
        while True:
            for bus_id in BUSES:
                payload = build_telemetry(bus_id, tick)
                topic = f"vehicles/{bus_id}/telemetry"
                client.publish(topic, json.dumps(payload), qos=1)
                print(f"[{topic}] {payload}")
            tick += 1
            time.sleep(PUBLISH_INTERVAL_SECONDS)
    except KeyboardInterrupt:
        print("\nStopping publisher.")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
