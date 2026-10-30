# 0027. Link out to Homely; do not extract its ratings

Status: Accepted

## Context
You asked whether the Suburb Card could show Homely's star rating for a suburb (for example Jesmond, 2.1 stars from 7
resident reviews). The rating is crowd-sourced opinion owned by Homely Group, shown on a public page with no API or
licence for reuse. Homely's Terms of Use prohibit using any robot, spider or retrieval application on the site (clause
4.1(h)) and restrict republishing its content (4.1(a), (b)). Your spec says never to scrape listing portals.

## Decision
- The app does not fetch, store or show anything from Homely's pages, however it would be retrieved.
- The Suburb Card shows one link, "Reviews on Homely (opens homely.com.au)", to `https://www.homely.com.au/suburb-profile/<name>-nsw-<postcode>`
  (for example `jesmond-nsw-2299`), opening in a new tab with `rel="noopener noreferrer"`. It is left out when the suburb
  has no postcode. An apostrophe is dropped and any other punctuation becomes a hyphen, so a name cannot add anything
  to the address.
- If you want the rating in the app, the way to get it is Homely's permission (or a licensed feed), or typing the score
  yourself in the notes and overrides planned for the shortlist. Open data (ABS SEIFA, NSW crime statistics) can supply
  suburb signals with proper source and confidence.

## Consequences
- The link is built from our own data, so it cannot break the card, but Homely may change its address scheme and a
  link could then land on a missing page; the card says where it goes.
- Nothing about Homely's content enters the database, so the licensing position of ADR 0018 is unaffected.
