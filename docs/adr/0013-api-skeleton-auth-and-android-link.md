# 0013. API skeleton, API key and the Android link

Status: Accepted — Partly superseded by 0019 (Android link)

## Context
PR 7 adds the Ktor API with a health check and configuration, and makes the Android app reach it. The spec lists LAN,
Tailscale or an API key for access, and leaves the port and configuration approach open. Android blocks plain HTTP by
default.

## Decision
- Port 8080, listening on all interfaces by default (`API_HOST`, `API_PORT`), so the emulator, a phone on the LAN and
  Tailscale later can all reach it.
- Access is controlled by an API key sent in an `X-API-Key` header. Everything under `/v1` needs it; `GET /health`
  stays open. Keys are compared by SHA-256 digest so timing does not leak how much matched. Responses never contain
  the key and the server never prints it.
- Configuration is read from environment variables into a typed `ServerConfig` (no new dependency). A missing or
  short key (under 16 characters) or a bad port stops the server at start-up and lists every problem. The result is
  a sealed `ConfigResult`, not an exception. `./gradlew :server:run` reads `server/.env` (copy `server/.env.example`).
- `shared` holds the API contract (paths, header name, `HealthResponse`, `StatusResponse`, `ApiError`) so server and
  clients cannot drift, and exposes `kotlinx-serialization-json` as an `api` dependency because `ApiJson` exposes its
  `Json` type.
- Android reaches the API with the Ktor client on the OkHttp engine (desktop uses CIO). The base URL defaults to
  `http://10.0.2.2:8080` (the emulator's host) and both it and the key come from Gradle properties `-PapiBaseUrl` and
  `-PapiKey`. Only debug builds allow plain HTTP, through a debug network security config; release builds stay
  HTTPS-only. The desktop app reads `HOMEFINDER_API_URL` and `HOMEFINDER_API_KEY`.
- The app shows a status badge: it asks the open health endpoint whether the server is up, then the authenticated
  status endpoint whether the key works, sending the key only to the second. Tap to check again.
- New dependencies: `ktor-client-core` (all targets), `ktor-client-okhttp` (Android), `ktor-client-cio` (desktop) and
  `ktor-server-test-host` (server tests only).

## Consequences
- The key is compiled into debug builds of the app through BuildConfig. That is acceptable for development on a
  trusted machine; a release build needs a different way to supply it (PR 25).
- Traffic is plain HTTP on the LAN in development; the key travels unencrypted on that network. Tailscale encrypts the
  link when it is used, and HTTPS can be added for release.
- The server logs no requests yet: there is no SLF4J provider on the classpath, so a start-up warning is expected.
  A logging library is a separate decision.
- No database is touched yet; the health check reports the server only, and a database check arrives with the first
  endpoint that reads it.
