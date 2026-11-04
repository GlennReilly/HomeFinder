#!/usr/bin/env bash
# Integration test: per-lot height above nearest drainage (ADR 0040) from a synthetic DEM made by PostGIS itself: a V valley down the
# column of lot 1001, its walls rising 0.5 m per 5 m pixel and its floor falling 0.01 m a row to the south, in a throwaway PostGIS.
# Runs the real script and the Kotlin tool through Gradle. Requires Docker, xxd and a JDK 17. Usage: tests/test_drainage.sh
set -euo pipefail

TEST_PROJECT=hf-drainage-test TEST_POSTGRES_PORT=55440
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null
q() { psql_db -t -A -c "$1" </dev/null; }

PX=0.000055063478; PY=0.000051601233
N=121; MID=61   # a square raster of N pixels with lot 1001's point in the middle pixel (MID), so the valley runs down column MID
OFFSETS=(10 25 -20 -40)   # tiny extra lots this many pixels east (+) or west (-) of lot 1001, on its row
read -r X1 Y1 < <(q "SELECT ST_X(ST_PointOnSurface(geom)) || ' ' || ST_Y(ST_PointOnSurface(geom)) FROM lot WHERE lot_id = '1001'" | tr '|' ' ')
for offset in "${OFFSETS[@]}"; do
  q "INSERT INTO lot (lot_id, suburb_id, geom) SELECT '80${offset#-}$([[ $offset -lt 0 ]] && echo 0 || echo 1)', '9001',
       ST_Multi(ST_MakeEnvelope($X1 + $offset * $PX, $Y1, $X1 + $offset * $PX + 0.00001, $Y1 + 0.00001, 7844))" >/dev/null
done

mkdir -p "$WORK/dem"
psql_db -t -A -q -c "SET postgis.gdal_enabled_drivers = 'GTiff'" -c "SELECT encode(ST_AsGDALRaster(r, 'GTiff'), 'hex') FROM (
     SELECT ST_MapAlgebra(ST_AddBand(ST_MakeEmptyRaster($N, $N, $X1 - ($MID - 0.5) * $PX, $Y1 + ($MID - 0.5) * $PY, $PX, -$PY, 0, 0, 4283), '32BF'::text, 0, -9999),
                          1, '32BF', '10 + abs([rast.x] - $MID) * 0.5 + ($N - [rast.y]) * 0.01') AS r) y" | xxd -r -p > "$WORK/dem/valley.tif" || fail "could not make the test raster"
[[ -s "$WORK/dem/valley.tif" ]] || fail "the test raster is empty"
echo "ok: synthetic valley made"

# Only the valley floor may count as a stream: a wall pixel drains at most the part of its row beyond it (under half the raster), while
# the floor collects whole rows. A stream of 70 pixels, less half a pixel so rounding up gives exactly that many.
CELLS=70
HA="$(awk -v c="$CELLS" -v lat="$Y1" 'BEGIN { pi = 3.14159265358979; d = 111195;
  printf "%.5f", (c - 0.5) * (5.5066079295e-5 * d * cos(lat * pi / 180)) * (5.1601233e-5 * d) / 10000 }')"
echo "stream size $HA ha ($CELLS pixels)"

report="$("$HERE/../scripts/load-drainage.sh" "$WORK/dem" "$HA")"
echo "$report"
grep -q "lots have a height above drainage" <<< "$report" || fail "the report should count the lots: $report"
# Lot 1001 and the four extra lots lie on the raster; the other fixture lots are kilometres away and have no DEM.
expected=$(( 1 + ${#OFFSETS[@]} ))
total="$expected"
[[ "$(q "SELECT count(*) FROM lot_drainage")" == "$expected" ]] || fail "expected $expected lots with a result but found $(q "SELECT count(*) FROM lot_drainage")"
[[ "$(q "SELECT count(*) FROM lot_drainage WHERE lot_id IN ('1002', '1003')")" == "0" ]] || fail "lots outside the DEM should have no result"

# Lot 1001 sits on the valley floor (the floor itself only falls 0.01 m a row); every other lot stands about 0.5 m per pixel from the
# floor, give or take the pixel its point falls in and where its water meets the stream.
h() { q "SELECT height_above_drainage_m FROM lot_drainage WHERE lot_id = '$1'"; }
awk -v v="$(h 1001)" 'BEGIN { exit !(v >= 0 && v < 0.6) }' || fail "lot 1001 should sit on the valley floor but is $(h 1001) m above drainage"
checked=0
while IFS='|' read -r id offset; do
  awk -v v="$(h "$id")" -v o="$offset" 'BEGIN { if (o < 0) o = -o; e = 0.5 * o; d = v - e; exit !(d > -0.9 && d < 0.9) }' \
    || fail "lot $id is $offset pixels from the valley floor so should stand about half that many metres above it, but is $(h "$id") m"
  checked=$((checked + 1))
done < <(q "SELECT lot_id || '|' || round(((ST_X(ST_PointOnSurface(geom)) - $X1) / $PX)::numeric, 1) FROM lot WHERE lot_id LIKE '80%' ORDER BY lot_id")
[[ "$checked" -eq "${#OFFSETS[@]}" ]] || fail "expected to check ${#OFFSETS[@]} lots but checked $checked"
echo "ok: $checked lots stand at their expected height above the valley floor"

# The size of the stream a lot drains to is reported, and is never smaller than the stream threshold.
awk -v c="$(q "SELECT min(stream_catchment_ha) FROM lot_drainage")" -v h="$HA" 'BEGIN { exit !(c >= h) }' || fail "a stream is at least $HA ha but one lot reports $(q "SELECT min(stream_catchment_ha) FROM lot_drainage")"
[[ "$(q "SELECT count(*) FROM lot_drainage WHERE computed_on = current_date OR computed_on = (now() AT TIME ZONE 'UTC')::date")" == "$total" ]] || fail "the load date should be kept"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_drainage z")"
"$HERE/../scripts/load-drainage.sh" "$WORK/dem" "$HA" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_drainage z")" ]] || fail "rerun changed the results"

# A missing directory and a directory with no GeoTIFFs stop with a clear message and leave the results as they were.
if "$HERE/../scripts/load-drainage.sh" "$WORK/nowhere" >/dev/null 2>"$WORK/err"; then fail "a missing directory should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the directory is missing: $(cat "$WORK/err")"
mkdir -p "$WORK/empty"
if "$HERE/../scripts/load-drainage.sh" "$WORK/empty" >/dev/null 2>"$WORK/err"; then fail "an empty directory should fail"; fi
grep -q "Missing GeoTIFF" "$WORK/err" || fail "the failure should say the files are missing: $(cat "$WORK/err")"
[[ "$before" == "$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_drainage z")" ]] || fail "a failed load must leave the results as they were"

echo "PASS: drainage"
