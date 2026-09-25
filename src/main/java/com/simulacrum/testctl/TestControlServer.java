package com.simulacrum.testctl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny localhost HTTP control endpoint enabled in test mode. Lets Robot Framework drive
 * publisher/subscriber/drawing actions without fighting JavaFX automation. Listens on
 * {@value #DEFAULT_PORT}.
 */
public final class TestControlServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 17355;

    public interface Handlers {
        void startPublisher(double hz);
        void stopPublisher();
        long publisherCount();
        long subscriberCount();
        void drawSamplePolygon();
        String exportKml();
        default void setThrottle(double pct) { }
        default void setRudder(double deg) { }
        default void setOrderedHeading(double deg) { }
        default void setAutopilot(boolean on) { }
        default void setAnchored(boolean on) { }
        default void emitHeartbeat() { }
        default void emitSensor(String kind) { }
        default String shipState() { return ""; }
        default String uiLocate(String id) { return ""; }
        default String uiList() { return ""; }
        default void uiFocus() { }
        /** Fired before each route runs. Lets the UI surface "Robot just hit X" feedback. */
        default void onRequest(String path, String query) { }
    }

    private final HttpServer server;
    private final AtomicLong hits = new AtomicLong();

    public TestControlServer(Handlers handlers) throws IOException {
        this(DEFAULT_PORT, handlers);
    }

    public TestControlServer(int port, Handlers handlers) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        route("/health", handlers, exchange -> respond(exchange, 200, "ok"));
        route("/pub/start", handlers, exchange -> {
            double hz = parseDouble(exchange.getRequestURI().getQuery(), "hz", 1.0);
            handlers.startPublisher(hz);
            respond(exchange, 200, "started@" + hz + "Hz");
        });
        route("/pub/stop", handlers, exchange -> {
            handlers.stopPublisher();
            respond(exchange, 200, "stopped");
        });
        route("/pub/count", handlers, exchange ->
                respond(exchange, 200, Long.toString(handlers.publisherCount())));
        route("/sub/count", handlers, exchange ->
                respond(exchange, 200, Long.toString(handlers.subscriberCount())));
        route("/draw/sample", handlers, exchange -> {
            handlers.drawSamplePolygon();
            respond(exchange, 200, "drawn");
        });
        route("/kml", handlers, exchange ->
                respond(exchange, 200, handlers.exportKml()));
        route("/ship/throttle", handlers, exchange -> {
            handlers.setThrottle(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        route("/ship/rudder", handlers, exchange -> {
            handlers.setRudder(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        route("/ship/heading", handlers, exchange -> {
            handlers.setOrderedHeading(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        route("/ship/autopilot", handlers, exchange -> {
            handlers.setAutopilot(parseBool(exchange.getRequestURI().getQuery()));
            respond(exchange, 200, "ok");
        });
        route("/ship/anchor", handlers, exchange -> {
            handlers.setAnchored(parseBool(exchange.getRequestURI().getQuery()));
            respond(exchange, 200, "ok");
        });
        route("/ship/state", handlers, exchange ->
                respond(exchange, 200, handlers.shipState()));
        route("/signal/heartbeat", handlers, exchange -> {
            handlers.emitHeartbeat();
            respond(exchange, 200, "ok");
        });
        route("/ui/locate", handlers, exchange ->
                respond(exchange, 200,
                        handlers.uiLocate(parseString(exchange.getRequestURI().getQuery(), "id", ""))));
        route("/ui/list", handlers, exchange -> respond(exchange, 200, handlers.uiList()));
        route("/ui/focus", handlers, exchange -> {
            handlers.uiFocus();
            respond(exchange, 200, "ok");
        });
        route("/signal/sensor", handlers, exchange -> {
            String kind = parseString(exchange.getRequestURI().getQuery(), "kind", "SONAR");
            handlers.emitSensor(kind);
            respond(exchange, 200, "ok");
        });
        server.setExecutor(null);
        server.start();
    }

    private void route(String path, Handlers handlers, HttpHandler inner) {
        server.createContext(path, exchange -> {
            try {
                handlers.onRequest(path, exchange.getRequestURI().getQuery());
            } catch (RuntimeException ignored) {
                // UI feedback must never break a route
            }
            inner.handle(exchange);
        });
    }

    public long hits() { return hits.get(); }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        hits.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String parseString(String query, String key, String fallback) {
        if (query == null) return fallback;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return fallback;
    }

    private static boolean parseBool(String query) {
        String v = parseString(query, "v", "true");
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on");
    }

    private static double parseDouble(String query, String key, double fallback) {
        if (query == null) return fallback;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return Double.parseDouble(kv[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
