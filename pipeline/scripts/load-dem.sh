#!/usr/bin/env bash
# Loads the GeoTIFF files in a directory (the output of download-dem.sh, or any 5 m DEM tiles in the DEM's grid) and works out
# each lot's height above sea level and slope into `lot_terrain` (ADR 0036). Idempotent. Needs load-lots.sh and load-suburbs.sh.
# Usage: load-dem.sh [dir]   (default: data/raw/dem)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/dem.env"; set +a
ensure_migrated

DIR="${1:-$RAW_DIR/dem}"
mapfile -t FILES < <(find "$DIR" -maxdepth 1 -type f \( -name '*.tif' -o -name '*.tiff' \) 2>/dev/null | sort)
[[ "${#FILES[@]}" -gt 0 ]] || { echo "Missing GeoTIFF files in $DIR; run download-dem.sh" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM lot")" -gt 0 ]] || { echo "No lots loaded; run load-lots.sh" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.dem_raw" -c "CREATE TABLE stg.dem_raw (src text, rast raster)"
for file in "${FILES[@]}"; do
  # Each file goes in as one statement: its bytes as hex, read by PostGIS's own GDAL (only the GTiff driver is allowed).
  {
    printf "SET postgis.gdal_enabled_drivers = 'GTiff';\n"
    printf "INSERT INTO stg.dem_raw (src, rast) VALUES ('%s', ST_FromGDALRaster(decode('" "$(basename "$file")"
    od -An -v -tx1 "$file" | tr -d ' \n'
    printf "', 'hex'), %s));\n" "$DEM_REQUEST_SRID"
  } | psql_db -q || { echo "Could not read $file as a GeoTIFF" >&2; exit 1; }
done

psql_db -q -v srid="$DEM_SRID" -v pixel_m="$DEM_PIXEL_M" -v tile_px="$DEM_TILE_PX" -v min_m="$DEM_MIN_M" -v max_m="$DEM_MAX_M" \
  < "$PIPELINE_DIR/sql/terrain.sql"
psql_db -q -c "DROP TABLE IF EXISTS stg.dem_raw"

echo "Terrain loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' of ' || (SELECT count(*) FROM lot) || ' lots have a height, mean slope ' || COALESCE(round(avg(mean_slope_pct)::numeric, 1)::text, 'n/a') || '%, heights ' || COALESCE(round(min(elevation_m)::numeric, 1)::text, 'n/a') || ' to ' || COALESCE(round(max(elevation_m)::numeric, 1)::text, 'n/a') || ' m' FROM lot_terrain"
