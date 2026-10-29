#!/usr/bin/env bash
# Downloads the NSW Valuer General Property Sales Information zips (yearly and weekly) into data/raw/sales and unzips
# them, including nested zips. Licence: CC BY-NC-ND 4.0, personal non-commercial use only (ADR 0018).
# Zips already present are kept (a published week never changes); the first download records a SHA-256, later runs must match.
# Usage: download-sales.sh [dest_dir]   (default: data/raw/sales)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/sales.env"; set +a

DEST="${1:-$RAW_DIR/sales}"
USER_AGENT="HomeFinder-personal-research/0.1 (glenn.reilly@gmail.com)"
PAUSE_SECONDS=1
mkdir -p "$DEST/yearly" "$DEST/weekly"

# Echoes "ok", "missing" (404: not published yet) or exits on any other failure.
fetch() {
  local url="$1" zip="$2" code
  [[ -f "$zip" ]] && { echo ok; return; }
  code="$(curl --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" \
    --write-out '%{http_code}' --output "$zip.part" "$url")"
  case "$code" in
    200) mv "$zip.part" "$zip"; sleep "$PAUSE_SECONDS"; echo ok ;;
    404) rm -f "$zip.part"; echo missing ;;
    *) rm -f "$zip.part"; echo "HTTP $code for $url" >&2; exit 1 ;;
  esac
}

verify() {
  local zip="$1" actual
  actual="$(sha256sum "$zip" | cut -d' ' -f1)"
  if [[ -f "$zip.sha256" ]]; then
    [[ "$actual" == "$(cat "$zip.sha256")" ]] || { echo "Checksum mismatch for $zip" >&2; exit 1; }
  else
    echo "$actual" > "$zip.sha256"
  fi
}

# Unzips into a folder named after the zip, then unzips any zips found inside, until none are left.
unpack() {
  local zip="$1" out="${1%.zip}"
  [[ -d "$out" ]] && return
  mkdir -p "$out.tmp"
  unzip -q -o "$zip" -d "$out.tmp"
  local nested
  while nested="$(find "$out.tmp" -iname '*.zip' | head -n 1)"; [[ -n "$nested" ]]; do
    unzip -q -o "$nested" -d "${nested%.*}"
    rm -f "$nested"
  done
  mv "$out.tmp" "$out"
}

got=0 missing=0
fetch_one() {
  local url="$1" zip="$2"
  if [[ "$(fetch "$url" "$zip")" == ok ]]; then verify "$zip"; unpack "$zip"; got=$((got + 1)); else missing=$((missing + 1)); fi
}

for year in $SALES_YEARS; do
  fetch_one "$SALES_BASE_URL/yearly/$year.zip" "$DEST/yearly/$year.zip"
done

# Weekly files are published on Mondays; stop at today, and treat 404 as "not published yet".
week="$SALES_WEEKLY_FROM"
today="$(date +%F)"
while [[ ! "$week" > "$today" ]]; do
  stamp="${week//-/}"
  fetch_one "$SALES_BASE_URL/weekly/$stamp.zip" "$DEST/weekly/$stamp.zip"
  week="$(date -d "$week + 7 days" +%F)"
done
echo "Sales files ready in $DEST ($got zips present, $missing not published)"
