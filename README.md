# Home Finder

Look up any NSW address or suburb on a map and see the factors that decide a purchase. See
[the spec](docs/spec/) for the product and technical design.

## Modules

| Module | Purpose |
| --- | --- |
| `shared` | Models and serialization (Android + JVM) |
| `composeApp` | Shared Compose UI and the desktop entry point (Android library + JVM) |
| `androidApp` | Android application entry point |
| `server` | Ktor API |
| `pipeline` | Data pipeline (later PRs) |

`androidApp` is separate from `composeApp` because AGP 9 does not allow the Android application plugin in a
Kotlin Multiplatform module (see [ADR 0001](docs/adr/0001-gradle-module-layout.md)).

## Build and test

Requires JDK 17+ and, for Android, the Android SDK (platform 37) with `sdk.dir` in `local.properties`.

```bash
./gradlew build
```

```bash
./gradlew :composeApp:run
```

## API server

```bash
cp server/.env.example server/.env   # then set API_KEY (16+ characters)
```
```bash
./gradlew :server:run
```

The server listens on `0.0.0.0:8080`. `GET /health` is open; everything under `/v1` needs the `X-API-Key` header.
Build the Android app against it with `-PapiBaseUrl=...` (default `http://10.0.2.2:8080`, the emulator's host) and
`-PapiKey=...`; the desktop app reads `HOMEFINDER_API_URL` and `HOMEFINDER_API_KEY`. See
[ADR 0013](docs/adr/0013-api-skeleton-auth-and-android-link.md).
