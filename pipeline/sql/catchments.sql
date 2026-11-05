-- Builds school_catchment from stg.catchment_raw (the census module's catchments tool output). Run with no variables.
-- Shapes arrive as WKT in GDA94 (EPSG:4283) and are reprojected to GDA2020 (EPSG:7844); ST_MakeValid repairs self-touching rings, keeping only the
-- polygon parts of the result. A zone with nothing left after repair is dropped (and counted in the report by the script).
BEGIN;
TRUNCATE school_catchment RESTART IDENTITY;

INSERT INTO school_catchment (school_code, level, catch_type, name, years, added_on, enrolment_year, geom)
SELECT r.school_code, r.level, r.catch_type, r.name, NULLIF(r.years, ''), to_date(NULLIF(r.added_on, ''), 'YYYYMMDD'), r.enrolment_year, g.geom
FROM stg.catchment_raw r
CROSS JOIN LATERAL (
    SELECT ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_Transform(ST_GeomFromText(r.wkt, 4283), 7844)), 3)) AS geom
) g
WHERE NOT ST_IsEmpty(g.geom);

-- Per suburb: how much of it each zone covers (ADR 0062). Zones are cut into small pieces so the point-in-polygon join is quick, and a lot is counted once
-- per school and level. Zones covering under 5% of a suburb are dropped.
TRUNCATE suburb_catchment;

CREATE TEMP TABLE zone_piece ON COMMIT DROP AS
SELECT catchment_id, ST_Subdivide(geom, 256) AS geom FROM school_catchment;
CREATE INDEX zone_piece_geom_idx ON zone_piece USING GIST (geom);
ANALYZE zone_piece;

CREATE TEMP TABLE lot_point ON COMMIT DROP AS
SELECT lot_id, suburb_id, ST_PointOnSurface(geom) AS pt FROM lot;
CREATE INDEX lot_point_pt_idx ON lot_point USING GIST (pt);
ANALYZE lot_point;

CREATE TEMP TABLE lot_zone ON COMMIT DROP AS
SELECT DISTINCT p.lot_id, p.suburb_id, z.catchment_id
FROM lot_point p JOIN zone_piece z ON ST_Covers(z.geom, p.pt);

INSERT INTO suburb_catchment (suburb_id, school_code, level, catch_type, name, years, lots, lot_share, enrolment_year)
SELECT g.suburb_id, g.school_code, g.level, g.catch_type, g.name, g.years, g.lots, g.lots::real / t.n, g.enrolment_year
FROM (
    SELECT z.suburb_id, c.school_code, c.level, count(DISTINCT z.lot_id)::int AS lots,
           min(c.catch_type) AS catch_type, min(c.name) AS name, min(c.years) AS years, max(c.enrolment_year) AS enrolment_year
    FROM lot_zone z JOIN school_catchment c USING (catchment_id)
    GROUP BY z.suburb_id, c.school_code, c.level
) g
JOIN (SELECT suburb_id, count(*) AS n FROM lot GROUP BY suburb_id) t USING (suburb_id)
WHERE g.lots::real / t.n >= 0.05;

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('school_catchment', jsonb_build_object('catchments', (SELECT count(*) FROM stg.catchment_raw), 'loaded', (SELECT count(*) FROM school_catchment), 'suburb_zones', (SELECT count(*) FROM suburb_catchment)), now());

COMMIT;
