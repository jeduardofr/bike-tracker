#!/usr/bin/env python3
"""
Convert a Google Earth Pro LineString KML to Android Emulator-compatible GPX.

Usage:
    python3 convert_route.py [input.kml] [output.gpx] [--speed KMH] [--start DATETIME]

Defaults:
    input   : route.kml
    output  : route_emulator.gpx
    speed   : 21.6 km/h  (~6 m/s)
    start   : 2026-03-30T08:00:00Z
"""

import re
import math
import argparse
from datetime import datetime, timedelta, timezone


def haversine_meters(lng1, lat1, lng2, lat2):
    R = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def parse_linestring_coords(kml_text):
    match = re.search(r"<coordinates>(.*?)</coordinates>", kml_text, re.DOTALL)
    if not match:
        raise ValueError("No <coordinates> found in KML")
    raw = match.group(1).strip()
    points = []
    for token in raw.split():
        parts = token.split(",")
        if len(parts) >= 2:
            lng, lat = float(parts[0]), float(parts[1])
            alt = float(parts[2]) if len(parts) >= 3 else 0.0
            points.append((lng, lat, alt))
    return points


def build_gpx(points, start_dt, speed_kmh):
    speed_mps = speed_kmh * 1000 / 3600
    timestamps = [start_dt]
    for i in range(1, len(points)):
        dist = haversine_meters(
            points[i - 1][0], points[i - 1][1],
            points[i][0],     points[i][1]
        )
        seconds = dist / speed_mps if speed_mps > 0 else 1.0
        timestamps.append(timestamps[-1] + timedelta(seconds=seconds))

    trkpts = []
    for (lng, lat, alt), ts in zip(points, timestamps):
        trkpts.append(
            f'      <trkpt lat="{lat}" lon="{lng}">\n'
            f'        <ele>{alt}</ele>\n'
            f'        <time>{ts.strftime("%Y-%m-%dT%H:%M:%SZ")}</time>\n'
            f'      </trkpt>'
        )

    total_dist_km = sum(
        haversine_meters(points[i-1][0], points[i-1][1], points[i][0], points[i][1])
        for i in range(1, len(points))
    ) / 1000
    total_time = timestamps[-1] - timestamps[0]

    print(f"Points    : {len(points)}")
    print(f"Distance  : {total_dist_km:.2f} km")
    print(f"Duration  : {str(total_time).split('.')[0]}  (at {speed_kmh} km/h)")
    print(f"Start     : {timestamps[0].strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print(f"End       : {timestamps[-1].strftime('%Y-%m-%dT%H:%M:%SZ')}")

    trkpts_xml = "\n".join(trkpts)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="convert_route"
     xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Bike Route</name>
    <trkseg>
{trkpts_xml}
    </trkseg>
  </trk>
</gpx>
"""


def main():
    parser = argparse.ArgumentParser(description="Google Earth KML → GPX for Android Emulator")
    parser.add_argument("input",  nargs="?", default="route.kml")
    parser.add_argument("output", nargs="?", default="route_emulator.gpx")
    parser.add_argument("--speed", type=float, default=21.6, help="Average speed in km/h (default: 21.6 — ~6 m/s)")
    parser.add_argument("--start", default="2026-03-30T08:00:00Z", help="Start datetime ISO 8601 UTC")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        kml_text = f.read()

    points = parse_linestring_coords(kml_text)
    start_dt = datetime.strptime(args.start, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    gpx = build_gpx(points, start_dt, args.speed)

    with open(args.output, "w", encoding="utf-8") as f:
        f.write(gpx)

    print(f"Written   : {args.output}")


if __name__ == "__main__":
    main()
