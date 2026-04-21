package com.simulacrum.amqp;

public record AmqpConfig(
        String host,
        int port,
        String username,
        String password,
        String address,
        boolean useEmbeddedBroker
) {
    public static AmqpConfig defaults() {
        return new AmqpConfig("localhost", 5672, "guest", "guest", "simulacrum.tracks", true);
    }

    public String uri() {
        return "amqp://" + host + ":" + port;
    }
}
