# 0011. Street names and other labels on the desktop vector map

Status: Accepted

## Context
ADR 0010 listed "road names along lines, house numbers and point-of-interest symbols" as not drawn. You asked for
street names and similar labels on the vector map.

## Decision
- Extends ADR 0010; its consequence about missing labels no longer holds for the items below.
- Street names are drawn along roads from the `transportation_name` layer: text is centred on the longest part of
  the road, rotated to follow it and never upside down. A road that bends under the text is skipped, because text is
  drawn straight. Names appear by road class (motorways from zoom 11, minor streets from zoom 15).
- Also labelled: park names at the centroid of the largest ring (zoom 12), points of interest as a dot with a name
  (zoom 15; bus stops are hidden as clutter), house numbers (zoom 17), airports and mountain peaks.
- Collision avoidance is greedy by priority (places first, then streets, then points of interest and house numbers).
  Labels with the same name (a street over several segments, a park over several tiles) are kept at least 300 to
  400 px apart so a name is not repeated everywhere.

## Consequences
- Curving roads can lack a name where no straight stretch is long enough; text along curves is not done.
- A name is chosen as `name` only, with no language fallback, which suits Australia.
- Still not drawn: icons or symbols, road shields and route numbers, building names and one-way arrows.
- Geometry (placing text along a line), label placement, style thresholds and tile preparation are unit tested. The
  drawing was checked by rendering the real app offscreen against live tiles at street level and closer.
