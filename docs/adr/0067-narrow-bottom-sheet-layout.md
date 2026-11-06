# 0067. Narrow windows: a half-height sheet, a compact lot header, a fold chevron

Status: Accepted

## Context
Below 600 px the right-hand panel is a bottom sheet (ADR 0048). Open, it took 60% of the height, so the scale bar and the map credit sat under it and little map
was left. The Lot Card header (two-line address plus a Parcel line) used a third of the screen before the first row, and a folded sheet gave no sign it could open.

## Decision
- The open sheet has a fixed height of `--sheet-open-height` (50dvh); the card scrolls inside it. The map credit and the scale bar are lifted to sit just above it
  (`body:has(#side[data-collapsed="false"])` in CSS, no script). Folded, they stay above the tab strip as before.
- On a narrow window the Parcel part of the Lot Card subtitle is hidden in the pinned header and shown once below it, so it scrolls away; the address title is smaller.
  Wide windows keep the ADR 0053 header unchanged.
- The selected tab shows a chevron (up when folded, down when open) drawn in CSS from `aria-expanded`, which was already set. `resize: none` on the sheet.

## Consequences
- The parcel text is in the DOM twice (one copy `display: none` per width).
- `:has()` is needed (all current browsers).
