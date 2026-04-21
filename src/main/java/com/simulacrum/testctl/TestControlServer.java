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
