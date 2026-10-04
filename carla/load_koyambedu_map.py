"""
Loads the converted Koyambedu Junction OpenDRIVE map into a running CARLA server.

Prerequisites: osm_to_xodr.py already run successfully (D:\\koyambedu_junction.xodr exists).

Usage:
    py -3.7 load_koyambedu_map.py
"""

import carla

XODR_PATH = r"D:\koyambedu_junction.xodr"


def main():
    with open(XODR_PATH, "r", encoding="utf-8") as f:
        xodr_data = f.read()

    client = carla.Client("localhost", 2000)
    client.set_timeout(300.0)  # dense real-city imports can take several minutes to mesh

    print("Generating world from Koyambedu Junction OpenDRIVE data.")
    print("This is a genuinely dense real-city import (2836 spawn points) -- ")
    print("this can take 3-5 minutes. The CARLA window may appear frozen/black")
    print("during this time; that is normal, do not close it.")
    params = carla.OpendriveGenerationParameters(
        vertex_distance=2.0,
        max_road_length=500.0,
        wall_height=0.0,       # no perimeter walls, keep it as an open road network
        additional_width=0.6,
        smooth_junctions=True,
        enable_mesh_visibility=True
    )
    world = client.generate_opendrive_world(xodr_data, params)

    print(f"Loaded map: {world.get_map().name}")
    spawn_points = world.get_map().get_spawn_points()
    print(f"Available spawn points: {len(spawn_points)}")


if __name__ == "__main__":
    main()
