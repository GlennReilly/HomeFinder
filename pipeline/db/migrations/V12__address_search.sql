-- Address search (ADR 0026): one lower-case line per address ("3 12 main street hamilton 2303"), word-prefix searched
-- through a full-text index. The text uses `||` (immutable), not concat_ws (stable), so it can be a generated column.
-- The flat number and the street number are separate words, so "3/12" is typed and stored as "3 12".
ALTER TABLE address ADD COLUMN search_text TEXT GENERATED ALWAYS AS (
    lower(btrim(
        COALESCE(flat_number || ' ', '') || COALESCE(number_first || ' ', '') || COALESCE(street_name || ' ', '')
        || COALESCE(street_type || ' ', '') || COALESCE(locality_name || ' ', '') || COALESCE(postcode, '')
    ))
) STORED;

-- The 'simple' configuration keeps every word as it is (no stemming, no stop words), which suits street names.
CREATE INDEX address_search_idx ON address USING GIN (to_tsvector('simple', search_text));
