package com.simulacrum.ui;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.MessageTransport;
import com.simulacrum.amqp.RateController;
import com.simulacrum.overlay.AddShapeCommand;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.Shape;
import com.simulacrum.overlay.UndoStack;
import com.simulacrum.proto.Heartbeat;
import com.simulacrum.proto.SensorReport;
import com.simulacrum.proto.TrackUpdate;
import com.simulacrum.ship.ShipModel;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ControlsPane {
    private final VBox root = new VBox(10);

    private final AmqpConfig config;
    private final MessageTransport transport;
    private final RateController rateController = new RateController();
    private final RateController heartbeatRate = new RateController();
    private final OverlayManager overlayManager;
    private final UndoStack undoStack;
    private final GlobePane globePane;
    private final ShipModel ship;

    private final Label transportLabel = new Label();
    private final Label publisherCountLabel = new Label("0");
    private final Label subscriberCountLabel = new Label("0");
    private final Label statusLabel = new Label("--");

    private final AtomicLong publisherCount = new AtomicLong();
    private final AtomicLong subscriberCount = new AtomicLong();
    private final AtomicLong heartbeatSeq = new AtomicLong();
    private final AtomicReference<Spinner<Double>> hzSpinnerRef = new AtomicReference<>();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final byte TAG_TRACK = 1;
    private static final byte TAG_SENSOR = 2;
    private static final byte TAG_HEARTBEAT = 3;
    private static final int LOG_MAX = 200;

    private final ListView<String> activityLog = new ListView<>();
    private final Circle txDot = new Circle(6, Color.web("#3a3f4b"));
    private final Circle rxDot = new Circle(6, Color.web("#3a3f4b"));
    private final Label lastRxLabel = new Label("none");

    private final java.util.Map<String, Node> nodeRegistry = new java.util.HashMap<>();

    public ControlsPane(AmqpConfig config,
                        MessageTransport transport,
                        OverlayManager overlayManager,
                        UndoStack undoStack,
                        GlobePane globePane,
                        ShipModel ship) {
        this.config = config;
        this.transport = transport;
        this.overlayManager = overlayManager;
        this.undoStack = undoStack;
        this.globePane = globePane;
        this.ship = ship;
        buildUi();
        subscribeToTracks();
        bindShipToGlobe();
    }

    public VBox node() { return root; }
    public long publisherCount() { return publisherCount.get(); }
    public long subscriberCount() { return subscriberCount.get(); }

    public void startPublisher(double hz) { rateController.start(hz, this::emitTrackUpdate); }
    public void stopPublisher() { rateController.stop(); }

    public void drawSamplePolygon() {
        double lat = ship.latitudeDeg.get();
        double lon = ship.longitudeDeg.get();
        Shape s = new Shape(Shape.Kind.POLYGON, List.of(
                new Shape.LatLon(lat + 0.04, lon - 0.04),
                new Shape.LatLon(lat + 0.04, lon + 0.04),
                new Shape.LatLon(lat - 0.04, lon + 0.04),
                new Shape.LatLon(lat - 0.04, lon - 0.04)));
        undoStack.push(new AddShapeCommand(s), overlayManager);
    }

    public String exportKml() { return com.simulacrum.overlay.KmlExporter.toKml(overlayManager.shapes()); }

    public void emitHeartbeatNow() { emitHeartbeat(); }

    private <T extends Node> T register(String id, T node) {
        node.setId(id);
        nodeRegistry.put(id, node);
        return node;
    }

    /** Returns "x,y,w,h" in screen pixels for the named registered node, or empty string if absent. */
    public String locateNode(String id) {
        Node n = nodeRegistry.get(id);
        if (n == null) return "";
        Bounds b = n.localToScreen(n.getBoundsInLocal());
        if (b == null) return "";
        return String.format("%.0f,%.0f,%.0f,%.0f", b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    public java.util.Set<String> nodeIds() { return java.util.Collections.unmodifiableSet(nodeRegistry.keySet()); }

    public void emitSensorNow(String kind) {
        emitSensor(kind, "TEST", 1.0);
    }

    private void buildUi() {
        root.setPadding(new Insets(10));
        root.getStyleClass().add("controls-host");
        transportLabel.setText("Transport: " + transport.describe());
        transportLabel.getStyleClass().add("stat");

        Label title = new Label("Simulacrum — Bridge");
        title.getStyleClass().add("section-title");

        root.getChildren().addAll(title, transportHealth(), statusSection(), bridgeSection(),
                signalsSection(), publisherSection(), subscriberSection(), activitySection(), overlaySection());
    }

    private HBox transportHealth() {
        boolean amqp = !transport.getClass().getSimpleName().equals("LoopbackTransport");
        Circle health = new Circle(6, amqp ? Color.web("#7ee787") : Color.web("#f0b541"));
        Label label = new Label((amqp ? "AMQP 1.0 " : "Loopback ") + transport.describe());
        label.getStyleClass().add("stat");
        Label txL = new Label("TX");
        Label rxL = new Label("RX");
        txL.getStyleClass().add("stat");
        rxL.getStyleClass().add("stat");
        HBox row = new HBox(8, health, label, new Label("  "), txDot, txL, rxDot, rxL);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox activitySection() {
        VBox box = section("Activity Log");
        activityLog.setPrefHeight(160);
        activityLog.getStyleClass().add("stat");
        Button clear = new Button("Clear");
        clear.setOnAction(e -> activityLog.getItems().clear());
        HBox header = new HBox(10, new Label("Last RX:"), lastRxLabel, clear);
        header.setAlignment(Pos.CENTER_LEFT);
        lastRxLabel.getStyleClass().add("stat");
        box.getChildren().addAll(header, activityLog);
        return box;
    }

    private void log(String line) {
        String entry = "[" + LocalTime.now().format(TS) + "] " + line;
        Platform.runLater(() -> {
            activityLog.getItems().add(0, entry);
            if (activityLog.getItems().size() > LOG_MAX) {
                activityLog.getItems().remove(LOG_MAX, activityLog.getItems().size());
            }
        });
    }

    private void blink(Circle dot, Color color) {
        Platform.runLater(() -> {
            dot.setFill(color);
            javafx.animation.PauseTransition pt =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));
            pt.setOnFinished(e -> dot.setFill(Color.web("#3a3f4b")));
            pt.play();
        });
    }

    private VBox statusSection() {
        VBox box = section("Own Ship");
        statusLabel.getStyleClass().add("stat");
        statusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("lat %+.4f°  lon %+.4f°   hdg %03.0f°   spd %+5.1f kn   thr %+4.0f%%   rud %+5.1f°%s",
                        ship.latitudeDeg.get(), ship.longitudeDeg.get(),
                        ship.headingDeg.get(), ship.speedKn.get(),
                        ship.throttle.get(), ship.rudderDeg.get(),
                        ship.anchored.get() ? "  [ANCHORED]" : ""),
                ship.latitudeDeg, ship.longitudeDeg, ship.headingDeg, ship.speedKn,
                ship.throttle, ship.rudderDeg, ship.anchored));
        CheckBox track = new CheckBox("Camera tracks ship");
        track.setSelected(true);
        track.selectedProperty().addListener((o, a, b) -> globePane.setTrackOwnShip(b));
        box.getChildren().addAll(statusLabel, track);
        return box;
    }

    private VBox bridgeSection() {
        VBox box = section("Bridge Controls");

        Slider throttle = new Slider(-100, 100, 0);
        throttle.setMajorTickUnit(25);
        throttle.setShowTickLabels(true);
        throttle.setShowTickMarks(true);
        ship.throttle.bindBidirectional(throttle.valueProperty());

        Button allAheadFull = register("btn.allAheadFull", new Button("All Ahead Full"));
        allAheadFull.setOnAction(e -> ship.throttle.set(100));
        Button standard = register("btn.aheadStd", new Button("Ahead Std"));
        standard.setOnAction(e -> ship.throttle.set(60));
        Button stop = register("btn.allStop", new Button("All Stop"));
        stop.setOnAction(e -> ship.throttle.set(0));
        Button astern = register("btn.back13", new Button("Back 1/3"));
        astern.setOnAction(e -> ship.throttle.set(-33));

        Slider rudder = new Slider(-ShipModel.MAX_RUDDER_DEG, ShipModel.MAX_RUDDER_DEG, 0);
        rudder.setMajorTickUnit(15);
        rudder.setShowTickLabels(true);
        rudder.setShowTickMarks(true);
        ship.rudderDeg.bindBidirectional(rudder.valueProperty());

        Button midships = register("btn.midships", new Button("Midships"));
        midships.setOnAction(e -> ship.rudderDeg.set(0));
        Button leftFull = register("btn.leftFull", new Button("Left Full"));
        leftFull.setOnAction(e -> ship.rudderDeg.set(-ShipModel.MAX_RUDDER_DEG));
        Button rightFull = register("btn.rightFull", new Button("Right Full"));
        rightFull.setOnAction(e -> ship.rudderDeg.set(ShipModel.MAX_RUDDER_DEG));

        Spinner<Double> ordered = new Spinner<>(0.0, 359.9, ship.orderedHeadingDeg.get(), 5.0);
        ordered.setEditable(true);
        ordered.setPrefWidth(90);
        ship.orderedHeadingDeg.bind(ordered.valueProperty());
        CheckBox autopilot = new CheckBox("Autopilot");
        autopilot.selectedProperty().bindBidirectional(ship.autopilot);

        CheckBox anchor = new CheckBox("Drop anchor");
        anchor.selectedProperty().bindBidirectional(ship.anchored);

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(4);
        grid.add(new Label("Throttle"), 0, 0);
        grid.add(throttle, 1, 0, 4, 1);
        grid.add(new HBox(4, allAheadFull, standard, stop, astern), 1, 1, 4, 1);
        grid.add(new Label("Rudder"), 0, 2);
        grid.add(rudder, 1, 2, 4, 1);
        grid.add(new HBox(4, leftFull, midships, rightFull), 1, 3, 4, 1);
        grid.add(new Label("Ord. Hdg"), 0, 4);
        grid.add(new HBox(6, ordered, autopilot, anchor), 1, 4, 4, 1);

        box.getChildren().add(grid);
        return box;
    }

    private VBox signalsSection() {
        VBox box = section("Signal Buttons (publish via AMQP)");

        Button heartbeatOnce = register("btn.heartbeat", new Button("Heartbeat"));
        heartbeatOnce.setOnAction(e -> emitHeartbeat());
        CheckBox hbAuto = new CheckBox("auto 1Hz");
        hbAuto.selectedProperty().addListener((o, a, on) -> {
            if (on) heartbeatRate.start(1.0, this::emitHeartbeat);
            else heartbeatRate.stop();
        });

        Button mob = register("btn.mob", new Button("Man Overboard"));
        mob.setOnAction(e -> emitSensor("MOB", "ALERT", 1));
        Button distress = register("btn.distress", new Button("Distress"));
        distress.setOnAction(e -> emitSensor("DISTRESS", "MAYDAY", 1));
        Button sonar = register("btn.sonar", new Button("Sonar Ping"));
        sonar.setOnAction(e -> emitSensor("SONAR", "RANGE_M", 1500 + Math.random() * 8000));
        Button radar = register("btn.radar", new Button("Radar Contact"));
        radar.setOnAction(e -> emitSensor("RADAR", "CONTACT_BEARING_DEG", Math.random() * 360));
        Button ais = register("btn.ais", new Button("AIS Burst"));
        ais.setOnAction(e -> emitSensor("AIS", "MMSI", 200000000 + (long) (Math.random() * 1_000_000)));
        Button ew = register("btn.ew", new Button("EW Detect"));
        ew.setOnAction(e -> emitSensor("EW", "FREQ_MHZ", 2400 + Math.random() * 6000));

        HBox row1 = new HBox(6, heartbeatOnce, hbAuto);
        HBox row2 = new HBox(6, mob, distress);
        HBox row3 = new HBox(6, sonar, radar, ais, ew);
        row1.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(row1, row2, row3);
        return box;
    }

    private VBox publisherSection() {
        VBox box = section("TrackUpdate Publisher");

        Spinner<Double> hzSpinner = new Spinner<>(0.1, 50.0, 1.0, 0.5);
        hzSpinner.setEditable(true);
        hzSpinner.setPrefWidth(90);
        hzSpinnerRef.set(hzSpinner);

        Button sendOnce = register("btn.sendOnce", new Button("Send once"));
        sendOnce.setOnAction(e -> emitTrackUpdate());
        Button startStream = register("btn.startStream", new Button("Start stream"));
        startStream.setOnAction(e -> startPublisher(hzSpinner.getValue()));
        Button stopStream = register("btn.stopStream", new Button("Stop stream"));
        stopStream.setOnAction(e -> stopPublisher());
        Button burst = register("btn.burst", new Button("Burst 100"));
        burst.setOnAction(e -> rateController.burst(100, this::emitTrackUpdate));

        HBox row = new HBox(6, new Label("Hz"), hzSpinner, sendOnce, startStream, stopStream, burst);
        publisherCountLabel.getStyleClass().add("stat");
        HBox counters = new HBox(12, new Label("Published:"), publisherCountLabel);
        box.getChildren().addAll(row, counters);
        return box;
    }

    private VBox subscriberSection() {
        VBox box = section("Subscriber");
        subscriberCountLabel.getStyleClass().add("stat");
        box.getChildren().addAll(new Label("Address: " + config.address()),
                new HBox(12, new Label("Received:"), subscriberCountLabel));
        return box;
    }

    private VBox overlaySection() {
        VBox box = section("Overlay / Drawing");
        Button sample = new Button("Drop ring around ship");
        sample.setOnAction(e -> drawSamplePolygon());
        Button undo = new Button("Undo");
        undo.setOnAction(e -> undoStack.undo(overlayManager));
        Button redo = new Button("Redo");
        redo.setOnAction(e -> undoStack.redo(overlayManager));
        box.getChildren().add(new HBox(6, sample, undo, redo));
        return box;
    }

    private VBox section(String titleText) {
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        Label h = new Label(titleText);
        h.getStyleClass().add("section-title");
        box.getChildren().add(h);
        return box;
    }

    private void bindShipToGlobe() {
        new AnimationTimer() {
            private long lastNs = 0;
            @Override public void handle(long now) {
                if (now - lastNs < 50_000_000L) return; // ~20 Hz
                lastNs = now;
                globePane.updateOwnShip(
                        ship.latitudeDeg.get(), ship.longitudeDeg.get(),
                        ship.headingDeg.get(), ship.speedKn.get());
            }
        }.start();
    }

    private void emitTrackUpdate() {
        try {
            TrackUpdate update = TrackUpdate.newBuilder()
                    .setTrackId(ship.trackId.get())
                    .setLatitudeDeg(ship.latitudeDeg.get())
                    .setLongitudeDeg(ship.longitudeDeg.get())
                    .setSpeedMps(ship.speedKn.get() * 0.514444)
                    .setHeadingDeg(ship.headingDeg.get())
                    .setTimestampMs(System.currentTimeMillis())
                    .build();
            byte[] bytes = update.toByteArray();
            transport.publish(config.address(), tag(TAG_TRACK, bytes));
            bumpPublisher();
            blink(txDot, Color.web("#58a6ff"));
            log(String.format("TX  TrackUpdate  id=%s lat=%+.4f lon=%+.4f hdg=%03.0f  (%dB)",
                    update.getTrackId(), update.getLatitudeDeg(), update.getLongitudeDeg(),
                    update.getHeadingDeg(), bytes.length));
        } catch (Exception ex) {
            log("TX FAIL TrackUpdate: " + ex.getMessage());
        }
    }

    private void emitHeartbeat() {
        try {
            Heartbeat hb = Heartbeat.newBuilder()
                    .setSource(ship.trackId.get())
                    .setTimestampMs(System.currentTimeMillis())
                    .setSequence(heartbeatSeq.incrementAndGet())
                    .build();
            byte[] bytes = hb.toByteArray();
            transport.publish(config.address(), tag(TAG_HEARTBEAT, bytes));
            bumpPublisher();
            blink(txDot, Color.web("#58a6ff"));
            log(String.format("TX  Heartbeat   src=%s seq=%d  (%dB)",
                    hb.getSource(), hb.getSequence(), bytes.length));
        } catch (Exception ex) {
            log("TX FAIL Heartbeat: " + ex.getMessage());
        }
    }

    private void emitSensor(String kind, String unit, double value) {
        try {
            SensorReport r = SensorReport.newBuilder()
                    .setSensorId(ship.trackId.get() + "/" + kind)
                    .setKind(kind)
                    .setValue(value)
                    .setUnit(unit)
                    .setTimestampMs(System.currentTimeMillis())
                    .build();
            byte[] bytes = r.toByteArray();
            transport.publish(config.address(), tag(TAG_SENSOR, bytes));
            bumpPublisher();
            blink(txDot, Color.web("#58a6ff"));
            log(String.format("TX  SensorReport %s=%.1f %s  (%dB)", kind, value, unit, bytes.length));
        } catch (Exception ex) {
            log("TX FAIL " + kind + ": " + ex.getMessage());
        }
    }

    private static byte[] tag(byte tag, byte[] body) {
        byte[] out = new byte[body.length + 1];
        out[0] = tag;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    private void bumpPublisher() {
        publisherCount.incrementAndGet();
        Platform.runLater(() -> publisherCountLabel.setText(Long.toString(publisherCount.get())));
    }

    private void subscribeToTracks() {
        try {
            transport.subscribe(config.address(), bytes -> handleIncoming(bytes));
        } catch (Exception ex) {
            log("SUB FAIL: " + ex.getMessage());
        }
    }

    private void handleIncoming(byte[] raw) {
        if (raw == null || raw.length < 1) return;
        byte tag = raw[0];
        byte[] body = new byte[raw.length - 1];
        System.arraycopy(raw, 1, body, 0, body.length);
        subscriberCount.incrementAndGet();
        Platform.runLater(() -> subscriberCountLabel.setText(Long.toString(subscriberCount.get())));
        blink(rxDot, Color.web("#7ee787"));
        try {
            switch (tag) {
                case TAG_TRACK -> {
                    TrackUpdate u = TrackUpdate.parseFrom(body);
                    log(String.format("RX  TrackUpdate  id=%s lat=%+.4f lon=%+.4f spd=%.1f m/s",
                            u.getTrackId(), u.getLatitudeDeg(), u.getLongitudeDeg(), u.getSpeedMps()));
                    Platform.runLater(() -> {
                        lastRxLabel.setText("TrackUpdate " + u.getTrackId());
                        if (!u.getTrackId().equals(ship.trackId.get())) {
                            globePane.dropTrackMarker(u.getLatitudeDeg(),
                                    u.getLongitudeDeg(), u.getTrackId());
                        }
                    });
                }
                case TAG_SENSOR -> {
                    SensorReport r = SensorReport.parseFrom(body);
                    log(String.format("RX  SensorReport %s value=%.2f %s",
                            r.getKind(), r.getValue(), r.getUnit()));
                    Platform.runLater(() -> lastRxLabel.setText("SensorReport " + r.getKind()));
                }
                case TAG_HEARTBEAT -> {
                    Heartbeat h = Heartbeat.parseFrom(body);
                    log(String.format("RX  Heartbeat   src=%s seq=%d", h.getSource(), h.getSequence()));
                    Platform.runLater(() -> lastRxLabel.setText("Heartbeat #" + h.getSequence()));
                }
                default -> log("RX  unknown tag=" + tag + " (" + body.length + "B)");
            }
        } catch (Exception ex) {
            log("RX PARSE FAIL: " + ex.getMessage());
        }
    }
}
