#!/usr/bin/env bash
# Links each G-NAF address to a cadastre lot: legal parcel id first (when exactly one lot has that key), then the
# lot containing the address point, then (low confidence) the nearest lot within a short distance. Prints the match rates; unmatched addresses are reported, never hidden.
source "$(dirname "$0")/common.sh"
ensure_migrated

# Addresses with a geocode in the road or kerb link to the nearest lot within this distance, as low confidence.
NEAREST_LOT_MAX_M=30
NEAREST_LOT_CANDIDATES=5

psql_db -q -v max_distance_m="$NEAREST_LOT_MAX_M" -v candidates="$NEAREST_LOT_CANDIDATES" \
  < "$PIPELINE_DIR/sql/address_lot.sql"
psql_db -t -A -F ' | ' -c "
SELECT 'addresses', count(*) FROM address
UNION ALL SELECT 'linked by legal_parcel_id', count(*) FROM address_lot WHERE method = 'legal_parcel_id'
UNION ALL SELECT 'linked by point_in_polygon', count(*) FROM address_lot WHERE method = 'point_in_polygon'
UNION ALL SELECT 'linked by nearest_lot (low confidence)', count(*) FROM address_lot WHERE method = 'nearest_lot'
UNION ALL SELECT 'unmatched', count(*) FROM address a WHERE NOT EXISTS (SELECT 1 FROM address_lot x WHERE x.gnaf_pid = a.gnaf_pid)
UNION ALL SELECT 'parcel-id links whose point is outside the lot', count(*)
  FROM address_lot x JOIN address a USING (gnaf_pid) JOIN lot l USING (lot_id)
  WHERE x.method = 'legal_parcel_id' AND NOT ST_Intersects(a.geom, l.geom)"
