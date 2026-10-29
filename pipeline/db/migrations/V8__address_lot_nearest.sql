-- Adds the low-confidence 'nearest_lot' link (geocode in the road or kerb) and the distance it spans.
ALTER TABLE address_lot ADD COLUMN distance_m DOUBLE PRECISION;
ALTER TABLE address_lot DROP CONSTRAINT address_lot_method_check;
ALTER TABLE address_lot ADD CONSTRAINT address_lot_method_check
    CHECK (method IN ('legal_parcel_id', 'point_in_polygon', 'nearest_lot'));
