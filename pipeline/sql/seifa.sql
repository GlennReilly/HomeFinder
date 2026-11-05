-- Builds suburb_seifa from stg.seifa_raw (the census module's SEIFA output). Run with no variables.
-- Matched to our suburbs exactly as the Census is (pipeline/sql/census.sql): by name, ignoring the "(NSW)" or "(Newcastle - NSW)" tag, with the
-- council area (LGA) settling a shared name; anything still ambiguous is left unmatched rather than guessed.
BEGIN;
TRUNCATE suburb_seifa;

CREATE TEMP TABLE sal ON COMMIT DROP AS
SELECT r.*, upper(btrim(regexp_replace(r.sal_name, '\s*\([^)]*\)\s*$', ''))) AS base,
       substring(r.sal_name from '\(([^)]*)\)\s*$') AS tag
FROM stg.seifa_raw r;

CREATE TEMP TABLE ours ON COMMIT DROP AS
SELECT s.suburb_id, upper(btrim(s.name)) AS base,
       (SELECT l.name FROM lga l WHERE ST_Covers(l.geom, ST_PointOnSurface(s.geom)) ORDER BY ST_Area(l.geom) LIMIT 1) AS lga,
       count(*) OVER (PARTITION BY upper(btrim(s.name))) AS same_name
FROM suburb s;

CREATE TEMP TABLE picked ON COMMIT DROP AS
WITH cand AS (
    SELECT o.suburb_id, o.lga, o.same_name, a.sal_code, a.tag, count(*) OVER (PARTITION BY o.suburb_id) AS n
    FROM ours o JOIN sal a ON a.base = o.base
)
SELECT suburb_id, sal_code FROM cand
WHERE (n = 1 AND same_name = 1) OR (lga IS NOT NULL AND upper(tag) LIKE upper(lga) || ' - %');

INSERT INTO suburb_seifa (suburb_id, sal_code, irsad_score, irsad_decile, population)
SELECT p.suburb_id, p.sal_code, r.irsad_score, r.irsad_decile, r.population
FROM picked p JOIN stg.seifa_raw r ON r.sal_code = p.sal_code
WHERE p.suburb_id IN (SELECT suburb_id FROM picked GROUP BY suburb_id HAVING count(*) = 1);

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('suburb_seifa', jsonb_build_object('seifa_suburbs', (SELECT count(*) FROM stg.seifa_raw), 'suburbs', (SELECT count(*) FROM suburb)), now());

COMMIT;
