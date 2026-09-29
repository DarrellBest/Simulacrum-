package com.simulacrum;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.EmbeddedBroker;
import com.simulacrum.amqp.MessageTransport;
import com.simulacrum.amqp.TransportFactory;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.UndoStack;
import com.simulacrum.ship.ShipModel;
import com.simulacrum.ship.ShipSimulator;
import com.simulacrum.testctl.TestControlServer;
import com.simulacrum.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private EmbeddedBroker broker;
    private MessageTransport transport;
    private TestControlServer controlServer;
    private Stage primaryStage;
    private MainView mainView;
    private OverlayManager overlayManager;
    private UndoStack undoStack;
    private ShipModel ship;
    private ShipSimulator shipSim;

    static boolean noGlobe;

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--headless-check")) {
            runHeadlessCheck();
            return;
        }
        noGlobe = Arrays.asList(args).contains("--no-globe");
        launch(args);
    }

    private static void runHeadlessCheck() {
        AmqpConfig config = AmqpConfig.defaults();
        EmbeddedBroker broker = new EmbeddedBroker(config);
        try (MessageTransport tx = TransportFactory.open(config, broker)) {
            log.info("UI ready (headless transport={})", tx.describe());
            System.out.println("UI ready (headless transport=" + tx.describe() + ")");
        } finally {
            broker.close();
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        AmqpConfig config = AmqpConfig.defaults();
        broker = new EmbeddedBroker(config);
        transport = TransportFactory.open(config, broker);
        overlayManager = new OverlayManager();
        undoStack = new UndoStack();
        ship = new ShipModel();
        shipSim = new ShipSimulator(ship);

        mainView = new MainView(config, transport, overlayManager, undoStack, ship, noGlobe);
        controlServer = new TestControlServer(new TestControlServer.Handlers() {
            @Override public void onRequest(String path, String query) {
                String label = query != null && !query.isEmpty() ? path + "?" + query : path;
                mainView.controlsPane().flashAction(label);
                String btn = buttonForRoute(path, query);
                if (btn != null) mainView.controlsPane().flashButton(btn);
            }
            @Override public void startPublisher(double hz) { mainView.controlsPane().startPublisher(hz); }
            @Override public void stopPublisher() { mainView.controlsPane().stopPublisher(); }
            @Override public long publisherCount() { return mainView.controlsPane().publisherCount(); }
            @Override public long subscriberCount() { return mainView.controlsPane().subscriberCount(); }
            @Override public void drawSamplePolygon() { Platform.runLater(mainView.controlsPane()::drawSamplePolygon); }
            @Override public String exportKml() { return mainView.controlsPane().exportKml(); }
            @Override public void setThrottle(double v) { Platform.runLater(() -> ship.throttle.set(v)); }
            @Override public void setRudder(double v) { Platform.runLater(() -> ship.rudderDeg.set(v)); }
            @Override public void setOrderedHeading(double v) { Platform.runLater(() -> ship.orderedHeadingDeg.set(v)); }
            @Override public void setAutopilot(boolean on) { Platform.runLater(() -> ship.autopilot.set(on)); }
            @Override public void setAnchored(boolean on) { Platform.runLater(() -> ship.anchored.set(on)); }
            @Override public void emitHeartbeat() { mainView.controlsPane().emitHeartbeatNow(); }
            @Override public void emitSensor(String kind) { mainView.controlsPane().emitSensorNow(kind); }
            @Override public String shipState() {
                return String.format("lat=%.5f lon=%.5f hdg=%.2f spd=%.2f thr=%.1f rud=%.2f",
                        ship.latitudeDeg.get(), ship.longitudeDeg.get(),
                        ship.headingDeg.get(), ship.speedKn.get(),
                        ship.throttle.get(), ship.rudderDeg.get());
            }
            @Override public String uiLocate(String id) {
                java.util.concurrent.CompletableFuture<String> f = new java.util.concurrent.CompletableFuture<>();
                Platform.runLater(() -> f.complete(mainView.controlsPane().locateNode(id)));
                try { return f.get(2, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception e) { return ""; }
            }
            @Override public String uiList() {
                return String.join(",", mainView.controlsPane().nodeIds());
            }
            @Override public void uiFocus() {
                Platform.runLater(() -> {
                    if (primaryStage != null) {
                        primaryStage.setIconified(false);
                        primaryStage.toFront();
                        primaryStage.requestFocus();
                    }
                });
            }
        });
        log.info("test-control HTTP listening on :{}", TestControlServer.DEFAULT_PORT);

        Scene scene = new Scene(mainView.root(), 1280, 800);
        stage.setTitle("Simulacrum");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
        shipSim.start();
        log.info("UI ready");
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (shipSim != null) shipSim.close();
        if (controlServer != null) controlServer.close();
        if (transport != null) transport.close();
        if (broker != null) broker.close();
    }

    private static String buttonForRoute(String path, String query) {
        if (path == null) return null;
        return switch (path) {
            case "/signal/heartbeat" -> "btn.heartbeat";
            case "/signal/sensor" -> {
                String kind = paramOf(query, "kind");
                yield kind != null ? "btn." + kind.toLowerCase(java.util.Locale.ROOT) : null;
            }
            case "/pub/start" -> "btn.startStream";
            case "/pub/stop" -> "btn.stopStream";
            default -> null;
        };
    }

    private static String paramOf(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }
}
