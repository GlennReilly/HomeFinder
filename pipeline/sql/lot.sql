-- Rebuilds `lot` from stg.lot_json. Duplicates from overlapping suburb envelopes are removed by cadid.
-- parcel_key matches G-NAF LEGAL_PARCEL_ID: lot[/section]/plan, with the "DP" prefix dropped ("SP" is kept).
BEGIN;

TRUNCATE lot CASCADE;

INSERT INTO lot (lot_id, lot_id_string, parcel_key, plan_label, lot_number, section_number, class_subtype,
                 title_status, suburb_id, geom)
SELECT DISTINCT ON (p ->> 'cadid')
       p ->> 'cadid',
       p ->> 'lotidstring',
       (p ->> 'lotnumber') || COALESCE('/' || NULLIF(p ->> 'sectionnumber', ''), '')
           || '/' || regexp_replace(COALESCE(p ->> 'planlabel', ''), '^DP', ''),
       p ->> 'planlabel',
       p ->> 'lotnumber',
       NULLIF(p ->> 'sectionnumber', ''),
       (p ->> 'classsubtype')::int,
       (p ->> 'itstitlestatus')::int,
       s.suburb_id,
       geo.g
FROM (SELECT doc::jsonb AS f FROM stg.lot_json) j,
     LATERAL (SELECT f -> 'properties' AS p) props,
     LATERAL (SELECT ST_Multi(ST_CollectionExtract(
                  ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON((f -> 'geometry')::text), 7844)), 3)) AS g) geo
JOIN suburb s ON ST_Within(ST_PointOnSurface(geo.g), s.geom)
WHERE NOT ST_IsEmpty(geo.g)
ORDER BY p ->> 'cadid', s.suburb_id;

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('lot', jsonb_build_object('lots_staged', (SELECT count(*) FROM stg.lot_json),
                                  'lots_loaded', (SELECT count(*) FROM lot)), now());

COMMIT;
