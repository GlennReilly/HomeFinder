#!/usr/bin/env bash
# Test of download-census.sh against a local file:// "server": a good zip is downloaded and unpacked once, a non-zip is refused, a zip without
# the table is reported, and an existing download is kept. No database or pipeline/.env needed. Usage: tests/test_download_census.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail() { echo "FAIL: $1" >&2; exit 1; }
export RAW_DIR="$WORK/raw"

mkdir -p "$WORK/src/2021 Census GCP Suburbs and Localities for NSW"
printf 'SAL_CODE_2021,Total\nSAL10001,5\n' > "$WORK/src/2021 Census GCP Suburbs and Localities for NSW/2021Census_G37A_NSW_SAL.csv"
printf 'SAL_CODE_2021,Total\nSAL10001,9\n' > "$WORK/src/2021 Census GCP Suburbs and Localities for NSW/2021Census_G01_NSW_SAL.csv"
python3 -I - "$WORK" <<'PY'
import sys, zipfile, os
w = sys.argv[1]
with zipfile.ZipFile(w + "/good.zip", "w") as z:
    for root, _, files in os.walk(w + "/src"):
        for f in files:
            p = os.path.join(root, f); z.write(p, os.path.relpath(p, w + "/src"))
with zipfile.ZipFile(w + "/notable.zip", "w") as z:
    z.writestr("only/2021Census_G01_NSW_SAL.csv", "a,b\n1,2\n")
PY
echo "<html>not a zip</html>" > "$WORK/page.html"

out="$(CENSUS_URL="file://$WORK/good.zip" "$HERE/../scripts/download-census.sh" "$WORK/dest")"
grep -q "2 CSV files" <<< "$out" || fail "should unpack two CSV files: $out"
grep -q "G37A_NSW_SAL.csv" <<< "$out" || fail "should list the G37 file: $out"
[[ -f "$WORK/dest/census_sal_nsw.zip.sha256" ]] || fail "the checksum should be recorded"

out="$(CENSUS_URL="file://$WORK/page.html" "$HERE/../scripts/download-census.sh" "$WORK/dest")"
grep -q "already downloaded" <<< "$out" || fail "an existing download should be kept: $out"

if CENSUS_URL="file://$WORK/page.html" "$HERE/../scripts/download-census.sh" "$WORK/bad" >/dev/null 2>"$WORK/err"; then fail "a non-zip should fail"; fi
grep -q "Not a zip file" "$WORK/err" || fail "the failure should say it is not a zip: $(cat "$WORK/err")"
[[ ! -e "$WORK/bad/census_sal_nsw.zip" ]] || fail "a refused download must not leave a zip behind"

if CENSUS_URL="file://$WORK/notable.zip" "$HERE/../scripts/download-census.sh" "$WORK/notable" >/dev/null 2>"$WORK/err"; then fail "a zip without the table should fail"; fi
grep -q "no file name contains G37" "$WORK/err" || fail "the failure should name the missing table: $(cat "$WORK/err")"
echo "PASS: census download"
