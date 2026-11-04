# 0046. Filters on beach distance, park share and homes rented

Status: Accepted

## Context
ADR 0035 made a filter panel with one criterion, school ICSEA, and planned beach distance and parkland next. The suburb roll-ups for the beach and
parks exist (ADR 0033) and the Census share rented was added with ADR 0044. The panel had the criterion hard-wired.

## Decision
- The panel offers four criteria: **School ICSEA**, **Distance to the beach** (km, straight line from the suburb's centre, the first figure on the Suburb
  Card), **Park share of the suburb** (%) and **Homes rented** (%). Each has a switch and two sliders in its own unit, rounded out to whole steps (10 ICSEA points,
  1 km, 1%). Several can be on at once and a suburb must pass **every** switched-on criterion (the "all must pass" mode of ADR 0035).
- One response, as before: `GET /v1/suburbs/filters` lists each suburb's `averageIcsea`, `beachKm`, `parkShare` and `rentedShare` (shares from 0 to 1), with
  provenance per criterion (`icseaSource`, and `beach`, `parks` and `rented` objects of source and date). The browser applies the filters, so moving a slider is instant.
- **Each data set may be missing on its own.** The server looks for the school, `suburb_amenity` and `suburb_census` tables separately; a criterion whose data is not
  loaded has no provenance and no values, and the panel says so in place of its sliders. Nothing loaded at all gives an empty response, as before.
- **Unknown is never a match** (ADR 0035): a suburb with no value for a switched-on criterion fails it. Each slider shows "N of M suburbs have no <thing> and never
  match". A suburb with no park of at least 0.5 ha mapped has no park share, matching the card (ADR 0041).
- Element ids are built from the criterion's key (`filter-<key>-on`, `-min`, `-max`, `-min-value`, `-max-value`, `-unknown`); the ICSEA ids are unchanged.

## Consequences
- The response grows by three numbers per suburb (a few more KB for 173 suburbs); the old fields and names still decode, and a client that ignores the new ones works.
- Transport (needs a Transport for NSW key) and the weighted-score mode of ADR 0035 are still to come; a new criterion is a new entry in `FilterCriterion` plus its
  value and provenance in the response.
- The beach distance is from the suburb's centre, not its nearest edge; a suburb that straddles the coast can read further than its closest street.
- The rented share rests on 2021 Census counts, which the ABS adjusts at random for small suburbs.
