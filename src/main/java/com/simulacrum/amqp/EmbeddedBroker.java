package com.simulacrum.amqp;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import java.nio.file.Files;
import java.nio.file.Path;

/** Embedded ActiveMQ Artemis broker speaking AMQP 1.0, used when {@link AmqpConfig#useEmbeddedBroker()} is true. */
public final class EmbeddedBroker implements AutoCloseable {
    private final EmbeddedActiveMQ server = new EmbeddedActiveMQ();
    private final AmqpConfig config;

    public EmbeddedBroker(AmqpConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        Path dataDir = Files.createTempDirectory("simulacrum-broker");
        Configuration cfg = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .setJournalDirectory(dataDir.resolve("journal").toString())
                .setBindingsDirectory(dataDir.resolve("bindings").toString())
                .setLargeMessagesDirectory(dataDir.resolve("large").toString())
                .setPagingDirectory(dataDir.resolve("paging").toString())
                .addAcceptorConfiguration("amqp",
                        "tcp://" + config.host() + ":" + config.port()
                                + "?protocols=AMQP;useEpoll=false");
        server.setConfiguration(cfg);
        server.start();
    }

    @Override
    public void close() {
        try {
            server.stop();
        } catch (Exception ignored) {
        }
    }
}
