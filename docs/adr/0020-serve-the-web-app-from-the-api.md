# 0020. Serve the web app from the API, local by default

Status: Accepted

## Context
ADR 0019 made the product a web app for one user, run locally or on a private host. The page and the API should share
one origin so no CORS is needed and the key never has to travel in a URL. The spec's "Access and security" section says
the server listens on localhost by default.

## Decision
- The Ktor server serves the built web bundle from `/` when `WEB_ROOT` names a folder containing `index.html`; without
  `WEB_ROOT` it serves the API only. The API routes (`/health`, `/v1`, `/tiles`) are more specific and win, so the
  API key rules are unchanged; the static route only answers what is left, and an unknown file is a plain 404.
- Files are sent with `Cache-Control: no-cache` because the bundle's file names carry no content hash. Ktor's static
  file support rejects paths that climb out of the folder; tests check several encodings.
- The default listen address changes from `0.0.0.0` to `127.0.0.1` (`API_HOST`). Reaching the app from a phone or another
  machine needs `API_HOST=0.0.0.0` set on purpose, on a private network or behind Tailscale.
- `./gradlew :server:run` builds the production bundle first and sets `WEB_ROOT` to it, so one command runs everything.
  A `WEB_ROOT` in `server/.env` overrides it. A `WEB_ROOT` without an `index.html` stops the server with a clear message.
- The dev server on port 8081 (`:webApp:jsBrowserDevelopmentRun`) stays for fast front-end work; it proxies the API.

## Consequences
- The server is no longer reachable from other devices unless you ask for it. This reverses the "all interfaces"
  default of ADR 0013, which existed for the Android emulator and phone.
- `:server:run` takes longer the first time (the web bundle build, about a minute), then is cached by Gradle.
- A change to the web app needs a rebuild to be served by the API; use the dev server while editing.
