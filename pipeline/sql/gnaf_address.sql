-- Rebuilds `address` from the G-NAF staging tables. Idempotent: a rerun gives the same rows.
-- Variables: release, zip_sha256, min_share. Empty PSV fields load as NULL (CSV format), so retired = non-NULL date.
BEGIN;

-- Decide which same-named G-NAF localities are ours: the share of a locality's current principal geocodes that lie
-- inside the official suburb of the same name must reach min_share.
TRUNCATE locality_decision;

INSERT INTO locality_decision (locality_pid, name, addresses, share_in_suburb, accepted)
WITH cand AS (
    SELECT l.locality_pid, upper(l.locality_name) AS name
    FROM stg.locality l
    JOIN stg.state s ON s.state_pid = l.state_pid AND s.state_abbreviation = 'NSW'
    JOIN pilot_locality p ON p.name = upper(l.locality_name)
), pts AS (
    SELECT c.locality_pid, c.name,
           ST_SetSRID(ST_MakePoint(g.longitude::float8, g.latitude::float8), 7844) AS geom
    FROM cand c
    JOIN stg.address_detail ad ON ad.locality_pid = c.locality_pid
         AND ad.date_retired IS NULL AND ad.alias_principal = 'P'
    JOIN stg.address_default_geocode g ON g.address_detail_pid = ad.address_detail_pid AND g.date_retired IS NULL
), share AS (
    SELECT locality_pid, name, count(*) AS addresses,
           avg((EXISTS (SELECT 1 FROM suburb sub
                        WHERE upper(sub.name) = pts.name AND ST_Within(pts.geom, sub.geom)))::int) AS share_in_suburb
    FROM pts GROUP BY locality_pid, name
)
SELECT locality_pid, name, addresses, share_in_suburb, share_in_suburb >= :'min_share'::numeric FROM share;

TRUNCATE address CASCADE;  -- also clears address_lot, which must be relinked afterwards

INSERT INTO address (gnaf_pid, flat_number, number_first, street_name, street_type, locality_name, state,
                     postcode, legal_parcel_id, confidence, geocode_type, source_release, geom)
SELECT ad.address_detail_pid,
       NULLIF(ad.flat_number, ''),
       NULLIF(ad.number_first, ''),
       NULLIF(sl.street_name, ''),
       NULLIF(sl.street_type_code, ''),
       l.locality_name,
       s.state_abbreviation,
       NULLIF(ad.postcode, ''),
       NULLIF(ad.legal_parcel_id, ''),
       NULLIF(ad.confidence, ''),
       NULLIF(g.geocode_type_code, ''),
       :'release',
       ST_SetSRID(ST_MakePoint(g.longitude::float8, g.latitude::float8), 7844)
FROM stg.address_detail ad
JOIN stg.locality l ON l.locality_pid = ad.locality_pid
JOIN locality_decision d ON d.locality_pid = l.locality_pid AND d.accepted
JOIN stg.state s ON s.state_pid = l.state_pid AND s.state_abbreviation = 'NSW'
JOIN pilot_locality p ON p.name = upper(l.locality_name)
JOIN stg.address_default_geocode g ON g.address_detail_pid = ad.address_detail_pid AND g.date_retired IS NULL
LEFT JOIN stg.street_locality sl ON sl.street_locality_pid = ad.street_locality_pid
WHERE ad.date_retired IS NULL
  AND ad.alias_principal = 'P';

INSERT INTO pipeline_run (stage, input_versions, finished_at)
VALUES ('gnaf_address',
        jsonb_build_object('gnaf_release', :'release', 'gnaf_zip_sha256', :'zip_sha256'),
        now());

COMMIT;
