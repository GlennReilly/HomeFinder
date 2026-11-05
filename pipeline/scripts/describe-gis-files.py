"""Prints the layout of the GIS and table files under a directory so it can be pasted back: shapefiles (record count, bounding box, attribute
fields and the first records, from the .shp and .dbf headers; the .prj text), GeoJSON/JSON (feature count and the first characters), CSV (first
lines) and GeoPackage (name only). Standard library only, because there is no GDAL here. Usage: python3 -I describe-gis-files.py <dir>"""
import json
import os
import struct
import sys

MAX_JSON_BYTES = 200 * 1024 * 1024
HEAD_CHARS = 600
SAMPLE_RECORDS = 3
SHP_HEADER_BYTES = 100
DBF_FIELD_BYTES = 32
DBF_TERMINATOR = 0x0D


def describe_shp(path):
    with open(path, "rb") as f:
        header = f.read(SHP_HEADER_BYTES)
    if len(header) < SHP_HEADER_BYTES or struct.unpack(">i", header[:4])[0] != 9994:
        print("    not a shapefile (bad header)")
        return
    shape_type = struct.unpack("<i", header[32:36])[0]
    xmin, ymin, xmax, ymax = struct.unpack("<4d", header[36:68])
    print(f"    shape type {shape_type}; bounding box x {xmin:.6f}..{xmax:.6f}, y {ymin:.6f}..{ymax:.6f}")


def describe_dbf(path):
    with open(path, "rb") as f:
        head = f.read(32)
        if len(head) < 32:
            print("    not a dBase file (too short)")
            return
        records, header_len, record_len = struct.unpack("<IHH", head[4:12])
        fields = []
        while True:
            raw = f.read(DBF_FIELD_BYTES)
            if len(raw) < DBF_FIELD_BYTES or raw[0] == DBF_TERMINATOR:
                break
            name = raw[:11].split(b"\0")[0].decode("latin-1")
            fields.append((name, chr(raw[11]), raw[16], raw[17]))
        print(f"    {records} records; fields (name, type, length, decimals):")
        for field in fields:
            print(f"      {field}")
        f.seek(header_len)
        for n in range(min(SAMPLE_RECORDS, records)):
            row = f.read(record_len)
            if len(row) < record_len:
                break
            pos = 1
            values = []
            for name, _, length, _ in fields:
                values.append(f"{name}={row[pos:pos + length].decode('latin-1').strip()!r}")
                pos += length
            print(f"    record {n + 1}: " + ", ".join(values))


def describe_json(path):
    size = os.path.getsize(path)
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        print("    starts: " + f.read(HEAD_CHARS).replace("\n", " "))
    if size > MAX_JSON_BYTES:
        print(f"    {size} bytes: too big to count features here")
        return
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features") if isinstance(data, dict) else None
        print(f"    {len(features)} features" if isinstance(features, list) else "    parsed, but not a FeatureCollection")
    except (ValueError, OSError) as failure:
        print(f"    could not parse: {failure}")


def describe_csv(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for _ in range(SAMPLE_RECORDS):
            line = f.readline()
            if not line:
                break
            print("    " + line.rstrip()[:400])


def main(root):
    found = False
    for folder, _, names in sorted(os.walk(root)):
        for name in sorted(names):
            lower = name.lower()
            path = os.path.join(folder, name)
            ext = os.path.splitext(lower)[1]
            if ext not in (".shp", ".dbf", ".prj", ".geojson", ".json", ".csv", ".gpkg"):
                continue
            found = True
            print(f"--- {path} ({os.path.getsize(path)} bytes)")
            if ext == ".shp":
                describe_shp(path)
            elif ext == ".dbf":
                describe_dbf(path)
            elif ext == ".prj":
                with open(path, "r", errors="replace") as f:
                    print("    " + f.read(HEAD_CHARS * 2).replace("\n", " "))
            elif ext in (".geojson", ".json"):
                describe_json(path)
            elif ext == ".csv":
                describe_csv(path)
            else:
                print("    GeoPackage: layers not listed here; paste this file name")
    if not found:
        print("No .shp, .dbf, .prj, .geojson, .json, .csv or .gpkg file found: the layout is not recognised. Paste the file list above.")


if __name__ == "__main__":
    main(sys.argv[1])
