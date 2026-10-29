#!/usr/bin/env bash
# Links each G-NAF address to a cadastre lot: legal parcel id first (when exactly one lot has that key), then the
# lot containing the address point. Prints the match rates; unmatched addresses are reported, never hidden.
source "$(dirname "$0")/common.sh"
ensure_migrated

psql_db -q < "$PIPELINE_DIR/sql/address_lot.sql"
psql_db -t -A -F ' | ' -c "
SELECT 'addresses', count(*) FROM address
UNION ALL SELECT 'linked by legal_parcel_id', count(*) FROM address_lot WHERE method = 'legal_parcel_id'
UNION ALL SELECT 'linked by point_in_polygon', count(*) FROM address_lot WHERE method = 'point_in_polygon'
UNION ALL SELECT 'unmatched', count(*) FROM address a WHERE NOT EXISTS (SELECT 1 FROM address_lot x WHERE x.gnaf_pid = a.gnaf_pid)
UNION ALL SELECT 'parcel-id links whose point is outside the lot', count(*)
  FROM address_lot x JOIN address a USING (gnaf_pid) JOIN lot l USING (lot_id)
  WHERE x.method = 'legal_parcel_id' AND NOT ST_Intersects(a.geom, l.geom)"
