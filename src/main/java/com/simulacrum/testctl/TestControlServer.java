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
    }

    private final HttpServer server;
    private final AtomicLong hits = new AtomicLong();

    public TestControlServer(Handlers handlers) throws IOException {
        this(DEFAULT_PORT, handlers);
    }

    public TestControlServer(int port, Handlers handlers) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", plain("ok"));
        server.createContext("/pub/start", exchange -> {
            double hz = parseDouble(exchange.getRequestURI().getQuery(), "hz", 1.0);
            handlers.startPublisher(hz);
            respond(exchange, 200, "started@" + hz + "Hz");
        });
        server.createContext("/pub/stop", exchange -> {
            handlers.stopPublisher();
            respond(exchange, 200, "stopped");
        });
        server.createContext("/pub/count", exchange ->
                respond(exchange, 200, Long.toString(handlers.publisherCount())));
        server.createContext("/sub/count", exchange ->
                respond(exchange, 200, Long.toString(handlers.subscriberCount())));
        server.createContext("/draw/sample", exchange -> {
            handlers.drawSamplePolygon();
            respond(exchange, 200, "drawn");
        });
        server.createContext("/kml", exchange ->
                respond(exchange, 200, handlers.exportKml()));
        server.createContext("/ship/throttle", exchange -> {
            handlers.setThrottle(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        server.createContext("/ship/rudder", exchange -> {
            handlers.setRudder(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        server.createContext("/ship/heading", exchange -> {
            handlers.setOrderedHeading(parseDouble(exchange.getRequestURI().getQuery(), "v", 0));
            respond(exchange, 200, "ok");
        });
        server.createContext("/ship/autopilot", exchange -> {
            handlers.setAutopilot(parseBool(exchange.getRequestURI().getQuery()));
            respond(exchange, 200, "ok");
        });
        server.createContext("/ship/anchor", exchange -> {
            handlers.setAnchored(parseBool(exchange.getRequestURI().getQuery()));
            respond(exchange, 200, "ok");
        });
        server.createContext("/ship/state", exchange ->
                respond(exchange, 200, handlers.shipState()));
        server.createContext("/signal/heartbeat", exchange -> {
            handlers.emitHeartbeat();
            respond(exchange, 200, "ok");
        });
        server.createContext("/ui/locate", exchange ->
                respond(exchange, 200,
                        handlers.uiLocate(parseString(exchange.getRequestURI().getQuery(), "id", ""))));
        server.createContext("/ui/list", exchange -> respond(exchange, 200, handlers.uiList()));
        server.createContext("/ui/focus", exchange -> {
            handlers.uiFocus();
            respond(exchange, 200, "ok");
        });
        server.createContext("/signal/sensor", exchange -> {
            String kind = parseString(exchange.getRequestURI().getQuery(), "kind", "SONAR");
            handlers.emitSensor(kind);
            respond(exchange, 200, "ok");
        });
        server.setExecutor(null);
        server.start();
    }

    public long hits() { return hits.get(); }

    private HttpHandler plain(String body) {
        return exchange -> respond(exchange, 200, body);
    }

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
