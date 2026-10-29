# Home Finder Web App — Product & Technical Spec

Oct 30, 2026 · @Glenn Reilly · Supersedes the multi-platform spec ([Home Finder App — Product & Technical Spec](Home%20Finder%20App%20%E2%80%94%20Product%20%26%20Technical%20Spec.md)), which stays for history. Decision: [ADR 0019](../adr/0019-pivot-to-web.md).

## What changed

The target platform is now **a web app**, for one user (you), served from this machine or a private host. Android, iOS and desktop are dropped. Everything about *what* the product does (flows, attributes, suburb view, data sources, risks, shortlist) is unchanged except where this document says otherwise. Sections that are unchanged are summarised and point to the old spec rather than repeated.

| Area | Old spec | This spec |
| --- | --- | --- |
| Clients | Android, iOS, desktop, web | Web only (browser, desktop and phone widths) |
| UI | Compose Multiplatform, all targets | Compose Multiplatform for web (Wasm), with a spike to prove the map first |
| Map | MapLibre behind `MapView`, per-target renderers | maplibre-compose in the browser (WebGL). No custom renderers |
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
Browser (Compose for web, Wasm)  ──same origin──►  Ktor server  ──►  PostGIS  ◄──  offline pipeline
  maplibre-compose (WebGL map)                      /v1 JSON API                     (SQL + bash, GDAL, tippecanoe)
  view model, Suburb / Property cards               /tiles/*.pbf
                                                    static web bundle
```

- **UI:** Compose Multiplatform targeting `wasmJs`. One responsive layout; no per-platform code.
- **Map:** maplibre-compose (web is Alpha in 0.20; see Risks). Vector tiles for suburbs and lots served by our API; the basemap is OpenFreeMap. The `MapRenderer` interface stays, so a different renderer can be swapped in if needed (the fallback is below).
- **State:** unchanged one-way data flow; selection is a suburb id in `MapViewModel`.
- **Client data:** Ktor client (JS engine), kotlinx.serialization. The shortlist and recent lookups use browser storage behind a small `ShortlistStore` interface (IndexedDB), replacing SQLDelight.
- **Backend:** unchanged: Ktor on PostGIS, pipeline run locally. The server additionally serves the compiled web bundle as static files so the page and API share one origin.
- **Models:** the `shared` module gains a `wasmJs` target alongside `jvm`; the Android target is removed.

**Fallback if the spike fails.** If maplibre-compose cannot be made to work in the browser, the UI becomes Kotlin/JS (or a Compose canvas page) that hosts a MapLibre GL JS map through a thin JS interop layer behind `MapRenderer`. The server, pipeline and shared models are unaffected either way.

## Access and security

The Android design needed an API key because the phone reached the server over a network. A key shipped inside browser code is not a secret, so the web design is:

- The server serves the web bundle and the API from the **same origin**. No CORS.
- The server listens on **localhost by default**. To reach it from a phone or another machine, bind it to a private interface or put it behind Tailscale; do not expose it to the internet.
- The API key stays as a defence-in-depth check: you enter it once in the browser, it is kept in `localStorage`, sent as `X-API-Key`, and as `?key=` for tile URLs via MapLibre's `transformRequest` where headers are awkward. **Open decision:** whether to keep this at all given the private binding (default: keep, it is already built and tested).

## What is already built (kept)

| Milestone | Status | Notes |
| --- | --- | --- |
| Pipeline: G-NAF addresses, suburbs, LGAs, cadastre lots, address-to-lot links | Done (PR 3–4) | ADR 0003–0007 |
| API skeleton, `/health`, `/v1/status`, shared models | Done (PR 6–7) | Android-specific parts of ADR 0013 retired |
| Suburb vector tiles from PostGIS | Done (PR 8a) | ADR 0014 |
| Suburb lookup (`/v1/suburbs/at`) and report endpoints | Done (PR 9a) | ADR 0016 |
| Selection model, card rows, card UI, API client | Done (PR 9b), UI shell in Compose | ADR 0017; reused in the web UI |
| Sales and suburb statistics pipeline | PR 10a open (#20) | ADR 0018; unaffected by the pivot |
| Desktop and Android renderers, `androidApp`, Java 25 desktop build | **Retired** | removed in W1 below |

## Milestones (revised)

Numbering continues from the PR list so history stays traceable. PRs stay small and each stands alone; every PR is based on `main`.

1. **W0, web feasibility spike (first, small, throwaway-allowed):** add a `wasmJs` target to `shared` and `composeApp`; render the MapLibre map with the OpenFreeMap basemap and our suburb tile layer in a browser; select a suburb and show the card. Pass criteria: map pans and zooms smoothly, suburb outline and names draw, a tap selects through `/v1/suburbs/at`, and the bundle loads in a reasonable time (target below). If it fails, record an ADR and switch to the fallback.
2. **W1, retire the old clients:** remove `androidApp`, the Android target, the desktop (JVM) entry point, the vector and raster renderers and their tests; drop Java 25 and the `-PmapRenderer` flag; update README and CI. Port the kept tests (view model, card rows, layout, API client) to run on the new targets. Done after W0 passes.
3. **W2, serve the web app:** the Ktor server serves the built bundle; bind address and key behaviour per "Access and security"; one command to build and run everything locally.
4. **PR 10b, real suburb stats on the card:** unchanged from the plan (server reads `suburb_stats` into `SuburbReport`; the card shows the real median). Not blocked by W0 because the server and tests are platform-independent, but verified in the browser once W2 lands.
5. **PR 11 onward** (address and suburb search, lots layer at zoom 16+, Property Card, flood, schools, price range, brick estimate, shortlist, compare, release) proceed as in M2–M6 of the old spec, with the web client as the only client. The old "store builds" item becomes "production web build and attribution screen".

## Non-functional targets (changed items only)

- Address lookup returns a full Property Card in under 800 ms at p95 on a warm cache (unchanged).
- Map pan and zoom hold 60 fps on a mid-range laptop at lot zoom and stay usable on a recent phone browser. *(Replaces the mid-range phone target.)*
- **Initial load:** the Wasm bundle should be interactive in under 5 s on a home broadband connection after the first visit, with the bundle cached. Proposed target; the W0 spike measures the real size and may change it.
- Shortlist and last viewed properties persist in the browser and work offline once loaded (service worker, M6). Search queries and the shortlist stay in the browser.
- Browsers: current Chrome, Firefox and Safari. Compose for web needs a Wasm-GC capable browser; anything older shows a plain "unsupported browser" message.
- Accessibility: unchanged (list-view equivalent of the map, screen-reader labels on cards, colour never the only indicator). Compose for web draws to a canvas, so W0 also checks keyboard navigation and screen-reader output on the card; if it is inadequate, the card can be built in DOM.

## Risks (changed or new)

| Risk | Impact | Mitigation |
| --- | --- | --- |
| maplibre-compose web is Alpha and depends on Compose/Skia internals | The map may not work or may break on upgrades | W0 spike first; pin versions; `MapRenderer` seam; documented fallback to MapLibre GL JS |
| Large Wasm bundle | Slow first load | Measure in W0; cache, compress, lazy-load heavy screens |
| Canvas-drawn UI limits accessibility and text selection | Weaker accessibility than DOM | Check in W0; build the card in DOM if needed |
| Compose for web browser support | Older browsers cannot run it | State supported browsers; clear unsupported message |
| API key in the browser is not secret | Anyone on the network could call the API | Same origin, localhost or private-network binding only; no public exposure |
| Public hosting reopens data licences | PSI and OSM terms change | Out of scope; ADR 0018 requires a PSI licence before any sharing |

All risks in the old spec (brick inference, flood gaps, aspect on irregular lots, price estimate, listing licensing, rendering millions of lots) still apply and keep their mitigations.

## Open decisions

Decided: web only; Compose Multiplatform for web with a spike; single user, local or private; Android and desktop retired; pilot region Newcastle and Lake Macquarie; price source is our own model on Valuer General sales (personal use).

Still open, with the default this spec assumes:
- Whether to keep the API key on a private deployment (default: keep).
- Browser storage mechanism for the shortlist (default: IndexedDB behind `ShortlistStore`).
- How the app is run day to day: from the Gradle build on this machine (default) or in a container.
- Bundle size and load-time target (default: 5 s, to be measured in W0).
