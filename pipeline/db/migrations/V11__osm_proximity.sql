-- OpenStreetMap features around the pilot area and, per lot, the nearest of each kind (ADR 0023). Features are stored
-- in GDA2020 (OSM's WGS84 differs from it by about 1.8 m, far below what distances here need) with a generated
-- projected copy in MGA zone 56 for metre distances and areas.
CREATE TABLE osm_feature (
    osm_type TEXT NOT NULL CHECK (osm_type IN ('node', 'way', 'relation')),
    osm_id   BIGINT NOT NULL,
    kind     TEXT NOT NULL CHECK (kind IN ('beach', 'park', 'supermarket', 'mall')),
    name     TEXT,
    geom     geometry(Geometry, 7844) NOT NULL,
    geom_mga geometry(Geometry, 7856) GENERATED ALWAYS AS (ST_Transform(geom, 7856)) STORED,
    area_m2  DOUBLE PRECISION,
    PRIMARY KEY (osm_type, osm_id, kind)
);

-- One partial index per kind, so "nearest park" never has to walk past nearer beaches and shops.
CREATE INDEX osm_feature_beach_idx ON osm_feature USING GIST (geom_mga) WHERE kind = 'beach';
CREATE INDEX osm_feature_park_idx ON osm_feature USING GIST (geom_mga) WHERE kind = 'park';
CREATE INDEX osm_feature_supermarket_idx ON osm_feature USING GIST (geom_mga) WHERE kind = 'supermarket';
CREATE INDEX osm_feature_mall_idx ON osm_feature USING GIST (geom_mga) WHERE kind = 'mall';

-- The latest import: when it ran and the age of the OSM data it holds (the as-of date shown on the card).
CREATE TABLE osm_import (
    only_row    BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (only_row),
    imported_at TIMESTAMPTZ NOT NULL,
    osm_base    TIMESTAMPTZ,
    features    INTEGER NOT NULL
);

-- The nearest features to each lot's representative point, nearest first (rank 1). Distances are straight-line metres
-- to the feature's edge (zero inside a park).
CREATE TABLE lot_nearest (
    lot_id     TEXT NOT NULL REFERENCES lot (lot_id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('beach', 'park', 'supermarket', 'mall')),
    rank       SMALLINT NOT NULL,
    osm_type   TEXT NOT NULL,
    osm_id     BIGINT NOT NULL,
    name       TEXT,
    distance_m DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (lot_id, kind, rank)
);

-- Park area (square metres) within the radius of each lot's representative point.
CREATE TABLE lot_amenity (
    lot_id            TEXT PRIMARY KEY REFERENCES lot (lot_id) ON DELETE CASCADE,
    park_area_radius_m2 DOUBLE PRECISION NOT NULL
);

-- Rebuilds lot_nearest and lot_amenity from osm_feature. Idempotent: the same features and lots give the same rows.
-- The nearest are found with the index (a few more candidates than needed, then ordered by true distance), because the
-- index orders by bounding box, which is not exact for large polygons.
CREATE FUNCTION refresh_lot_proximity(p_nearest_parks integer, p_park_radius_m double precision)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    spec record;
    candidate_factor CONSTANT integer := 4;
BEGIN
    TRUNCATE lot_nearest, lot_amenity;

    FOR spec IN SELECT * FROM (VALUES ('beach', 1), ('park', p_nearest_parks), ('supermarket', 1), ('mall', 1)) AS t(kind, n) LOOP
        -- The kind is a literal in the statement so the planner can use that kind's partial index.
        EXECUTE format($sql$
            INSERT INTO lot_nearest (lot_id, kind, rank, osm_type, osm_id, name, distance_m)
            SELECT p.lot_id, %L, row_number() OVER (PARTITION BY p.lot_id ORDER BY n.d, n.osm_id), n.osm_type, n.osm_id, n.name, n.d
            FROM (SELECT lot_id, ST_PointOnSurface(geom_mga) AS pt FROM lot) p
            CROSS JOIN LATERAL (
                SELECT c.osm_type, c.osm_id, c.name, ST_Distance(c.geom_mga, p.pt) AS d
                FROM (SELECT * FROM osm_feature f WHERE f.kind = %L ORDER BY f.geom_mga <-> p.pt LIMIT %s) c
                ORDER BY d, c.osm_id LIMIT %s
            ) n
        $sql$, spec.kind, spec.kind, spec.n * candidate_factor, spec.n);
    END LOOP;

    INSERT INTO lot_amenity (lot_id, park_area_radius_m2)
    SELECT p.lot_id,
           COALESCE((SELECT sum(ST_Area(ST_Intersection(p.buf, f.geom_mga)))
                     FROM osm_feature f
                     WHERE f.kind = 'park' AND f.geom_mga && p.buf AND ST_Intersects(f.geom_mga, p.buf)), 0)
    FROM (SELECT lot_id, ST_Buffer(ST_PointOnSurface(geom_mga), p_park_radius_m) AS buf FROM lot) p;

    INSERT INTO pipeline_run (stage, input_versions, finished_at)
    VALUES ('lot_proximity',
            jsonb_build_object('features', (SELECT count(*) FROM osm_feature), 'lots', (SELECT count(*) FROM lot),
                               'nearest_parks', p_nearest_parks, 'park_radius_m', p_park_radius_m),
            now());
END
$$;
