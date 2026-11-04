# 0048. Suburb and Filters tabs in the right-hand panel

Status: Accepted

## Context
The filters sat behind a small button on the far left, away from the Suburb Card on the right, and were easy to miss (ADR 0035). The filter panel is tall (four filters,
the score weights and the top 10), as is the card, so they cannot both sit open on the right without squeezing each other, and on a narrow window the card is a bottom sheet.

## Decision
- The right-hand panel (a bottom sheet below 600 px) has a tab strip that is always visible: **Suburb** and **Filters**. The floating Filters button is gone.
- It opens on **Filters**, so they are the first thing seen. The Filters tab shows a live count while any filter is on ("Filters (12 of 173)").
- Selecting a suburb or lot switches to the **Suburb** tab; clearing the selection (Close or Escape) goes back to Filters. Filter state lives in the page and is untouched by switching.
- With nothing selected, the Suburb tab says to tap a suburb or lot.
- Tapping the open tab folds the panel down to its tab strip, and tapping it again unfolds it. On a narrow window the panel starts folded so it does not cover the map.
- The pure state (`SidePanelState`) lives in the client module and is unit tested.

## Consequences
- The card and the sliders cannot be seen at the same time; one tap switches, and the tab count keeps the effect of the sliders visible.
- The panel is always on screen, so the map is narrower by the panel width even with nothing selected (the search box already allowed for this); folding it frees the space.
- Transport and other future criteria join the Filters tab without new controls.
