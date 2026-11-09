# 0068. A NAPLAN score per school, typed in by hand from My School

Status: Partly superseded by 0069

## Context
You wanted a NAPLAN figure beside each school on the cards. ICSEA (ADR 0034) describes a school's students' background, not
its results. NAPLAN results are published on ACARA's [My School](https://www.myschool.edu.au/) site, but its terms of use
allow copying only for personal, private, non-commercial use (clause 6.1) with the attribution "Source: Australian
Curriculum, Assessment and Reporting Authority (ACARA)" (clause 6.2), and they bar bulk copying and automated access. ACARA's
Data Access Program, which supplies NAPLAN data in bulk, is for organisations, not individuals. Third-party sites that rank
schools (for example SchoolRank) repackage the same data under their own terms, so they are not read either. This mirrors
the Homely score (ADR 0027, 0028): the app links out and you type the figure in.

## Decision
- **Manual entry only.** The app never fetches, scrapes or republishes anything from My School. You type one overall number
  per school from its My School page; the form links to My School's home page only (no guessed per-school URLs).
- **One number per school, plus its year.** Not the five domains or the year levels: you pick the figure you care about and
  can say which in a short optional note (up to 200 characters).
- **Storage.** Table `school_naplan` (migration V25): school code (primary key), score, year, note, updated-at. No foreign key
  to `school`, so reloading the schools never deletes what you typed. It stays in your own database and is never sent anywhere
  else.
- **Validation.** NAPLAN national scale scores run 0 to 1000, so the score must be a whole number from 0 to 1000; the year
  must be from 2008 (the first NAPLAN) to the current year; the note at most 200 characters. The rules live once in
  `NaplanRules` (shared module) and answer with a sealed result (`NaplanCheck`), used by the form and the server; the server
  answers `400 invalid_naplan` and saves nothing. A code that is not in the school list or the catchments is `404`. The
  database also refuses out-of-range values.
- **API.** `GET /v1/schools/naplan` lists every figure (`?codes=1,2` for some schools); `PUT /v1/schools/{code}/naplan` with
  `{"score": 512, "year": 2025, "note": "..."}` sets one and returns it with the day; `DELETE` removes it (204, also when there
  was none). All need the API key and use the writable pool, as for ratings.
- **Cards.** The suburb and lot reports carry the figure on each `School` (`naplan`, absent when not entered). It reads
  "NAPLAN 512 (2025)" beside the school's ICSEA in the Suburb Card's Schools and Zoned schools rows and the Lot Card's Schools
  row (zoned and nearest-not-zoned schools). Each listed school has an "Add NAPLAN" / "Edit NAPLAN" link that opens one small
  form under it (score, year, note; Save, Clear, Cancel). When a row shows a figure its closed Source line adds "NAPLAN typed
  in by you from My School. Source: Australian Curriculum, Assessment and Reporting Authority (ACARA)".
- **No filter, sort or weighting yet.** The Suburb Card's school roll-ups and the ICSEA filters are unchanged. A NAPLAN filter
  or sort is a possible later step.

## Consequences
- The user's database needs migration V25 before a figure can be saved; until then the cards show no figures and saving
  answers 503.
- A figure is only as good and as current as what you typed; the year beside it shows its age.
- The data stays personal: if the app were ever shared or published, these figures must not go with it.
