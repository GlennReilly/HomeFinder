# Home Finder

Look up any NSW address or suburb on a map and see the factors that decide a purchase. See
[the spec](docs/spec/) for the product and technical design.

## Modules

| Module | Purpose |
| --- | --- |
| `shared` | Models and serialization (JVM + JS) |
| `client` | UI-free client logic: API client, suburb selection, Suburb Card rows and card HTML (JVM + JS) |
| `webApp` | The web client: Kotlin/JS, MapLibre GL JS and a DOM UI ([webApp/README.md](webApp/README.md)) |
| `server` | Ktor API |
| `pipeline` | Data pipeline: PostGIS, G-NAF, suburbs, lots, sales statistics ([pipeline/README.md](pipeline/README.md)) |

The product is a web app ([ADR 0019](docs/adr/0019-pivot-to-web.md)); the Android and desktop clients were retired.

## Build and test

Requires JDK 17. Gradle downloads Node and the npm packages for the web build.

```bash
./gradlew build
```

## Run it locally

Start the API (below), then the web dev server, and open http://localhost:8081. The first visit asks for the API key.

```bash
./gradlew :webApp:jsBrowserDevelopmentRun --continuous
```

## API server

```bash
cp server/.env.example server/.env   # then set API_KEY (16+ characters)
```
```bash
./gradlew :server:run
```

The server listens on `0.0.0.0:8080`. `GET /health` is open; everything under `/v1` needs the `X-API-Key` header.
The web app sends the key you enter in the browser. See [ADR 0013](docs/adr/0013-api-skeleton-auth-and-android-link.md)
(its Android parts are superseded by ADR 0019).

### Suburb tiles

`GET /tiles/suburbs/{z}/{x}/{y}.pbf` serves suburb boundaries and name points as vector tiles (zoom 5 to 14). They need
the PostGIS database: set `DB_URL`, `DB_USER` and `DB_PASSWORD` in `server/.env`. Loading suburbs
(`pipeline/scripts/load-suburbs.sh`) precomputes the tile data; `pipeline/scripts/refresh-suburb-tiles.sh` rebuilds it
alone. The key can be the `X-API-Key` header or a `?key=` query parameter on tiles. Tile SQL tests:
`server/tests/run-db-tests.sh` (needs Docker). See [ADR 0014](docs/adr/0014-suburb-vector-tiles.md).

### Suburb endpoints

`GET /v1/suburbs/at?lat=&lon=` returns the suburb under a point and `GET /v1/suburbs/{suburbId}` its report (name,
postcode, area, and every other statistic explicitly unknown until the pipeline computes it). Both need the
`X-API-Key` header and the database. See [ADR 0016](docs/adr/0016-suburb-lookup-and-report-endpoints.md).

### Selecting a suburb

Tap a suburb to select it: it is highlighted and a card opens (a bottom sheet on narrow windows, a side panel on wide
ones). Escape or Close clears it. Taps need the API and its key. See
[ADR 0017](docs/adr/0017-suburb-selection-and-card.md).
