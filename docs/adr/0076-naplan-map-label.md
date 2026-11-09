# 0076. Each suburb's NAPLAN average as a clickable map label

Status: Accepted

## Context
You asked for each suburb's average NAPLAN figure on the map, in a different colour and symbol from the Homely score labels (ADR 0028), and for a click on
it to go to that suburb's Schools section. The figure already exists: the share-weighted average of your typed figures for the suburb's zoned schools, worked
out by the server for the NAPLAN filter (ADR 0074) and sent in `GET /v1/suburbs/filters`, which the page reloads after every NAPLAN save or clear. The map
needs a point to draw it at, and the filter values had none.

## Decision
- **Data:** the filter values gain `latitude` and `longitude` per suburb: the point the suburb's name is drawn at (`suburb_label`, else a point inside the
  suburb), the same point the ratings list uses. No new request, no migration, no load script. The label shows the server's `zonedNaplan` as it is (one
  average, never a second one). A suburb with no typed figure for a listed zoned school gets no label. Every filter-values load, including the reload after a
  NAPLAN save or clear (ADR 0074), redraws the labels; a failed reload leaves them as they were.
- **Look:** a dark-blue diamond (an icon the page draws on a canvas, so it never depends on the map's font server having the glyph) followed by the signed
  whole number ("+27", "-41", "0") in dark blue `#0d47a1` with a white halo. The Homely label stays brown-orange `#8a4b00` stars; both colours are named side by
  side in `LabelColors`. On its white halo the blue is 8.6:1; it is neither red nor green, so it is not read as the filter tint. No wording names a source;
  the NAPLAN filter's Details note explains the label.
- **Zoom:** from `RatingStyle.MIN_ZOOM` (12, the Homely label's zoom) up to `TileContract.LOTS_MIN_ZOOM` (16), so it is never shown, nor clickable, in lot mode.
- **No overlap with the Homely label:** both labels are placed by the same `LabelPlacement` call for a suburb, so they share a point, and the NAPLAN label sits
  below it with a fixed vertical offset chosen per feature: further down (26 px) when the suburb has a Homely label (two lines), less (17 px) under a plain
  one-line name. Like the Homely label it is always drawn and never pushes other names off (`allow-overlap` and `ignore-placement`).
- **Click:** the map's one click handler first asks MapLibre for a NAPLAN label under the pointer (`queryRenderedFeatures` on that layer); a hit goes to the
  label handler instead of the suburb tap, so the two never both run. The pointer cursor shows over a label. The label handler selects the suburb (as a tap
  does), switches the panel to the Suburb tab, and once the card is loaded opens the Schools group (the per-page-load fold state, ADR 0064) and scrolls it in
  just under the pinned header (ADR 0053), then focuses its heading. `SchoolsJump` (client, UI-free) holds the wish while the card loads and drops it if
  something else is selected or the card fails.
- **Keyboard:** a map label cannot be reached by keyboard, so the Suburb Card's pinned bar gains a "Jump to Schools" button (a 44 px layout button beside
  Customise) that does the same; it is drawn only when the card has a Schools group to go to and not while customising.

## Consequences
- The filter-values response grows by two numbers per suburb (a few kilobytes); older clients ignore them.
- Only suburbs you have typed a figure for get a label, so the map shows as much as you have typed, no more; a label can rest on one school (as the filter can).
- A suburb whose name is out of view is labelled at the visible part of the suburb nearest the middle, as the Homely label is; its ordinary name may then be
  elsewhere, which is harmless.
- The offsets assume the current label sizes; changing the name or Homely text size means checking them again.
