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

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('school_catchment', jsonb_build_object('catchments', (SELECT count(*) FROM stg.catchment_raw), 'loaded', (SELECT count(*) FROM school_catchment)), now());

COMMIT;
