package com.simulacrum.ui;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.MessageTransport;
import com.simulacrum.amqp.RateController;
import com.simulacrum.overlay.AddShapeCommand;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.Shape;
import com.simulacrum.overlay.UndoStack;
import com.simulacrum.proto.TrackUpdate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ControlsPane {
    private final VBox root = new VBox(8);

    private final AmqpConfig config;
    private final MessageTransport transport;
    private final RateController rateController = new RateController();
    private final OverlayManager overlayManager;
    private final UndoStack undoStack;
    private final GlobePane globePane;

    private final Label transportLabel = new Label();
    private final Label publisherCountLabel = new Label("0");
    private final Label subscriberCountLabel = new Label("0");

    private final AtomicLong publisherCount = new AtomicLong();
    private final AtomicLong subscriberCount = new AtomicLong();

    private final Slider latSlider = new Slider(-90, 90, 37.78);
    private final Slider lonSlider = new Slider(-180, 180, -122.42);
    private final Slider speedSlider = new Slider(0, 50, 10);
    private final Slider headingSlider = new Slider(0, 360, 90);

    public ControlsPane(AmqpConfig config,
                        MessageTransport transport,
                        OverlayManager overlayManager,
                        UndoStack undoStack,
                        GlobePane globePane) {
        this.config = config;
        this.transport = transport;
        this.overlayManager = overlayManager;
        this.undoStack = undoStack;
        this.globePane = globePane;
        buildUi();
        subscribeToTracks();
    }

    public VBox node() { return root; }
    public long publisherCount() { return publisherCount.get(); }
    public long subscriberCount() { return subscriberCount.get(); }

    public void startPublisher(double hz) {
        rateController.start(hz, this::emitOne);
    }

    public void stopPublisher() {
        rateController.stop();
    }

    public void drawSamplePolygon() {
        Shape s = new Shape(Shape.Kind.POLYGON, List.of(
                new Shape.LatLon(37.80, -122.44),
                new Shape.LatLon(37.80, -122.40),
                new Shape.LatLon(37.76, -122.40),
                new Shape.LatLon(37.76, -122.44)
        ));
        undoStack.push(new AddShapeCommand(s), overlayManager);
    }

    private void buildUi() {
        root.setPadding(new Insets(8));
        transportLabel.setText("Transport: " + transport.describe());
        transportLabel.getStyleClass().add("stat");

        Label title = new Label("Simulacrum Controls");
        title.getStyleClass().add("section-title");

        root.getChildren().addAll(title, transportLabel, publisherSection(), subscriberSection(), overlaySection());
    }

    private VBox publisherSection() {
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        Label h = new Label("Publisher");
        h.getStyleClass().add("section-title");

        ComboBox<String> msgType = new ComboBox<>();
        msgType.getItems().addAll("TrackUpdate", "SensorReport", "Heartbeat");
        msgType.getSelectionModel().selectFirst();

        Spinner<Double> hzSpinner = new Spinner<>(0.1, 1000.0, 1.0, 1.0);
        hzSpinner.setEditable(true);

        Button sendOnce = new Button("Send once");
        sendOnce.setOnAction(e -> emitOne());

        Button startStream = new Button("Start stream");
        Button stopStream = new Button("Stop stream");
        startStream.setOnAction(e -> startPublisher(hzSpinner.getValue()));
        stopStream.setOnAction(e -> stopPublisher());

        Button burst = new Button("Burst 100");
        burst.setOnAction(e -> rateController.burst(100, this::emitOne));

        HBox row = new HBox(6, msgType, new Label("Hz"), hzSpinner, sendOnce, startStream, stopStream, burst);
        row.setPadding(new Insets(4, 0, 4, 0));

        VBox sliders = new VBox(4,
                labeled("Latitude", latSlider),
                labeled("Longitude", lonSlider),
                labeled("Speed (m/s)", speedSlider),
                labeled("Heading (deg)", headingSlider));

        HBox counters = new HBox(12, new Label("Published:"), publisherCountLabel);
        publisherCountLabel.getStyleClass().add("stat");

        box.getChildren().addAll(h, row, sliders, counters);
        return box;
    }

    private VBox subscriberSection() {
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        Label h = new Label("Subscriber");
        h.getStyleClass().add("section-title");
        subscriberCountLabel.getStyleClass().add("stat");
        box.getChildren().addAll(h, new Label("Address: " + config.address()), new HBox(12, new Label("Received:"), subscriberCountLabel));
        return box;
    }

    private VBox overlaySection() {
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        Label h = new Label("Overlay / Drawing");
        h.getStyleClass().add("section-title");
        Button sample = new Button("Draw sample polygon");
        sample.setOnAction(e -> drawSamplePolygon());
        Button undo = new Button("Undo");
        undo.setOnAction(e -> undoStack.undo(overlayManager));
        Button redo = new Button("Redo");
        redo.setOnAction(e -> undoStack.redo(overlayManager));
        box.getChildren().addAll(h, new HBox(6, sample, undo, redo));
        return box;
    }

    private VBox labeled(String label, Slider slider) {
        Label l = new Label(label);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        return new VBox(2, l, slider);
    }

    private void emitOne() {
        try {
            double lat = latSlider.getValue();
            double lon = lonSlider.getValue();
            TrackUpdate update = TrackUpdate.newBuilder()
                    .setTrackId("track-1")
                    .setLatitudeDeg(lat)
                    .setLongitudeDeg(lon)
                    .setSpeedMps(speedSlider.getValue())
                    .setHeadingDeg(headingSlider.getValue())
                    .setTimestampMs(System.currentTimeMillis())
                    .build();
            transport.publish(config.address(), update.toByteArray());
            publisherCount.incrementAndGet();
            Platform.runLater(() -> publisherCountLabel.setText(Long.toString(publisherCount.get())));
        } catch (Exception ignored) {
        }
    }

    private void subscribeToTracks() {
        try {
            transport.subscribe(config.address(), bytes -> {
                try {
                    TrackUpdate update = TrackUpdate.parseFrom(bytes);
                    subscriberCount.incrementAndGet();
                    Platform.runLater(() -> {
                        subscriberCountLabel.setText(Long.toString(subscriberCount.get()));
                        globePane.dropTrackMarker(
                                update.getLatitudeDeg(),
                                update.getLongitudeDeg(),
                                update.getTrackId());
                    });
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    public String exportKml() {
        return com.simulacrum.overlay.KmlExporter.toKml(overlayManager.shapes());
    }
}
