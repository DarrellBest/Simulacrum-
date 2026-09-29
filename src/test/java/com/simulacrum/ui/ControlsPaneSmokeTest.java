package com.simulacrum.ui;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.LoopbackTransport;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.UndoStack;
import com.simulacrum.ship.ShipModel;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds a ControlsPane on a loopback transport under a live JavaFX toolkit. */
class ControlsPaneSmokeTest {

    @BeforeAll
    static void initFx() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException already) { /* once-per-jvm */ }
        Platform.setImplicitExit(false);
    }

    @Test
    void buildsHierarchyAndRoundTripsHeartbeatThroughLoopback() throws Exception {
        AmqpConfig config = new AmqpConfig("localhost", 5672, "u", "p", "simulacrum.tracks", false);
        LoopbackTransport tx = new LoopbackTransport();
        OverlayManager overlays = new OverlayManager();
        UndoStack undo = new UndoStack();
        ShipModel ship = new ShipModel();
        GlobePane globe = new GlobePane(overlays); // no initialize(): wwd stays null, calls no-op safely

        AtomicReference<ControlsPane> ref = new AtomicReference<>();
        runFx(() -> ref.set(new ControlsPane(config, tx, overlays, undo, globe, ship)));
        ControlsPane pane = ref.get();
        assertNotNull(pane);
        assertNotNull(pane.node());

        assertTrue(containsLabel(pane.node(), "Bridge Controls"), "missing Bridge section");
        assertTrue(containsLabel(pane.node(), "Signal Buttons (publish via AMQP)"), "missing Signals section");
        assertTrue(containsLabel(pane.node(), "Activity Log"), "missing Activity Log");
        assertTrue(containsLabel(pane.node(), "TrackUpdate Publisher"), "missing Publisher section");

        long beforePub = pane.publisherCount();
        long beforeSub = pane.subscriberCount();
        runFx(pane::emitHeartbeatNow);
        // subscribe handler hops to the FX thread
        runFx(() -> { });
        assertTrue(pane.publisherCount() > beforePub, "publisher count should increase");
        assertTrue(pane.subscriberCount() > beforeSub, "loopback subscriber should receive the heartbeat");
    }

    @Test
    void sensorEmitGoesOutAndCountsRoundTrip() throws Exception {
        AmqpConfig config = new AmqpConfig("localhost", 5672, "u", "p", "simulacrum.tracks", false);
        LoopbackTransport tx = new LoopbackTransport();
        OverlayManager overlays = new OverlayManager();
        UndoStack undo = new UndoStack();
        ShipModel ship = new ShipModel();
        GlobePane globe = new GlobePane(overlays);

        AtomicReference<ControlsPane> ref = new AtomicReference<>();
        runFx(() -> ref.set(new ControlsPane(config, tx, overlays, undo, globe, ship)));
        ControlsPane pane = ref.get();

        long beforeSub = pane.subscriberCount();
        runFx(() -> pane.emitSensorNow("SONAR"));
        runFx(() -> { });
        assertTrue(pane.subscriberCount() > beforeSub, "sensor should round-trip via loopback");
    }

    private static void runFx(Runnable r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { latch.countDown(); } });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX runnable timed out");
    }

    private static boolean containsLabel(Parent root, String text) {
        for (Node n : root.getChildrenUnmodifiable()) {
            if (n instanceof Label l && text.equals(l.getText())) return true;
            if (n instanceof Parent p && containsLabel(p, text)) return true;
        }
        return false;
    }
}
