# 0026. Address and suburb search

Status: Accepted

## Context
The spec's flows start with typing an address or a suburb. You chose word-prefix matching for addresses, and, for a result
whose address has no known lot, flying to the address with a notice. PR 11 is split: 11a is the database index, the shared
contract and the endpoint (this decision), 11b is the search box and map behaviour in the web app.

## Decision
- **Endpoint.** `GET /v1/search?q=` (API key, header only) returns up to 8 `SearchResult`s: suburbs first (at most 4),
  then addresses. A query of fewer than 2 or more than 100 characters is a `400 invalid_query`; a query with no letters or
  digits has no results; no database or a failing one is `503`.
- **Words.** The typed text is reduced to lower-case letters and digits, at most 8 words; punctuation, a slash in "3/12" and
  anything else only separates words. Only those words are ever put in a text-search query, as bound parameters, so
  nothing typed can add syntax.
- **Addresses** match when every typed word begins a word of the address, using a generated lower-case `search_text`
  column and a GIN full-text index (migration V12, `simple` configuration: no stemming or stop words). A fully typed
  street-type abbreviation (st, rd, ave, dr ...) also matches the full word, so "12 main st" finds "12 MAIN STREET".
  A flat and its street number are two words, so "3/12" finds unit 3 at number 12.
- **Ranking of addresses:** an address whose text starts with the typed words (abbreviations spelled out) first, then
  shorter addresses, then street name and number order.
- **Suburbs** match by name (the whole name or any word of it) or, for a single typed number, by postcode prefix;
  an exact name first, then a name that starts with the text, then bigger suburbs, so a duplicate name (two BELMONTs)
  lists the bigger first and every result shows its postcode.
- **Results** carry what the map needs: a suburb its bounds; an address its point and, if G-NAF linked it to a lot (ADR
  0005 to 0007), the `lotId`, otherwise null. The label is the formatted address.

## Consequences
- Measured on your 250,105 addresses (index 8.8 MB): typical searches take 1 to 10 ms and the worst case, a single
  digit that matches a large share of addresses, 80 ms.
- No typo tolerance: "mian street" finds nothing. A fuzzy fallback (trigram) can be added later.
- G-NAF can hold several records with the same address text (for example separate buildings); they appear as separate
  suggestions.
- Existing databases need migration V12, which any pipeline script applies; it rewrites the address table once.
