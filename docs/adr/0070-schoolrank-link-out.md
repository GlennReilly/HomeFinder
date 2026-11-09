# 0070. A SchoolRank link beside each school (link out only)

Status: Accepted

## Context
You read schools' NAPLAN figures on [SchoolRank](https://schoolrank.com.au/) and type them in (ADR 0069). A link from each school on the cards to
its SchoolRank page saves searching for it. We have not read SchoolRank's terms, and the site answers automated requests with 429 (too many
requests), so the app must not fetch, scrape or check anything there (the same rule as Homely and the listing portals, ADR 0027).

## Decision
- **Link out only.** Every school in the Suburb Card's Schools and Zoned schools rows and the Lot Card's Schools row (zoned and
  nearest-not-zoned) gets a "SchoolRank" link on the line of its NAPLAN button. It opens in a new tab with `rel="noopener noreferrer"`, through
  the same `externalLink` helper the listing links now use (ADR 0061), and has a 44 px tap area. Its title says "SchoolRank page for <school>
  (opens in a new tab; address built by us, may not exist)". No SchoolRank data enters the app, and the app makes no claim that it uses
  their data.
- **Built address, unverified.** `https://schoolrank.com.au/school/<slug>`, where the slug is the school's name, then the locality of its
  address, then `nsw`: lowercase, apostrophes dropped, every other run of punctuation or spaces one hyphen, hyphens trimmed. The locality is
  the Department of Education's `Town_suburb` (already loaded into `school.town_suburb`, migration V15, now carried to the cards as
  `School.locality`). The one example we have, Elermore Vale Public School in Wallsend
  (`elermore-vale-public-school-wallsend-nsw`), matches: the data gives "Wallsend" for it. "&" is written "and", which is a guess.
- **No locality, no link.** A zoned school whose code is missing from the school list has no locality, so it gets no link.
- **Most likely to break:** schools whose SchoolRank name differs from the Department's (abbreviations, renamed schools); localities spelt
  differently or different from the Department's; multi-campus schools ("Georges River College, Oatley Senior Campus"); names with "&";
  schools whose name already contains the locality, if SchoolRank leaves out the repeat.
- The host and path prefix are named constants in `ExternalLinks`; the link attributes are constants in `HtmlNames.kt`.

## Consequences
- Some links will lead to SchoolRank's "not found" page; the title says so up front. Nothing in the app depends on the link working.
- No migration or data load is needed: the locality was already in the `school` table.
- The feature can be removed by deleting `schoolRankLink` and its call (plus the constants and `School.locality` if unused).
