"""
Phase 1 CARLA telemetry publisher.

Replaces carla/publisher/mock_bus_publisher.py. Spawns two bus-like vehicles
in a running CARLA server, reads their live transform/velocity each tick,
and publishes the SAME JSON schema the Java backend already understands
(no backend changes needed).

Prerequisites:
- CarlaUE4.exe already running (WindowsNoEditor build)
- Python 3.7 with `carla` (bundled wheel) and `paho-mqtt` installed
- Mosquitto broker running (from Phase 0, database/mosquitto.conf)

Usage:
    py -3.7 carla_bus_publisher.py
"""

import json
import time
import uuid
from collections import defaultdict

import carla
import paho.mqtt.client as mqtt

BROKER_HOST = "localhost"
BROKER_PORT = 1883
PUBLISH_INTERVAL_SECONDS = 1.0

# After this many publish ticks (~seconds, given the 1s loop interval), BUS-01
# is treated as having encountered an accident: it physically stops (blocking
# the lane) and reports a high-severity ACCIDENT event. Chosen so it happens
# clearly before either bus reaches the shared junction (~100m at ~8m/s is
# roughly 12-13s), giving BUS-02 real time to react to the reroute command.
ACCIDENT_TRIGGER_TICK = 8

# CARLA's bus-like vehicle blueprint. If this ID doesn't exist in your
# blueprint library (varies slightly by version/content), the script
# will fall back to any blueprint whose id contains "bus" or "fusorosa",
# then finally to any four-wheeled vehicle so the pipeline still runs.
PREFERRED_BUS_BLUEPRINT = "vehicle.mitsubishi.fusorosa"

VEHICLE_IDS = ["BUS-01", "BUS-02"]

# Hardcoded from find_junction_spawn_points.py output for the Koyambedu Junction
# import (junction id=16, tight ~500m bbox re-export -- 8 distinct incoming
# roads, 287 total spawn points). Re-run that diagnostic script if the map
# is regenerated and these stop making sense.
BUS_SPAWN_TRANSFORMS = {
    "BUS-01": carla.Transform(
        carla.Location(x=1075.57, y=-459.20, z=0.3),
        carla.Rotation(yaw=132.69)
    ),
    "BUS-02": carla.Transform(
        carla.Location(x=1012.20, y=-300.60, z=0.3),
        carla.Rotation(yaw=-137.71)
    ),
}


def find_bus_blueprint(blueprint_library):
    bp = blueprint_library.filter(PREFERRED_BUS_BLUEPRINT)
    if len(bp) > 0:
        return bp[0]
    for candidate in blueprint_library.filter("vehicle.*"):
        if "bus" in candidate.id.lower() or "fusorosa" in candidate.id.lower():
            return candidate
    print(f"WARNING: no bus blueprint found, falling back to a generic vehicle.")
    return blueprint_library.filter("vehicle.*")[0]


def apply_color_if_supported(blueprint, rgb_string, vehicle_id):
    """Sets a blueprint's paint color BEFORE spawning, if this vehicle model
    actually supports recoloring (some CARLA vehicles have a fixed livery
    and will not have a modifiable 'color' attribute)."""
    if not blueprint.has_attribute("color"):
        print(f"NOTE: {vehicle_id}'s blueprint has no 'color' attribute "
              f"(fixed livery) -- cannot recolor it.")
        return
    color_attr = blueprint.get_attribute("color")
    if not color_attr.is_modifiable:
        print(f"NOTE: {vehicle_id}'s 'color' attribute exists but is not "
              f"modifiable -- cannot recolor it.")
        return
    blueprint.set_attribute("color", rgb_string)
    print(f"{vehicle_id} color set to {rgb_string}")


def spawn_buses(world):
    blueprint_library = world.get_blueprint_library()

    actors = {}
    for vehicle_id, transform in BUS_SPAWN_TRANSFORMS.items():
        bus_bp = find_bus_blueprint(blueprint_library)
        if vehicle_id == "BUS-02":
            apply_color_if_supported(bus_bp, "0,0,255", vehicle_id)  # blue, to visually distinguish

        actor = world.try_spawn_actor(bus_bp, transform)
        if actor is None:
            raise RuntimeError(
                f"Failed to spawn {vehicle_id} at the hardcoded Koyambedu Junction "
                f"approach point {transform.location}. Another actor may already be "
                f"there -- restart CarlaUE4.exe and retry."
            )
        actor.set_autopilot(True)
        actors[vehicle_id] = actor
        print(f"Spawned {vehicle_id} (blueprint={bus_bp.id}) at {transform.location}, "
              f"heading {transform.rotation.yaw:.1f} degrees")

    return actors


def build_telemetry(vehicle_id, actor, tick, accident_active):
    transform = actor.get_transform()
    velocity = actor.get_velocity()
    speed_mps = (velocity.x ** 2 + velocity.y ** 2 + velocity.z ** 2) ** 0.5

    event = None
    if vehicle_id == "BUS-01" and accident_active:
        event = {"type": "ACCIDENT", "severity": 3}

    return {
        "schemaVersion": "1.0",
        "messageId": f"MSG-{uuid.uuid4()}",
        "vehicleId": vehicle_id,
        "timestamp": int(time.time() * 1000),
        "position": {"x": round(transform.location.x, 2), "y": round(transform.location.y, 2)},
        "speed": round(speed_mps, 2),
        "heading": round(transform.rotation.yaw % 360, 2),
        "routeId": "REC-01",
        "event": event,
    }


def find_alternate_route_point(world, exclude_road_ids, approach_distance_m=30.0):
    """
    Finds a point on a DIFFERENT road feeding the same busy junction, for
    BUS-02 to divert toward when it receives a REROUTE command. Reuses the
    same junction-identification logic as find_junction_spawn_points.py.
    """
    topology = world.get_map().get_topology()
    junction_entries = defaultdict(list)
    for entry_wp, _exit_wp in topology:
        junction = entry_wp.get_junction()
        if junction is not None:
            junction_entries[junction.id].append(entry_wp)

    if not junction_entries:
        return None

    best_junction_id = max(
        junction_entries,
        key=lambda jid: len({wp.road_id for wp in junction_entries[jid]})
    )
    entries = junction_entries[best_junction_id]
    for entry_wp in entries:
        if entry_wp.road_id not in exclude_road_ids:
            back_wps = entry_wp.previous(approach_distance_m)
            target_wp = back_wps[0] if back_wps else entry_wp
            return target_wp.transform.location

    return None


def main():
    client_carla = carla.Client("localhost", 2000)
    client_carla.set_timeout(30.0)
    world = client_carla.get_world()
    print(f"Connected to CARLA map: {world.get_map().name}")

    traffic_manager = client_carla.get_trafficmanager()  # default port 8000, matches set_autopilot(True)

    # Clean up any BUS-01/BUS-02 actors left over from a previous crashed run
    # (e.g. if the script died before reaching the finally block).
    for actor in world.get_actors().filter("vehicle.*"):
        if actor.id and "fusorosa" in actor.type_id.lower():
            print(f"Destroying leftover actor from a previous run: {actor.type_id} (id={actor.id})")
            actor.destroy()

    mqtt_client = mqtt.Client(client_id=f"carla-publisher-{uuid.uuid4()}")

    actors = {}
    accident_triggered = {"value": False}
    reroute_applied = {"value": False}

    def on_command_message(client, userdata, msg):
        if reroute_applied["value"]:
            return  # only react once per run for this demo
        try:
            command = json.loads(msg.payload.decode("utf-8"))
        except Exception as e:
            print(f"Failed to parse command message: {e}")
            return

        if command.get("commandType") != "REROUTE":
            return

        bus02 = actors.get("BUS-02")
        if bus02 is None or not bus02.is_alive:
            return

        print(f"\n*** BUS-02 received REROUTE command: {command.get('reason')} ***")
        alt_location = find_alternate_route_point(
            world, exclude_road_ids={704, 705, 702, 703}
        )
        if alt_location is None:
            print("Could not find an alternate route point -- ignoring command.")
            return

        traffic_manager.set_path(bus02, [alt_location])
        print(f"*** BUS-02 diverted toward alternate location {alt_location} ***\n")
        reroute_applied["value"] = True

    mqtt_client.on_message = on_command_message
    mqtt_client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
    mqtt_client.subscribe("vehicles/BUS-02/command", qos=1)
    mqtt_client.loop_start()
    print(f"Connected to MQTT broker at {BROKER_HOST}:{BROKER_PORT}, "
          f"subscribed to vehicles/BUS-02/command")

    try:
        actors = spawn_buses(world)
        print("Publishing live CARLA telemetry. Press Ctrl+C to stop.\n")

        tick = 0
        while True:
            if tick == ACCIDENT_TRIGGER_TICK and not accident_triggered["value"]:
                bus01 = actors.get("BUS-01")
                if bus01 is not None and bus01.is_alive:
                    bus01.set_autopilot(False)
                    bus01.apply_control(carla.VehicleControl(throttle=0.0, brake=1.0, hand_brake=True))
                    accident_triggered["value"] = True
                    print(f"\n*** BUS-01 ACCIDENT triggered at tick {tick} -- lane blocked ***\n")

            for vehicle_id, actor in actors.items():
                if not actor.is_alive:
                    continue
                is_bus01_accident = (vehicle_id == "BUS-01" and accident_triggered["value"])
                payload = build_telemetry(vehicle_id, actor, tick, is_bus01_accident)
                topic = f"vehicles/{vehicle_id}/telemetry"
                mqtt_client.publish(topic, json.dumps(payload), qos=1)
                print(f"[{topic}] {payload}")
            tick += 1
            time.sleep(PUBLISH_INTERVAL_SECONDS)
    except KeyboardInterrupt:
        print("\nStopping publisher and cleaning up spawned actors.")
    finally:
        for actor in actors.values():
            if actor.is_alive:
                actor.destroy()
        mqtt_client.loop_stop()
        mqtt_client.disconnect()


if __name__ == "__main__":
    main()
