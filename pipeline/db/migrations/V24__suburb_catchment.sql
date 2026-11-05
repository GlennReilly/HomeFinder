-- For each suburb, the school catchments (V23) its lots lie in and how much of the suburb each covers (ADR 0062). A lot counts for a zone when its
-- representative point (ST_PointOnSurface) is inside it, as on the Lot Card (ADR 0056). lot_share is lots inside the zone / all lots in the suburb;
-- a school with more than one zone row of a level (a split zone) is counted once per lot. Only zones covering at least 5% of the suburb are kept.
-- Derived on each catchment load (load-catchments.sh), so it needs the lots loaded first. Department of Education data, Creative Commons Attribution 3.0 Australia.
CREATE TABLE suburb_catchment (
    suburb_id      TEXT NOT NULL REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    school_code    INTEGER NOT NULL,
    level          TEXT NOT NULL CHECK (level IN ('PRIMARY', 'HIGH')),
    -- The Department's own zone type, short name and years, as in school_catchment (one of the school's zone rows).
    catch_type     TEXT NOT NULL,
    name           TEXT NOT NULL,
    years          TEXT,
    lots           INTEGER NOT NULL CHECK (lots > 0),
    lot_share      REAL NOT NULL CHECK (lot_share > 0 AND lot_share <= 1),
    enrolment_year INTEGER,
    PRIMARY KEY (suburb_id, school_code, level)
);

CREATE INDEX suburb_catchment_school_idx ON suburb_catchment (school_code);
