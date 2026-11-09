# 0082. A compass that is always on the map

Status: Accepted

## Context

The map already had MapLibre's `NavigationControl`, whose compass button is the third button under the zoom buttons. It was only 29 px, drawn as a small
faint grey arrow, so the user did not see it and reported that the map had no compass.

## Decision

Keep MapLibre's own compass (`showCompass: true`, no pitch tilt) so it turns with the bearing and a click, tap or Enter resets the map to north. CSS makes
the three buttons 44 px (the tap size) and draws the compass as a red north arrow with an "N". The button gets the spoken name from a constant
(`MapDefaults.COMPASS_LABEL`, set with `Aria.LABEL`). It sits top-left under the zoom buttons, clear of the search box, the scale bar (bottom) and the
right-hand panel or bottom sheet. An e2e test checks it is visible on load, after zooming and after the map is turned.

## Consequences

- No new control to maintain; MapLibre updates may change the compass class name, and the e2e test would show it.
- The zoom buttons are now 44 px wide, so the control column is 15 px wider (the search box starts at 64 px and still clears it).
