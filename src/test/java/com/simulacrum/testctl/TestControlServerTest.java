package com.simulacrum.testctl;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestControlServerTest {

    private static final class FakeHandlers implements TestControlServer.Handlers {
        double lastHz;
        long pub;
        long sub;
        boolean drawn;
        @Override public void startPublisher(double hz) { lastHz = hz; pub++; }
        @Override public void stopPublisher() { }
        @Override public long publisherCount() { return pub; }
        @Override public long subscriberCount() { return sub; }
        @Override public void drawSamplePolygon() { drawn = true; }
        @Override public String exportKml() { return "<kml></kml>"; }
    }

    @Test
    void respondsToHealthAndDrivesHandlers() throws Exception {
        FakeHandlers fh = new FakeHandlers();
        try (TestControlServer srv = new TestControlServer(0, fh)) {
            int port = fieldPort(srv);
            HttpClient client = HttpClient.newHttpClient();
            assertEquals("ok", get(client, port, "/health"));
            assertTrue(get(client, port, "/pub/start?hz=5").contains("started"));
            assertEquals(5.0, fh.lastHz, 1e-9);
            get(client, port, "/draw/sample");
            assertTrue(fh.drawn);
            assertTrue(get(client, port, "/kml").contains("kml"));
        }
    }

    private static String get(HttpClient client, int port, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return resp.body();
    }

    private static int fieldPort(TestControlServer srv) throws Exception {
        var f = srv.getClass().getDeclaredField("server");
        f.setAccessible(true);
        com.sun.net.httpserver.HttpServer server = (com.sun.net.httpserver.HttpServer) f.get(srv);
        return server.getAddress().getPort();
    }
}
