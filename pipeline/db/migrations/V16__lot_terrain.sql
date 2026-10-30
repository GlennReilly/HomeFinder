-- Height above sea level and slope per lot, from Geoscience Australia's 5 m LiDAR DEM (ADR 0036). Raster support is needed
-- by load-dem.sh while it works; the table below holds only the per-lot results. lot_id has no foreign key because lots are
-- rebuilt on each cadastre load; the server joins on it.
CREATE EXTENSION IF NOT EXISTS postgis_raster;

CREATE TABLE lot_terrain (
    lot_id          TEXT PRIMARY KEY,
    elevation_m     REAL NOT NULL,
    mean_slope_pct  REAL,
    max_slope_pct   REAL,
    pixels          INTEGER NOT NULL,
    dem_loaded_on   DATE NOT NULL
);
