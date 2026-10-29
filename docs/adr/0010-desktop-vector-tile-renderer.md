# 0010. Desktop vector-tile renderer

Status: Superseded by 0019 (labels extended by 0011)

## Context
ADR 0008 found that maplibre-compose cannot render on this machine's GPU, and ADR 0009 added a raster-tile fallback.
You asked for a second fallback that keeps the vector look of Android and the same data, and chose an own
vector-tile renderer over an embedded browser.

## Decision
- `VectorTileRenderer` fetches OpenFreeMap vector tiles, decodes them with our own Mapbox Vector Tile decoder (PR 5c,
  no protobuf dependency) and draws them on a Compose canvas, so it needs no GPU interop.
- The tile URL comes from OpenFreeMap's TileJSON at start-up because it contains a release version that changes.
  Tiles beyond the source's maximum zoom (14) are the deepest tiles scaled up, so detail stays crisp.
- A simplified style for the OpenMapTiles schema: land cover, land use, parks, water, waterways, roads with casings
  by class and zoom, rail, paths, buildings (from zoom 13), boundaries, and place and water labels with a halo and
  greedy collision avoidance. Decoded tiles become one Path per layer and class, built off the UI thread and kept in
  an LRU cache of 192 tiles.
- The pan, zoom, buttons and attribution interaction is shared with the raster renderer (`PannableMap`).
- Selection on desktop: `-PmapRenderer=vector` for this renderer, `-PmapRenderer=maplibre` for MapLibre, otherwise
  the raster fallback stays the default.
- New dependency (jvm only): `kotlinx-serialization-json`, already in the version catalog, to read the TileJSON.

## Consequences
- Desktop can show the same data as Android, but with our own simplified style, not Android's full one. Icons, house
  numbers, road names along lines and point-of-interest symbols are not drawn.
- Vector layers we add later (suburbs, lots, flood) can be drawn from the same tile pipeline on desktop.
- Seams between tiles can show as hairlines because neighbouring tiles are drawn independently.
- A tile that fails to download stays blank until the next pan or zoom, as in the raster renderer.
- Decoder, style, label placement and tile preparation are unit tested. The canvas drawing was checked by rendering
  the real app offscreen against live tiles at region, city and over-zoomed levels. It has not run in a real window.
