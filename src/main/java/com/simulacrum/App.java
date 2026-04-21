package com.simulacrum;

import com.simulacrum.amqp.AmqpConfig;
import com.simulacrum.amqp.EmbeddedBroker;
import com.simulacrum.amqp.MessageTransport;
import com.simulacrum.amqp.TransportFactory;
import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.UndoStack;
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
    private MainView mainView;
    private OverlayManager overlayManager;
    private UndoStack undoStack;

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
        AmqpConfig config = AmqpConfig.defaults();
        broker = new EmbeddedBroker(config);
        transport = TransportFactory.open(config, broker);
        overlayManager = new OverlayManager();
        undoStack = new UndoStack();

        mainView = new MainView(config, transport, overlayManager, undoStack, noGlobe);
        controlServer = new TestControlServer(new TestControlServer.Handlers() {
            @Override public void startPublisher(double hz) { mainView.controlsPane().startPublisher(hz); }
            @Override public void stopPublisher() { mainView.controlsPane().stopPublisher(); }
            @Override public long publisherCount() { return mainView.controlsPane().publisherCount(); }
            @Override public long subscriberCount() { return mainView.controlsPane().subscriberCount(); }
            @Override public void drawSamplePolygon() { Platform.runLater(mainView.controlsPane()::drawSamplePolygon); }
            @Override public String exportKml() { return mainView.controlsPane().exportKml(); }
        });
        log.info("test-control HTTP listening on :{}", TestControlServer.DEFAULT_PORT);

        Scene scene = new Scene(mainView.root(), 1280, 800);
        stage.setTitle("Simulacrum — SWFTS Simulator");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
        log.info("UI ready");
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (controlServer != null) controlServer.close();
        if (transport != null) transport.close();
        if (broker != null) broker.close();
    }
}
