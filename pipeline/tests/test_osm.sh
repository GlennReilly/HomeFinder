#!/usr/bin/env bash
# Integration test: OpenStreetMap features and the per-lot nearest beach, parks and shops, from fixtures and a
# throwaway PostGIS. Requires Docker and jq. Usage: tests/test_osm.sh
set -euo pipefail

TEST_PROJECT=hf-osm-test TEST_POSTGRES_PORT=55437
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null

report="$("$HERE/../scripts/load-osm.sh" "$HERE/fixtures/osm")"
q() { psql_db -t -A -c "$1"; }

# Kept: the ocean beach (the lake beach is not near the coastline), the two parks and the oval over 0.5 ha (the 410 m²
# park and the unclosed line are not parks), both supermarkets and the mall (the convenience store is not queried).
counts="$(q "SELECT string_agg(kind || '=' || n, ',' ORDER BY kind) FROM (SELECT kind, count(*) n FROM osm_feature GROUP BY kind) t")"
[[ "$counts" == "beach=1,mall=1,park=3,supermarket=2" ]] || fail "expected beach=1,mall=1,park=3,supermarket=2 but got '$counts'"
grep -q "park | 3" <<< "$report" || fail "report should list the feature counts: $report"
[[ "$(q "SELECT osm_base FROM osm_import")" == "2026-10-29 22:00:00+00" ]] || fail "osm_base should be the answer's timestamp: $(q "SELECT osm_base FROM osm_import")"

# A relation becomes the area its member lines enclose, hole included: the ring is about 41,500 m² less a 1,660 m² hole.
ring="$(q "SELECT round(area_m2) FROM osm_feature WHERE osm_id = 7003")"
[[ "$ring" -ge 38000 && "$ring" -le 41000 ]] || fail "ring park area should be about 39,800 m² but was $ring"

# Distances from lot 1001's representative point (151.7500, -32.9200), within 3 % of the straight-line figures.
near() { q "SELECT round(distance_m) FROM lot_nearest WHERE lot_id = '1001' AND kind = '$1' AND rank = $2"; }
within() { [[ "$1" -ge "$(( $2 * 97 / 100 ))" && "$1" -le "$(( $2 * 103 / 100 ))" ]] || fail "$3: expected about $2 m but got $1"; }
within "$(near beach 1)" 1794 "nearest ocean beach"
within "$(near park 1)" 187 "nearest park"
within "$(near park 2)" 374 "second park"
within "$(near park 3)" 935 "third park"
within "$(near supermarket 1)" 1112 "nearest supermarket"
within "$(near mall 1)" 561 "nearest mall"
[[ "$(q "SELECT name FROM lot_nearest WHERE lot_id = '1001' AND kind = 'park' AND rank = 1")" == "Big Park" ]] || fail "nearest park should be Big Park"
[[ "$(q "SELECT name FROM lot_nearest WHERE lot_id = '1001' AND kind = 'beach'")" == "Ocean Beach" ]] || fail "the lake beach must not be chosen"

# Park area within 800 m: Big Park (about 10,400 m²) and Ring Park (about 39,800 m²) lie inside, the oval (935 m away) outside.
area="$(q "SELECT round(park_area_radius_m2) FROM lot_amenity WHERE lot_id = '1001'")"
[[ "$area" -ge 48000 && "$area" -le 52500 ]] || fail "park area within 800 m should be about 50,000 m² but was $area"

# Every lot gets every kind (nearest first), and the lot count matches.
[[ "$(q "SELECT count(DISTINCT lot_id) FROM lot_nearest")" == "$(q "SELECT count(*) FROM lot")" ]] || fail "every lot should have nearest features"
[[ "$(q "SELECT count(*) FROM lot_nearest WHERE lot_id = '1001'")" == "6" ]] || fail "lot 1001 should have 1 beach, 3 parks, 1 supermarket and 1 mall"
[[ "$(q "SELECT count(*) FROM lot_amenity")" == "$(q "SELECT count(*) FROM lot")" ]] || fail "every lot should have a park area"

# Reloading gives identical rows.
before="$(q "SELECT md5(string_agg(n::text, '|' ORDER BY lot_id, kind, rank)) FROM lot_nearest n")$(q "SELECT md5(string_agg(a::text, '|' ORDER BY lot_id)) FROM lot_amenity a")"
"$HERE/../scripts/load-osm.sh" "$HERE/fixtures/osm" >/dev/null
after="$(q "SELECT md5(string_agg(n::text, '|' ORDER BY lot_id, kind, rank)) FROM lot_nearest n")$(q "SELECT md5(string_agg(a::text, '|' ORDER BY lot_id)) FROM lot_amenity a")"
[[ "$before" == "$after" ]] || fail "rerun changed the proximity rows"

# A missing file stops the load with a clear message.
mv "$HERE/fixtures/osm/park.json" "$WORK/park.json.bak"
if "$HERE/../scripts/load-osm.sh" "$HERE/fixtures/osm" >/dev/null 2>"$WORK/err"; then mv "$WORK/park.json.bak" "$HERE/fixtures/osm/park.json"; fail "a missing park.json should fail"; fi
mv "$WORK/park.json.bak" "$HERE/fixtures/osm/park.json"
grep -q "Missing" "$WORK/err" || fail "the failure should say which file is missing"
echo "PASS"
