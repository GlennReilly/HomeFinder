# 0001. Gradle module layout and version pins

Status: Accepted

## Context
The spec proposed `shared`, `composeApp` (Android + desktop), `server`, `pipeline`, `docs`. AGP 9 does not allow
`com.android.application` in a Kotlin Multiplatform module.

## Decision
- `shared` and `composeApp` are KMP modules (`com.android.kotlin.multiplatform.library` + `jvm()`).
- `androidApp` is a plain Android application module that depends on `composeApp`.
- Desktop entry point lives in `composeApp` jvmMain.
- Pins: Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.0, Gradle 9.8.2, compileSdk 37, minSdk 26, JVM 17.
- Tests: JUnit 6 (Jupiter) and MockK. Koin and Ktor are wired as dependencies only.
- CI: GitHub Actions.

## Consequences
One extra module compared with the spec layout. Versions were chosen as latest stable on 2026-10-29 and verified by a
local build.
