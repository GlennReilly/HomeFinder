-- Builds osm_feature from stg.osm_raw. Run with: psql -v min_park_m2=5000 -v beach_coast_m=150 -v osm_base='2026-10-30T01:00:00Z'
-- Nodes are points, closed ways polygons, other ways lines, relations the areas their member lines enclose. WGS84 is
-- stored as GDA2020 (a difference of about 1.8 m).
BEGIN;

-- Geometry of every raw row, in the projected CRS, with relations joined into areas.
CREATE TEMP TABLE osm_built ON COMMIT DROP AS
SELECT osm_type, osm_id, kind, NULLIF(name, '') AS name, g AS geom
FROM (
    SELECT osm_type, osm_id, kind, name,
           CASE WHEN osm_type = 'relation'
                THEN ST_BuildArea(ST_LineMerge(ST_CollectionExtract(ST_SetSRID(ST_GeomFromGeoJSON(geojson), 7844), 2)))
                ELSE ST_SetSRID(ST_GeomFromGeoJSON(geojson), 7844) END AS g
    FROM stg.osm_raw
    WHERE kind IS NOT NULL AND geojson IS NOT NULL
) t
WHERE g IS NOT NULL AND NOT ST_IsEmpty(g);

CREATE INDEX ON osm_built USING GIST (geom);

-- The coastline: closed ways are islands, so only their edge counts.
CREATE TEMP TABLE osm_coast ON COMMIT DROP AS
SELECT ST_Transform(CASE WHEN GeometryType(geom) IN ('POLYGON', 'MULTIPOLYGON') THEN ST_Boundary(geom) ELSE geom END, 7856) AS geom
FROM osm_built WHERE kind = 'coastline';
CREATE INDEX ON osm_coast USING GIST (geom);

TRUNCATE osm_feature;

INSERT INTO osm_feature (osm_type, osm_id, kind, name, geom, area_m2)
SELECT b.osm_type, b.osm_id, b.kind, b.name, b.geom,
       CASE WHEN GeometryType(b.geom) IN ('POLYGON', 'MULTIPOLYGON') THEN ST_Area(ST_Transform(b.geom, 7856)) END
FROM osm_built b
WHERE
    -- An ocean beach lies on the mapped coastline; lake and river beaches do not.
    (b.kind = 'beach' AND EXISTS (SELECT 1 FROM osm_coast c WHERE ST_DWithin(c.geom, ST_Transform(b.geom, 7856), :beach_coast_m)))
    -- A park is an area of at least the minimum size; mapped lines and points are not parks.
    OR (b.kind = 'park' AND GeometryType(b.geom) IN ('POLYGON', 'MULTIPOLYGON') AND ST_Area(ST_Transform(b.geom, 7856)) >= :min_park_m2)
    OR b.kind IN ('supermarket', 'mall')
ON CONFLICT DO NOTHING;

TRUNCATE osm_import;
INSERT INTO osm_import (imported_at, osm_base, features)
SELECT now(), NULLIF(:'osm_base', '')::timestamptz, count(*) FROM osm_feature;

COMMIT;
ANALYZE osm_feature;
