-- Height above nearest drainage per lot, worked out from the 5 m DEM by the Kotlin tool in the `drainage` module (ADR 0040). Only the
-- per-lot results are kept. lot_id has no foreign key because lots are rebuilt on each cadastre load; the server joins on it.
CREATE TABLE lot_drainage (
    lot_id                  TEXT PRIMARY KEY,
    -- Ground height above the first stream pixel the lot's water reaches, in metres (never negative).
    height_above_drainage_m REAL NOT NULL CHECK (height_above_drainage_m >= 0),
    -- How much ground drains through that stream pixel, in hectares: the size of the stream the lot drains to.
    stream_catchment_ha     REAL NOT NULL CHECK (stream_catchment_ha > 0),
    computed_on             DATE NOT NULL
);
