-- The sales windows now end on the latest contract date in the data, not on the day the files were downloaded (the label on the card
-- said "to <download day>", which overstated how recent the sales are: sales reach the Valuer General weeks after exchange).
-- Same function as V10 except for as_of. Run refresh-suburb-stats.sh (or load-sales.sh) to rebuild suburb_stats.
CREATE OR REPLACE FUNCTION refresh_suburb_stats(p_min_sales integer, p_min_block_lots integer, p_min_block_m2 numeric)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    -- The latest contract date on file (ignoring any dated after the newest download, which can only be a typing error).
    as_of date := (SELECT max(contract_date) FROM sale
                   WHERE exclusion_reason IS NULL AND contract_date <= (SELECT max(downloaded_on) FROM sale));
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
