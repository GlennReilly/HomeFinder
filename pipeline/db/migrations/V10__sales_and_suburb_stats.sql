-- NSW Valuer General sales (bulk Property Sales Information) and the per-suburb statistics built from them and from
-- the cadastre. Sales are kept even when they do not count, with the reason, so nothing is silently dropped.
CREATE TABLE sale (
    sale_id          BIGSERIAL PRIMARY KEY,
    district_code    TEXT NOT NULL,
    property_id      TEXT NOT NULL,
    dealing_number   TEXT,
    locality         TEXT,
    postcode         TEXT,
    unit_number      TEXT,
    house_number     TEXT,
    street_name      TEXT,
    contract_date    DATE,
    settlement_date  DATE,
    price_aud        BIGINT,
    nature           TEXT,
    strata_lot       TEXT,
    percent_interest NUMERIC,
    land_area_m2     NUMERIC,
    kind             TEXT CHECK (kind IN ('house', 'unit')),
    -- Matched by locality name and postcode; plain text (no foreign key) so reloading suburbs never deletes sales.
    suburb_id        TEXT,
    -- NULL means the sale counts towards the medians; otherwise why it does not.
    exclusion_reason TEXT,
    downloaded_on    DATE,
    source_file      TEXT NOT NULL
);

CREATE INDEX sale_suburb_idx ON sale (suburb_id, kind, contract_date) WHERE exclusion_reason IS NULL;

-- One row per suburb. Medians are NULL when fewer than the minimum number of sales (or lots) back them; the counts are
-- always kept so the card can say how few there were.
CREATE TABLE suburb_stats (
    suburb_id              TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    as_of                  DATE,
    house_sales            INTEGER NOT NULL DEFAULT 0,
    house_median_aud       BIGINT,
    unit_sales             INTEGER NOT NULL DEFAULT 0,
    unit_median_aud        BIGINT,
    house_prev_sales       INTEGER NOT NULL DEFAULT 0,
    house_prev_median_aud  BIGINT,
    unit_prev_sales        INTEGER NOT NULL DEFAULT 0,
    unit_prev_median_aud   BIGINT,
    house_change_pct       NUMERIC,
    unit_change_pct        NUMERIC,
    block_lots             INTEGER NOT NULL DEFAULT 0,
    block_median_m2        NUMERIC,
    block_p10_m2           NUMERIC,
    block_p90_m2           NUMERIC
);

-- Rebuilds suburb_stats. Idempotent: the same inputs give the same rows, because "now" is the latest file date in the
-- sales data, not the clock. The current window is the 12 months to p_as_of by contract date and the previous window the
-- 12 months before that. Block size uses lots that have an address, are not strata parcels (SP or CP plans) and are at
-- least p_min_block_m2 square metres.
CREATE FUNCTION refresh_suburb_stats(p_min_sales integer, p_min_block_lots integer, p_min_block_m2 numeric)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    as_of date := (SELECT max(downloaded_on) FROM sale WHERE exclusion_reason IS NULL);
BEGIN
    TRUNCATE suburb_stats;

    INSERT INTO suburb_stats (suburb_id, as_of, house_sales, house_median_aud, unit_sales, unit_median_aud, house_prev_sales,
                              house_prev_median_aud, unit_prev_sales, unit_prev_median_aud, house_change_pct, unit_change_pct,
                              block_lots, block_median_m2, block_p10_m2, block_p90_m2)
    WITH counted AS (
        SELECT suburb_id, kind,
               (contract_date > as_of - interval '12 months' AND contract_date <= as_of) AS current_window,
               price_aud
        FROM sale
        WHERE exclusion_reason IS NULL AND suburb_id IS NOT NULL AND as_of IS NOT NULL
          AND contract_date > as_of - interval '24 months' AND contract_date <= as_of
    ),
    prices AS (
        SELECT suburb_id,
               count(*) FILTER (WHERE kind = 'house' AND current_window)      AS house_n,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY price_aud) FILTER (WHERE kind = 'house' AND current_window)      AS house_med,
               count(*) FILTER (WHERE kind = 'unit' AND current_window)       AS unit_n,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY price_aud) FILTER (WHERE kind = 'unit' AND current_window)       AS unit_med,
               count(*) FILTER (WHERE kind = 'house' AND NOT current_window)  AS house_prev_n,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY price_aud) FILTER (WHERE kind = 'house' AND NOT current_window)  AS house_prev_med,
               count(*) FILTER (WHERE kind = 'unit' AND NOT current_window)   AS unit_prev_n,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY price_aud) FILTER (WHERE kind = 'unit' AND NOT current_window)   AS unit_prev_med
        FROM counted GROUP BY suburb_id
    ),
    blocks AS (
        SELECT l.suburb_id, count(*) AS n,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY l.area_m2) AS med,
               percentile_cont(0.1) WITHIN GROUP (ORDER BY l.area_m2) AS p10,
               percentile_cont(0.9) WITHIN GROUP (ORDER BY l.area_m2) AS p90
        FROM lot l
        WHERE l.area_m2 >= p_min_block_m2
          AND l.parcel_key !~ '(^|/)(SP|CP)'
          AND EXISTS (SELECT 1 FROM address_lot al WHERE al.lot_id = l.lot_id)
        GROUP BY l.suburb_id
    ),
    medians AS (
        SELECT s.suburb_id, p.house_n, p.unit_n, p.house_prev_n, p.unit_prev_n,
               CASE WHEN p.house_n >= p_min_sales THEN round(p.house_med)::bigint END AS house_m,
               CASE WHEN p.unit_n >= p_min_sales THEN round(p.unit_med)::bigint END AS unit_m,
               CASE WHEN p.house_prev_n >= p_min_sales THEN round(p.house_prev_med)::bigint END AS house_prev_m,
               CASE WHEN p.unit_prev_n >= p_min_sales THEN round(p.unit_prev_med)::bigint END AS unit_prev_m
        FROM suburb s LEFT JOIN prices p ON p.suburb_id = s.suburb_id
    )
    SELECT m.suburb_id, as_of,
           COALESCE(m.house_n, 0), m.house_m, COALESCE(m.unit_n, 0), m.unit_m,
           COALESCE(m.house_prev_n, 0), m.house_prev_m, COALESCE(m.unit_prev_n, 0), m.unit_prev_m,
           CASE WHEN m.house_m IS NOT NULL AND m.house_prev_m IS NOT NULL
                THEN round((m.house_m - m.house_prev_m)::numeric * 100 / m.house_prev_m, 1) END,
           CASE WHEN m.unit_m IS NOT NULL AND m.unit_prev_m IS NOT NULL
                THEN round((m.unit_m - m.unit_prev_m)::numeric * 100 / m.unit_prev_m, 1) END,
           COALESCE(b.n, 0),
           CASE WHEN b.n >= p_min_block_lots THEN round(b.med::numeric, 1) END,
           CASE WHEN b.n >= p_min_block_lots THEN round(b.p10::numeric, 1) END,
           CASE WHEN b.n >= p_min_block_lots THEN round(b.p90::numeric, 1) END
    FROM medians m LEFT JOIN blocks b ON b.suburb_id = m.suburb_id;

    INSERT INTO pipeline_run (stage, input_versions, finished_at)
    VALUES ('suburb_stats',
            jsonb_build_object('as_of', as_of, 'sales_counted', (SELECT count(*) FROM sale WHERE exclusion_reason IS NULL),
                               'min_sales', p_min_sales, 'min_block_lots', p_min_block_lots, 'min_block_m2', p_min_block_m2),
            now());
END
$$;
