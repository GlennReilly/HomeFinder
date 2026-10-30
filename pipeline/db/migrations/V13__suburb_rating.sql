-- A Homely star score typed in by hand for a suburb (ADR 0028). There is no foreign key to `suburb` on purpose: reloading
-- the suburbs (load-suburbs.sh) must never delete what you typed in. Suburb ids are the NSW boundary ids, which are stable.
CREATE TABLE suburb_rating (
    suburb_id    TEXT PRIMARY KEY,
    stars        NUMERIC(2, 1) NOT NULL CHECK (stars BETWEEN 0 AND 5),
    review_count INTEGER CHECK (review_count >= 0),
    rated_on     DATE NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
