-- NSW public schools from the Department of Education's master dataset (ADR 0034), with the ICSEA value ACARA publishes for them.
-- ICSEA is NULL where it is not published ("np", small or senior secondary schools). suburb_id is the pilot suburb whose boundary
-- contains the school (NULL elsewhere); it is derived on each load, so there is no foreign key.
CREATE TABLE school (
    school_code   INTEGER PRIMARY KEY,
    name          TEXT NOT NULL,
    town_suburb   TEXT,
    postcode      TEXT,
    level_label   TEXT,
    selective     TEXT,
    icsea         INTEGER CHECK (icsea BETWEEN 400 AND 1500),
    enrolment_fte NUMERIC,
    website       TEXT,
    geom          geometry(Point, 7844) NOT NULL,
    suburb_id     TEXT,
    extracted_on  DATE
);

CREATE INDEX school_geom_idx ON school USING GIST (geom);
CREATE INDEX school_suburb_idx ON school (suburb_id);
