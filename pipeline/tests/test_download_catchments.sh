#!/usr/bin/env bash
# Test of download-catchments.sh against a local file:// "server": a zip holding a tiny hand-made shapefile (.shp header, .dbf, .prj) is
# downloaded, unpacked and its layout printed, a non-zip is refused, and an existing download is kept. No database or pipeline/.env needed.
# Usage: tests/test_download_catchments.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail() { echo "FAIL: $1" >&2; exit 1; }
export RAW_DIR="$WORK/raw"

python3 -I - "$WORK" <<'PY'
import struct, sys, zipfile
w = sys.argv[1]
# A .shp with only its 100-byte header: file code 9994, shape type 5 (polygon), box x 150..152, y -34..-32.
shp = struct.pack(">i", 9994) + b"\0" * 20 + struct.pack(">i", 50) + struct.pack("<ii", 1000, 5) + struct.pack("<8d", 150.0, -34.0, 152.0, -32.0, 0, 0, 0, 0)
# A .dbf with two fields (SCHOOL_CODE C 6, LEVEL C 10) and two records.
fields = [(b"SCHOOL_CODE", 6), (b"LEVEL", 10)]
header_len = 32 + 32 * len(fields) + 1
record_len = 1 + sum(n for _, n in fields)
dbf = struct.pack("<BBBBIHH20x", 3, 126, 11, 1, 2, header_len, record_len)
for name, n in fields:
    dbf += name.ljust(11, b"\0") + b"C" + b"\0" * 4 + bytes([n, 0]) + b"\0" * 14
dbf += b"\x0d"
for code, level in [("1234", "PRIMARY"), ("5678", "SECONDARY")]:
    dbf += b" " + code.ljust(6).encode() + level.ljust(10).encode()
with zipfile.ZipFile(w + "/good.zip", "w") as z:
    z.writestr("primary/primary.shp", shp)
    z.writestr("primary/primary.dbf", dbf)
    z.writestr("primary/primary.prj", 'GEOGCS["GCS_GDA_1994"]')
open(w + "/page.html", "w").write("<html>not a zip</html>")
PY

out="$(CATCHMENTS_URL="file://$WORK/good.zip" "$HERE/../scripts/download-catchments.sh" "$WORK/dest")"
echo "$out"
grep -q "primary/primary.shp" <<< "$out" || fail "should list the shapefile: $out"
grep -q "bounding box x 150.000000..152.000000, y -34.000000..-32.000000" <<< "$out" || fail "should print the bounding box: $out"
grep -q "2 records" <<< "$out" || fail "should print the record count: $out"
grep -q "('SCHOOL_CODE', 'C', 6, 0)" <<< "$out" || fail "should print the fields: $out"
grep -q "SCHOOL_CODE='1234', LEVEL='PRIMARY'" <<< "$out" || fail "should print the first record: $out"
grep -q "GCS_GDA_1994" <<< "$out" || fail "should print the projection: $out"
[[ -f "$WORK/dest/catchments.zip.sha256" ]] || fail "the checksum should be recorded"

out="$(CATCHMENTS_URL="file://$WORK/page.html" "$HERE/../scripts/download-catchments.sh" "$WORK/dest")"
grep -q "already downloaded" <<< "$out" || fail "an existing download should be kept: $out"

if CATCHMENTS_URL="file://$WORK/page.html" "$HERE/../scripts/download-catchments.sh" "$WORK/bad" >/dev/null 2>"$WORK/err"; then fail "a non-zip should fail"; fi
grep -q "Not a zip file" "$WORK/err" || fail "the failure should say it is not a zip: $(cat "$WORK/err")"
[[ ! -e "$WORK/bad/catchments.zip" ]] || fail "a refused download must not leave a zip behind"

# A zip with nothing recognisable says so.
python3 -I - "$WORK" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1] + "/odd.zip", "w") as z:
    z.writestr("readme.txt", "hello")
PY
out="$(CATCHMENTS_URL="file://$WORK/odd.zip" "$HERE/../scripts/download-catchments.sh" "$WORK/odd")"
grep -q "layout is not recognised" <<< "$out" || fail "an unrecognised zip should say so: $out"
echo "PASS: catchments download"
