# 0053. Pinned card header, Reset all, and a card that keeps its scroll position

Status: Accepted

## Context
The Suburb and Lot Cards are tall (18 rows on the Suburb Card), so the name, postcode and Customise button scrolled out of view; the Filters tab had no way to undo several
filters at once; and choosing another suburb on the map rebuilt the card from its short Loading state, which collapsed the scroll position to the top, so comparing the same
row across suburbs meant scrolling again each time.

## Decision
- **Pinned header.** The card's header (name, postcode, close button) and its Customise bar (hint, Done and Reset while customising) sit in one block, `card-sticky`, that is
  `position: sticky` at the top of the card's scroll area, with the card's background and a bottom rule. The Lot Card uses the same block. While customising, the bar is taller
  and so is the pinned block.
- **Reset all.** The Filters tab has a "Reset all" button beside its heading. It switches every criterion off and puts each slider back to the value it was drawn with (its
  starting end), which restores the map, the summary and the tab's count. It is disabled while no filter is on.
- **Kept scroll.** `CardScroll` (client module, unit tested) remembers the card's scroll position. The position is read from the page only when the last thing drawn was a
  loaded card (the short Loading and Failed cards cannot scroll and would report 0), and is put back after each redraw, so the same row stays in view through the Loading card
  and into the next suburb's card. It returns to the top when the kind of card changes (suburb to lot, or a card opened from nothing).

## Consequences
- While the next suburb loads the page briefly shows the short Loading card at the top, then jumps back to the remembered position; a placeholder as tall as the old card would
  avoid the flash but was not worth the complication.
- If the new card is shorter than the remembered position, the browser clamps it to the bottom.
- The position is not kept when you switch to the Filters tab and back (a hidden panel forgets its scroll).
