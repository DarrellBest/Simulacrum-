package com.simulacrum.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Black-box test: spawns build/libs/simulacrum-all.jar with --no-globe, drives the live UI via
 * TestControlServer endpoints, asserts behavior. No JavaFX/WorldWind in the test JVM.
 *
 * <p>Run with: <code>./gradlew integrationTest</code> (depends on shadowJar).
 * <p>Skipped if the jar isn't present.
 */
@Tag("integration")
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class JarIntegrationTest {

    private static final String BASE = "http://127.0.0.1:17355";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static Process app;

    @BeforeAll
    static void launchJar() throws Exception {
        String jarProp = System.getProperty("simulacrum.jar", "build/libs/simulacrum-all.jar");
        Path jar = Paths.get(jarProp);
        assumeTrue(Files.exists(jar),
                "jar missing at " + jar + " — run shadowJar (or debloat) first");

        String logName = "build/integration-test-" + jar.getFileName() + ".log";
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString(), "--no-globe")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(Paths.get(logName).toFile()));
        app = pb.start();

        // Wait up to 60s for /health to come up.
        long deadline = System.currentTimeMillis() + 60_000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            if (!app.isAlive()) {
                throw new IllegalStateException("app exited before becoming healthy; see build/integration-test-app.log");
            }
            try {
                if ("ok".equals(get("/health"))) return;
            } catch (Exception ex) {
                last = ex;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("timed out waiting for /health", last);
    }

    @AfterAll
    static void killJar() {
        if (app != null && app.isAlive()) {
            app.destroy();
            try { app.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            if (app.isAlive()) app.destroyForcibly();
        }
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void healthEndpointResponds() throws Exception {
        assertEquals("ok", get("/health"));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void shipStateExposesNorfolkStart() throws Exception {
        String state = get("/ship/state");
        assertNotNull(state);
        assertTrue(state.contains("lat="), "state missing lat field: " + state);
        double lat = parseField(state, "lat");
        double lon = parseField(state, "lon");
        assertEquals(36.95, lat, 0.5);
        assertEquals(-76.0, lon, 0.5);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void heartbeatRoundTripsThroughBroker() throws Exception {
        long pubBefore = Long.parseLong(get("/pub/count"));
        long subBefore = Long.parseLong(get("/sub/count"));
        get("/signal/heartbeat");
        // Allow async dispatch through Artemis.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Long.parseLong(get("/pub/count")) > pubBefore
                    && Long.parseLong(get("/sub/count")) > subBefore) return;
            Thread.sleep(100);
        }
        long pubAfter = Long.parseLong(get("/pub/count"));
        long subAfter = Long.parseLong(get("/sub/count"));
        assertTrue(pubAfter > pubBefore, "publisher count did not increment");
        assertTrue(subAfter > subBefore, "subscriber count did not increment");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void sensorRoundTripsThroughBroker() throws Exception {
        long subBefore = Long.parseLong(get("/sub/count"));
        get("/signal/sensor?kind=RADAR");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Long.parseLong(get("/sub/count")) > subBefore) return;
            Thread.sleep(100);
        }
        assertTrue(Long.parseLong(get("/sub/count")) > subBefore,
                "sensor signal did not round-trip via AMQP");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void throttleAndAutopilotMoveTheShip() throws Exception {
        double startLat = parseField(get("/ship/state"), "lat");
        double startLon = parseField(get("/ship/state"), "lon");

        get("/ship/heading?v=90");
        get("/ship/autopilot?v=true");
        get("/ship/throttle?v=100");
        Thread.sleep(8000); // 8s × ~15 kn ≈ 60 m of motion, well above noise
        get("/ship/throttle?v=0");
        get("/ship/autopilot?v=false");

        String endState = get("/ship/state");
        double endLat = parseField(endState, "lat");
        double endLon = parseField(endState, "lon");
        double dlat = Math.abs(endLat - startLat);
        double dlon = Math.abs(endLon - startLon);
        assertTrue(dlon > dlat,
                "heading 090 should produce mostly longitude change; got dlat=" + dlat + " dlon=" + dlon);
        assertTrue(dlon > 1e-4, "ship should have moved measurably in longitude");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void publisherStreamIncrementsCountQuickly() throws Exception {
        long before = Long.parseLong(get("/pub/count"));
        get("/pub/start?hz=20");
        Thread.sleep(1500);
        get("/pub/stop");
        long after = Long.parseLong(get("/pub/count"));
        assertTrue(after - before >= 15,
                "20Hz × 1.5s should produce ≥15 messages, got " + (after - before));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void overlayDrawAndKmlExport() throws Exception {
        get("/draw/sample");
        String kml = get("/kml");
        assertTrue(kml.contains("<kml") || kml.contains("<KML"), "KML missing root element: " + kml);
        assertTrue(kml.toLowerCase().contains("polygon") || kml.toLowerCase().contains("placemark"),
                "KML should contain polygon/placemark");
    }

    private static String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GET " + path + " -> " + resp.statusCode() + " " + resp.body());
        }
        return resp.body();
    }

    private static double parseField(String state, String key) {
        Matcher m = Pattern.compile(key + "=(-?\\d+\\.?\\d*)").matcher(state);
        if (!m.find()) throw new IllegalArgumentException("no field '" + key + "' in: " + state);
        return Double.parseDouble(m.group(1));
    }
}
