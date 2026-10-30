#!/usr/bin/env bash
# Downloads the Geoscience Australia 5 m DEM for the pilot region from its WCS, as GeoTIFF files of DEM_CELL_DEG degrees square
# in GDA94 (only the squares that touch a pilot suburb) into data/raw/dem. Run by you. Existing files are kept, so an interrupted run
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
# The values of <$1> elements inside the coverage list (capabilities) or the coverage description: the service's own <name> is left out.
names() { tr '\n' ' ' | sed -E 's/<(\/?)([A-Za-z0-9_]+:)/<\1/g; s/.*<(ContentMetadata|CoverageDescription)[ >]//' | grep -o "<$1[ >][^<]*</$1>" | sed -E 's/<[^>]*>//g'; }

COVERAGE="$DEM_COVERAGE"
if [[ -z "$COVERAGE" ]]; then
  mapfile -t OFFERED < <(wcs "$DEM_WCS_URL?service=WCS&version=1.0.0&request=GetCapabilities" | names name)
  if [[ "${#OFFERED[@]}" -eq 1 ]]; then
    COVERAGE="${OFFERED[0]}"
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

WIDTH="$(awk -v c="$DEM_CELL_DEG" -v p="$DEM_PIXEL_DEG_X" 'BEGIN { printf "%d", c / p + 0.5 }')"
HEIGHT="$(awk -v c="$DEM_CELL_DEG" -v p="$DEM_PIXEL_DEG_Y" 'BEGIN { printf "%d", c / p + 0.5 }')"
echo "dem: coverage $COVERAGE as $FORMAT, ${WIDTH}x${HEIGHT} pixel requests"

# The cells, on a grid of DEM_CELL_DEG degrees, that touch any pilot suburb (as column and row numbers of that grid). The cadastre is
# GDA2020 and the service GDA94; the 1.8 m difference is nothing next to a cell.
mapfile -t CELLS < <(psql_db -t -A -F ' ' -c "
  WITH b AS (SELECT floor(ST_XMin(e) / $DEM_CELL_DEG)::int AS x0, floor(ST_XMax(e) / $DEM_CELL_DEG)::int AS x1,
                    floor(ST_YMin(e) / $DEM_CELL_DEG)::int AS y0, floor(ST_YMax(e) / $DEM_CELL_DEG)::int AS y1
             FROM (SELECT ST_Extent(geom) AS e FROM suburb) t),
       grid AS (SELECT x, y FROM b, generate_series(b.x0, b.x1) x, generate_series(b.y0, b.y1) y)
  SELECT x, y FROM grid WHERE EXISTS (
    SELECT 1 FROM suburb s WHERE ST_Intersects(s.geom, ST_MakeEnvelope(x * $DEM_CELL_DEG, y * $DEM_CELL_DEG, (x + 1) * $DEM_CELL_DEG, (y + 1) * $DEM_CELL_DEG, 7844)))
  ORDER BY x, y")

# A TIFF starts II*\0, MM\0* (or II+\0 / MM\0+ for BigTIFF); anything else is the service's error message.
is_tiff() { [[ "$(head -c 4 "$1" | od -An -tx1 | tr -d ' \n')" =~ ^(49492a00|4d4d002a|49492b00|4d4d002b)$ ]]; }

done_count=0
for cell in "${CELLS[@]}"; do
  read -r column row <<< "$cell"
  bbox="$(awk -v x="$column" -v y="$row" -v c="$DEM_CELL_DEG" 'BEGIN { printf "%.6f,%.6f,%.6f,%.6f", x * c, y * c, (x + 1) * c, (y + 1) * c }')"
  file="$DEST/cell_${column}_${row}.tif"
  if [[ ! -f "$file" ]]; then
    # Not --fail: a refused request has a body that says why, and is shown below.
    curl --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$file.part" \
      "$DEM_WCS_URL?service=WCS&version=1.0.0&request=GetCoverage&coverage=$COVERAGE&crs=EPSG:$DEM_REQUEST_SRID&format=$FORMAT&width=$WIDTH&height=$HEIGHT&bbox=$bbox"
    if ! is_tiff "$file.part"; then
      echo "The service did not return a GeoTIFF for $bbox:" >&2
      head -c 600 "$file.part" >&2; echo >&2
      rm -f "$file.part"
      exit 1
    fi
    mv "$file.part" "$file"
  fi
  done_count=$((done_count + 1))
done
echo "dem: ${done_count} files in $DEST"
