# 0031. The score label waits until you have zoomed in two taps

Status: Accepted

## Context
ADR 0029 made a rated suburb's name and score visible at every zoom from 5. You asked for them not to show while the map is
zoomed out, only once you have zoomed in the equivalent of two taps of the "+" button (you cannot see the zoom level).
You also asked for the "Reviews on Homely" link to sit above the Homely rating on the card.

## Decision
- The map opens at zoom 10 and one tap of "+" is one zoom level, so the name-and-score label appears from zoom 12 (a
  named constant, `RatingStyle.ZOOM_TAPS_BEFORE_SHOWN` = 2 above the opening zoom). It then stays at every closer zoom, as
  in ADR 0029.
- Zoomed out further than that, a rated suburb shows its ordinary name like any other suburb, without the score, so no
  suburb loses its name; a second layer carries those names until the score label takes over.
- On the card, under the price change: the "Reviews on Homely" link, then the Homely rating (once entered), then its entry
  form. The link no longer sits at the bottom. (This changes the order of ADR 0030.)

## Consequences
- Searching for a suburb (which zooms to about 14) or tapping one shows its score straight away; the wide view stays
  uncluttered.
- If the opening zoom ever changes, the threshold moves with it.
