-- Socio-economic advantage per suburb from the ABS Socio-Economic Indexes for Areas (SEIFA) 2021, Suburbs and Localities (ADR 0049).
-- The Index of Relative Socio-economic Advantage and Disadvantage (IRSAD): the decile (1 = most disadvantaged, 10 = most advantaged)
-- is the figure to show; the score is on an arbitrary scale (average 1,000) and is kept for reference. ABS data, Creative Commons
-- Attribution 4.0: shown as "Based on ABS data".
CREATE TABLE suburb_seifa (
    suburb_id    TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    sal_code     TEXT NOT NULL,
    irsad_score  DOUBLE PRECISION NOT NULL,
    irsad_decile SMALLINT NOT NULL CHECK (irsad_decile BETWEEN 1 AND 10),
    -- Usual resident population (2021 Census) the ABS used for the suburb.
    population   INTEGER NOT NULL CHECK (population >= 0),
    seifa_year   INTEGER NOT NULL DEFAULT 2021
);
