#!/usr/bin/env bash
# Integration test: SEIFA socio-economic advantage per suburb (ADR 0049). Builds a tiny SEIFA workbook (made with Python's zipfile) in a throwaway
# PostGIS, runs the real script and the Kotlin tool through Gradle, and checks the matching: a name with one ABS suburb matches; a shared name
# stays unmatched until a council area settles it; a suburb of another state is left out. Requires Docker, python3 and a JDK 17.
# Usage: tests/test_seifa.sh
set -euo pipefail

TEST_PROJECT=hf-seifa-test TEST_POSTGRES_PORT=55443
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
q() { psql_db -t -A -c "$1" </dev/null; }

FILE="$WORK/seifa.xlsx"
python3 -I - "$FILE" <<'PY'
import sys, zipfile
strings = ["2021 Suburbs and Localities (SAL) Code", "2021 Suburbs and Localities (SAL) Name", "Score", "Decile", "Usual Resident Population",
           "Hamilton (NSW)", "Belmont (Lake Macquarie - NSW)", "Belmont (Newcastle - NSW)", "Hamilton (Vic.)", "Elsewhere"]
si = "".join(f"<si><t>{s}</t></si>" for s in strings)
def s(ref, i): return f'<c r="{ref}" t="s"><v>{i}</v></c>'
def n(ref, v): return f'<c r="{ref}"><v>{v}</v></c>'
header = '<row r="1">' + s("A1", 0) + s("B1", 1) + s("C1", 2) + s("D1", 3) + s("E1", 2) + s("F1", 3) + s("K1", 4) + "</row>"
def row(r, code, name, score, decile, pop):
    return f'<row r="{r}">' + n(f"A{r}", code) + s(f"B{r}", name) + n(f"E{r}", score) + n(f"F{r}", decile) + n(f"K{r}", pop) + "</row>"
rows = [row(2, 10001, 5, 1035.7, 8, 4614),    # Hamilton (NSW)
        row(3, 10002, 6, 1010.2, 6, 900),     # Belmont (Lake Macquarie - NSW)
        row(4, 10003, 7, 987.5, 4, 3000),     # Belmont (Newcastle - NSW)
        row(5, 21129, 8, 944.3, 3, 10346)]    # Hamilton (Vic.): another state, left out
ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("xl/sharedStrings.xml", f'<?xml version="1.0"?><sst xmlns="{ns}">{si}</sst>')
    z.writestr("xl/worksheets/sheet1.xml", f'<?xml version="1.0"?><worksheet xmlns="{ns}"><sheetData>{header}{"".join(rows)}</sheetData></worksheet>')
PY

report="$("$HERE/../scripts/load-seifa.sh" "$FILE")"
echo "$report"
grep -q "1 of 2 suburbs have a SEIFA decile" <<< "$report" || fail "only Hamilton should match at first: $report"
grep -q "Not matched.*BELMONT" <<< "$report" || fail "the shared name should be reported unmatched: $report"
row="$(q "SELECT sal_code || '|' || irsad_score || '|' || irsad_decile || '|' || population FROM suburb_seifa c JOIN suburb s USING (suburb_id) WHERE s.name = 'HAMILTON'")"
[[ "$row" == "SAL10001|1035.7|8|4614" ]] || fail "Hamilton's row was wrong (the Victorian Hamilton must not count): $row"
[[ -z "$(q "SELECT 1 FROM suburb_seifa c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")" ]] || fail "a shared name must not be guessed"

# A council area that settles it: Belmont lies in LAKE MACQUARIE, so the "(Lake Macquarie - NSW)" suburb is its match.
q "INSERT INTO lga (name, geom) SELECT 'LAKE MACQUARIE', ST_Multi(ST_Envelope(ST_Buffer(geom, 0.01))) FROM suburb WHERE name = 'BELMONT'" >/dev/null
report="$("$HERE/../scripts/load-seifa.sh" "$FILE")"
grep -q "2 of 2 suburbs have a SEIFA decile" <<< "$report" || fail "both suburbs should match once the council area settles Belmont: $report"
row="$(q "SELECT sal_code || '|' || irsad_decile FROM suburb_seifa c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")"
[[ "$row" == "SAL10002|6" ]] || fail "Belmont should take the Lake Macquarie suburb: $row"
echo "ok: matching by name and council area"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_seifa c")"
"$HERE/../scripts/load-seifa.sh" "$FILE" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_seifa c")" ]] || fail "rerun changed the SEIFA rows"

# A missing file and a file that is not the SEIFA workbook stop with a clear message and leave the rows as they were.
if "$HERE/../scripts/load-seifa.sh" "$WORK/nowhere.xlsx" >/dev/null 2>"$WORK/err"; then fail "a missing file should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the file is missing: $(cat "$WORK/err")"
echo "not a workbook" > "$WORK/bad.xlsx"
if "$HERE/../scripts/load-seifa.sh" "$WORK/bad.xlsx" >/dev/null 2>"$WORK/err"; then fail "a bad workbook should fail"; fi
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_seifa c")" ]] || fail "a failed load must leave the rows as they were"
echo "PASS: seifa"
