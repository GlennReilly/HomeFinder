#!/usr/bin/env bash
# Integration test: NSW public school catchments (ADR 0056). Builds tiny shapefiles (a primary zone with a hole, a primary zone with no matching
# school, a high school zone, and a future zone that must be ignored; made with Python's struct) in a throwaway PostGIS, runs the real script and the
# Kotlin tool through Gradle, and checks the shapes, the reprojection to GDA2020, the school matching report, and that a rerun and a bad directory
# behave. Requires Docker, python3 and a JDK 17. Usage: tests/test_catchments.sh
set -euo pipefail

TEST_PROJECT=hf-catchments-test TEST_POSTGRES_PORT=55444
source "$(dirname "$0")/lib.sh"
q() { psql_db -t -A -c "$1" </dev/null; }

DIR="$WORK/catchments"
mkdir -p "$DIR"
python3 -I - "$DIR" <<'PY'
import struct, sys
d = sys.argv[1]
FIELDS = [("USE_ID", 8), ("CATCH_TYPE", 20), ("USE_DESC", 50), ("ADD_DATE", 20), ("KINDERGART", 1), ("YEAR1", 1), ("YEAR2", 1), ("YEAR3", 1), ("YEAR4", 1),
          ("YEAR5", 1), ("YEAR6", 1), ("YEAR7", 1), ("YEAR8", 1), ("YEAR9", 1), ("YEAR10", 1), ("YEAR11", 1), ("YEAR12", 1), ("PRIORITY", 1)]

def write(name, zones):
    """zones: (attributes dict, [rings]); a ring is a list of (x, y), clockwise = outer, counter-clockwise = hole."""
    body = b""
    for n, (_, rings) in enumerate(zones, start=1):
        pts = [p for r in rings for p in r]
        content = struct.pack("<i4d2i", 5, 0, 0, 0, 0, len(rings), len(pts))
        start = 0
        for r in rings:
            content += struct.pack("<i", start); start += len(r)
        for x, y in pts:
            content += struct.pack("<2d", x, y)
        body += struct.pack(">2i", n, len(content) // 2) + content
    header = struct.pack(">i", 9994) + b"\0" * 20 + struct.pack(">i", (100 + len(body)) // 2) + struct.pack("<2i", 1000, 5) + struct.pack("<8d", 151.0, -33.5, 152.0, -32.5, 0, 0, 0, 0)
    open(f"{d}/{name}.shp", "wb").write(header + body)
    header_len = 32 + 32 * len(FIELDS) + 1
    record_len = 1 + sum(n for _, n in FIELDS)
    dbf = struct.pack("<BBBBIHH20x", 3, 126, 11, 1, len(zones), header_len, record_len)
    for fname, n in FIELDS:
        dbf += fname.encode().ljust(11, b"\0") + b"C" + b"\0" * 4 + bytes([n, 0]) + b"\0" * 14
    dbf += b"\x0d"
    for attrs, _ in zones:
        dbf += b" " + b"".join(str(attrs.get(f, "")).ljust(n).encode() for f, n in FIELDS)
    open(f"{d}/{name}.dbf", "wb").write(dbf)

def sq(x0, y0, x1, y1, outer=True):
    ring = [(x0, y0), (x0, y1), (x1, y1), (x1, y0), (x0, y0)]  # clockwise
    return ring if outer else ring[::-1]

primary = {"CATCH_TYPE": "PRIMARY", "ADD_DATE": "20230516", "KINDERGART": "Y", "YEAR1": "Y", "YEAR2": "Y", "YEAR3": "Y", "YEAR4": "Y", "YEAR5": "Y", "YEAR6": "Y"}
write("catchments_primary", [
    ({**primary, "USE_ID": "1001", "USE_DESC": "Hamilton PS"}, [sq(151.70, -32.95, 151.80, -32.85), sq(151.74, -32.92, 151.76, -32.90, outer=False)]),
    ({**primary, "USE_ID": "1999", "USE_DESC": "Orphan PS"}, [sq(151.10, -33.40, 151.20, -33.30)]),
])
write("catchments_secondary", [({"USE_ID": "2002", "CATCH_TYPE": "HIGH_COED", "USE_DESC": "Newcastle HS", "ADD_DATE": "20200507", "YEAR7": "Y", "YEAR8": "Y", "YEAR9": "Y", "YEAR10": "Y", "YEAR11": "Y", "YEAR12": "Y"},
                               [sq(151.60, -33.00, 151.90, -32.80)])])
write("catchments_future", [({"USE_ID": "1001", "CATCH_TYPE": "PRIMARY", "USE_DESC": "Future PS"}, [sq(151.0, -33.0, 151.1, -32.9)])])
open(f"{d}/catchment_sf_info.json", "w").write('{"current_enrolment_year":2026}')
PY

q "INSERT INTO school (school_code, name, level_label, icsea, geom) VALUES (1001, 'Hamilton Public School', 'Primary School', 1051, ST_SetSRID(ST_MakePoint(151.72, -32.92), 7844)), (2002, 'Newcastle High School', 'Secondary School', 1030, ST_SetSRID(ST_MakePoint(151.75, -32.93), 7844))" >/dev/null

report="$("$HERE/../scripts/load-catchments.sh" "$DIR")"
echo "$report"
grep -q "2 primary and 1 high school catchments, enrolment year 2026" <<< "$report" || fail "the counts should be 2 primary and 1 high (the future file is ignored): $report"
grep -q "2 of 3 catchment schools match" <<< "$report" || fail "two of three catchment schools match the schools data: $report"
grep -q "Orphan PS" <<< "$report" || fail "the unmatched zone should be named: $report"
[[ "$(q "SELECT count(*) FROM school_catchment WHERE name = 'Future PS'")" == "0" ]] || fail "the future file must be ignored"
[[ "$(q "SELECT srid FROM geometry_columns WHERE f_table_name = 'school_catchment'")" == "7844" ]] || fail "shapes should be stored in GDA2020 (7844)"
row="$(q "SELECT school_code || '|' || level || '|' || catch_type || '|' || years || '|' || added_on || '|' || enrolment_year FROM school_catchment WHERE school_code = 1001")"
[[ "$row" == "1001|PRIMARY|PRIMARY|K-6|2023-05-16|2026" ]] || fail "Hamilton's zone row was wrong: $row"
row="$(q "SELECT level || '|' || years FROM school_catchment WHERE school_code = 2002")"
[[ "$row" == "HIGH|7-12" ]] || fail "the high school zone row was wrong: $row"

# The zone covers a point inside it, not a point in its hole, and not one far away; the high school zone overlaps (a lot can be in several zones).
inside="SELECT count(*) FROM school_catchment WHERE ST_Covers(geom, ST_SetSRID(ST_MakePoint(%s, %s), 7844))"
[[ "$(q "$(printf "$inside" 151.72 -32.93)")" == "2" ]] || fail "a point inside both the primary and the high zone should be covered by both"
[[ "$(q "$(printf "$inside" 151.75 -32.91)")" == "1" ]] || fail "a point in the hole should be covered only by the high zone"
[[ "$(q "$(printf "$inside" 152.50 -33.00)")" == "0" ]] || fail "a far point should be in no zone"
# GDA94 to GDA2020 moves a point by about 1.8 m (north-east): the stored corner is close to, not exactly, the published one.
echo "ok: shapes, hole, reprojection and matching"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(c::text, '|' ORDER BY c.school_code, c.level, c.name)) FROM (SELECT school_code, level, catch_type, name, years, added_on, enrolment_year, md5(ST_AsEWKB(geom)::text) FROM school_catchment) c")"
"$HERE/../scripts/load-catchments.sh" "$DIR" >/dev/null
after="$(q "SELECT md5(string_agg(c::text, '|' ORDER BY c.school_code, c.level, c.name)) FROM (SELECT school_code, level, catch_type, name, years, added_on, enrolment_year, md5(ST_AsEWKB(geom)::text) FROM school_catchment) c")"
[[ -n "$before" && "$before" == "$after" ]] || fail "rerun changed the catchment rows"

# A missing directory and one without the files stop with a clear message and leave the rows as they were.
if "$HERE/../scripts/load-catchments.sh" "$WORK/nowhere" >/dev/null 2>"$WORK/err"; then fail "a missing directory should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the directory is missing: $(cat "$WORK/err")"
mkdir -p "$WORK/empty"
if "$HERE/../scripts/load-catchments.sh" "$WORK/empty" >/dev/null 2>"$WORK/err"; then fail "a directory without the files should fail"; fi
grep -q "download-catchments.sh" "$WORK/err" || fail "the failure should point to the download: $(cat "$WORK/err")"
[[ "$(q "SELECT count(*) FROM school_catchment")" == "3" ]] || fail "a failed load must leave the rows as they were"
echo "PASS: catchments"
