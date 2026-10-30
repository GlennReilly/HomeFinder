# 0028. A Homely star score you enter by hand

Status: Accepted

## Context
You wanted Homely's suburb star rating on the Suburb Card and over the suburb on the map. ADR 0027 decided the app never
fetches it, because Homely's terms forbid automated retrieval. You asked instead to type the score in yourself, stored in
the database, with a star score drawn under the suburb's name on the map. PR 14 is split: 14a is storage, the endpoints
and the report field (this decision), 14b is the card form and the map label.

## Decision
- **Storage.** Table `suburb_rating` (migration V13): suburb id, stars (0 to 5, one decimal), an optional review count, the
  day you entered it. The suburb id has no foreign key, so reloading the suburbs never deletes your scores; a score for a
  suburb that no longer exists is kept but left out of the list.
- **API.** `GET /v1/suburbs/ratings` lists every rated suburb with the point its name is drawn at (for the map label);
  `PUT /v1/suburbs/{id}/rating` with `{"stars": 2.1, "reviewCount": 7}` sets one and returns it; `DELETE` removes it (204,
  also when there was none). All need the API key, like everything under `/v1`. These are the API's first writes.
- **Validation.** Stars must be a number from 0 to 5, rounded to one decimal; the review count, if given, a whole number
  from 0 to 1,000,000; the suburb must exist (404). Anything else is `400 invalid_rating` and saves nothing. The database
  also refuses out-of-range values.
- **Report.** `SuburbReport.homelyRating` is an `Attribute`: when entered it carries source "Homely (entered by you)", the
  day you entered it, a note ("Homely users' opinion, typed in by you; not fetched from Homely (7 reviews)") and
  confidence by the review count you noted (under 15 or none noted is low, as for sales); otherwise it is unknown, "Not
  entered". The field has a default, so reports from before it existed still decode.
- **Write access.** The tile and lookup pool stays read-only; ratings use a second, writable pool, so a bug in a lookup
  cannot write.
- **Map labels** come from the list endpoint, not from the vector tiles, so a change shows at once without waiting for tile
  caches (14b).

## Consequences
- Existing databases need migration V13 before a rating can be saved; until then the list is empty and saving answers 503.
- The score is only as good as what you type; the card says it was entered by you and from how many reviews.
- It adds a write path, so the API key now protects data you typed as well as data we loaded.
