#!/usr/bin/env bash
# Integration test: per-lot height and slope from a DEM, using a synthetic GeoTIFF made by PostGIS itself (a plane rising 0.5 m
# per 5 m pixel eastwards, so a 10 % slope), in a throwaway PostGIS. Requires Docker, jq and xxd. Usage: tests/test_terrain.sh
set -euo pipefail

TEST_PROJECT=hf-terrain-test TEST_POSTGRES_PORT=55439
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null
q() { psql_db -t -A -c "$1"; }

# A plane over the lots' extent (plus a margin), one 5 m pixel per 0.5 m rise from west to east; one pixel is no data.
mkdir -p "$WORK/dem"
psql_db -t -A -q -c "SET postgis.gdal_enabled_drivers = 'GTiff'" -c "SELECT encode(ST_AsGDALRaster(r, 'GTiff'), 'hex') FROM (
     SELECT ST_SetValue(
              ST_MapAlgebra(ST_AddBand(ST_MakeEmptyRaster(
                  ((ST_XMax(e) - ST_XMin(e) + 400) / 5)::int, ((ST_YMax(e) - ST_YMin(e) + 400) / 5)::int,
                  ST_XMin(e) - 200, ST_YMax(e) + 200, 5, -5, 0, 0, 28356), '32BF'::text, 0, -9999), 1, '32BF', '10 + [rast.x] * 0.5'),
              1, 1, 1, -9999) AS r
     FROM (SELECT ST_Extent(geom_mga) AS e FROM lot) x) y" | xxd -r -p > "$WORK/dem/plane.tif" || fail "could not make the test raster"
[[ -s "$WORK/dem/plane.tif" ]] || fail "the test raster is empty"
echo "ok: synthetic raster made"
# A lot too small to hold a pixel centre (1 m square between pixel centres, beside lot 1001) takes the pixel under it.
q "WITH r AS (SELECT ST_XMin(e) - 200 AS x0, ST_YMax(e) + 200 AS y0, ST_X(ST_Centroid(l.geom_mga)) AS cx FROM
      (SELECT ST_Extent(geom_mga) AS e FROM lot) x, lot l WHERE l.lot_id = '1001'),
   g AS (SELECT x0 + 5 * floor((cx - x0) / 5) + 0.5 AS px, y0 - 5 * 20 - 1.5 AS py FROM r)
   INSERT INTO lot (lot_id, suburb_id, geom) SELECT '9999', '9001', ST_Multi(ST_Transform(ST_MakeEnvelope(px, py, px + 1, py + 1, 7856), 7844)) FROM g" >/dev/null

report="$("$HERE/../scripts/load-dem.sh" "$WORK/dem")"
grep -q "4 of 4 lots have a height" <<< "$report" || fail "the report should count 4 of 4 lots: $report"
t() { q "SELECT $1 FROM lot_terrain WHERE lot_id = '$2'"; }

# The plane rises 0.5 m per 5 m pixel eastwards: a 10 % slope everywhere, and heights that grow to the east.
for id in 1001 1002 1003 9999; do
  slope="$(t "mean_slope_pct" "$id")"
  [[ -n "$slope" ]] || fail "lot $id has no terrain"
  awk -v s="$slope" 'BEGIN { exit !(s >= 9.5 && s <= 10.05) }' || fail "lot $id mean slope should be about 10 % but was $slope"
done
awk -v a="$(t elevation_m 1001)" -v b="$(t elevation_m 1002)" -v c="$(t elevation_m 1003)" 'BEGIN { exit !(a > b && b > c) }' || fail "heights should rise to the east"
# Lot 1001's mean height is the plane's height at its centre: 10 + 0.5 per pixel eastwards from the raster's west edge.
expected="$(q "SELECT 10 + 0.5 * ((ST_X(ST_Centroid(l.geom_mga)) - (ST_XMin(e) - 200)) / 5) FROM lot l, (SELECT ST_Extent(geom_mga) AS e FROM lot WHERE lot_id <> '9999') x WHERE l.lot_id = '1001'")"
awk -v a="$(t elevation_m 1001)" -v e="$expected" 'BEGIN { d = a - e; exit !(d > -3 && d < 3) }' || fail "lot 1001 height should be about $expected but was $(t elevation_m 1001)"
[[ "$(t pixels 9999)" == "1" ]] || fail "the tiny lot should use one pixel: $(t pixels 9999)"
[[ "$(t "dem_loaded_on" 1001)" == "$(date +%F)" || "$(t "dem_loaded_on" 1001)" == "$(date -u +%F)" ]] || fail "the load date should be kept"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_terrain z")"
"$HERE/../scripts/load-dem.sh" "$WORK/dem" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_terrain z")" ]] || fail "rerun changed the terrain"

# An empty directory and a file that is not a GeoTIFF stop with a clear message and leave the results as they were.
mkdir -p "$WORK/empty" "$WORK/bad"
if "$HERE/../scripts/load-dem.sh" "$WORK/empty" >/dev/null 2>"$WORK/err"; then fail "an empty directory should fail"; fi
grep -q "Missing GeoTIFF" "$WORK/err" || fail "the failure should say the files are missing: $(cat "$WORK/err")"
echo "not a raster" > "$WORK/bad/x.tif"
if "$HERE/../scripts/load-dem.sh" "$WORK/bad" >/dev/null 2>"$WORK/err"; then fail "a bad file should fail"; fi
grep -q "Could not read" "$WORK/err" || fail "the failure should name the bad file: $(cat "$WORK/err")"
[[ "$before" == "$(q "SELECT md5(string_agg(z::text, '|' ORDER BY lot_id)) FROM lot_terrain z")" ]] || fail "a failed load must leave the terrain as it was"

# The download script against a stand-in WCS (capabilities, a coverage description and GeoTIFFs; one square is refused).
cat > "$WORK/fake_wcs.py" <<'PY'
import http.server, sys, urllib.parse
tif = open(sys.argv[2], "rb").read()
log = open(sys.argv[3], "a")
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        q = {k: v[0] for k, v in urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).items()}
        log.write(q.get("request", "") + " " + q.get("coverage", "") + " " + q.get("format", "") + " " + q.get("width", "") + " " + q.get("crs", "") + " " + q.get("bbox", "") + "\n"); log.flush()
        if q.get("request") == "GetCapabilities":
            body, kind = b"<wcs:WCS_Capabilities xmlns:wcs='x'><wcs:ContentMetadata><wcs:CoverageOfferingBrief><wcs:name>dem5m</wcs:name></wcs:CoverageOfferingBrief></wcs:ContentMetadata></wcs:WCS_Capabilities>", "text/xml"
        elif q.get("request") == "DescribeCoverage":
            body, kind = b"<CoverageDescription><CoverageOffering><supportedFormats><formats>PNG</formats><formats>GeoTIFF</formats></supportedFormats></CoverageOffering></CoverageDescription>", "text/xml"
        elif sys.argv[4] == "refuse":
            body, kind = b"<ServiceExceptionReport>Too big</ServiceExceptionReport>", "text/xml"
        else:
            body, kind = tif, "image/tiff"
        self.send_response(200); self.send_header("Content-Type", kind); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
http.server.HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
PY
serve() { python3 "$WORK/fake_wcs.py" 55441 "$WORK/dem/plane.tif" "$WORK/wcs.log" "$1" & WCS_PID=$!; sleep 1; }
export DEM_REQUEST_M=5000 DEM_WCS_URL="http://127.0.0.1:55441/wcs"
serve ok
"$HERE/../scripts/download-dem.sh" "$WORK/dl" > "$WORK/dl.out" || { kill "$WCS_PID"; fail "the download failed: $(cat "$WORK/dl.out")"; }
kill "$WCS_PID"
grep -q "coverage dem5m as GeoTIFF, 1000x1000 pixel requests" "$WORK/dl.out" || fail "the coverage and format should be discovered: $(cat "$WORK/dl.out")"
files="$(find "$WORK/dl" -name '*.tif' | wc -l)"
[[ "$files" -ge 2 ]] || fail "expected a file per square touching a suburb but got $files"
grep -q "^GetCoverage dem5m GeoTIFF 1000 EPSG:28356 3[0-9]*,[0-9]*,3[0-9]*,[0-9]*$" "$WORK/wcs.log" || fail "the GetCoverage request is wrong: $(grep GetCoverage "$WORK/wcs.log" | head -1)"
# Squares already on disk are not fetched again.
: > "$WORK/wcs.log"; serve ok
"$HERE/../scripts/download-dem.sh" "$WORK/dl" > /dev/null; kill "$WCS_PID"
! grep -q GetCoverage "$WORK/wcs.log" || fail "existing files should not be downloaded again"
# A service error is shown, and no partial file is left.
serve refuse
if "$HERE/../scripts/download-dem.sh" "$WORK/dl2" >/dev/null 2>"$WORK/err"; then kill "$WCS_PID"; fail "a refused request should fail"; fi
kill "$WCS_PID"
grep -q "did not return a GeoTIFF" "$WORK/err" && grep -q "Too big" "$WORK/err" || fail "the service's message should be shown: $(cat "$WORK/err")"
[[ -z "$(find "$WORK/dl2" -name '*.part' 2>/dev/null)" ]] || fail "no partial file should remain"
echo "PASS"
