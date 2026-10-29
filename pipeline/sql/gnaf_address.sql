-- Rebuilds `address` from the G-NAF staging tables. Idempotent: a rerun gives the same rows.
-- Variables: release, zip_sha256. Empty PSV fields load as NULL (CSV format), so retired = non-NULL date.
BEGIN;

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
