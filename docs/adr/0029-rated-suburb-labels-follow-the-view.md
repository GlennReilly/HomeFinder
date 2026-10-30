# 0029. Rated suburb labels follow the view

Status: Accepted

## Context
ADR 0028 puts a Homely star score under the suburb's name on the map. You pointed out that the name and score showed only
for a while: they vanished when you zoomed in a little, and you want both always on screen. Two things caused it: the
ordinary suburb names stop at zoom 15 (where lots take over), and each name is a single point per suburb, which scrolls
out of view as you zoom into a big suburb. Drawing the labels from the map's outline tiles instead put one label in every
tile the suburb crosses (Jesmond was labelled three times at once), which was no better.

## Decision
- A rated suburb is labelled by its own layer ("NAME" over "★ 2.1") at every zoom from 5 up, allowed to overlap other
  labels so nothing hides it. The ordinary name layer leaves rated suburbs out, so a name is drawn once.
- There is exactly one such label per rated suburb. Where it sits is worked out on screen each time the map settles (after
  a move and when tiles finish loading): the suburb's usual label point while that is in view, otherwise the point
  inside the visible part of the suburb nearest the middle of the view. If no part of the suburb is in view there is no
  label.
- The placement is plain geometry (`LabelPlacement` in `client`, unit tested on the JVM): visible part of the outline
  pieces MapLibre holds, point-in-polygon with holes, a grid of candidates, a vertex fallback for thin slivers.
- Unrated suburbs keep the old names (to zoom 14, one fixed point), which suits a crowded map.

## Consequences
- A rated suburb's label can move a little as you pan or zoom, then settles; it follows the view rather than a fixed spot.
- It relies on the outline tiles being loaded for what is on screen, which they are for the visible area.
- Making every suburb name behave this way would need the same placement for all 173 suburbs; not done.
