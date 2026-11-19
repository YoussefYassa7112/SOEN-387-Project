-- V5: map coordinates for each parking spot (Epic 10, LOCUS-52).

ALTER TABLE parking_spots ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE parking_spots ADD COLUMN longitude DOUBLE PRECISION;
