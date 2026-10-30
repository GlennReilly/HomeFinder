# 0035. Suburb filter side panel

Status: Accepted

## Context

You want to narrow the map to suburbs that suit you by distance to the beach, parkland percentage, school ICSEA and later public transport. The data for the first three is already in the database as per-suburb roll-ups (ADR 0033, ADR 0034). Transport needs a Transport for NSW key and a new source, so it is not in the first version.

## Decision

- **Panel:** a side panel with one min/max slider per criterion, each switched on or off. The map highlights suburbs that pass and dims the rest.
- **Unknown values:** a suburb whose value is Unknown for an active criterion does not pass and is dimmed. The panel shows, per criterion, how many suburbs are Unknown, so missing data is visible and never silently counted as a match.
- **Combining:** the target is both modes, switchable. The first slice ships "all active criteria must pass". The weighted score (suburbs shaded by score, with a weight per criterion) follows in a later PR.
- **Where it runs:** a new server endpoint returns every suburb's roll-up values with their provenance (source, as-of, confidence) in one response. The browser applies the filter and restyles the map instantly. The server stays free of filter state.
- **First slice:** school ICSEA, then beach distance and parkland percentage; transport later.
- ICSEA measures socio-educational advantage, not school quality, so the slider is labelled that way.

## Consequences

- Moving a slider is instant, with no server call.
- One response holds the values for all suburbs (173 now, a few hundred KB at most), so it is fetched once and kept in the browser.
- The weighted mode needs a later decision on weights and normalisation, so it gets its own PR.
- Any new criterion must supply provenance and an Unknown reason, like every other attribute.
