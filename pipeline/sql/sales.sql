-- Rebuilds `sale` from stg.sale_raw (the B records of the downloaded files, one text column per field) and marks every
-- sale that must not count with the reason. Run with: psql -v min_price=50000
-- Rules (ADR 0018): residential only, one property per dealing, full interest, a real price and date, a matching suburb.
TRUNCATE sale;

INSERT INTO sale (district_code, property_id, dealing_number, locality, postcode, unit_number, house_number, street_name,
                  contract_date, settlement_date, price_aud, nature, strata_lot, percent_interest, land_area_m2, kind,
                  downloaded_on, source_file)
SELECT DISTINCT ON (district_code, property_id, contract_date, price_aud, dealing_number)
       district_code, property_id, dealing_number, locality, postcode, unit_number, house_number, street_name,
       contract_date, settlement_date, price_aud, nature, strata_lot, percent_interest, land_area_m2,
       CASE WHEN strata_lot IS NOT NULL THEN 'unit' ELSE 'house' END,
       downloaded_on, source_file
FROM (
    SELECT NULLIF(btrim(c2), '') AS district_code,
           NULLIF(btrim(c3), '') AS property_id,
           NULLIF(btrim(c24), '') AS dealing_number,
           upper(NULLIF(btrim(c10), '')) AS locality,
           NULLIF(btrim(c11), '') AS postcode,
           NULLIF(btrim(c7), '') AS unit_number,
           NULLIF(btrim(c8), '') AS house_number,
           NULLIF(btrim(c9), '') AS street_name,
           CASE WHEN btrim(c14) ~ '^\d{8}$' THEN to_date(btrim(c14), 'YYYYMMDD') END AS contract_date,
           CASE WHEN btrim(c15) ~ '^\d{8}$' THEN to_date(btrim(c15), 'YYYYMMDD') END AS settlement_date,
           CASE WHEN btrim(c16) ~ '^\d+(\.\d+)?$' THEN round(btrim(c16)::numeric)::bigint END AS price_aud,
           NULLIF(btrim(c18), '') AS nature,
           NULLIF(btrim(c20), '') AS strata_lot,
           CASE WHEN btrim(c23) ~ '^\d+(\.\d+)?$' THEN btrim(c23)::numeric END AS percent_interest,
           CASE WHEN btrim(c12) ~ '^\d+(\.\d+)?$' THEN
                    btrim(c12)::numeric * CASE upper(btrim(c13)) WHEN 'H' THEN 10000 ELSE 1 END END AS land_area_m2,
           CASE WHEN left(btrim(c5), 8) ~ '^\d{8}$' THEN to_date(left(btrim(c5), 8), 'YYYYMMDD') END AS downloaded_on,
           source_file
    FROM stg.sale_raw
    WHERE btrim(c2) IN (SELECT code FROM stg.sale_district)
) parsed
WHERE property_id IS NOT NULL
-- When a sale appears in several files (a later file repeats or revises it) the most recent file wins.
ORDER BY district_code, property_id, contract_date, price_aud, dealing_number, downloaded_on DESC, source_file DESC;

-- Reasons are applied in this order; the first that matches is recorded.
UPDATE sale SET exclusion_reason = 'not_residential' WHERE nature IS DISTINCT FROM 'R';
-- 0 means "not stated" (about half of all full-price sales carry it, at normal prices), not a zero share: only a real
-- partial share (above 0, below 100) is a part interest.
UPDATE sale SET exclusion_reason = 'part_interest' WHERE exclusion_reason IS NULL AND percent_interest > 0 AND percent_interest < 100;
UPDATE sale s SET exclusion_reason = 'multi_property'
WHERE s.exclusion_reason IS NULL AND s.dealing_number IS NOT NULL AND EXISTS (
    SELECT 1 FROM sale o
    WHERE o.district_code = s.district_code AND o.dealing_number = s.dealing_number AND o.property_id <> s.property_id);
UPDATE sale SET exclusion_reason = 'no_price' WHERE exclusion_reason IS NULL AND COALESCE(price_aud, 0) <= 0;
UPDATE sale SET exclusion_reason = 'low_price' WHERE exclusion_reason IS NULL AND price_aud < :min_price;
UPDATE sale SET exclusion_reason = 'no_contract_date' WHERE exclusion_reason IS NULL AND contract_date IS NULL;

-- Suburb: locality name plus postcode, accepted only when exactly one suburb has that pair.
UPDATE sale s SET suburb_id = m.suburb_id
FROM (SELECT upper(name) AS name, postcode, min(suburb_id) AS suburb_id
      FROM suburb WHERE postcode IS NOT NULL GROUP BY upper(name), postcode HAVING count(*) = 1) m
WHERE m.name = s.locality AND m.postcode = s.postcode;
-- A sale in a pilot council with no postcode matches by locality name alone, when exactly one suburb has that name (ADR 0025).
UPDATE sale s SET suburb_id = m.suburb_id
FROM (SELECT upper(name) AS name, min(suburb_id) AS suburb_id FROM suburb GROUP BY upper(name) HAVING count(*) = 1) m
WHERE s.suburb_id IS NULL AND s.postcode IS NULL AND m.name = s.locality
  AND s.district_code IN (SELECT code FROM stg.sale_district WHERE name_only_match);
UPDATE sale SET exclusion_reason = 'no_suburb_match' WHERE exclusion_reason IS NULL AND suburb_id IS NULL;

ANALYZE sale;
