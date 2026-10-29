-- Precomputed tile geometry for suburbs, so serving a tile is an indexed read (the work is done once, here).
-- suburb_tile_geometry holds each suburb simplified for every served zoom level in Web Mercator;
-- suburb_label holds one label point per suburb (centre of its largest inscribed circle) and its area.
CREATE TABLE suburb_tile_geometry (
    suburb_id TEXT    NOT NULL REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    zoom      INTEGER NOT NULL,
    geom      geometry(MultiPolygon, 3857) NOT NULL,
    PRIMARY KEY (suburb_id, zoom)
);

CREATE INDEX suburb_tile_geometry_geom_idx ON suburb_tile_geometry USING GIST (geom);
CREATE INDEX suburb_tile_geometry_zoom_idx ON suburb_tile_geometry (zoom);

CREATE TABLE suburb_label (
    suburb_id TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    area_km2  NUMERIC NOT NULL,
    geom      geometry(Point, 3857) NOT NULL
);

CREATE INDEX suburb_label_geom_idx ON suburb_label USING GIST (geom);

-- Rebuilds both tables from `suburb`. Idempotent: a rerun gives the same rows. Zoom range and the one-pixel
-- simplification match the API's TileContract (zoom 5 to 14, 256 px tiles).
CREATE FUNCTION refresh_suburb_tile_data() RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    min_zoom CONSTANT integer := 5;
    max_zoom CONSTANT integer := 14;
    world_width_metres CONSTANT double precision := 40075016.685578488;
    tile_pixels CONSTANT double precision := 256;
    zoom_level integer;
BEGIN
    TRUNCATE suburb_tile_geometry, suburb_label;

    INSERT INTO suburb_label (suburb_id, area_km2, geom)
    SELECT suburb_id,
           round((ST_Area(geom_mga) / 1000000.0)::numeric, 3),
           ST_Transform((ST_MaximumInscribedCircle(geom_mga)).center, 3857)
    FROM suburb;

    FOR zoom_level IN min_zoom..max_zoom LOOP
        INSERT INTO suburb_tile_geometry (suburb_id, zoom, geom)
        SELECT suburb_id, zoom_level, simplified
        FROM (
            SELECT suburb_id,
                   ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SimplifyPreserveTopology(
                       ST_Transform(geom, 3857), world_width_metres / (tile_pixels * power(2, zoom_level)))), 3)) AS simplified
            FROM suburb
        ) s
        WHERE NOT ST_IsEmpty(simplified);
    END LOOP;
END
$$;
