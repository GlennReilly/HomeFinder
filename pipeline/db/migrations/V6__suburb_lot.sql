-- Official NSW suburb boundaries and cadastre lots for the pilot region.
-- Geometry is stored as supplied (GDA2020 geographic, 7844); *_mga columns are GDA2020 / MGA zone 56 (7856)
-- for area, distance and slope work.
CREATE TABLE suburb (
    suburb_id TEXT PRIMARY KEY,
    name      TEXT NOT NULL,
    postcode  TEXT,
    geom      geometry(MultiPolygon, 7844) NOT NULL,
    geom_mga  geometry(MultiPolygon, 7856) GENERATED ALWAYS AS (ST_Transform(geom, 7856)) STORED
);

CREATE INDEX suburb_geom_idx ON suburb USING GIST (geom);

CREATE TABLE lot (
    lot_id         TEXT PRIMARY KEY,
    lot_id_string  TEXT,
    parcel_key     TEXT,
    plan_label     TEXT,
    lot_number     TEXT,
    section_number TEXT,
    class_subtype  INTEGER,
    title_status   INTEGER,
    suburb_id      TEXT NOT NULL REFERENCES suburb (suburb_id),
    geom           geometry(MultiPolygon, 7844) NOT NULL,
    geom_mga       geometry(MultiPolygon, 7856) GENERATED ALWAYS AS (ST_Transform(geom, 7856)) STORED,
    area_m2        DOUBLE PRECISION GENERATED ALWAYS AS (ST_Area(ST_Transform(geom, 7856))) STORED
);

CREATE INDEX lot_geom_idx ON lot USING GIST (geom);
CREATE INDEX lot_parcel_key_idx ON lot (parcel_key);
CREATE INDEX lot_suburb_idx ON lot (suburb_id);

CREATE TABLE address_lot (
    gnaf_pid TEXT PRIMARY KEY REFERENCES address (gnaf_pid),
    lot_id   TEXT NOT NULL REFERENCES lot (lot_id) ON DELETE CASCADE,
    method   TEXT NOT NULL CHECK (method IN ('legal_parcel_id', 'point_in_polygon'))
);

CREATE INDEX address_lot_lot_idx ON address_lot (lot_id);
