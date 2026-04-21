package com.simulacrum.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Returns a real AMQP transport if one is reachable; otherwise a {@link LoopbackTransport}. */
public final class TransportFactory {
    private static final Logger log = LoggerFactory.getLogger(TransportFactory.class);

    private TransportFactory() {
    }

    public static MessageTransport open(AmqpConfig config, EmbeddedBroker broker) {
        try {
            if (broker != null && config.useEmbeddedBroker()) {
                broker.start();
            }
            return new QpidJmsTransport(config);
        } catch (Exception e) {
            log.warn("AMQP unavailable ({}); falling back to loopback", e.getMessage());
            return new LoopbackTransport();
        }
    }
}
