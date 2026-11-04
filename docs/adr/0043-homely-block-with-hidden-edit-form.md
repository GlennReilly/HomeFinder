# 0043. The Homely block holds the rating, its reviews link and a hidden edit form

Status: Accepted

## Context
ADR 0030 put the Homely rating row, then the "Your Homely rating" form, under the price change, with the "Reviews on Homely" link above the
rating. The open form took a lot of room on the card, and the link sat apart from the score it belongs to.

## Decision
- Under the price change (anchored to that row as before, ADR 0030) comes one **Homely block**: the "Homely rating" row (only when a score has
  been entered), the "Reviews on Homely (opens homely.com.au)" link, then an **"Edit the Homely rating"** link.
- The form (stars, reviews, Save and Remove) is behind that link as a native `<details>` element: no script, keyboard and screen reader friendly, closed
  when the card is drawn. Saving or removing a score redraws the card, which closes it again; a refused or failed save does not redraw, so its message
  stays beside the open form.
- The link text is the same with or without a score.

## Consequences
- Supersedes the placement in ADR 0030 of the link and the form (the rating row still follows the price change, and the other rows keep their places).
- Typed but unsaved values are lost if the card redraws (for example on a layout change); this was already so.
- The form's boxes stay in the page, so the page's submit handler is unchanged.
