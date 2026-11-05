-- Recorded criminal incidents per suburb over the latest 24 months of the NSW Bureau of Crime Statistics and Research (BOCSAR) suburb file (ADR 0051).
-- Two measures: violent (assaults, robberies, sexual offences) and property (break-ins, vehicle theft, stealing from cars and homes, malicious damage).
-- Plain counts; the rate per 1,000 residents is worked out where it is shown, from suburb_seifa.population. BOCSAR data, Creative Commons Attribution.
CREATE TABLE suburb_crime (
    suburb_id          TEXT PRIMARY KEY REFERENCES suburb (suburb_id) ON DELETE CASCADE,
    bocsar_name        TEXT NOT NULL,
    violent_incidents  INTEGER NOT NULL CHECK (violent_incidents >= 0),
    property_incidents INTEGER NOT NULL CHECK (property_incidents >= 0),
    -- The months the counts cover, as shown ("Jul 2024 to Jun 2026"), and the last day of the last month.
    window_text        TEXT NOT NULL,
    as_of              DATE NOT NULL
);
