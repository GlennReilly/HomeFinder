-- Suburb roll-ups of the OpenStreetMap features (ADR 0033): how far the nearest ocean beach is, how much of the suburb is
-- park, and how many shopping centres and supermarkets are in or near it. Rebuilt from osm_feature by refresh_suburb_amenity().
CREATE TABLE suburb_amenity (
    suburb_id            TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    beach_from_centre_m  DOUBLE PRECISION,
    beach_from_edge_m    DOUBLE PRECISION,
    beach_name           TEXT,
    suburb_area_m2       DOUBLE PRECISION NOT NULL,
    park_area_m2         DOUBLE PRECISION NOT NULL,
    shopping_centres     INTEGER NOT NULL,
    supermarkets         INTEGER NOT NULL
);

-- Straight-line distances in metres (MGA zone 56), like the per-lot figures (ADR 0023). "Centre" is the point the suburb's name
-- is drawn at; "edge" is the nearest point of its boundary. Park area is the union of parks of at least the minimum size clipped
-- to the suburb, so overlapping parks are not counted twice. Shops count when they lie in the suburb or within the radius of it.
-- Idempotent: the same features and suburbs give the same rows.
CREATE FUNCTION refresh_suburb_amenity(p_shopping_radius_m double precision)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    TRUNCATE suburb_amenity;

    INSERT INTO suburb_amenity (suburb_id, beach_from_centre_m, beach_from_edge_m, beach_name, suburb_area_m2, park_area_m2,
                                shopping_centres, supermarkets)
    SELECT s.suburb_id,
           b.from_centre, b.from_edge, b.name,
           ST_Area(s.geom_mga),
           COALESCE((SELECT ST_Area(ST_Intersection(s.geom_mga, ST_UnaryUnion(ST_Collect(ST_MakeValid(f.geom_mga)))))
                     FROM osm_feature f
                     WHERE f.kind = 'park' AND f.geom_mga && s.geom_mga AND ST_Intersects(f.geom_mga, s.geom_mga)), 0),
           (SELECT count(*) FROM osm_feature f WHERE f.kind = 'mall' AND ST_DWithin(f.geom_mga, s.geom_mga, p_shopping_radius_m)),
           (SELECT count(*) FROM osm_feature f WHERE f.kind = 'supermarket' AND ST_DWithin(f.geom_mga, s.geom_mga, p_shopping_radius_m))
    FROM suburb s
    LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
    -- The beach nearest the suburb's edge, then its distance from the centre. The index orders by bounding box, so a few
    -- candidates are taken and re-ordered by true distance.
    LEFT JOIN LATERAL (
        SELECT c.name,
               ST_Distance(c.geom_mga, s.geom_mga) AS from_edge,
               ST_Distance(c.geom_mga, COALESCE(ST_Transform(l.geom, 7856), ST_PointOnSurface(s.geom_mga))) AS from_centre
        FROM (SELECT * FROM osm_feature f WHERE f.kind = 'beach' ORDER BY f.geom_mga <-> s.geom_mga LIMIT 5) c
        ORDER BY from_edge LIMIT 1
    ) b ON TRUE;

    INSERT INTO pipeline_run (stage, input_versions, finished_at)
    VALUES ('suburb_amenity',
            jsonb_build_object('features', (SELECT count(*) FROM osm_feature), 'suburbs', (SELECT count(*) FROM suburb),
                               'shopping_radius_m', p_shopping_radius_m),
            now());
END
$$;
