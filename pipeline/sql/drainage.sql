-- Builds lot_drainage from stg.drainage_raw (the output of the drainage tool). Run with no variables.
-- A row for a lot that no longer exists is dropped; a row with a missing or impossible figure is skipped.
BEGIN;
TRUNCATE lot_drainage;

INSERT INTO lot_drainage (lot_id, height_above_drainage_m, stream_catchment_ha, computed_on)
SELECT r.lot_id, r.height_above_drainage_m, r.stream_catchment_ha, CURRENT_DATE
FROM stg.drainage_raw r
JOIN lot l ON l.lot_id = r.lot_id
WHERE r.height_above_drainage_m >= 0 AND r.stream_catchment_ha > 0;

COMMIT;
