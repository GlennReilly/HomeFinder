-- Builds lot_terrain from stg.dem_raw. Run with: psql -v srid=28356 -v pixel_m=5 -v tile_px=256 -v min_m=-50 -v max_m=1500
-- The service gives degrees (GDA94), so each file is first resampled (bilinear) to a pixel_m metre grid in MGA zone 56; slope is
-- measured on that grid. Tiles outside every pilot suburb are dropped. Heights outside min_m..max_m (fill values) count as no data. Slope comes from
-- the 3x3 neighbourhood of each tile (percent, rise over run), so a strip one pixel wide along each tile edge has no slope and
-- those pixels are left out of the slope figures. A lot's figures are over the pixels whose centre lies inside it; a lot too
-- small to hold a pixel centre takes the pixel under its representative point. The cadastre is GDA2020 and the DEM GDA94: the
-- 1.8 m difference is under half a pixel, so the lot outline is used as it is on the DEM's grid.
BEGIN;

CREATE TEMP TABLE pilot_area ON COMMIT DROP AS
SELECT ST_Union(ST_Transform(geom, :srid)) AS geom FROM suburb;

-- The service declares no no-data value and its fill pixels can be NaN or absurd, so anything outside min_m..max_m is first set
-- to -9999, declared as no data, and only then resampled, so that fill never blends into heights or becomes a zero height.
CREATE TEMP TABLE dem_clean ON COMMIT DROP AS
SELECT ST_Transform(
           ST_MapAlgebra(
               ST_SetBandNoDataValue(rast, 1, -9999), 1, '32BF',
               'CASE WHEN [rast] BETWEEN ' || :min_m || ' AND ' || :max_m || ' THEN [rast] ELSE -9999 END', -9999
           ), :srid, :pixel_m, :pixel_m, 'Bilinear') AS rast
FROM stg.dem_raw;

CREATE TEMP TABLE dem_tile ON COMMIT DROP AS
SELECT t.tile_id, t.height, ST_Slope(t.height, 1, '32BF', 'PERCENT') AS slope
FROM (
    SELECT row_number() OVER () AS tile_id, ST_SetBandNoDataValue(ST_SetSRID(tile, :srid), 1, -9999) AS height
    FROM (SELECT ST_Tile(rast, :tile_px, :tile_px) AS tile FROM dem_clean) x
) t, pilot_area p
WHERE t.height IS NOT NULL AND ST_Intersects(ST_ConvexHull(t.height), p.geom);

CREATE INDEX ON dem_tile USING GIST (ST_ConvexHull(height));
ANALYZE dem_tile;

-- One row per lot and tile it touches; tiles are combined below.
CREATE TEMP TABLE lot_pairs ON COMMIT DROP AS
SELECT l.lot_id, h.count AS height_n, h.sum AS height_sum, s.count AS slope_n, s.sum AS slope_sum, s.max AS slope_max
FROM lot l
JOIN dem_tile t ON ST_Intersects(ST_ConvexHull(t.height), ST_SetSRID(l.geom_mga, :srid))
CROSS JOIN LATERAL ST_SummaryStats(ST_Clip(t.height, 1, ST_SetSRID(l.geom_mga, :srid), NULL::float8, true), 1, true) h
CROSS JOIN LATERAL ST_SummaryStats(ST_Clip(t.slope, 1, ST_SetSRID(l.geom_mga, :srid), NULL::float8, true), 1, true) s;

TRUNCATE lot_terrain;

INSERT INTO lot_terrain (lot_id, elevation_m, mean_slope_pct, max_slope_pct, pixels, dem_loaded_on)
SELECT lot_id,
       (sum(height_sum) / sum(height_n))::real,
       (sum(slope_sum) / NULLIF(sum(slope_n), 0))::real,
       max(slope_max)::real,
       sum(height_n)::int,
       CURRENT_DATE
FROM lot_pairs
WHERE height_n > 0
GROUP BY lot_id;

-- Lots with no pixel centre inside them: the pixel under the representative point.
INSERT INTO lot_terrain (lot_id, elevation_m, mean_slope_pct, max_slope_pct, pixels, dem_loaded_on)
SELECT l.lot_id, v.height::real, v.slope::real, v.slope::real, 1, CURRENT_DATE
FROM lot l
JOIN dem_tile t ON ST_Intersects(ST_ConvexHull(t.height), ST_SetSRID(ST_PointOnSurface(l.geom_mga), :srid))
CROSS JOIN LATERAL (
    SELECT ST_Value(t.height, 1, ST_SetSRID(ST_PointOnSurface(l.geom_mga), :srid)) AS height,
           ST_Value(t.slope, 1, ST_SetSRID(ST_PointOnSurface(l.geom_mga), :srid)) AS slope
) v
WHERE v.height IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lot_terrain z WHERE z.lot_id = l.lot_id)
ON CONFLICT (lot_id) DO NOTHING;

COMMIT;
