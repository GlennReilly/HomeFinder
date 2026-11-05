#!/usr/bin/env bash
# Test of download-seifa.sh against a local file:// "server": a good workbook is downloaded once, a non-workbook is refused, and an existing
# download is kept. No database or pipeline/.env needed. Usage: tests/test_download_seifa.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail() { echo "FAIL: $1" >&2; exit 1; }
export RAW_DIR="$WORK/raw"

python3 -I - "$WORK" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1] + "/good.xlsx", "w") as z:
    z.writestr("xl/sharedStrings.xml", "<sst/>")
PY
echo "<html>not a workbook</html>" > "$WORK/page.html"

out="$(SEIFA_URL="file://$WORK/good.xlsx" "$HERE/../scripts/download-seifa.sh" "$WORK/dest")"
grep -q "downloaded" <<< "$out" || fail "should download: $out"
[[ -f "$WORK/dest/seifa-2021-sal.xlsx.sha256" ]] || fail "the checksum should be recorded"

out="$(SEIFA_URL="file://$WORK/page.html" "$HERE/../scripts/download-seifa.sh" "$WORK/dest")"
grep -q "already downloaded" <<< "$out" || fail "an existing download should be kept: $out"

if SEIFA_URL="file://$WORK/page.html" "$HERE/../scripts/download-seifa.sh" "$WORK/bad" >/dev/null 2>"$WORK/err"; then fail "a non-workbook should fail"; fi
grep -q "Not an Excel workbook" "$WORK/err" || fail "the failure should say it is not a workbook: $(cat "$WORK/err")"
[[ ! -e "$WORK/bad/seifa-2021-sal.xlsx" ]] || fail "a refused download must not leave a file behind"
echo "PASS: seifa download"
