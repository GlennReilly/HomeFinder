# 0015. Suburb outlines and names on the clients

Status: Accepted

## Context
PR 8b draws the suburb tiles of ADR 0014 on every client: Android through MapLibre, and the two desktop fallbacks
(raster and our own vector renderer). The spec asks for a thin outline and a name per suburb at wide zoom, with the
outline fading and the names going once the map reaches street level.

## Decision
- One shared style (`SuburbStyle`): a purple 1.2 dp outline from zoom 5, fully opaque to zoom 12 and fading to a faint
  line by zoom 15; names from zoom 5 until zoom 14; bigger suburbs win where names collide.
- Android adds a MapLibre vector source (key as a `?key=` query parameter) with a line layer and a symbol layer in
  `rememberMapState`. The fade is done through the line colour's alpha. Names use the "Noto Sans Bold" font, which
  OpenFreeMap's glyph server has; MapLibre's default font stack (Open Sans) is not there (HTTP 404).
- Desktop gets one overlay, `SuburbOverlay`, drawn on top of either base map. It loads tiles through our own decoder
  with the key as a header, draws one outline path per tile and places names with the existing label placer.
- A suburb layer is added only when the API and its key are known (`mapConfig`), so the map still works without them.

## Findings
- The layer's `opacity` and `layerOpacity` parameters, the `format(span(...))` text field and MapLibre's default font
  were part of a first version that drew no layers on Android. The cause was not isolated, because the first checks
  counted tile requests, which MapLibre's cache (and the server's 5 minute cache header) hid. The working version
  avoids all of those; use screenshots, not request counts, to check the map.
- Verified on the real 173-suburb data on the Pixel 8 Pro emulator: outlines and names at the region view, and at about
  zoom 13 fainter outlines with names still showing. Desktop was checked by rendering both renderers offscreen.

## Consequences
- The fade on Android is a colour interpolation, so it is a fade of the line's alpha, not a layer opacity.
- Names are uppercase because the NSW boundary data is.
- The clients draw suburbs but do not select them; selection and hover are PR 9.
