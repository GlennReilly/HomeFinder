#!/usr/bin/env bash
# Integration test: loads the fixture G-NAF extract into a throwaway PostGIS and checks the filtering rules.
# Requires Docker. Usage: tests/test_load_gnaf.sh
set -euo pipefail

source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
first="$(psql_db -t -A -c "SELECT string_agg(gnaf_pid, ',' ORDER BY gnaf_pid) FROM address")"
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null   # rerun must be idempotent
second="$(psql_db -t -A -c "SELECT string_agg(gnaf_pid, ',' ORDER BY gnaf_pid) FROM address")"

[[ "$first" == "GANSW1,GANSW5,GANSW6,GANSW7" ]] || fail "expected GANSW1,GANSW5,GANSW6,GANSW7 but got '$first'"
[[ "$first" == "$second" ]] || fail "rerun changed output: '$second'"
# A same-named Hamilton elsewhere in NSW (GANSW8) is rejected because its geocode is outside the Hamilton suburb.
decision="$(psql_db -t -A -c "SELECT string_agg(locality_pid || ':' || accepted, ',' ORDER BY locality_pid) FROM locality_decision")"
[[ "$decision" == "NSW1:true,NSW3:true,NSW8:false" ]] || fail "unexpected locality decisions '$decision'"
srid="$(psql_db -t -A -c "SELECT ST_SRID(geom) FROM address LIMIT 1")"
[[ "$srid" == "7844" ]] || fail "expected SRID 7844 but got $srid"
runs="$(psql_db -t -A -c "SELECT count(*) FROM pipeline_run WHERE stage='gnaf_address' AND input_versions->>'gnaf_release'='$GNAF_RELEASE'")"
[[ "$runs" == "2" ]] || fail "expected 2 pipeline_run rows but got $runs"
# The completeness check must catch a locality that is missing from the pilot list.
psql_db -q -c "DELETE FROM pilot_locality WHERE name = 'HAMILTON'"
if "$HERE/../scripts/check-gnaf.sh" >/dev/null 2>&1; then fail "check-gnaf.sh missed an in-LGA locality outside the list"; fi
# Restoring the list must pass; a locality flagged as empty must still exist in G-NAF.
psql_db -q -c "INSERT INTO pilot_locality (name, postcode) VALUES ('HAMILTON', '2303')"
"$HERE/../scripts/check-gnaf.sh" >/dev/null 2>&1 || fail "check-gnaf.sh failed on a complete list"
psql_db -q -c "UPDATE pilot_locality SET expect_addresses = false WHERE name = 'ADAMSTOWN'"
if "$HERE/../scripts/check-gnaf.sh" >/dev/null 2>&1; then fail "check-gnaf.sh accepted an empty locality that is not in G-NAF"; fi
echo "PASS"
