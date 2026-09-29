package com.simulacrum.amqp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-process transport used when the broker is unavailable. */
public final class LoopbackTransport implements MessageTransport {
    private final Map<String, List<Consumer<byte[]>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(String address, byte[] payload) {
        List<Consumer<byte[]>> handlers = subscribers.get(address);
        if (handlers == null) return;
        for (Consumer<byte[]> h : handlers) {
            h.accept(payload);
        }
    }

    @Override
    public AutoCloseable subscribe(String address, Consumer<byte[]> handler) {
        List<Consumer<byte[]>> list = subscribers.computeIfAbsent(address, k -> new CopyOnWriteArrayList<>());
        list.add(handler);
        return () -> list.remove(handler);
    }

    @Override
    public String describe() {
        return "loopback (in-process)";
    }

    @Override
    public void close() {
        subscribers.clear();
    }
}
