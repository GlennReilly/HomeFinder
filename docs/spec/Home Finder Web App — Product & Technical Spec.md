# Home Finder Web App — Product & Technical Spec

Oct 30, 2026 · @Glenn Reilly · Supersedes the multi-platform spec ([Home Finder App — Product & Technical Spec](Home%20Finder%20App%20%E2%80%94%20Product%20%26%20Technical%20Spec.md)), which stays for history. Decision: [ADR 0019](../adr/0019-pivot-to-web.md).

## What changed

The target platform is now **a web app**, for one user (you), served from this machine or a private host. Android, iOS and desktop are dropped. Everything about *what* the product does (flows, attributes, suburb view, data sources, risks, shortlist) is unchanged except where this document says otherwise. Sections that are unchanged are summarised and point to the old spec rather than repeated.

| Area | Old spec | This spec |
| --- | --- | --- |
| Clients | Android, iOS, desktop, web | Web only (browser, desktop and phone widths) |
| UI | Compose Multiplatform, all targets | Kotlin/JS, ordinary DOM (HTML and CSS). No Compose UI |
| Map | MapLibre behind `MapView`, per-target renderers | MapLibre GL JS in the browser (WebGL), driven from Kotlin through a thin interop layer. No custom renderers |
| Client storage | SQLDelight on device | Browser storage (IndexedDB / localStorage), shortlist stays in the browser |
| Access | API key on the LAN or Tailscale | Same-origin web app and API on a private host; key handling in "Access and security" |
| Audience | One buyer, possible sharing later | One user, local or private. Sharing or a public site is out of scope |

## Principles, scope and flows

Unchanged: every attribute shows source, as-of date and confidence; missing data is "unknown"; AUD only; one map and one detail panel; screening tool, not advice. Out of scope also unchanged (listings marketplace, mortgage calculators, auction tracking, rentals, social). Added to out of scope: accounts, multi-user hosting, a public website, native apps.

The five user flows (address search, suburb search, browse, layers, save) and the zoom table are unchanged. The detail panel is a **bottom sheet below 600 px wide and a side panel above it**, which is what the app already does. Hover tint on suburbs now applies (mouse), and Escape or the Back button clears the selection.

## Property attributes, suburb view, data sources

Unchanged. See the old spec for the attribute table, the aspect convention, the Suburb Card contents, boundary rendering, and the data sources and licensing table. Two clarifications for the web target:

- **Licences.** Because the app is private, ADR 0018 holds: the Valuer General data and derived medians stay on this machine and are not redistributed. Putting the app on a public URL, or letting others use it, would reopen that (a PSI licence is needed) and is out of scope.
- **Attribution.** The map shows OpenStreetMap, OpenMapTiles and OpenFreeMap attribution in the browser's map control, which satisfies those licences. The attribution screen stays in M6.

## Architecture

```
Browser (Kotlin/JS)  ──same origin──►  Ktor server  ──►  PostGIS  ◄──  offline pipeline
  MapLibre GL JS map (WebGL)            /v1 JSON API                   (SQL + bash, GDAL, tippecanoe)
  DOM UI: search, Suburb / Property     /tiles/*.pbf
  cards, shortlist                      static web bundle
  client logic in Kotlin (view model, API client)
```

- **Language:** Kotlin/JS (IR compiler) for the client, so the models, API contract, view model and API client are the same code the server uses.
- **Map:** MapLibre GL JS, the reference web implementation, added as an npm dependency through Gradle and called from Kotlin through hand-written `external` declarations for the small part of its API we use (map, sources, layers, camera, click events, feature state). Vector tiles for suburbs and lots served by our API; the basemap is OpenFreeMap. The `MapRenderer` seam stays, so the map can be replaced without touching the rest.
- **UI:** plain DOM, built from Kotlin. Layout and styling in CSS, so the bottom sheet / side panel switch is a media query and accessibility (focus, headings, labels) is native HTML. DOM is built with `kotlinx-html` (a small type-safe HTML DSL, no framework). The view model exposes state as a `StateFlow`; a `render(state)` function redraws the changed region, and the HTML output is unit-tested on the JVM.
- **State:** unchanged one-way data flow; selection is a suburb id in `MapViewModel`, observed through Kotlin `StateFlow`. The map, search box and detail panel all render from it.
- **Client data:** Ktor client (JS engine), kotlinx.serialization. The shortlist and recent lookups use browser storage behind a small `ShortlistStore` interface (IndexedDB), replacing SQLDelight.
- **Backend:** unchanged: Ktor on PostGIS, pipeline run locally. The server additionally serves the compiled web bundle as static files so the page and API share one origin.
- **Modules:** `shared` (models and API contract) gains a `js` target next to `jvm`. The client logic that has no UI (view model, card rows, layout rule, API client, map config) moves into a new `client` module with `jvm` and `js` targets so its existing tests keep running on the JVM. A new `webApp` module holds the DOM UI, the MapLibre interop and the bundle. `composeApp` and `androidApp` are removed.

## Access and security

The Android design needed an API key because the phone reached the server over a network. A key shipped inside browser code is not a secret, so the web design is:

- The server serves the web bundle and the API from the **same origin**. No CORS.
- The server listens on **localhost by default** (`API_HOST`, ADR 0020). To reach it from a phone or another machine, bind it to a private interface or put it behind Tailscale; do not expose it to the internet.
- The API key stays as a defence-in-depth check: you enter it once in the browser, it is kept in `localStorage`, sent as `X-API-Key`, and attached to tile requests by MapLibre's `transformRequest` (as the header, so the `?key=` form is no longer needed). **Open decision:** whether to keep this at all given the private binding (default: keep, it is already built and tested).

## What is already built (kept)

| Milestone | Status | Notes |
| --- | --- | --- |
| Pipeline: G-NAF addresses, suburbs, LGAs, cadastre lots, address-to-lot links | Done (PR 3–4) | ADR 0003–0007 |
| API skeleton, `/health`, `/v1/status`, shared models | Done (PR 6–7) | Android-specific parts of ADR 0013 retired |
| Suburb vector tiles from PostGIS | Done (PR 8a) | ADR 0014 |
| Suburb lookup (`/v1/suburbs/at`) and report endpoints | Done (PR 9a) | ADR 0016 |
| Selection model, card rows, API client | Done (PR 9b), moved to `client` (W0a) | ADR 0017 |
| Card UI, app shell, map | Rewritten (W0b) as DOM and MapLibre GL JS code | `webApp`; the Compose versions were removed in W1 |
| Sales and suburb statistics pipeline | PR 10a open (#20) | ADR 0018; unaffected by the pivot |
| Desktop and Android renderers, `androidApp`, `composeApp`, Java 25 desktop build | **Retired (W1, done)** | removed; Gradle now runs on Java 17 |

## Milestones (revised)

Numbering continues from the PR list so history stays traceable. PRs stay small and each stands alone; every PR is based on `main`.

1. **W0, web foundation and spike:** add a `js` target to `shared`; create the `client` module (moving the Compose-free logic and its tests) and the `webApp` module; render a MapLibre GL JS map with the OpenFreeMap basemap and our suburb tile layer; select a suburb by tap through `/v1/suburbs/at` and show the card in the DOM. Pass criteria: the map pans and zooms smoothly, suburb outlines and names draw with the same styling as before, the tap selects, the card shows name, postcode, area and unknown rows, and the bundle loads quickly (target below). It also settles the hand-written MapLibre `external` declarations.
2. **W1, retire the old clients:** remove `androidApp`, `composeApp` (Android and desktop targets, the vector and raster renderers and their tests), Compose and Material3 from the build, Java 25 and the `-PmapRenderer` flag; update README and CI. Done after W0 passes, so the old apps keep working until the web app does.
3. **W2, serve the web app (done, ADR 0020):** the Ktor server serves the built bundle; bind address and key behaviour per "Access and security"; one command to build and run everything locally.
4. **PR 10b, real suburb stats on the card (ADR 0021):** unchanged from the plan (server reads `suburb_stats` into `SuburbReport`; the card shows the real median). Not blocked by W0 because the server and tests are platform-independent, but verified in the browser once W2 lands.
5. **PR 12 (lots layer), in two parts (ADR 0022):** 12a server tiles, lookup and lot card; 12b lot outlines, lot selection and the lot card in the web app.
6. **PR 13 (OpenStreetMap proximity), in two parts (ADR 0023):** 13a pipeline for the nearest beach, parks and shops per lot; 13b server and lot card (ADR 0024).
7. **PR 11, search, in two parts (ADR 0026):** 11a database index, contract and `/v1/search`; 11b the search box, fly-to and selection in the web app (done).
8. **PR 14 onward** (Property Card, flood, schools, price range, brick estimate, shortlist, compare, release) proceed as in M2–M6 of the old spec, with the web client as the only client. The old "store builds" item becomes "production web build and attribution screen".

## Non-functional targets (changed items only)

- Address lookup returns a full Property Card in under 800 ms at p95 on a warm cache (unchanged).
- Map pan and zoom hold 60 fps on a mid-range laptop at lot zoom and stay usable on a recent phone browser. *(Replaces the mid-range phone target.)*
- **Initial load:** the page should be interactive in under 2 s on a home broadband connection once cached, and under 4 s cold. MapLibre GL JS is about 0.8 MB gzipped and the Kotlin/JS bundle is small, so this should be easy; W0 measures it.
- Shortlist and last viewed properties persist in the browser and work offline once loaded (service worker, M6). Search queries and the shortlist stay in the browser.
- Browsers: current Chrome, Firefox, Safari and Edge, desktop and phone, with WebGL. Without WebGL the page shows a plain message.
- Accessibility: unchanged (list-view equivalent of the map, screen-reader labels on cards, colour never the only indicator). The card and controls are real HTML, so keyboard and screen-reader support come from the browser; the map canvas needs the list-view equivalent.

## Risks (changed or new)

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Hand-written Kotlin `external` declarations for MapLibre GL JS | Mistakes or gaps show up only at runtime; library upgrades can break them | Keep the used API small and behind `MapRenderer`; pin the npm version; a browser smoke test in CI; fall back to a small JavaScript file if an API is awkward to declare |
| Kotlin/JS DOM tooling is thinner than React's | Slower UI work, more hand-written code | Keep the UI small and server-driven; `kotlinx-html` output is tested on the JVM; the logic stays in Kotlin with its tests |
| Frontend build (npm packages through Gradle) | Slower, less familiar build | One documented build command; pin versions; cache in CI |
| API key in the browser is not secret | Anyone on the network could call the API | Same origin, localhost or private-network binding only; no public exposure |
| Public hosting reopens data licences | PSI and OSM terms change | Out of scope; ADR 0018 requires a PSI licence before any sharing |

All risks in the old spec (brick inference, flood gaps, aspect on irregular lots, price estimate, listing licensing, rendering millions of lots) still apply and keep their mitigations.

## Open decisions

Decided: web only; Kotlin/JS with MapLibre GL JS (chosen over Compose for web and over React); `kotlinx-html` for the DOM (chosen over Compose HTML, approved as a new dependency); single user, local or private; Android and desktop retired; pilot region Newcastle and Lake Macquarie; price source is our own model on Valuer General sales (personal use).

Still open, with the default this spec assumes:
- Whether to keep the API key on a private deployment (default: keep).
- Browser storage mechanism for the shortlist (default: IndexedDB behind `ShortlistStore`).
- How the app is run day to day: from the Gradle build on this machine (default) or in a container.
- Load-time targets (default: 2 s cached, 4 s cold, to be measured in W0).
