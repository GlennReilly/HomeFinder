CREATE SCHEMA IF NOT EXISTS stg;

-- Locality names in the pilot region; loaded from pipeline/config/pilot_localities.csv.
CREATE TABLE pilot_locality (
    name     TEXT PRIMARY KEY,
    postcode TEXT
);

-- Principal, current G-NAF addresses in the pilot region. Geometry is GDA2020 (EPSG:7844) as supplied.
CREATE TABLE address (
    gnaf_pid        TEXT PRIMARY KEY,
    flat_number     TEXT,
    number_first    TEXT,
    street_name     TEXT,
    street_type     TEXT,
    locality_name   TEXT NOT NULL,
    state           TEXT NOT NULL,
    postcode        TEXT,
    legal_parcel_id TEXT,
    confidence      TEXT,
    geocode_type    TEXT,
    source_release  TEXT NOT NULL,
    geom            geometry(Point, 7844) NOT NULL
);

CREATE INDEX address_geom_idx ON address USING GIST (geom);
CREATE INDEX address_locality_idx ON address (locality_name);
