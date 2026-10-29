-- Audit of which G-NAF localities with a pilot name were accepted. Several NSW suburbs share a name, so a
-- locality is accepted only when most of its geocodes fall inside the same-named official suburb.
CREATE TABLE locality_decision (
    locality_pid    TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    addresses       INTEGER NOT NULL,
    share_in_suburb NUMERIC NOT NULL,
    accepted        BOOLEAN NOT NULL
);
