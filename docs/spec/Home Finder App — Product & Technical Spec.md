# Home Finder App — Product & Technical Spec

Oct 29, 2026 · @Glenn Reilly

## Overview

Home Finder lets one buyer look up any NSW address or suburb on a map and see the factors that decide a purchase: wall material, backyard aspect, flood risk, block size, slope, estimated price and distance to the beach, schools, parks, shops and transport. It is one Kotlin codebase running on Android, iOS, desktop and web.

**Primary user:** an individual buyer shortlisting homes. Accounts, sharing and agent tooling are out of scope for v1.

**Principles**

- Every attribute shows its source, as-of date and a confidence level. Derived or inferred values are labelled as such.
- Missing data shows as "unknown", never as a silent default. "Flood not mapped" never reads as "no flood risk".
- Address search and map browsing share one map and one detail panel.
- All prices display in AUD.
- This is a screening tool, not advice. Flood, aspect and price are indicative and must be verified against the contract of sale, a council planning certificate and a building inspection.

**Out of scope for v1:** listings marketplace, mortgage calculators, auction tracking, rentals, social features.

**Assumption:** coverage starts in NSW, because the best free spatial datasets (cadastre, flood, catchments, GTFS) are state-level. Other states need separate data work.

## Core user flows

The map is the home screen. What a tap selects depends on zoom, so browsing, suburb lookup and address lookup all end in the same detail panel (bottom sheet on phones, side panel on desktop and web).

**1. Address search.** Type an address, pick an autocomplete result, and the map flies to the lot, outlines its boundary and opens the Property Card.

**2. Suburb search.** Type a suburb name. The map fits to the suburb boundary, highlights it and opens the Suburb Card.

**3. Browse with no filter.** Pan and zoom freely. Suburb boundaries and names are drawn at wide zoom and each suburb is tappable. Zooming in switches the tap target from suburbs to lots.

**4. Layers.** Toggle overlays for flood extent, slope, school catchments, transport stops and parks.

**5. Save.** Star a property or suburb to add it to the shortlist.

| Zoom level | Drawn on map | Tap selects |
| --- | --- | --- |
| 5–12 (state to city) | Suburb boundaries, suburb names | Suburb |
| 13–15 (suburb to street) | Suburb boundaries fade to outline, streets, amenity icons | Suburb |
| 16+ (street to lot) | Lot boundaries, building footprints, optional overlays | Lot (Property Card) |

The selected suburb or lot gets a thick highlight and the rest of the map dims slightly. Back or Escape clears the selection.

## Property attributes

Of your eleven factors, block size, distance to beach and transport are straightforward lookups. Aspect, slope and flood are computed from geometry and mapped layers. Wall material and price are the weak points: no authoritative open dataset exists for either, so both ship with visible confidence labels.

| Attribute | How it is derived | Main source | Confidence |
| --- | --- | --- | --- |
| Wall material (brick) | Parse listing text for brick, double brick, brick veneer. Fall back to an image classifier on street imagery. User can override. | Licensed listing text, Mapillary imagery | Low to medium. Show "Likely brick" with the evidence. |
| Backyard aspect | Take the street-facing lot edge as the front. Measure the bearing from the dwelling centroid to the rear boundary midpoint. Flag north-facing if within 45° of true north. | NSW cadastre lot, building footprint, road centrelines | Medium to high on regular lots. Corner, battle-axe and irregular lots flagged as ambiguous. |
| Flood risk | Intersect the lot with mapped flood extents. Report class (none mapped, low, medium, high) and the AEP event it applies to where known (for example 1% AEP). | NSW Flood Data Portal, council flood studies, planning flood layers | Patchy coverage. "Not mapped" is shown distinctly from "low". |
| Block size (m²) | Area of the lot polygon in a projected CRS (GDA2020 / MGA). Cross-check against listing land size when present. | NSW Digital Cadastral Database | High. Strata and multi-lot titles need special handling. |
| Slope | Sample a DEM across the lot. Report mean and max slope (%), plus fall direction (to street, to rear, to side). Classes: flat under 5%, gentle 5–10%, moderate 10–20%, steep over 20%. | 1 m or 5 m LiDAR-derived DEM from Geoscience Australia ELVIS | Medium to high. |
| Estimated price (AUD) | A range, not a point. Built from recent nearby sales, adjusted for land size and dwelling type, with a list of the comparable sales used. Optional licensed AVM later. | NSW Valuer General sales data | Medium to low. Always show the range and the comps. |
| Distance to beach (km) | Walking route distance and straight-line distance to the nearest ocean beach. | OpenStreetMap beaches, routing engine | High. |
| Schools | Whether the address is in the public primary and high school catchment, plus nearest schools by sector with walking and driving time. | NSW Department of Education catchments and locations | High for catchment. |
| Parks | Nearest parks over 0.5 ha with walking time, and park area within 800 m. | OpenStreetMap, council open space | Medium to high. |
| Shopping centres | Nearest supermarket and nearest shopping centre by type, with walking and driving time. | OpenStreetMap, POI data | Medium. |
| Transport | Nearest train, light rail, ferry and bus stops with walk time, peak-hour frequency, and optional travel time to a work address. | Transport for NSW GTFS open data | High. |

**Aspect convention.** In Australia a north-facing backyard means the garden sits on the north side of the house, so the house fronts south. The app uses that meaning and shows the computed bearing so you can check it against the real lot.

## Suburb view

Selecting a suburb shows the same factors rolled up to suburb level, so you can rule suburbs in or out before looking at individual lots.

**Suburb Card contents**

- Median house and unit price in AUD, 12-month change, and the number of sales behind it.
- Typical block size: median and 10th to 90th percentile in m².
- Share of lots touching mapped flood extent, and how much of the suburb has flood mapping at all.
- Share of lots likely brick, with the share of lots that have enough evidence to say.
- Distance from the suburb centre and from its nearest edge to the beach.
- Schools: public schools inside the suburb and the catchments that cover it.
- Parks: park area as a share of suburb area.
- Shopping: shopping centres and supermarkets inside or within 2 km.
- Transport: train, light rail and ferry stations, bus stop count, and peak frequency at the best stop.

**Boundary rendering**

- Use official NSW suburb and locality boundaries as the drawn geometry. Join to ABS Suburbs and Localities only where Census statistics are needed.
- Serve boundaries as vector tiles with geometry simplified per zoom level, not as one large GeoJSON file.
- Draw a thin outline and name label by default, a filled tint on hover (desktop and web), and a thick outline with a fill on selection.
- Selection state is held by suburb ID in the shared view model, so search, map tap and shortlist all select the same way.
- Suburb search uses name plus postcode to separate duplicate names.

## Data sources and licensing

Most of the spatial data is free from NSW and federal agencies. The two risks are listing-derived data (brick, asking price), which needs a commercial licence, and flood data, which is free but uneven across councils.

| Source | Provides | Access | Risk or licence note |
| --- | --- | --- | --- |
| NSW Spatial Services | Lot boundaries, suburb boundaries, address points | Free web services. Bulk download to be confirmed in M0 | Open licence with attribution. Confirm current terms. |
| G-NAF (Geoscape, via data.gov.au) | Address autocomplete and geocoding | Free quarterly release | Open licence. Quarterly lag on new addresses. |
| Building footprints (OpenStreetMap, Microsoft, or Geoscape Buildings) | Dwelling outline for aspect | Free (OSM, Microsoft) or commercial (Geoscape) | OSM is share-alike. Geoscape is more complete but paid. |
| NSW Valuer General | Historical sales for price model | Free bulk files | Sales only, no dwelling features. Publication lag. |
| NSW Flood Data Portal and council studies | Flood extents and levels | Free, per study | Formats and coverage vary. Many lots have no mapping. |
| Geoscience Australia ELVIS | Elevation models | Free download | Large rasters. Pre-process server-side. |
| Transport for NSW Open Data | GTFS stops, routes, timetables | Free with API key | Rate limits. Refresh timetables regularly. |
| NSW Department of Education | School locations and catchments | Free | Updated nightly at source. The publisher says not to rely on it for property purchases. |
| ACARA MySchool | School profiles, ICSEA | Public site | Reuse terms are restrictive. Link out rather than copy. |
| OpenStreetMap | Beaches, parks, shops | Free | Share-alike. Attribution required on the map. |
| Listing portals | Listing text, photos, asking price | Domain offers a commercial API. realestate.com.au has no public API. | Do not scrape. Portal terms prohibit it. |

**Verification:** on 29 October 2026 I checked the NSW cadastre, flood layers, GTFS, Valuer General sales, school catchments and G-NAF against publisher or data.gov.au pages. ELVIS, OpenStreetMap, building footprints, ACARA and Domain are from general knowledge and not yet checked. Milestone 0 confirms them before any build depends on them.

## Architecture

One Kotlin codebase serves every client, and a local PostGIS database, fed by an offline pipeline, holds the heavy geospatial work computed ahead of time so the apps stay thin.

&#91;embedded content: system architecture · 4 layers, data flows up\]

Data flows up from the open sources to the apps; requests go down.

**Stack**

- **UI:** Compose Multiplatform for Android, iOS, desktop (JVM) and web (Wasm). Shared screens, with platform-specific code only where a target needs it.
- **Map:** MapLibre behind a `MapView` interface, so a target can swap in another renderer if MapLibre support lags there. Vector tiles for suburbs and lots; GeoJSON only for the single selected feature.
- **State:** view models with one-way data flow. The selected suburb or lot is a single value that the map, search box and detail panel all observe.
- **Client data:** Ktor client, kotlinx.serialization, and SQLDelight for the on-device shortlist and recent lookups.
- **Backend:** PostGIS in Docker on your machine with a thin Ktor API on top. The pipeline (PostGIS SQL, GDAL for raster work such as slope, tippecanoe for tiles) runs locally and loads finished tables on each data release, so request handlers only read. Android reaches it over the LAN or Tailscale.
- **Why a server:** lot, elevation and flood datasets are far too large to ship to a phone, and some licences do not allow redistribution.

**Later options.** Hosted PostGIS is a dump and restore, or a load of the pipeline's output tables into a managed instance. Keep raster work out of the database so any provider works. If offline use matters, export per-region SQLite packs and PMTiles from the same tables.

## Data model and API

Every attribute is wrapped with its provenance, so the UI can always show source, date and confidence without special cases. The models live in the shared Kotlin module and are used by both the server and every client.

```kotlin
@Serializable
data class Attribute<T>(
    val value: T?,                 // null = unknown
    val confidence: Confidence,    // HIGH, MEDIUM, LOW, NONE
    val source: String,
    val asOf: LocalDate?,
    val note: String? = null,      // e.g. "corner lot, aspect ambiguous"
)

@Serializable
data class PropertyReport(
    val addressId: String,         // G-NAF PID
    val lotId: String,
    val suburbId: String,
    val wallMaterial: Attribute<WallMaterial>,
    val backyardAspect: Attribute<AspectResult>,   // bearing + isNorthFacing
    val flood: Attribute<FloodClass>,
    val blockAreaM2: Attribute<Double>,
    val slope: Attribute<SlopeSummary>,            // mean %, max %, fall direction
    val estimatedPriceAud: Attribute<PriceRange>,  // low, mid, high + comps
    val beach: Attribute<Proximity>,
    val schools: Attribute<SchoolInfo>,            // catchment + nearest
    val parks: Attribute<ProximityList>,
    val shopping: Attribute<ProximityList>,
    val transport: Attribute<TransportInfo>,
)
```

**Endpoints (Ktor, JSON)**

- `GET /v1/search?q=` returns address and suburb suggestions with a type tag.
- `GET /v1/properties/{addressId}` returns a `PropertyReport`.
- `GET /v1/lots/at?lat=&lon=` resolves a map tap at lot zoom to an address and lot.
- `GET /v1/suburbs/{suburbId}` returns a `SuburbReport`.
- `GET /tiles/{layer}/{z}/{x}/{y}.pbf` serves vector tiles for suburbs, lots, flood, catchments and stops. PMTiles over HTTP range requests is the lower-cost alternative.

**Storage.** PostGIS holds lots, suburbs and precomputed attributes. Slope, aspect, flood intersection and amenity distances are computed in a batch pipeline per lot, not per request, so a lookup is a single indexed read.

## Shortlist and comparison

Choosing between homes needs a shortlist and a side-by-side view, so these ship right after the core lookup features.

- **Shortlist:** star properties and suburbs. Stored on-device in SQLDelight, with optional sync later.
- **Compare:** up to four properties in columns, one factor per row, with the best value in each row highlighted and unknowns shown as "unknown".
- **Hard filters:** for example "must be brick" or "exclude high flood". Filters grey out non-matching lots on the map.
- **Weighted score:** set a weight per factor, normalise each factor to 0–100, and show the composite. Unknown factors are excluded from the score and the score displays how many factors it used, so a lot with missing data cannot outrank one with full data by default.
- **Notes:** free-text note and your own override per attribute (for example "confirmed double brick at inspection").

## Risks and non-functional requirements

The biggest risk is false confidence: a clean-looking card for a lot where the underlying data is missing or inferred. Every mitigation below comes back to showing uncertainty plainly.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Brick data is inferred, not authoritative | Wrong wall material drives a wrong shortlist | Label "Likely brick" with evidence, allow user override, show coverage per suburb |
| Flood mapping missing for many lots | "No flood shown" read as "safe" | Distinct "not mapped" state, link to council planning certificate, never score unmapped as low |
| Aspect wrong on corner, battle-axe or irregular lots | Misleading north-facing result | Detect lot shape, flag as ambiguous, show bearing and let user flip the front edge |
| Price estimate inaccurate | Anchors the buyer on a bad number | Range plus comps, no single figure, show sample size |
| Listing data licensing | No legal source for brick text and asking price | Licensed API or user paste-in, no scraping |
| Rendering millions of lots | Slow or crashing map | Vector tiles, lots drawn only at zoom 16 and above, server-side simplification |
| Compose Multiplatform map support differs by target | Web or desktop map lags Android and iOS | Wrap the map behind an interface, prove it on each target in Milestone 0 |

**Non-functional targets**

- Address lookup returns a full Property Card in under 800 ms at p95 on a warm cache.
- Map pan and zoom hold 60 fps on a mid-range phone at lot zoom.
- Shortlist and last viewed properties work offline.
- Search queries and shortlist stay on the device unless sync is turned on.
- Data refresh: addresses quarterly, sales and flood on release, GTFS weekly, catchments quarterly.
- Accessibility: map has a list-view equivalent, screen-reader labels on all cards, and colour is never the only flood or slope indicator.

## Milestones and open decisions

Build the cheap, high-confidence attributes first and prove the risky ones early.

1. **M0, feasibility spike:** confirm each dataset's licence and format, stand up local PostGIS and load one suburb end to end, and prove the map renders on Android and desktop.
2. **M1, map shell:** suburb boundaries, suburb search and selection, Suburb Card with price, block size and distances.
3. **M2, property lookup:** address autocomplete, lot outline, Property Card with block size, slope, aspect, beach, parks, shops and transport.
4. **M3, risk and schools:** flood layer and classification, school catchments, overlay toggles.
5. **M4, inferred attributes:** price range with comps, brick estimate with evidence and override.
6. **M5, decisions:** shortlist, compare, hard filters and weighted score.
7. **M6, release:** offline cache, accessibility pass, store builds, attribution screen.

**Open decisions** — decided: Android and desktop ship first, then iOS and web, with local PostGIS and a thin Ktor API for the MVP (hosted PostGIS or static packs later). Launch region decided (Newcastle and Lake Macquarie, see the decision log). Still open, with the default this spec assumes: the price source (own model on Valuer General sales).

## Decision log

Answers to the "Ask me first" questions, recorded as they are made. Full reasoning is in [docs/adr](../adr/README.md).

| PR | Decision | ADR |
| --- | --- | --- |
| 1 | Repo is the existing `HomeFinder` folder. CI is GitHub Actions. Package prefix `au.homefinder`. Layout as proposed, plus a separate `androidApp` module because AGP 9 disallows the Android application plugin in a KMP module. | [0001](../adr/0001-gradle-module-layout.md) |
| 1 | Versions: Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.0, Gradle 9.8.2, compileSdk 37, JVM 17. | [0001](../adr/0001-gradle-module-layout.md) |
| 1 | Dependencies approved: Compose MP + Material3, JUnit + MockK + coroutines-test, Ktor server + kotlinx.serialization, Koin. | [0001](../adr/0001-gradle-module-layout.md) |
| 2 | Migrations with Flyway (plain SQL, Docker image). Credentials in a gitignored `.env` with `.env.example`. Postgres bound to `127.0.0.1`. Image `postgis/postgis:18-3.6`. | [0002](../adr/0002-postgis-flyway-and-credentials.md) |
| 3 | Pilot region: Newcastle and Lake Macquarie, filtered by a suburb-name list. Full G-NAF Aug 2026 GDA2020 PSV. Raw data in gitignored `data/raw`. Download script run by the user. | [0003](../adr/0003-gnaf-pilot-region-and-loading.md) |
| 3 | No missing addresses: include every suburb overlapping either council (172), load LGA polygons, and fail the load if an in-council address is missed. | [0004](../adr/0004-gnaf-completeness-any-overlap-suburbs.md) |

## Working agreement for Claude Code

These rules apply to every PR. They exist so Claude Code never builds on a guess.

- **Ask, don't assume.** If a requirement, data source, schema, dependency, library version, licence or file location is ambiguous, stop and ask me before writing code. Offer 2–4 concrete options with a recommended one, so I can answer with a selection.
- **Small PRs.** One concern per PR, aiming for under about 400 changed lines excluding generated files and lockfiles. If a task would exceed that, propose a split before starting.
- **Every PR stands alone.** It builds, passes its tests, and can be demoed or checked on its own. No half-wired features and no drive-by refactors.
- **PR description.** What changed, why, how it was tested, which questions I answered, and which are still open.
- **Dependencies need approval.** Ask before adding any library or changing a major version.
- **No scraping** of listing portals, and no copying of data whose licence is unclear. Ask first.
- **Provenance everywhere.** Every attribute carries value, confidence, source and as-of date. "Unknown" is a valid value; silent defaults are not.
- **Prices display in AUD.**
- **Pipeline stages are idempotent** and record their input versions, so a rerun gives the same output.
- **Mark guesses.** Where this spec says "to confirm" or lists a default, treat it as a question to ask, not a decision.

**Proposed repo layout (confirm with me in PR 1):** `shared` (models, serialization), `composeApp` (Android and desktop targets, with iOS and web targets added later), `server` (Ktor API), `pipeline` (SQL, GDAL and tiling scripts, plus Docker Compose for PostGIS), `docs`.

## PR plan

Twenty-five PRs take the project from empty repo to a release on Android and desktop. Each row lists the questions Claude Code must put to me before starting, and what "done" means. Order matters: later PRs depend on earlier ones.

| # | PR | Ask me first | Done when |
| --- | --- | --- | --- |
| 1 | Repo skeleton: Gradle modules, version catalog, CI build | Repo location and name, CI provider, Kotlin and Compose Multiplatform versions, layout above | Empty app builds on Android and desktop in CI |
| 2 | Local PostGIS in Docker Compose, with schema migrations | Migration tool, how credentials are stored | `docker compose up` gives a migrated, empty database |
| 3 | Pipeline: load G-NAF addresses for the pilot region | Pilot region (open decision), where raw downloads are kept | Addresses for the pilot load and are queryable |
| 4 | Pipeline: load cadastre lots and suburb boundaries | Bulk download or web service, target CRS | Lots and suburbs for the pilot load with valid geometry |
| 5 | Map spike: MapLibre in Compose Multiplatform on Android and desktop | Map library choice, basemap source and licence | A map renders and pans on both targets |
| 6 | Shared models and serialization tests | Attribute shape and enum values in the Data model section | `Attribute<T>`, `PropertyReport` and `SuburbReport` round-trip in tests |
| 7 | Ktor API skeleton with health check and config | Port, auth approach (LAN, Tailscale, API key) | API runs locally and the app reaches it from Android |
| 8 | Suburb tiles and boundary layer with name labels | Tile server choice (PMTiles or Martin) | Suburb outlines and names draw at wide zoom |
| 9 | Suburb selection state and a basic Suburb Card | Card layout on phone and desktop | Tap or search selects a suburb with a highlight and card |
| 10 | Pipeline: suburb stats (price medians, block size spread) | Price source (open decision), sales file licence check | Suburb Card shows real median price in AUD |
| 11 | Address autocomplete endpoint and search box | Search ranking, how duplicate suburb names resolve | Typing an address returns suggestions and flies the map to it |
| 12 | Lot layer at zoom 16+, tap to resolve a lot | Lot tile source and zoom thresholds | Tapping a lot highlights it and returns its ID |
| 13 | Pipeline: block size and slope from the DEM | DEM resolution to use, slope class thresholds | Per-lot area and slope classes stored |
| 14 | Pipeline: backyard aspect | Building footprint source, how to handle corner and battle-axe lots | Per-lot bearing and north-facing flag with an ambiguity flag |
| 15 | Pipeline: distances to beach, parks and shopping | Park size cutoff, what counts as a shopping centre, walking route engine | Nearest-item distances stored per lot |
| 16 | Pipeline: transport proximity from GTFS | API key handling, which modes count | Nearest stops and peak frequency stored per lot |
| 17 | `PropertyReport` endpoint and Property Card UI | Card layout, how confidence is shown | Address search ends in a populated Property Card |
| 18 | Flood ingest, classification and map overlay | Which flood layers to trust, how to show "not mapped" | Lots show a flood class or "not mapped", overlay toggles |
| 19 | School catchments and School Card | Wording of the verify-with-school notice | Card shows catchment schools plus the notice |
| 20 | Price estimate model with comparable sales | Model approach, how many comps, licensing | Property Card shows an AUD range with its comps |
| 21 | Brick: paste-in listing text, keyword rules, manual override | Keyword list, how overrides are stored | User can set and see wall material with evidence |
| 22 | Shortlist on device with SQLDelight | Sync or local only | Starred items persist across restarts |
| 23 | Compare view for up to four properties | Which rows, how to mark best and unknown | Side-by-side table works on both targets |
| 24 | Hard filters and weighted score | Default weights, how unknowns count | Filters grey out lots and the score shows factors used |
| 25 | Release hardening: offline cache, accessibility, attribution screen, signed builds | Signing setup, store targets | Release builds install and pass the accessibility checklist |

**Not in this plan:** iOS and web targets, hosted PostGIS, static packs and imagery-based brick detection. Each becomes its own plan once the Android and desktop MVP is in use.

## Handoff prompt

Paste this into Claude Code from an empty repo, with this spec exported or attached.

```markdown
You are building the Home Finder app described in the attached spec. Read all of it first.

Rules:
- Follow the "Working agreement for Claude Code" section exactly.
- Work through the "PR plan" in order. Start with PR 1 only.
- Before writing any code for a PR, ask me every question listed in its "Ask me first" column, plus any others you have. Give 2-4 options with a recommendation so I can answer by selecting. Do not assume answers, and do not treat spec defaults as decisions.
- Keep each PR small (about 400 changed lines or fewer, excluding generated files). If it will be bigger, propose a split and wait for my answer.
- After each PR, summarise what changed, how you tested it, and which questions remain open, then stop and wait for me before starting the next PR.
- Never scrape listing portals, and never add a dependency without asking.

Start now by asking the PR 1 questions.
```
