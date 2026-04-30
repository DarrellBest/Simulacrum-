package com.simulacrum.ui;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.MessageTransport;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.UndoStack;
import com.simulacrum.ship.ShipModel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/** Loads main.fxml and wires controls + globe into its {@code StackPane} mount points. */
public final class MainView {
    private final Parent root;
    private final ControlsPane controlsPane;
    private final GlobePane globePane;

    public MainView(AmqpConfig config,
                    MessageTransport transport,
                    OverlayManager overlayManager,
                    UndoStack undoStack,
                    ShipModel ship,
                    boolean disableGlobe) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        this.root = loader.load();
        BorderPane rootPane = (BorderPane) root;

        this.globePane = new GlobePane(overlayManager);
        this.controlsPane = new ControlsPane(config, transport, overlayManager, undoStack, globePane, ship);

        SplitPane splitPane = (SplitPane) rootPane.getCenter();
        StackPane controlsHost = (StackPane) splitPane.getItems().get(0);
        StackPane globeHost = (StackPane) splitPane.getItems().get(1);
        ScrollPane controlsScroll = new ScrollPane(controlsPane.node());
        controlsScroll.setFitToWidth(true);
        controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScroll.getStyleClass().add("controls-scroll");
        controlsHost.getChildren().setAll(controlsScroll);
        if (disableGlobe) {
            Label placeholder = new Label("Globe disabled (--no-globe mode)");
            placeholder.getStyleClass().add("stat");
            globeHost.getChildren().setAll(placeholder);
        } else {
            globeHost.getChildren().setAll(globePane.node());
            globePane.initialize();
        }

        if (transport.getClass().getSimpleName().equals("LoopbackTransport")) {
            Label banner = new Label("AMQP unavailable — using in-process loopback");
            banner.getStyleClass().add("fallback-banner");
            banner.setMaxWidth(Double.MAX_VALUE);
            rootPane.setBottom(banner);
        }
    }

    public Parent root() { return root; }
    public ControlsPane controlsPane() { return controlsPane; }
    public GlobePane globePane() { return globePane; }
}
