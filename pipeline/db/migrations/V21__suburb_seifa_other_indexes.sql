-- The other three SEIFA 2021 indexes beside IRSAD (ADR 0050), as deciles (1 = lowest tenth of areas in Australia, 10 = highest): disadvantage (IRSD),
-- economic resources (IER) and education and occupation (IEO). Null where the ABS gives the suburb no score for that index.
ALTER TABLE suburb_seifa
    ADD COLUMN irsd_decile SMALLINT CHECK (irsd_decile BETWEEN 1 AND 10),
    ADD COLUMN ier_decile  SMALLINT CHECK (ier_decile BETWEEN 1 AND 10),
    ADD COLUMN ieo_decile  SMALLINT CHECK (ieo_decile BETWEEN 1 AND 10);
