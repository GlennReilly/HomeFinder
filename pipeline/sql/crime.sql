-- Builds suburb_crime from stg.crime_raw (the census module's crime tool output). Run with no variables.
-- BOCSAR writes a suburb name alone when it is unique in NSW and "Name (Council area)" when it is shared, e.g. "Back Creek (Tweed)". A suburb of ours matches
-- a BOCSAR name when both have that name once, or, where the name is shared, when the tag names the council area (LGA) the suburb lies in;
-- anything still ambiguous is left unmatched rather than guessed.
BEGIN;
TRUNCATE suburb_crime;

CREATE TEMP TABLE bocsar ON COMMIT DROP AS
SELECT r.*, upper(btrim(regexp_replace(r.bocsar_name, '\s*\([^)]*\)\s*$', ''))) AS base,
       substring(r.bocsar_name from '\(([^)]*)\)\s*$') AS tag
FROM stg.crime_raw r;

CREATE TEMP TABLE ours ON COMMIT DROP AS
SELECT s.suburb_id, upper(btrim(s.name)) AS base,
       (SELECT l.name FROM lga l WHERE ST_Covers(l.geom, ST_PointOnSurface(s.geom)) ORDER BY ST_Area(l.geom) LIMIT 1) AS lga,
       count(*) OVER (PARTITION BY upper(btrim(s.name))) AS same_name
FROM suburb s;

CREATE TEMP TABLE picked ON COMMIT DROP AS
WITH cand AS (
    SELECT o.suburb_id, o.lga, o.same_name, b.bocsar_name, b.tag, count(*) OVER (PARTITION BY o.suburb_id) AS n
    FROM ours o JOIN bocsar b ON b.base = o.base
)
SELECT suburb_id, bocsar_name FROM cand
WHERE (n = 1 AND same_name = 1) OR (lga IS NOT NULL AND upper(tag) = upper(lga));

INSERT INTO suburb_crime (suburb_id, bocsar_name, violent_incidents, property_incidents, window_text, as_of)
SELECT p.suburb_id, p.bocsar_name, r.violent, r.property, r.window_text, r.as_of
FROM picked p JOIN stg.crime_raw r ON r.bocsar_name = p.bocsar_name
WHERE p.suburb_id IN (SELECT suburb_id FROM picked GROUP BY suburb_id HAVING count(*) = 1);

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('suburb_crime', jsonb_build_object('bocsar_suburbs', (SELECT count(*) FROM stg.crime_raw), 'suburbs', (SELECT count(*) FROM suburb)), now());

COMMIT;
