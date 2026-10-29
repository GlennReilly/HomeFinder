-- Some localities legitimately have no current G-NAF addresses; the completeness check must accept those.
ALTER TABLE pilot_locality ADD COLUMN expect_addresses BOOLEAN NOT NULL DEFAULT true;
