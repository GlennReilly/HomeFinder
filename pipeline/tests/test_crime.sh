#!/usr/bin/env bash
# Integration test: BOCSAR crime counts per suburb (ADR 0051). Builds a tiny SuburbData.csv (26 month columns, so only the latest 24 count) in a
# throwaway PostGIS, runs the real script and the Kotlin tool through Gradle, and checks the matching: a name BOCSAR lists once matches; a name it
# tags with a council area stays unmatched until our suburb lies in that area. Requires Docker, python3 and a JDK 17.
# Usage: tests/test_crime.sh
set -euo pipefail

TEST_PROJECT=hf-crime-test TEST_POSTGRES_PORT=55444
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
q() { psql_db -t -A -c "$1" </dev/null; }

FILE="$WORK/SuburbData.csv"
python3 -I - "$FILE" <<'PY'
import sys
months = ["Mar 2024", "Apr 2024"] + [f"{m} {y}" for y, ms in ((2024, "May Jun Jul Aug Sep Oct Nov Dec".split()), (2025, "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split()), (2026, "Jan Feb Mar Apr".split())) for m in ms]
assert len(months) == 26
def line(cells): return ",".join('"' + c + '"' for c in cells)
def row(suburb, cat, sub, early, each): return line([suburb, cat, sub] + [str(early)] * 2 + [str(each)] * 24)
rows = [line(["Suburb", "Offence category", "Subcategory"] + months),
        row("Hamilton", "Assault", "Domestic violence related assault", 40, 1),   # 24 in the window (the first two months do not count)
        row("Hamilton", "Theft", "Break and enter dwelling", 40, 2),               # 48
        row("Hamilton", "Theft", "Fraud", 40, 9),                                  # not a measure
        row("Belmont (Lake Macquarie)", "Assault", "Assault Police", 0, 1),        # 24
        row("Belmont (Newcastle)", "Assault", "Assault Police", 0, 3),             # 72
        row("Elsewhere", "Robbery", "Robbery without a weapon", 0, 1)]             # a suburb of ours that no one asked about
open(sys.argv[1], "w").write("\n".join(rows) + "\n")
PY

report="$("$HERE/../scripts/load-crime.sh" "$FILE")"
echo "$report"
grep -q "1 of 2 suburbs have BOCSAR crime counts (May 2024 to Apr 2026)" <<< "$report" || fail "only Hamilton should match at first: $report"
grep -q "Not matched.*BELMONT" <<< "$report" || fail "the shared name should be reported unmatched: $report"
row="$(q "SELECT violent_incidents || '|' || property_incidents || '|' || window_text || '|' || as_of FROM suburb_crime c JOIN suburb s USING (suburb_id) WHERE s.name = 'HAMILTON'")"
[[ "$row" == "24|48|May 2024 to Apr 2026|2026-04-30" ]] || fail "Hamilton's counts were wrong (fraud must not count, nor the first two months): $row"
[[ -z "$(q "SELECT 1 FROM suburb_crime c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")" ]] || fail "a shared name must not be guessed"

# A council area that settles it: Belmont lies in NEWCASTLE here, so the "(Newcastle)" entry is its match.
q "INSERT INTO lga (name, geom) SELECT 'NEWCASTLE', ST_Multi(ST_Envelope(ST_Buffer(geom, 0.01))) FROM suburb WHERE name = 'BELMONT'" >/dev/null
report="$("$HERE/../scripts/load-crime.sh" "$FILE")"
grep -q "2 of 2 suburbs have BOCSAR crime counts" <<< "$report" || fail "both suburbs should match once the council area settles Belmont: $report"
row="$(q "SELECT violent_incidents || '|' || bocsar_name FROM suburb_crime c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")"
[[ "$row" == "72|Belmont (Newcastle)" ]] || fail "Belmont should take the Newcastle entry: $row"
echo "ok: matching by name and council area"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_crime c")"
"$HERE/../scripts/load-crime.sh" "$FILE" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_crime c")" ]] || fail "rerun changed the crime rows"

# A missing file and a file that is not the BOCSAR layout stop with a clear message and leave the rows as they were.
if "$HERE/../scripts/load-crime.sh" "$WORK/nowhere.csv" >/dev/null 2>"$WORK/err"; then fail "a missing file should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the file is missing: $(cat "$WORK/err")"
echo "a,b,c" > "$WORK/bad.csv"
if "$HERE/../scripts/load-crime.sh" "$WORK/bad.csv" >/dev/null 2>"$WORK/err"; then fail "a bad file should fail"; fi
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_crime c")" ]] || fail "a failed load must leave the rows as they were"
echo "PASS: crime"
