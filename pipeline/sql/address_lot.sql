BEGIN;

TRUNCATE address_lot;

WITH unique_key AS (
    SELECT parcel_key, min(lot_id) AS lot_id FROM lot GROUP BY parcel_key HAVING count(*) = 1
)
INSERT INTO address_lot (gnaf_pid, lot_id, method)
SELECT a.gnaf_pid, k.lot_id, 'legal_parcel_id'
FROM address a
JOIN unique_key k ON k.parcel_key = a.legal_parcel_id;

-- Several lots can contain a point (stratum); the smallest lot wins, then the lowest id, so reruns are stable.
INSERT INTO address_lot (gnaf_pid, lot_id, method)
SELECT DISTINCT ON (a.gnaf_pid) a.gnaf_pid, l.lot_id, 'point_in_polygon'
FROM address a
JOIN lot l ON ST_Within(a.geom, l.geom)
WHERE NOT EXISTS (SELECT 1 FROM address_lot x WHERE x.gnaf_pid = a.gnaf_pid)
ORDER BY a.gnaf_pid, l.area_m2, l.lot_id;

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('address_lot', jsonb_build_object('addresses', (SELECT count(*) FROM address),
                                          'lots', (SELECT count(*) FROM lot)), now());

COMMIT;
