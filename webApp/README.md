# webApp

The Home Finder web client (ADR 0019): Kotlin/JS, a MapLibre GL JS map and a DOM UI built with `kotlinx-html`. The
UI-free logic (API client, selection view model, Suburb Card rows and card HTML) lives in `client` and is tested on the JVM.

## Run it (development)

Start the API (see the main README), then:

```bash
./gradlew :webApp:jsBrowserDevelopmentRun --continuous
```

Open http://localhost:8081. The dev server forwards `/v1`, `/tiles` and `/health` to the API on port 8080, so the page and
API share one origin as they will in production. The first visit asks for the API key and keeps it in this browser's
`localStorage`; it is sent as the `X-API-Key` header (also on tile requests) and never put in a URL.

## Production bundle

```bash
./gradlew :webApp:jsBrowserDistribution   # build/dist/js/productionExecutable
```

About 1.9 MB of JavaScript (about 0.5 MB gzipped) plus MapLibre's 0.5 MB worker file.

## Gotchas

- maplibre-gl 6 is ES-module only, so the target uses `useEsModules()`.
- MapLibre starts its web worker from a separate file a bundler does not emit; `copyMaplibreWorker` copies it next to the
  page and `SuburbMap` calls `setWorkerUrl`.
- Suburb layers are added on the map's `load` event; `style.load` is too early (no tile manager for the source yet).
- The map is checked in a real browser (there are no browser tests yet); the card HTML is unit-tested in `client`.
