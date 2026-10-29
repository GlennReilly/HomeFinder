#!/usr/bin/env bash
# Integration test: suburbs, lots and the address-to-lot link, using fixtures and a throwaway PostGIS.
# Requires Docker and jq. Usage: tests/test_lots.sh
set -euo pipefail

TEST_PROJECT=hf-lots-test TEST_POSTGRES_PORT=55434
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null
# Loading suburbs also precomputes the API tile data: one simplified geometry per served zoom (5 to 14) and a label each.
tile_rows="$(psql_db -t -A -c "SELECT (SELECT count(*) FROM suburb_tile_geometry) || '/' || (SELECT count(*) FROM suburb_label) || '/' || (SELECT min(zoom) || '-' || max(zoom) FROM suburb_tile_geometry)")"
[[ "$tile_rows" == "20/2/5-14" ]] || fail "expected 20 tile geometries, 2 labels, zooms 5-14 but got '$tile_rows'"
"$HERE/../scripts/refresh-suburb-tiles.sh" >/dev/null   # refreshing again must give the same rows
[[ "$tile_rows" == "$(psql_db -t -A -c "SELECT (SELECT count(*) FROM suburb_tile_geometry) || '/' || (SELECT count(*) FROM suburb_label) || '/' || (SELECT min(zoom) || '-' || max(zoom) FROM suburb_tile_geometry)")" ]] || fail "refresh changed the tile data"
lots="$(psql_db -t -A -c "SELECT string_agg(lot_id || ':' || suburb_id, ',' ORDER BY lot_id) FROM lot")"
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null   # rerun must be idempotent
lots2="$(psql_db -t -A -c "SELECT string_agg(lot_id || ':' || suburb_id, ',' ORDER BY lot_id) FROM lot")"

# L1 appears in two page files (deduplicated); L4 is outside every pilot suburb (excluded).
[[ "$lots" == "1001:9001,1002:9002,1003:9002" ]] || fail "unexpected lots '$lots'"
[[ "$lots" == "$lots2" ]] || fail "rerun changed lots: '$lots2'"

key="$(psql_db -t -A -c "SELECT parcel_key FROM lot WHERE lot_id = '1001'")"
[[ "$key" == "1/100" ]] || fail "expected parcel_key 1/100 but got '$key'"
area_ok="$(psql_db -t -A -c "SELECT bool_and(area_m2 BETWEEN 5000 AND 15000) FROM lot WHERE lot_id = '1001'")"
[[ "$area_ok" == "t" ]] || fail "lot area in MGA zone 56 is out of range"
srid="$(psql_db -t -A -c "SELECT ST_SRID(geom) || '/' || ST_SRID(geom_mga) FROM lot LIMIT 1")"
[[ "$srid" == "7844/7856" ]] || fail "expected SRIDs 7844/7856 but got $srid"

"$HERE/../scripts/link-address-lots.sh" >/dev/null
links() { psql_db -t -A -c "SELECT string_agg(gnaf_pid || '>' || lot_id || ':' || method, ',' ORDER BY gnaf_pid) FROM address_lot"; }
first="$(links)"
"$HERE/../scripts/link-address-lots.sh" >/dev/null
[[ "$first" == "$(links)" ]] || fail "rerun changed links"
want="GANSW1>1001:legal_parcel_id,GANSW5>1002:legal_parcel_id,GANSW6>1003:point_in_polygon,GANSW9>1001:nearest_lot"
[[ "$first" == "$want" ]] || fail "expected '$want' but got '$first'"
report="$("$HERE/../scripts/link-address-lots.sh")"
dist="$(psql_db -t -A -c "SELECT round(distance_m) FROM address_lot WHERE gnaf_pid = 'GANSW9'")"
[[ "$dist" -ge 5 && "$dist" -le 30 ]] || fail "expected GANSW9 nearest-lot distance between 5 and 30 m but got '$dist'"
grep -q "nearest_lot (low confidence) | 1" <<< "$report" || fail "nearest_lot count missing: $report"
grep -q "unmatched | 1" <<< "$report" || fail "GANSW7 should be reported as unmatched: $report"
grep -q "point is outside the lot | 1" <<< "$report" || fail "GANSW5 should be flagged as outside its lot: $report"

# Dropping a suburb clears dependent lots and links, and the missing-suburb check fails when it is strict.
CHECK_MISSING_SUBURBS=fail "$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null 2>&1 \
  && fail "load-suburbs.sh should fail when pilot localities have no boundary"
[[ "$(psql_db -t -A -c "SELECT count(*) FROM lot")" == "0" ]] || fail "rebuilding suburbs should clear lots"
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb_tile_geometry")" == "20" ]] || fail "rebuilding suburbs should rebuild the tile data"
echo "PASS"
