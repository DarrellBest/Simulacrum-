package com.simulacrum.amqp;

import java.util.function.Consumer;

public interface MessageTransport extends AutoCloseable {
    void publish(String address, byte[] payload) throws Exception;

    AutoCloseable subscribe(String address, Consumer<byte[]> handler) throws Exception;

    String describe();

    @Override
    void close();
}
