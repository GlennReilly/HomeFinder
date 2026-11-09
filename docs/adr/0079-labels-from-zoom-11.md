# 0079. The Homely and NAPLAN map labels show from zoom 11

Status: Accepted

## Context
ADR 0031 held the Homely score label back until two taps of the "+" button from where the map opens (zoom 12) so a zoomed-out map is not cluttered. The
NAPLAN label (ADR 0076) shares that zoom. You asked for both to appear one tap sooner.

## Decision
- `RatingStyle.ZOOM_TAPS_BEFORE_SHOWN` goes from 2 to 1, so both labels start at zoom 11 (the map opens at 10). The NAPLAN label still ends where lot
  outlines start (zoom 16).
- Below zoom 11 a rated suburb still shows its ordinary name, as before.

## Consequences
- More labels are on screen at once; at zoom 11 the suburbs are small, so some labels may crowd or be dropped by the map's own collision handling.
- ADR 0031's "two zoom taps" is superseded; its other decisions stand.
