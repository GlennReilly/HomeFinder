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
| `census` | Kotlin tools for the pipeline: tenure and dwelling structure per suburb from the 2021 Census ([ADR 0044](docs/adr/0044-census-tenure-and-dwelling-structure.md)) SEIFA socio-economic advantage ([ADR 0049](docs/adr/0049-seifa-socio-economic-advantage.md)) and BOCSAR recorded crime ([ADR 0051](docs/adr/0051-bocsar-crime-rates.md)) |
| `drainage` | Kotlin tool for the pipeline: height above nearest drainage per lot from the 5 m DEM ([ADR 0040](docs/adr/0040-height-above-drainage-hint.md)) |
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

In the app: open a suburb's card, press "Edit the Homely rating" in the Homely block (under the price change, beside the "Reviews on Homely" link), copy the score from its Homely page into the form (stars, and the number of reviews if you like) and press Save.
The card then shows a "Homely rating" row under the area, and a "★ 2.1" label appears under the suburb's name on the map. Remove clears both. See
[ADR 0028](docs/adr/0028-manual-homely-rating.md).

### Filtering suburbs

`GET /v1/suburbs/filters` gives every suburb's values for seven criteria, each with its source and date, in one response: average school ICSEA,
distance to the beach (km from the suburb's centre), park share (%) and share of homes rented (%). In the app, the right-hand panel has two tabs, **Suburb** and **Filters** (it opens on Filters, and the tab shows the live match count, for example "Filters (12 of 173)"; tap the open tab to fold the panel down). Each criterion has one slider with a fixed direction ("At least" for ICSEA, park share and SEIFA; "At most" for beach distance, homes rented and the two crime rates); it starts where every suburb passes, and moving it switches the criterion on. Suburbs that pass every switched-on criterion are tinted green and the rest are
washed out, with no further requests. A suburb with no value for a switched-on criterion never matches, and each slider says how many suburbs
that is. A criterion whose data has not been loaded says so instead of showing its slider. The data comes from `pipeline/scripts/load-schools.sh`
(ICSEA), `load-osm.sh` (beach and parks) `load-census.sh` (homes rented) and `load-seifa.sh` (socio-economic advantage, SEIFA decile) and `load-crime.sh` (recorded crime per 1,000 residents, BOCSAR; needs SEIFA's population for the rate, [ADR 0051](docs/adr/0051-bocsar-crime-rates.md)). Transport comes later; see
[ADR 0035](docs/adr/0035-suburb-filter-panel.md), [ADR 0046](docs/adr/0046-filters-beach-parks-rented.md), [ADR 0048](docs/adr/0048-right-hand-tabs.md) and [ADR 0052](docs/adr/0052-single-slider-filters.md).

### Customising the cards

The card's name, postcode, close button and Customise bar stay pinned at the top while you scroll, and choosing another suburb keeps the card scrolled to the same place, so you can compare rows ([ADR 0053](docs/adr/0053-pinned-card-header-reset-all-kept-scroll.md)). The Filters tab has a **Reset all** button.

Press **Customise** on the suburb or lot card to hide rows or move them up and down, then **Done**; **Reset to default** undoes it. The choice is
kept in this browser only (nothing goes to the server). The Homely block stays under "Price change, 12 months". See
[ADR 0039](docs/adr/0039-card-rows-hidden-and-reordered-in-the-browser.md).

### Selecting a suburb

The Suburb Card's beach, parks and shopping rows come from the OpenStreetMap roll-up once `pipeline/scripts/load-osm.sh` has run ([ADR 0033](docs/adr/0033-suburb-osm-rollup.md)). The Suburb Card's Schools row lists the NSW public schools inside the suburb with their ICSEA once `pipeline/scripts/load-schools.sh` has run ([ADR 0034](docs/adr/0034-suburb-schools-and-icsea.md); data: NSW Department of Education, CC BY; ICSEA from ACARA). The Lot Card's Height above sea level and Slope rows come from Geoscience Australia's 5 m LiDAR DEM once `pipeline/scripts/download-dem.sh` and `load-dem.sh` have run ([ADR 0036](docs/adr/0036-lot-height-and-slope-from-dem.md); CC BY 4.0, surveys 2001 to 2015; height is not flood risk). The Suburb Card ends with a link to the suburb's Homely page (no Homely data is fetched or shown; [ADR 0027](docs/adr/0027-link-out-to-homely-no-extraction.md)).


Tap a suburb to select it: it is highlighted and a card opens (a bottom sheet on narrow windows, a side panel on wide
ones). Escape or Close clears it. From zoom 16 a tap selects a lot instead (outlines appear at the same zoom) and opens the basic lot card. Taps need the API and its key. See
[ADR 0017](docs/adr/0017-suburb-selection-and-card.md).
