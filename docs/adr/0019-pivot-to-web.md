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
- The UI is Compose Multiplatform for web (Wasm), chosen over Kotlin/JS with MapLibre GL JS and over React, to reuse
  the existing view model, card rows and card UI. maplibre-compose lists web as Alpha, so the first milestone (W0) is a
  spike that must show the map, our suburb tiles and the card working in a browser. If it fails, the fallback is a
  MapLibre GL JS map behind the `MapRenderer` interface, recorded in a new ADR.
- Kept unchanged: the whole `pipeline/`, the Ktor `server/` (it additionally serves the web bundle from the same origin),
  the `shared` models and API contract (gaining a `wasmJs` target), the selection model, card rows, card UI and API client.
- Retired after W0 passes (milestone W1): `androidApp`, the Android target, the desktop JVM entry point, the vector and
  raster renderers and their tests, the Java 25 desktop build and the `mapRenderer` flag.
- Access: same-origin app and API, server bound to localhost or a private network, API key kept as a second check
  (a browser-held key is not a secret). The shortlist moves from SQLDelight to browser storage.
- Data licences: the app stays private, so ADR 0018 (Valuer General personal use, no redistribution) is unchanged.

## Consequences
- Roughly 1,650 lines of renderer code and ~1,650 lines of their tests are deleted, and the Java 25 requirement goes.
- Compose for web depends on an Alpha map library, on Wasm-GC browsers and on a canvas UI with weaker accessibility than
  DOM; the spike measures all three before more client work is built.
- The server gains static file serving; CORS is not needed because the origin is shared.
- This supersedes the client parts of ADR 0001 (module layout and Android/desktop targets), 0008 to 0011 (map library and
  desktop renderers), 0013 (the Android link and cleartext HTTP) and 0015 (client layers). Their pipeline, server and
  tile decisions (0002 to 0007, 0012, 0014, 0016 to 0018) stand. 0017 (selection and card) stands, with the Android back
  handling retired.
