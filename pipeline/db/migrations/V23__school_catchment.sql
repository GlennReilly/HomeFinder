-- Current NSW public school catchments (intake areas) from the Department of Education's "School intake zones" download (ADR 0056).
-- One row per zone: a school can have more than one (a split zone) and a lot can lie in several (an infants zone inside a primary zone, a central
-- school's primary and high zones). school_code matches school.school_code (ADR 0034) where the school is in that data. Shapes are reprojected from
-- GDA94 (EPSG:4283) to GDA2020 (EPSG:7844), the system the lots use. Department of Education data, Creative Commons Attribution 3.0 Australia.
CREATE TABLE school_catchment (
    catchment_id   SERIAL PRIMARY KEY,
    school_code    INTEGER NOT NULL,
    -- PRIMARY (the primary file: primary, infants and central schools' primary years) or HIGH (the secondary file).
    level          TEXT NOT NULL CHECK (level IN ('PRIMARY', 'HIGH')),
    -- The Department's own type: PRIMARY, INFANTS, CENTRAL_PRIMARY, HIGH_COED, HIGH_BOYS, HIGH_GIRLS, CENTRAL_HIGH.
    catch_type     TEXT NOT NULL,
    -- The Department's short name, for example "Grafton PS".
    name           TEXT NOT NULL,
    -- The year groups the zone serves, for example "K-6" or "7-12".
    years          TEXT,
    added_on       DATE,
    -- The enrolment year the zones apply to (from catchment_sf_info.json), for example 2026.
    enrolment_year INTEGER,
    loaded_on      DATE NOT NULL DEFAULT CURRENT_DATE,
    geom           geometry(MultiPolygon, 7844) NOT NULL
);

CREATE INDEX school_catchment_geom_idx ON school_catchment USING GIST (geom);
CREATE INDEX school_catchment_school_idx ON school_catchment (school_code);
