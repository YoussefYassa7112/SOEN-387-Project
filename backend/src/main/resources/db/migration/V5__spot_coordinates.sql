-- V5: map coordinates for each parking spot (Epic 10, LOCUS-52).
-- The columns can be empty: spots made before this change, or spots whose address
-- could not be found by the geocoding service, simply have no point on the map.
-- This only adds columns, so old code and old data keep working (expand step, Chapter 6).

ALTER TABLE parking_spots ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE parking_spots ADD COLUMN longitude DOUBLE PRECISION;
