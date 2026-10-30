#!/usr/bin/env bash
# Downloads the Geoscience Australia 5 m DEM for the pilot region from its WCS, as GeoTIFF files of DEM_REQUEST_M metres square
# (only the squares that touch a pilot suburb) into data/raw/dem. Run by you. Existing files are kept, so an interrupted run
# resumes; delete the folder to fetch afresh. Needs load-suburbs.sh first (it reads the suburb outlines) and jq-free curl.
# Usage: download-dem.sh [dest_dir]   (default: data/raw/dem)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/dem.env"; set +a
ensure_migrated

DEST="${1:-$RAW_DIR/dem}"
USER_AGENT="HomeFinder-personal-research/0.1 (glenn.reilly@gmail.com)"
mkdir -p "$DEST"
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

wcs() { curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" "$@"; }
names() { sed -E 's/<(\/?)([A-Za-z0-9_]+:)/<\1/g' | grep -o "<$1[^>]*>[^<]*</$1>" | sed -E 's/<[^>]*>//g'; }

COVERAGE="$DEM_COVERAGE"
if [[ -z "$COVERAGE" ]]; then
  mapfile -t OFFERED < <(wcs "$DEM_WCS_URL?service=WCS&version=1.0.0&request=GetCapabilities" | names name)
  # With several coverages, the one whose name says DEM is the elevation model (the service also lists a bare layer number).
  mapfile -t LIKELY < <(printf '%s\n' "${OFFERED[@]}" | grep -i 'dem' || true)
  if [[ "${#OFFERED[@]}" -eq 1 ]]; then
    COVERAGE="${OFFERED[0]}"
  elif [[ "${#LIKELY[@]}" -eq 1 ]]; then
    COVERAGE="${LIKELY[0]}"
  else
    echo "The service offers ${#OFFERED[@]} coverages; set DEM_COVERAGE to the elevation one:" >&2
    printf '  %s\n' "${OFFERED[@]}" >&2
    exit 1
  fi
fi

mapfile -t FORMATS < <(wcs "$DEM_WCS_URL?service=WCS&version=1.0.0&request=DescribeCoverage&coverage=$COVERAGE" | names formats)
FORMAT=""
for candidate in "${FORMATS[@]}"; do
  if [[ "${candidate,,}" == *tif* ]]; then FORMAT="$candidate"; break; fi
done
[[ -n "$FORMAT" ]] || { echo "The coverage $COVERAGE offers no GeoTIFF format (it offers: ${FORMATS[*]:-nothing})" >&2; exit 1; }

PIXELS=$((DEM_REQUEST_M / DEM_PIXEL_M))
echo "dem: coverage $COVERAGE as $FORMAT, ${PIXELS}x${PIXELS} pixel requests"

# The squares, on a grid of DEM_REQUEST_M metres in the DEM's own coordinates, that touch any pilot suburb.
mapfile -t SQUARES < <(psql_db -t -A -F ' ' -c "
  WITH s AS (SELECT ST_Transform(geom, $DEM_SRID) AS g FROM suburb),
       b AS (SELECT floor(ST_XMin(e) / $DEM_REQUEST_M)::int AS x0, floor(ST_XMax(e) / $DEM_REQUEST_M)::int AS x1,
                    floor(ST_YMin(e) / $DEM_REQUEST_M)::int AS y0, floor(ST_YMax(e) / $DEM_REQUEST_M)::int AS y1
             FROM (SELECT ST_Extent(g) AS e FROM s) t),
       grid AS (SELECT x * $DEM_REQUEST_M AS e, y * $DEM_REQUEST_M AS n FROM b, generate_series(b.x0, b.x1) x, generate_series(b.y0, b.y1) y)
  SELECT e, n FROM grid WHERE EXISTS (SELECT 1 FROM s WHERE ST_Intersects(s.g, ST_MakeEnvelope(e, n, e + $DEM_REQUEST_M, n + $DEM_REQUEST_M, $DEM_SRID)))
  ORDER BY e, n")

# A TIFF starts II*\0, MM\0* (or II+\0 / MM\0+ for BigTIFF); anything else is the service's error message.
is_tiff() { [[ "$(head -c 4 "$1" | od -An -tx1 | tr -d ' \n')" =~ ^(49492a00|4d4d002a|49492b00|4d4d002b)$ ]]; }

done_count=0
for square in "${SQUARES[@]}"; do
  read -r east north <<< "$square"
  file="$DEST/e${east}_n${north}.tif"
  if [[ ! -f "$file" ]]; then
    wcs --output "$file.part" \
      "$DEM_WCS_URL?service=WCS&version=1.0.0&request=GetCoverage&coverage=$COVERAGE&crs=EPSG:$DEM_SRID&format=$FORMAT&width=$PIXELS&height=$PIXELS&bbox=$east,$north,$((east + DEM_REQUEST_M)),$((north + DEM_REQUEST_M))"
    if ! is_tiff "$file.part"; then
      echo "The service did not return a GeoTIFF for $east,$north:" >&2
      head -c 600 "$file.part" >&2; echo >&2
      rm -f "$file.part"
      exit 1
    fi
    mv "$file.part" "$file"
  fi
  done_count=$((done_count + 1))
done
echo "dem: ${done_count} files in $DEST"
