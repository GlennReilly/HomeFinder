# Home Finder

Look up any NSW address or suburb on a map and see the factors that decide a purchase. See
[the spec](docs/spec/) for the product and technical design.

## Modules

| Module | Purpose |
| --- | --- |
| `shared` | Models and serialization (JVM + JS) |
| `client` | UI-free client logic: API client, suburb selection, Suburb Card rows and card HTML (JVM + JS) |
| `webApp` | The web client: Kotlin/JS, MapLibre GL JS and a DOM UI ([webApp/README.md](webApp/README.md)) |
| `e2e` | Browser smoke tests (Playwright for Java driving Chrome against a fake of the API), run with `./gradlew :e2e:e2eTest` |
| `server` | Ktor API |
| `pipeline` | Data pipeline: PostGIS, G-NAF, suburbs, lots, sales statistics ([pipeline/README.md](pipeline/README.md)) |

The product is a web app ([ADR 0019](docs/adr/0019-pivot-to-web.md)); the Android and desktop clients were retired.

## Build and test

Requires JDK 17. Gradle downloads Node and the npm packages for the web build.

```bash
./gradlew build
```

## Browser smoke tests

```bash
./gradlew :e2e:e2eTest
```

Builds the web app, serves it with a small fake of the API and drives the installed Chrome through it: the key prompt, a tap on the map,
search, and saving and removing a Homely score. Needs Google Chrome (set `E2E_BROWSER_CHANNEL` to use another, for example `msedge`).
They are not part of `./gradlew build`; CI runs them as their own job. See [ADR 0032](docs/adr/0032-browser-smoke-tests.md).

## Run it locally

One command builds the web app and serves it together with the API (needs `server/.env`, see below):

```bash
./gradlew :server:run
```

Open http://localhost:8080 and enter the `API_KEY` value from `server/.env` when asked. For quick front-end work, run the API
as above and start the web dev server on http://localhost:8081, which rebuilds on change and proxies the API:

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

The server listens on `127.0.0.1:8080` (this machine only); set `API_HOST=0.0.0.0` to reach it from another device on a private network, and `WEB_ROOT` to serve a built web app (`:server:run` sets it for you; [ADR 0020](docs/adr/0020-serve-the-web-app-from-the-api.md)). `GET /health` is open; everything under `/v1` needs the `X-API-Key` header.
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
postcode, area, the sales and block-size statistics once `pipeline/scripts/load-sales.sh` has run, and every other statistic explicitly unknown until the pipeline computes it). Both need the
`X-API-Key` header and the database. See [ADR 0016](docs/adr/0016-suburb-lookup-and-report-endpoints.md) and [ADR 0021](docs/adr/0021-suburb-statistics-in-the-report.md).

### Lots

`GET /tiles/lots/{z}/{x}/{y}.pbf` serves lot boundaries for zoom 16 to 18, `GET /v1/lots/at?lat=&lon=` finds the lot under a
point and `GET /v1/lots/{lotId}` returns the basic lot card (lot, suburb, addresses, measured area, and once `pipeline/scripts/load-osm.sh` has run the nearest beach, parks and shops; other factors
unknown). They need the database and the key. See [ADR 0022](docs/adr/0022-lot-tiles-and-lookup.md) and [ADR 0024](docs/adr/0024-proximity-in-the-lot-report.md).

### Search

`GET /v1/search?q=` suggests suburbs and addresses for what has been typed (word-prefix matching, "12 main st" finds
"12 MAIN STREET"; unit "3/12"). It needs the database with migration V12 applied. See
[ADR 0026](docs/adr/0026-address-and-suburb-search.md).

The search box (top left of the map) suggests as you type, 200 ms after you pause. Up and Down move through the suggestions,
Enter or a click chooses one, Escape closes the list. A suburb fits the map to its boundary and opens its card; an address
flies to street level and opens its lot card, or says "No lot found for this address" when G-NAF has no lot for it.

### Your Homely scores

`GET /v1/suburbs/ratings` lists the Homely star scores you have typed in, and `PUT` / `DELETE /v1/suburbs/{id}/rating` set and remove one
(stars 0 to 5, an optional review count). Nothing is fetched from Homely. They need migration V13.

In the app: open a suburb's card, copy the score from its Homely page into "Your Homely rating" (stars, and the number of reviews if you like) and press Save.
The card then shows a "Homely rating" row under the area, and a "★ 2.1" label appears under the suburb's name on the map. Remove clears both. See
[ADR 0028](docs/adr/0028-manual-homely-rating.md).

### Selecting a suburb

The Suburb Card ends with a link to the suburb's Homely page (no Homely data is fetched or shown; [ADR 0027](docs/adr/0027-link-out-to-homely-no-extraction.md)).


Tap a suburb to select it: it is highlighted and a card opens (a bottom sheet on narrow windows, a side panel on wide
ones). Escape or Close clears it. From zoom 16 a tap selects a lot instead (outlines appear at the same zoom) and opens the basic lot card. Taps need the API and its key. See
[ADR 0017](docs/adr/0017-suburb-selection-and-card.md).
