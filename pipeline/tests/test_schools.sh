#!/usr/bin/env bash
# Integration test: the NSW public schools master dataset, loaded from a fixture into a throwaway PostGIS.
# Requires Docker. Usage: tests/test_schools.sh
set -euo pipefail

TEST_PROJECT=hf-schools-test TEST_POSTGRES_PORT=55438
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
q() { psql_db -t -A -c "$1"; }

report="$("$HERE/../scripts/load-schools.sh" "$HERE/fixtures/schools/master_dataset.csv")"

# Eight data rows: one has no position and one has a non-numeric code, so six are loaded.
[[ "$(q "SELECT count(*) FROM school")" == "6" ]] || fail "expected 6 schools but got $(q "SELECT count(*) FROM school")"
grep -q "6 schools" <<< "$report" || fail "the report should say 6 schools: $report"

row() { q "SELECT $1 FROM school WHERE school_code = $2"; }
[[ "$(row "name || '|' || town_suburb || '|' || postcode || '|' || level_label || '|' || icsea || '|' || enrolment_fte" 1001)" == "Hamilton Public School|Hamilton|2303|Primary School|1051|412.5" ]] || fail "school 1001 was read wrongly: $(row "name || '|' || town_suburb || '|' || postcode || '|' || level_label || '|' || icsea || '|' || enrolment_fte" 1001)"
# A suburb written with a trailing space is trimmed; "np", a blank and an impossible ICSEA are NULL, not zero.
[[ "$(row "town_suburb" 1005)" == "Abbotsford" ]] || fail "the trailing space should be trimmed"
[[ "$(row "COALESCE(icsea::text, 'null')" 1002)" == "null" ]] || fail "np should be a null ICSEA"
[[ "$(row "COALESCE(icsea::text, 'null')" 1003)" == "null" ]] || fail "a blank ICSEA should be null"
[[ "$(row "COALESCE(icsea::text, 'null')" 1007)" == "null" ]] || fail "an ICSEA of 3000 is outside the range and should be null"
[[ "$(row "COALESCE(enrolment_fte::text, 'null')" 1003)" == "null" ]] || fail "a blank enrolment should be null"
[[ "$(row "extracted_on" 1001)" == "2026-10-02" ]] || fail "the extraction date should be kept"

# Each school is placed in the suburb that contains it; one outside every pilot suburb has none.
[[ "$(row "suburb_id" 1001)" == "9001" && "$(row "suburb_id" 1002)" == "9001" && "$(row "suburb_id" 1003)" == "9001" ]] || fail "the Hamilton schools should be in suburb 9001"
[[ "$(row "suburb_id" 1004)" == "9002" && "$(row "suburb_id" 1007)" == "9002" ]] || fail "the Belmont schools should be in suburb 9002"
[[ "$(row "COALESCE(suburb_id, 'none')" 1005)" == "none" ]] || fail "Abbotsford is outside the pilot suburbs"
grep -q "5 inside a pilot suburb" <<< "$report" || grep -q "inside a pilot suburb" <<< "$report" || fail "the report should count schools inside suburbs: $report"
[[ "$(q "SELECT count(*) FROM school WHERE suburb_id IS NOT NULL")" == "5" ]] || fail "5 schools should be inside pilot suburbs"

# Reloading gives identical rows, also when the file starts with a byte order mark and uses plain line endings.
before="$(q "SELECT md5(string_agg(s::text, '|' ORDER BY school_code)) FROM school s")"
"$HERE/../scripts/load-schools.sh" "$HERE/fixtures/schools/master_dataset.csv" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(s::text, '|' ORDER BY school_code)) FROM school s")" ]] || fail "rerun changed the schools"
{ printf '\xEF\xBB\xBF'; tr -d '\r' < "$HERE/fixtures/schools/master_dataset.csv"; } > "$WORK/bom.csv"
"$HERE/../scripts/load-schools.sh" "$WORK/bom.csv" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(s::text, '|' ORDER BY school_code)) FROM school s")" ]] || fail "a file with a byte order mark loaded differently"

# A file without a column the loader needs, and a missing file, stop with a clear message.
sed '1s/ICSEA_value/ICSEA_renamed/' "$HERE/fixtures/schools/master_dataset.csv" > "$WORK/renamed.csv"
if "$HERE/../scripts/load-schools.sh" "$WORK/renamed.csv" >/dev/null 2>"$WORK/err"; then fail "a renamed column should fail"; fi
grep -q "no ICSEA_value column" "$WORK/err" || fail "the failure should name the missing column: $(cat "$WORK/err")"
if "$HERE/../scripts/load-schools.sh" "$WORK/nothing.csv" >/dev/null 2>"$WORK/err"; then fail "a missing file should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the file is missing"
[[ "$(q "SELECT count(*) FROM school")" == "6" ]] || fail "a failed load must leave the schools as they were"
echo "PASS"
