-- Council (LGA) polygons for the pilot region, used to prove no in-council address is missed.
CREATE TABLE lga (
    name TEXT PRIMARY KEY,
    geom geometry(MultiPolygon, 7844) NOT NULL
);

CREATE INDEX lga_geom_idx ON lga USING GIST (geom);
