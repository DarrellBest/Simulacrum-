package com.simulacrum.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.awt.Robot;
import java.awt.event.InputEvent;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the live JavaFX window with java.awt.Robot — sends real OS mouse events at named buttons
 * located via the running app's HTTP control endpoint. Verifies side-effects via the same endpoint.
 *
 * <p>Run with: <code>./gradlew guiTest</code> (built jar) or <code>./gradlew guiTestDebloated</code>.
 * <p>The app must run with a real display — this test SKIPS in headless environments.
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RobotGuiTest {

    private static final String BASE = "http://127.0.0.1:17355";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private static Process app;
    private static Robot robot;

    @BeforeAll
    static void launchJarAndRobot() throws Exception {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "GUI test needs a real display");

        String jarProp = System.getProperty("simulacrum.jar", "build/libs/simulacrum-all.jar");
        Path jar = Paths.get(jarProp);
        assumeTrue(Files.exists(jar), "jar missing: " + jar);

        String logName = "build/gui-test-" + jar.getFileName() + ".log";
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(Paths.get(logName).toFile()));
        app = pb.start();

        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            if (!app.isAlive()) throw new IllegalStateException("app died; see " + logName);
            try { if ("ok".equals(get("/health"))) break; } catch (Exception ignored) { }
            Thread.sleep(500);
        }
        if (!"ok".equals(get("/health"))) throw new IllegalStateException("/health timeout");

        // Give JavaFX a beat to lay out the scene before we start querying screen coords.
        Thread.sleep(2000);
        robot = new Robot();
        robot.setAutoDelay(40);
        get("/ui/focus");
        Thread.sleep(400);
    }

    @AfterAll
    static void stopApp() {
        if (app != null && app.isAlive()) {
            app.destroy();
            try { app.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            if (app.isAlive()) app.destroyForcibly();
        }
    }

    @Test @Order(1)
    void uiListExposesRegisteredNodes() throws Exception {
        String list = get("/ui/list");
        for (String id : new String[]{
                "btn.allAheadFull", "btn.allStop", "btn.midships", "btn.heartbeat",
                "btn.distress", "btn.sonar", "btn.startStream", "btn.stopStream"}) {
            assertTrue(list.contains(id), "registry missing " + id + " (got: " + list + ")");
        }
    }

    @Test @Order(2)
    void clickingAllAheadFullRunsThrottleTo100() throws Exception {
        get("/ui/focus");
        Thread.sleep(200);
        clickButton("btn.allAheadFull");
        Thread.sleep(300);
        double thr = field("/ship/state", "thr");
        assertEquals(100.0, thr, 1.0, "throttle should be 100 after clicking 'All Ahead Full'");
    }

    @Test @Order(3)
    void clickingAllStopReturnsThrottleToZero() throws Exception {
        clickButton("btn.allStop");
        Thread.sleep(300);
        assertEquals(0.0, field("/ship/state", "thr"), 1.0);
    }

    @Test @Order(4)
    void clickingRudderButtonsMovesRudder() throws Exception {
        clickButton("btn.leftFull");
        Thread.sleep(300);
        double rud = field("/ship/state", "rud");
        assertEquals(-35.0, rud, 0.5, "Left Full should set rudder to -35°");

        clickButton("btn.midships");
        Thread.sleep(300);
        assertEquals(0.0, field("/ship/state", "rud"), 0.5);
    }

    @Test @Order(5)
    void clickingSignalButtonsRoundTripsThroughBroker() throws Exception {
        long subBefore = Long.parseLong(get("/sub/count"));
        clickButton("btn.heartbeat");
        clickButton("btn.sonar");
        clickButton("btn.distress");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Long.parseLong(get("/sub/count")) >= subBefore + 3) return;
            Thread.sleep(100);
        }
        long after = Long.parseLong(get("/sub/count"));
        assertTrue(after - subBefore >= 3,
                "expected ≥3 round-trips from clicking signal buttons, got " + (after - subBefore));
    }

    @Test @Order(6)
    void streamButtonsStartAndStopPublisher() throws Exception {
        long pubBefore = Long.parseLong(get("/pub/count"));
        clickButton("btn.startStream");
        Thread.sleep(1500);
        clickButton("btn.stopStream");
        long midpoint = Long.parseLong(get("/pub/count"));
        Thread.sleep(800);
        long after = Long.parseLong(get("/pub/count"));
        assertTrue(midpoint - pubBefore > 0, "stream should produce messages while running");
        assertEquals(after, midpoint,
                "publisher should be quiescent after stop, got delta " + (after - midpoint));
    }

    @Test @Order(7)
    void clickingBackOneThirdProducesNegativeThrottle() throws Exception {
        clickButton("btn.back13");
        Thread.sleep(300);
        double thr = field("/ship/state", "thr");
        assertNotEquals(0.0, thr);
        assertTrue(thr < 0, "Back 1/3 should produce negative throttle, got " + thr);
        clickButton("btn.allStop");
    }

    private static void clickButton(String id) throws Exception {
        String loc = get("/ui/locate?id=" + id);
        if (loc.isEmpty()) throw new IllegalStateException("button '" + id + "' not located");
        String[] parts = loc.split(",");
        int x = Integer.parseInt(parts[0]) + Integer.parseInt(parts[2]) / 2;
        int y = Integer.parseInt(parts[1]) + Integer.parseInt(parts[3]) / 2;
        robot.mouseMove(x, y);
        Thread.sleep(60);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static double field(String path, String key) throws Exception {
        Matcher m = Pattern.compile(key + "=(-?\\d+\\.?\\d*)").matcher(get(path));
        if (!m.find()) throw new IllegalStateException("no field '" + key + "' in " + path);
        return Double.parseDouble(m.group(1));
    }

    private static String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GET " + path + " -> " + resp.statusCode() + " " + resp.body());
        }
        return resp.body();
    }
}
