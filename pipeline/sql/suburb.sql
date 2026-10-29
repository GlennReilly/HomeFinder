-- Rebuilds `suburb` from stg.suburb_json. Only suburbs that overlap an LGA polygon are kept, so a same-named
-- suburb elsewhere in NSW is ignored.
BEGIN;

TRUNCATE suburb CASCADE;

INSERT INTO suburb (suburb_id, name, postcode, geom)
SELECT f -> 'properties' ->> 'cadid',
       f -> 'properties' ->> 'suburbname',
       f -> 'properties' ->> 'postcode',
       g
FROM (SELECT doc::jsonb AS f FROM stg.suburb_json) j,
     LATERAL (SELECT ST_Multi(ST_CollectionExtract(
                  ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON((f -> 'geometry')::text), 7844)), 3)) AS g) geo
WHERE EXISTS (SELECT 1 FROM lga WHERE ST_Intersects(lga.geom, geo.g));

COMMIT;
