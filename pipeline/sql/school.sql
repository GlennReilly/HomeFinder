-- Builds `school` from stg.school_raw. Run with: psql -v min_icsea=400 -v max_icsea=1500
-- A row without a numeric school code or a usable position is skipped. ICSEA and enrolment are kept only when published as a number
-- ("np" and blanks become NULL). Names and suburbs are trimmed (the file pads some with a trailing space).
BEGIN;
TRUNCATE school;

INSERT INTO school (school_code, name, town_suburb, postcode, level_label, selective, icsea, enrolment_fte, website, geom, extracted_on)
SELECT DISTINCT ON (btrim("School_code")::int)
       btrim("School_code")::int,
       btrim("School_name"),
       NULLIF(btrim("Town_suburb"), ''),
       NULLIF(btrim("Postcode"), ''),
       NULLIF(btrim("Level_of_schooling"), ''),
       NULLIF(btrim("Selective_school"), ''),
       CASE WHEN btrim("ICSEA_value") ~ '^[0-9]+(\.[0-9]+)?$' AND round(btrim("ICSEA_value")::numeric) BETWEEN :min_icsea AND :max_icsea
            THEN round(btrim("ICSEA_value")::numeric)::int END,
       CASE WHEN btrim("latest_year_enrolment_FTE") ~ '^[0-9]+(\.[0-9]+)?$' THEN btrim("latest_year_enrolment_FTE")::numeric END,
       NULLIF(btrim("Website"), ''),
       ST_SetSRID(ST_MakePoint(btrim("Longitude")::float8, btrim("Latitude")::float8), 7844),
       CASE WHEN btrim("Date_extracted") ~ '^\d{4}-\d{2}-\d{2}$' THEN btrim("Date_extracted")::date END
FROM stg.school_raw
WHERE btrim("School_code") ~ '^[0-9]+$' AND btrim("School_name") <> ''
  AND btrim("Latitude") ~ '^-?[0-9]+(\.[0-9]+)?$' AND btrim("Longitude") ~ '^-?[0-9]+(\.[0-9]+)?$'
ORDER BY btrim("School_code")::int;

-- The pilot suburb that contains the school (the smallest if boundaries overlap); schools elsewhere keep a NULL suburb.
UPDATE school s SET suburb_id = (
    SELECT x.suburb_id FROM suburb x WHERE ST_Covers(x.geom, s.geom) ORDER BY ST_Area(x.geom_mga), x.suburb_id LIMIT 1
);

COMMIT;
ANALYZE school;
