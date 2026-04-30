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
        double lastHz, throttle, rudder, ordered;
        long pub, sub;
        boolean drawn, autopilot, anchored, heartbeat;
        String sensorKind = "";
        @Override public void startPublisher(double hz) { lastHz = hz; pub++; }
        @Override public void stopPublisher() { }
        @Override public long publisherCount() { return pub; }
        @Override public long subscriberCount() { return sub; }
        @Override public void drawSamplePolygon() { drawn = true; }
        @Override public String exportKml() { return "<kml></kml>"; }
        @Override public void setThrottle(double v) { throttle = v; }
        @Override public void setRudder(double v) { rudder = v; }
        @Override public void setOrderedHeading(double v) { ordered = v; }
        @Override public void setAutopilot(boolean on) { autopilot = on; }
        @Override public void setAnchored(boolean on) { anchored = on; }
        @Override public void emitHeartbeat() { heartbeat = true; }
        @Override public void emitSensor(String kind) { sensorKind = kind; }
        @Override public String shipState() { return "lat=0 lon=0"; }
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

    @Test
    void shipAndSignalEndpointsRouteToHandlers() throws Exception {
        FakeHandlers fh = new FakeHandlers();
        try (TestControlServer srv = new TestControlServer(0, fh)) {
            int port = fieldPort(srv);
            HttpClient client = HttpClient.newHttpClient();
            get(client, port, "/ship/throttle?v=75");
            assertEquals(75.0, fh.throttle, 1e-9);
            get(client, port, "/ship/rudder?v=-15");
            assertEquals(-15.0, fh.rudder, 1e-9);
            get(client, port, "/ship/heading?v=270");
            assertEquals(270.0, fh.ordered, 1e-9);
            get(client, port, "/ship/autopilot?v=true");
            assertTrue(fh.autopilot);
            get(client, port, "/ship/anchor?v=true");
            assertTrue(fh.anchored);
            get(client, port, "/signal/heartbeat");
            assertTrue(fh.heartbeat);
            get(client, port, "/signal/sensor?kind=RADAR");
            assertEquals("RADAR", fh.sensorKind);
            assertTrue(get(client, port, "/ship/state").contains("lat="));
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
