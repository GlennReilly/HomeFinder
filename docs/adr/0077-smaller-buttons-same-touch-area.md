# 0077. Buttons drawn smaller, with the same 44 px touch area

Status: Accepted

## Context
The UI review (ADR 0067 and the CSS tokens it added) gave every small button a 44 px minimum height so it is easy to tap. You found the buttons too large, and trimming the
padding by 20% changed almost nothing, because the 44 px minimum, not the padding, set their height.

## Decision
- The drawn height of the Save / Clear / Cancel, Add / Edit NAPLAN, Customise-bar, layout and Reset all buttons is `--button-height` (32 px).
- Each keeps a 44 px touch area: an invisible `::after` strip of `--tap-target` height, centred on the button, takes the taps. The 44 px rule is kept; only
  what is drawn changes.
- No change to the map controls, the close button, sliders, summaries or links.

## Consequences
- Buttons look about 27% shorter; they are as easy to tap.
- Two buttons stacked closer than 12 px would have overlapping strips, and the later one in the page would win the overlap. None are today.
- Any new button class that wants this look must be added to the two selector lists at the end of `styles.css`.
