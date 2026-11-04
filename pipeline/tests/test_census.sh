#!/usr/bin/env bash
# Integration test: Census tenure and dwelling counts per suburb (ADR 0044). Builds a tiny DataPack (a G37 CSV and a geography workbook,
# made with Python's zipfile) in a throwaway PostGIS, runs the real script and the Kotlin tool through Gradle, and checks the matching: a
# name with one Census suburb matches; a shared name stays unmatched until a council area settles it. Requires Docker, python3 and a JDK 17.
# Usage: tests/test_census.sh
set -euo pipefail

TEST_PROJECT=hf-census-test TEST_POSTGRES_PORT=55442
source "$(dirname "$0")/lib.sh"

"$HERE/../scripts/load-lga.sh" "$HERE/fixtures/gnaf/lga.geojson" >/dev/null
"$HERE/../scripts/load-suburbs.sh" "$HERE/fixtures/suburbs.geojson" >/dev/null
q() { psql_db -t -A -c "$1" </dev/null; }

PACK="$WORK/census"
mkdir -p "$PACK/2021 Census GCP Suburbs and Localities for NSW" "$PACK/Metadata"
python3 -I - "$PACK" <<'PY'
import sys, zipfile
pack = sys.argv[1]
header = "SAL_CODE_2021,R_Tot_Total,Total_Total,Ten_type_NS_Total,Total_DS_Sep_house,Total_DS_SemiD_ro_or_tce_h_th,Total_DS_Flat_apart,Total_DS_Oth_dwell,Total_DS_not_stated"
rows = ["SAL1,450,1000,0,500,250,250,0,0",      # Hamilton (NSW): 45% rented, half houses
        "SAL2,7,40,2,30,5,3,2,0",              # Belmont (Lake Macquarie - NSW)
        "SAL3,90,200,10,150,20,30,0,0",        # Belmont (Newcastle - NSW)
        "SAL4,5,10,0,10,0,0,0,0"]              # a suburb of ours that no one asked about
open(pack + "/2021 Census GCP Suburbs and Localities for NSW/2021Census_G37_NSW_SAL.csv", "w").write("\n".join([header] + rows) + "\n")
names = [("SAL1", "Hamilton (NSW)"), ("SAL2", "Belmont (Lake Macquarie - NSW)"), ("SAL3", "Belmont (Newcastle - NSW)"), ("SAL4", "Elsewhere")]
strings = ["SAL", "CED", "CED1", "Banks"] + [x for pair in names for x in pair]
si = "".join(f"<si><t>{s}</t></si>" for s in strings)
def row(i, cells):
    return f'<row r="{i}">' + "".join(cells) + "</row>"
body = [row(1, ['<c r="A1" t="s"><v>1</v></c><c r="B1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c>'])]
for n, (code, name) in enumerate(names, start=2):
    body.append(row(n, [f'<c r="A{n}" t="s"><v>0</v></c><c r="B{n}" t="s"><v>{4 + 2*(n-2)}</v></c><c r="C{n}"><v>{n}</v></c><c r="D{n}" t="s"><v>{5 + 2*(n-2)}</v></c>']))
ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
with zipfile.ZipFile(pack + "/Metadata/2021Census_geog_desc_test.xlsx", "w") as z:
    z.writestr("xl/sharedStrings.xml", f'<?xml version="1.0"?><sst xmlns="{ns}">{si}</sst>')
    z.writestr("xl/worksheets/sheet1.xml", f'<?xml version="1.0"?><worksheet xmlns="{ns}"><sheetData>{"".join(body)}</sheetData></worksheet>')
PY

report="$("$HERE/../scripts/load-census.sh" "$PACK")"
echo "$report"
grep -q "1 of 2 suburbs have Census" <<< "$report" || fail "only Hamilton should match at first: $report"
grep -q "Not matched.*BELMONT" <<< "$report" || fail "the shared name should be reported unmatched: $report"
row="$(q "SELECT sal_code || '|' || tenure_stated || '|' || rented || '|' || houses || '|' || semis || '|' || flats || '|' || other_dwellings FROM suburb_census c JOIN suburb s USING (suburb_id) WHERE s.name = 'HAMILTON'")"
[[ "$row" == "SAL1|1000|450|500|250|250|0" ]] || fail "Hamilton's counts were wrong: $row"
[[ -z "$(q "SELECT 1 FROM suburb_census c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")" ]] || fail "a shared name must not be guessed"

# A council area that settles it: Belmont lies in LAKE MACQUARIE, so the "(Lake Macquarie - NSW)" suburb is its match.
q "INSERT INTO lga (name, geom) SELECT 'LAKE MACQUARIE', ST_Multi(ST_Envelope(ST_Buffer(geom, 0.01))) FROM suburb WHERE name = 'BELMONT'" >/dev/null
report="$("$HERE/../scripts/load-census.sh" "$PACK")"
grep -q "2 of 2 suburbs have Census" <<< "$report" || fail "both suburbs should match once the council area settles Belmont: $report"
row="$(q "SELECT sal_code || '|' || tenure_stated || '|' || rented FROM suburb_census c JOIN suburb s USING (suburb_id) WHERE s.name = 'BELMONT'")"
[[ "$row" == "SAL2|38|7" ]] || fail "Belmont should take the Lake Macquarie suburb's counts (tenure base 40 less 2 not stated): $row"
echo "ok: matching by name and council area"

# Reloading gives the same rows.
before="$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_census c")"
"$HERE/../scripts/load-census.sh" "$PACK" >/dev/null
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_census c")" ]] || fail "rerun changed the census rows"

# A missing directory and a directory without the table stop with a clear message and leave the rows as they were.
if "$HERE/../scripts/load-census.sh" "$WORK/nowhere" >/dev/null 2>"$WORK/err"; then fail "a missing directory should fail"; fi
grep -q "Missing" "$WORK/err" || fail "the failure should say the directory is missing: $(cat "$WORK/err")"
mkdir -p "$WORK/empty"
if "$HERE/../scripts/load-census.sh" "$WORK/empty" >/dev/null 2>"$WORK/err"; then fail "a directory without the table should fail"; fi
grep -q "2021Census_G37_NSW_SAL.csv" "$WORK/err" || fail "the failure should name the missing table: $(cat "$WORK/err")"
[[ "$before" == "$(q "SELECT md5(string_agg(c::text, '|' ORDER BY suburb_id)) FROM suburb_census c")" ]] || fail "a failed load must leave the rows as they were"
echo "PASS: census"
