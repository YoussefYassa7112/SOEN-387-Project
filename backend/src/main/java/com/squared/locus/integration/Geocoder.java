package com.squared.locus.integration;

// The foreign map service that turns an address into a point on the map (LOCUS-52).
// NominatimGeocoder is the real one; tests use a fake, so they never call the internet.
public interface Geocoder {

    // Returns null when the address cannot be found or the service is not reachable.
    Coordinates findCoordinates(String address);
}
