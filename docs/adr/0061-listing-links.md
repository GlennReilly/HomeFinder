# 0061. Links to listings on realestate.com.au and Domain

Status: Accepted

## Context
You wanted a way from the cards to the homes actually for sale: under the median house and unit prices (Suburb Card) and under the block size (Lot Card), on
realestate.com.au and domain.com.au, opening in a new tab. ADR 0027 says listing portals are linked to, never scraped or fetched: their terms forbid automated
retrieval. So the app cannot check whether a page has listings; it can only build the address of the page.

## Decision
- **Suburb Card**: under *Median house price*, "Houses for sale: realestate.com.au · domain.com.au" (houses for sale in the suburb); under *Median unit price*, "Units for sale:
  ..." (units and apartments). They are built from the suburb's name and postcode, and shown whenever both are known.
- **Lot Card**: under *Block size*, "This address: realestate.com.au · domain.com.au", the lot's first address on each site (one address is used when a strata plan has several).
  Shown whenever the lot has an address, a suburb name and a postcode.
- Every link has `target="_blank"` and `rel="noopener noreferrer"` and a title saying where it goes and that it opens in a new tab.
- **"If they exist"**: a link is built, not checked, so "exists" means "we have what is needed to build it"; a page with no listings is for the visitor to see. Nothing is requested
  from the sites.
- **Rows with no value** are left off the card (ADR 0058), but their for-sale links are still good, so a price (or block size) row with no value is replaced by a small row of
  its links ("Houses for sale", "Units for sale", "This address"). A row you hid yourself takes its links with it; while customising the links stay under their row.
- **URL shapes** (built in `ExternalLinks`, with unit tests):
  - Domain suburb: `https://www.domain.com.au/sale/<suburb>-nsw-<postcode>/house/` and `.../apartment-unit-flat/`. The apartment path was seen on Domain's own pages
    (`/sale/russell-lea-nsw-2046/apartment-unit-flat/`); the `/house/` path was seen under region pages (`/sale/eastern-suburbs-nsw/house/`), not for a single suburb.
  - Domain address: `https://www.domain.com.au/property-profile/<number>-<street>-<type>-<suburb>-nsw-<postcode>`, seen on many Hamilton pages; a unit's `3/12` is written `3-12`.
  - realestate.com.au suburb: `https://www.realestate.com.au/buy/property-house-in-<suburb>,+nsw+<postcode>/list-1` (units: `property-unit+apartment-in-...`).
    **Unverified**: the search tool could not read realestate.com.au pages; the shape is from memory of the site's search addresses.
  - realestate.com.au address: `https://www.realestate.com.au/property/<number>-<street>-<abbreviated type>-<suburb>-nsw-<postcode>/` (street, road, avenue... become st, rd, ave...).
    **Unverified**, as above. If the site changes or the shape is wrong the link lands on a not-found or search page; nothing else is affected.
- **Address rule**: the street part is everything before the last comma of "12 MAIN STREET, HAMILTON NSW 2303"; words, lower-cased, apostrophes removed, other punctuation a hyphen;
  dwelling words (unit, flat, apt, apartment, suite, shop, level, lot) are dropped; the slash of "3/12" becomes a hyphen. Only the public address goes into a URL.

## Consequences
- A link may lead to a page with no listings, or (realestate.com.au shapes) to a page that does not exist; checking either would need requests to the sites, which ADR 0027 rules out.
- The price rows' links are one more reason a card can show a row for a price that is not known: the heading says "Houses for sale", not a price.
- If a shape is wrong it is one function (`ExternalLinks`) to fix.
