package com.squared.locus.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// Tests the geocoder against a small fake Nominatim server on this computer,
// so the tests never call the real service on the internet.
class NominatimGeocoderTest {

    private HttpServer fakeServer;
    private String lastUserAgent;

    @AfterEach
    void stopServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    @Test
    void foundAddress_returnsItsCoordinates() throws Exception {
        String baseUrl = startFakeServer(200, "[{\"lat\":\"45.4972\",\"lon\":\"-73.5790\"}]");
        NominatimGeocoder geocoder = new NominatimGeocoder(baseUrl, "Locus-test");

        Coordinates coordinates = geocoder.findCoordinates("1455 De Maisonneuve Blvd W, Montreal");

        assertThat(coordinates).isEqualTo(new Coordinates(45.4972, -73.5790));
        assertThat(lastUserAgent).isEqualTo("Locus-test");   // the usage policy asks for one
    }

    @Test
    void unknownAddress_returnsNull() throws Exception {
        String baseUrl = startFakeServer(200, "[]");
        NominatimGeocoder geocoder = new NominatimGeocoder(baseUrl, "Locus-test");

        assertThat(geocoder.findCoordinates("nowhere at all")).isNull();
    }

    @Test
    void serverError_returnsNull() throws Exception {
        String baseUrl = startFakeServer(503, "busy");
        NominatimGeocoder geocoder = new NominatimGeocoder(baseUrl, "Locus-test");

        assertThat(geocoder.findCoordinates("1455 De Maisonneuve Blvd W, Montreal")).isNull();
    }

    @Test
    void serviceNotReachable_returnsNullInsteadOfFailing() {
        // Nothing listens on port 1, like when the map service is down.
        NominatimGeocoder geocoder = new NominatimGeocoder("http://localhost:1", "Locus-test");

        assertThat(geocoder.findCoordinates("1455 De Maisonneuve Blvd W, Montreal")).isNull();
    }

    private String startFakeServer(int status, String body) throws Exception {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/search", exchange -> {
            lastUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeServer.start();
        return "http://localhost:" + fakeServer.getAddress().getPort();
    }
}
