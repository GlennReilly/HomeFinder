# 0009. Desktop raster-tile fallback map

Status: Accepted

## Context
ADR 0008 found that maplibre-compose cannot render on desktop on the development machine (GeForce GT 710, nouveau):
its desktop bridge needs a Vulkan device with external-memory support that matches Compose's OpenGL device, and
software rendering does not satisfy it. The spec says a target can swap renderer behind the map interface.

## Decision
- Desktop draws a raster-tile map itself on a Compose canvas (`RasterTileRenderer`), so it needs no GPU interop and
  runs on any hardware. Android keeps the MapLibre vector map.
- Tiles come from the OpenStreetMap standard tile server (`tile.openstreetmap.org`), fetched with the Ktor client (CIO
  engine) using an identifying User-Agent, at most two parallel requests and an in-memory LRU cache of 512 tiles, as
  the OSM tile usage policy asks. The map shows "© OpenStreetMap contributors".
- Interaction: drag to pan, scroll wheel zooms around the cursor, and zoom buttons. Fractional zoom scales tiles from
  the integer zoom below.
- Desktop uses the fallback by default. `./gradlew :composeApp:run -PmapRenderer=maplibre` selects the MapLibre
  vector map on machines with a capable GPU.
- Dependencies added with approval: `ktor-client-core` and `ktor-client-cio` (jvm only).

## Consequences
- Desktop shows a raster basemap, so it looks different from Android's vector map. Later overlays (suburbs, lots,
  flood) must be drawn by us on this canvas as well as through MapLibre on Android.
- OSM's policy forbids heavy or bulk use, so this is not suitable for prefetching or offline packs; a self-hosted
  raster source would replace it if needed.
- A tile that fails to download stays blank until the next pan or zoom re-requests it; there is no retry timer.
- Tile maths and the cache are unit tested; the canvas and input handling were checked by rendering the real app
  offscreen to an image, including a simulated drag and scroll.
