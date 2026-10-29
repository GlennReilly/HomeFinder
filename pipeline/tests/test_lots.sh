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
want="GANSW1>1001:legal_parcel_id,GANSW5>1002:legal_parcel_id,GANSW6>1003:point_in_polygon"
[[ "$first" == "$want" ]] || fail "expected '$want' but got '$first'"
report="$("$HERE/../scripts/link-address-lots.sh")"
grep -q "unmatched | 1" <<< "$report" || fail "GANSW7 should be reported as unmatched: $report"
grep -q "point is outside the lot | 1" <<< "$report" || fail "GANSW5 should be flagged as outside its lot: $report"

# Dropping a suburb clears dependent lots and links, and the missing-suburb check fails when it is strict.
CHECK_MISSING_SUBURBS=fail "$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null 2>&1 \
  && fail "load-suburbs.sh should fail when pilot localities have no boundary"
[[ "$(psql_db -t -A -c "SELECT count(*) FROM lot")" == "0" ]] || fail "rebuilding suburbs should clear lots"
echo "PASS"
