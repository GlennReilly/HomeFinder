#!/usr/bin/env bash
# Test of download-bocsar.sh against a local file:// "server": a good zip is downloaded, unpacked and its CSV layout printed once, a non-zip is
# refused, and an existing download is kept. No database or pipeline/.env needed. Usage: tests/test_download_bocsar.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail() { echo "FAIL: $1" >&2; exit 1; }
export RAW_DIR="$WORK/raw"

python3 -I - "$WORK" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1] + "/good.zip", "w") as z:
    z.writestr("SuburbData.csv", "Suburb,Offence category,Offence subcategory,Jan 2024\nHAMILTON,Theft,Steal from motor vehicle,3\n")
PY
echo "<html>not a zip</html>" > "$WORK/page.html"

out="$(BOCSAR_URL="file://$WORK/good.zip" "$HERE/../scripts/download-bocsar.sh" "$WORK/dest")"
grep -q "SuburbData.csv" <<< "$out" || fail "should list the CSV: $out"
grep -q "Suburb,Offence category" <<< "$out" || fail "should print the header line: $out"
[[ -f "$WORK/dest/bocsar_suburb_data.zip.sha256" ]] || fail "the checksum should be recorded"

out="$(BOCSAR_URL="file://$WORK/page.html" "$HERE/../scripts/download-bocsar.sh" "$WORK/dest")"
grep -q "already downloaded" <<< "$out" || fail "an existing download should be kept: $out"

if BOCSAR_URL="file://$WORK/page.html" "$HERE/../scripts/download-bocsar.sh" "$WORK/bad" >/dev/null 2>"$WORK/err"; then fail "a non-zip should fail"; fi
grep -q "Not a zip file" "$WORK/err" || fail "the failure should say it is not a zip: $(cat "$WORK/err")"
[[ ! -e "$WORK/bad/bocsar_suburb_data.zip" ]] || fail "a refused download must not leave a zip behind"
echo "PASS: bocsar download"
