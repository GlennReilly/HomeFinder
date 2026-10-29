# 0019. Pivot the target platform to web

Status: Accepted

## Context
The product was specified for Android, iOS, desktop and web with Compose Multiplatform. In practice the work went into an
Android app and a desktop app, and the desktop map needed three renderers (MapLibre, a raster fallback and our own vector
renderer) because MapLibre failed on the available GPU. You decided to make the web the only target. The audience is
you alone, run locally or on a private host. An audit of the code showed which parts depend on the old clients.

## Decision
- The target is a single responsive web app. Android, iOS and desktop are dropped. The new spec is
  `docs/spec/Home Finder Web App — Product & Technical Spec.md`; the old one is kept for history.
- The client is Kotlin/JS with MapLibre GL JS and a plain DOM UI. It was chosen over Compose Multiplatform for web (the
  map library is Alpha there and the page would be drawn to a canvas) and over React (breaks the Kotlin-first rule).
  MapLibre GL JS is the mature web map, is called through hand-written `external` declarations behind `MapRenderer`,
  and the DOM UI (built with `kotlinx-html`) gives native accessibility and a small bundle.
- Kept unchanged: the whole `pipeline/`, the Ktor `server/` (it additionally serves the web bundle from the same origin),
  the `shared` models and API contract (gaining a `js` target), and the Compose-free client logic (view model, card
  rows, layout rule, API client, map config, about 440 lines with tests), which moves to a new `client` module with
  `jvm` and `js` targets so its tests keep running on the JVM.
- New: a `webApp` module for the DOM UI, the MapLibre interop and the bundle (W0). Retired after W0 passes (W1):
  `androidApp`, `composeApp` (Compose UI, the Android and desktop targets, the vector and raster renderers and their
  tests), Compose and Material3 from the build, the Java 25 desktop requirement and the `mapRenderer` flag.
- Access: same-origin app and API, server bound to localhost or a private network, API key kept as a second check
  (a browser-held key is not a secret) and sent on tile requests as a header. The shortlist moves from SQLDelight to
  browser storage.
- Data licences: the app stays private, so ADR 0018 (Valuer General personal use, no redistribution) is unchanged.

## Consequences
- About 400 lines of Compose UI and about 1,650 lines of renderer code (plus their tests) are replaced or deleted, and
  the Java 25 requirement goes. The map becomes a standard, widely used library.
- The Kotlin MapLibre bindings are ours to write and maintain; the used API surface is deliberately small.
- Frontend tooling (npm packages through Gradle) is new, and so is `kotlinx-html` for building the DOM, approved by you
  over Compose HTML (smaller, no Compose in the web build, output testable on the JVM). Updates are explicit: a
  `render(state)` function redraws the changed region, which is manageable for a small UI; if the UI grows far (compare
  tables, filters) revisiting Compose HTML is a contained change because the view model and API client do not depend on it.
- The server gains static file serving; CORS is not needed because the origin is shared.
- This supersedes the client parts of ADR 0001 (module layout and Android/desktop targets), 0008 to 0011 (map library and
  desktop renderers), 0013 (the Android link and cleartext HTTP) and 0015 (client layers, replaced by MapLibre GL JS
  layers with the same look). Their pipeline, server and tile decisions (0002 to 0007, 0012, 0014, 0016 to 0018) stand.
  0017 (selection and card) stands in behaviour; its Compose implementation is replaced and the Android back handling
  retired.
