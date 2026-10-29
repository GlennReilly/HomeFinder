# 0008. Map library, basemap and the desktop finding

Status: Superseded by 0019

## Context
PR 5 proves a map renders and pans on Android and desktop (spec milestone M0). The spec asks which map library and
basemap to use, and says to wrap the map behind an interface so a target can swap renderers.

## Decision
- Library: maplibre-compose 0.20.0 (BSD-3-Clause, MapLibre org). Android is beta and desktop is alpha upstream.
- Basemap: OpenFreeMap "liberty" vector style (free, no API key, OpenStreetMap data via OpenMapTiles). The map
  control shows the required attribution.
- The map sits behind `MapView` and a `MapRenderer` interface (`MapConfig.Pilot` holds style, camera and attribution
  text), so a target can swap renderer.
- Android runtime: OpenGL ES (`maplibre-compose-runtime-opengl-android`). Desktop runtime: OpenGL on Linux x64.
  Other desktop OS runtimes are added when those OSs are targeted.
- The desktop target now needs Java 25 or later (a maplibre-compose requirement); `composeApp` uses a JDK 25
  toolchain and the run task passes `--enable-native-access=ALL-UNNAMED`. Other modules stay on JDK 17.

## Findings (2026-10-29)
- Android: renders and pans. Checked on the Pixel 8 Pro API 33 emulator with software GL: map centred on the pilot
  region, tiles load, swipes pan.
- Desktop (Linux, GeForce GT 710 on the open-source nouveau driver): does not render. The map surface fails with
  "No Vulkan device supports VK_KHR_external_memory_fd and matches the Compose graphics device", with both the Vulkan
  and OpenGL runtimes. Forcing Compose software rendering fails too, because the library needs Compose on OpenGL.
  The GPU and driver are too old for the library's desktop bridge. Not yet tried on newer hardware.

## Consequences
- The M0 done-criterion (renders and pans on both targets) is met on Android and not on this desktop machine. The
  desktop map is unproven; options are another machine or GPU, a fallback renderer behind `MapRenderer`, or waiting
  for upstream desktop support.
- JDK 25 is now needed to build `composeApp`; CI installs it alongside JDK 17.
- The API is unstable: parameter names were read from the 0.20.0 jar (`rememberMapState(baseStyle,
  initialCameraPosition)`) and may change in minor releases.
