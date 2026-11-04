-- Tenure and dwelling structure per suburb from the ABS 2021 Census (table G37, General Community Profile for Suburbs and Localities;
-- ADR 0044). Plain counts of occupied private dwellings; the shares are worked out where they are shown. ABS data, Creative Commons
-- Attribution 4.0: shown as "Based on ABS data".
CREATE TABLE suburb_census (
    suburb_id       TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    sal_code        TEXT NOT NULL,
    -- Occupied private dwellings whose tenure was stated (never below rented): the base for the share rented.
    tenure_stated   INTEGER NOT NULL CHECK (tenure_stated >= 0),
    rented          INTEGER NOT NULL CHECK (rented >= 0 AND rented <= tenure_stated),
    -- By structure: separate houses; semi-detached, row, terrace and townhouses; flats, units and apartments; other dwellings.
    houses          INTEGER NOT NULL CHECK (houses >= 0),
    semis           INTEGER NOT NULL CHECK (semis >= 0),
    flats           INTEGER NOT NULL CHECK (flats >= 0),
    other_dwellings INTEGER NOT NULL CHECK (other_dwellings >= 0),
    census_year     INTEGER NOT NULL DEFAULT 2021
);
