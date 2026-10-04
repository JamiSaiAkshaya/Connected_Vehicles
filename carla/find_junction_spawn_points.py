"""
Diagnostic step: identifies the busiest junction in the imported Koyambedu
map (should be the real interchange, since our OSM export was centered on it)
and prints two candidate spawn transforms on DIFFERENT incoming roads, each
~100m back from the junction so buses have room to visibly approach it.

Run this AFTER load_koyambedu_map.py has generated the world (map stays
loaded once generated -- no need to re-run the generation step).

Usage:
    py -3.7 find_junction_spawn_points.py
"""

import carla
from collections import defaultdict

APPROACH_DISTANCE_M = 100.0  # how far back from the junction to place each bus


def main():
    client = carla.Client("localhost", 2000)
    client.set_timeout(30.0)
    world = client.get_world()
    carla_map = world.get_map()
    print(f"Connected to map: {carla_map.name}")

    topology = carla_map.get_topology()  # list of (entry_waypoint, exit_waypoint)

    # Group entry waypoints by junction id to find the busiest junction.
    junction_entries = defaultdict(list)
    for entry_wp, _exit_wp in topology:
        junction = entry_wp.get_junction()
        if junction is not None:
            junction_entries[junction.id].append(entry_wp)

    if not junction_entries:
        print("No junctions found in this map. Something is wrong with the import.")
        return

    # Busiest junction = most distinct incoming road ids.
    best_junction_id = max(
        junction_entries,
        key=lambda jid: len({wp.road_id for wp in junction_entries[jid]})
    )
    entries = junction_entries[best_junction_id]
    distinct_roads = {wp.road_id: wp for wp in entries}
    print(f"Busiest junction id={best_junction_id}, "
          f"{len(distinct_roads)} distinct incoming roads, "
          f"{len(entries)} total incoming lanes.")

    if len(distinct_roads) < 2:
        print("Fewer than 2 distinct roads feed this junction -- cannot pick "
              "two different approach directions. Inspect the map manually.")
        return

    # Distinct road_id does NOT guarantee a different physical approach --
    # a divided carriageway often has separate road_ids per direction/lane
    # group at nearly the same location. Instead, pick the PAIR of entries
    # whose locations are farthest apart, so we get genuinely different
    # approach directions into the junction.
    road_reps = list(distinct_roads.values())
    best_pair = None
    best_dist = -1.0
    for i in range(len(road_reps)):
        for j in range(i + 1, len(road_reps)):
            loc_i = road_reps[i].transform.location
            loc_j = road_reps[j].transform.location
            d = loc_i.distance(loc_j)
            if d > best_dist:
                best_dist = d
                best_pair = (road_reps[i], road_reps[j])

    chosen = list(best_pair)
    print(f"Selected the two farthest-apart approach roads "
          f"({best_dist:.1f}m apart at their junction entry points).\n")
    print("\nCandidate spawn points (paste these into carla_bus_publisher.py):\n")
    for i, entry_wp in enumerate(chosen, start=1):
        back_wps = entry_wp.previous(APPROACH_DISTANCE_M)
        if not back_wps:
            print(f"  [Bus {i}] road_id={entry_wp.road_id}: could not walk back "
                  f"{APPROACH_DISTANCE_M}m (road too short) -- using junction entry directly.")
            spawn_wp = entry_wp
        else:
            spawn_wp = back_wps[0]
        t = spawn_wp.transform
        print(f"  [Bus {i}] road_id={entry_wp.road_id}  "
              f"location=({t.location.x:.2f}, {t.location.y:.2f}, {t.location.z:.2f})  "
              f"rotation_yaw={t.rotation.yaw:.2f}")


if __name__ == "__main__":
    main()
