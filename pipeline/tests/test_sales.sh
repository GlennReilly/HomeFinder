#!/usr/bin/env bash
# Integration test: sales cleaning rules and suburb statistics, using fixtures and a throwaway PostGIS.
# Requires Docker and jq. Usage: tests/test_sales.sh
set -euo pipefail

TEST_PROJECT=hf-sales-test TEST_POSTGRES_PORT=55435
# Small thresholds so the fixtures stay small; the production defaults live in config/sales.env.
export MIN_SALES_FOR_MEDIAN=3 MIN_LOTS_FOR_BLOCK_SIZE=2
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
"$HERE/../scripts/load-lots.sh" "$HERE/fixtures/lots" >/dev/null
"$HERE/../scripts/link-address-lots.sh" >/dev/null

report="$("$HERE/../scripts/load-sales.sh" "$HERE/fixtures/sales")"
q() { psql_db -t -A -c "$1"; }

# 30 B records in configured districts (district 999 is ignored); one is repeated in a later file, leaving 29.
[[ "$(q "SELECT count(*) FROM sale")" == "29" ]] || fail "expected 29 sales but got $(q "SELECT count(*) FROM sale")"
[[ "$(q "SELECT count(*) FROM sale WHERE district_code = '999'")" == "0" ]] || fail "unconfigured district was loaded"
[[ "$(q "SELECT source_file FROM sale WHERE property_id = '1007'")" == *20260601* ]] || fail "the later file should win for a repeated sale"
reasons="$(q "SELECT string_agg(COALESCE(exclusion_reason, 'counted') || '=' || n, ',' ORDER BY COALESCE(exclusion_reason, 'counted')) FROM (SELECT exclusion_reason, count(*) n FROM sale GROUP BY 1) t")"
want="counted=18,low_price=1,multi_property=2,no_contract_date=1,no_price=1,no_suburb_match=3,not_residential=2,part_interest=1"
[[ "$reasons" == "$want" ]] || fail "expected '$want' but got '$reasons'"
grep -q "multi_property | 2" <<< "$report" || fail "report should list exclusion reasons: $report"

stats() { q "SELECT $1 FROM suburb_stats WHERE suburb_id = '$2'"; }
# Hamilton houses: current window 700k, 800k, 900k; previous 600k, 640k, 680k and 1m on the boundary day (a sale exactly
# 12 months back belongs to the previous window, one exactly 24 months back to neither).
[[ "$(stats "house_sales || '/' || house_median_aud" 9001)" == "4/800000" ]] || fail "Hamilton house stats: $(stats "house_sales || '/' || house_median_aud" 9001)"
[[ "$(stats "house_prev_sales || '/' || house_prev_median_aud" 9001)" == "4/660000" ]] || fail "Hamilton previous house stats"
[[ "$(stats "house_change_pct" 9001)" == "21.2" ]] || fail "Hamilton house change: $(stats "house_change_pct" 9001)"
[[ "$(stats "unit_sales || '/' || unit_median_aud" 9001)" == "3/450000" ]] || fail "Hamilton unit stats"
# Two previous unit sales is below the minimum, so there is no previous median and so no change.
[[ "$(stats "unit_prev_sales || '/' || COALESCE(unit_prev_median_aud::text, 'null') || '/' || COALESCE(unit_change_pct::text, 'null')" 9001)" == "2/null/null" ]] || fail "Hamilton unit change should be unknown"
[[ "$(stats "as_of" 9001)" == "2026-06-01" ]] || fail "as_of should be the latest file date"
# Belmont has only two house sales in the window: the count is kept, the median is withheld.
[[ "$(stats "house_sales || '/' || COALESCE(house_median_aud::text, 'null')" 9002)" == "2/null" ]] || fail "Belmont should have no median from two sales"

# Block size: lots with an address, not strata, at least the minimum area. Belmont has two (below the minimum of two is
# fine, at two it is shown); Hamilton has one, so its spread is withheld but the lot count is kept.
[[ "$(stats "block_lots" 9002)" == "2" ]] || fail "Belmont block lots: $(stats "block_lots" 9002)"
[[ "$(stats "block_lots || '/' || COALESCE(block_median_m2::text, 'null')" 9001)" == "1/null" ]] || fail "Hamilton block size should be withheld"
ok="$(q "SELECT block_p10_m2 < block_median_m2 AND block_median_m2 < block_p90_m2 AND abs(block_median_m2 - (SELECT avg(area_m2) FROM lot WHERE lot_id IN ('1002','1003'))) < 0.1 FROM suburb_stats WHERE suburb_id = '9002'")"
[[ "$ok" == "t" ]] || fail "Belmont block spread is wrong"
q "UPDATE lot SET parcel_key = 'SP12345' WHERE lot_id = '1003'" >/dev/null
"$HERE/../scripts/refresh-suburb-stats.sh" >/dev/null
[[ "$(stats "block_lots || '/' || COALESCE(block_median_m2::text, 'null')" 9002)" == "1/null" ]] || fail "strata lots must not count towards block size"

# Reloading gives identical rows.
before="$(q "SELECT md5(string_agg(s::text, '|' ORDER BY suburb_id)) FROM suburb_stats s")"
"$HERE/../scripts/load-sales.sh" "$HERE/fixtures/sales" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(s::text, '|' ORDER BY suburb_id)) FROM suburb_stats s")" ]] || fail "rerun changed the statistics"
[[ "$(q "SELECT count(*) FROM sale")" == "29" ]] || fail "rerun changed the sale count"
# A blank postcode: matched by name in a pilot council, left alone in a neighbouring one.
blank() { q "SELECT COALESCE(suburb_id, 'none') || '/' || COALESCE(exclusion_reason, 'counted') FROM sale WHERE property_id = '$1'"; }
[[ "$(blank 3011)" == "9001/counted" ]] || fail "a blank-postcode Hamilton sale in Newcastle should match by name: $(blank 3011)"
[[ "$(blank 4004)" == "9002/counted" ]] || fail "a blank-postcode Belmont sale in Lake Macquarie should match by name: $(blank 4004)"
[[ "$(blank 5001)" == "none/no_suburb_match" ]] || fail "a blank-postcode sale in Maitland must stay unmatched: $(blank 5001)"
# A name shared by two suburbs is ambiguous, so it is not guessed.
q "INSERT INTO suburb (suburb_id, name, postcode, geom) SELECT '9099', name, '2999', geom FROM suburb WHERE suburb_id = '9002'" >/dev/null
"$HERE/../scripts/load-sales.sh" "$HERE/fixtures/sales" >/dev/null
[[ "$(blank 4004)" == "none/no_suburb_match" ]] || fail "an ambiguous suburb name must not be guessed: $(blank 4004)"
[[ "$(blank 4001)" == "9002/counted" ]] || fail "a sale with its postcode still matches exactly: $(blank 4001)"
echo "PASS"
