# 0014. Suburb vector tiles served by the API

Status: Accepted

## Context
The map must draw suburb outlines and names at wide zoom (spec: zoom 5 to 12 selects suburbs). The spec says to serve
boundaries as vector tiles with geometry simplified per zoom level, not as one large GeoJSON file, and to compute
heavy geometry in the pipeline so request handlers only read. It names PMTiles or Martin as tile server options and
lists `GET /tiles/{layer}/{z}/{x}/{y}.pbf` among the API endpoints.

## Decision
- The Ktor API serves the tiles itself at `/tiles/suburbs/{z}/{x}/{y}.pbf` (Mapbox vector tile, zoom 5 to 14; a tile
  outside that range or with nothing in it is `204 No Content`; clients over-zoom level 14). No separate tile server.
- Tiles are assembled with PostGIS `ST_AsMVT` from two tables the pipeline precomputes with the SQL function
  `refresh_suburb_tile_data()` (migration V9): `suburb_tile_geometry` (each suburb simplified to about one pixel for
  each served zoom, in Web Mercator) and `suburb_label` (one label point per suburb, the centre of its largest
  inscribed circle, with its area). `load-suburbs.sh` runs it after every suburb load; `refresh-suburb-tiles.sh` runs
  it alone. A tile request is then an indexed read.
- A tile has two layers: `suburb` (polygons with `suburb_id`, `name`, `postcode`) and `suburb_label` (one point per
  suburb with the same properties and `area_km2`). A label point falls in exactly one tile, so a name is never drawn
  twice.
- Tiles are protected like the rest of the API. The key may be sent as the `X-API-Key` header or, on `/tiles` only, as
  a `key` query parameter, because the Android map style cannot easily add headers. The header wins if both are sent.
- The API gets an optional database configuration (`DB_URL`, `DB_USER`, `DB_PASSWORD`, all or none). Without it tile
  requests answer 503 and `/health` reports `database: not_configured`; with it `/health` reports `up` or `down`.
- New server dependencies: the PostgreSQL JDBC driver (42.7.14) and the HikariCP connection pool (7.1.0).
- PR 8 is split: this PR (8a) is the server side; the client layers (Android MapLibre and the desktop overlay) are 8b,
  built after the map and API PRs merge.

## Consequences
- Measured on the real 173-suburb database: computing simplification, label circles and reprojection per request took
  about 1.3 s for a wide tile; reading the precomputed tables takes a few milliseconds. A wide tile is about 20 KB.
- The precomputed zoom range and the API's range (`TileContract`) must agree; a test checks the table's zooms against
  the contract.
- Simplifying each suburb on its own can leave hairline gaps or overlaps between neighbours at wide zoom.
- A key in a URL can end up in logs or history; acceptable on a development network, and only for tiles.
- The tile SQL is tested against a real PostGIS (`server/tests/run-db-tests.sh`, also a CI job); those tests are
  skipped when no test database is configured.
