"""
Converts the exported Koyambedu Junction OSM data into OpenDRIVE (.xodr),
the road-network format CARLA actually loads.

Usage:
    py -3.7 osm_to_xodr.py
"""

import carla

INPUT_OSM = r"D:\koyambedu_junction.osm"
OUTPUT_XODR = r"D:\koyambedu_junction.xodr"


def main():
    with open(INPUT_OSM, "r", encoding="utf-8") as f:
        osm_data = f.read()

    settings = carla.Osm2OdrSettings()
    settings.generate_traffic_lights = True
    # Keep sidewalks/traffic lights on: we want a realistic junction for the
    # accident/reroute demo, not a bare road skeleton.
    settings.set_osm_way_types(["motorway", "motorway_link", "trunk", "trunk_link",
                                 "primary", "primary_link", "secondary", "secondary_link",
                                 "tertiary", "tertiary_link", "residential", "unclassified"])

    print("Converting OSM -> OpenDRIVE (this can take a minute for a busy junction)...")
    xodr_data = carla.Osm2Odr.convert(osm_data, settings)

    with open(OUTPUT_XODR, "w", encoding="utf-8") as f:
        f.write(xodr_data)

    print(f"Done. Wrote {OUTPUT_XODR} ({len(xodr_data)} bytes)")


if __name__ == "__main__":
    main()
