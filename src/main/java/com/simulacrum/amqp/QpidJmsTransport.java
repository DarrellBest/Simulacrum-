package com.simulacrum.amqp;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.apache.qpid.jms.JmsConnectionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** AMQP 1.0 transport backed by a Qpid JMS client. Targets the embedded Artemis broker. */
public final class QpidJmsTransport implements MessageTransport {
    private final AmqpConfig config;
    private final Connection connection;
    private final Session session;
    private final Map<String, MessageProducer> producers = new ConcurrentHashMap<>();

    public QpidJmsTransport(AmqpConfig config) throws Exception {
        this.config = config;
        ConnectionFactory factory = new JmsConnectionFactory(config.uri());
        this.connection = factory.createConnection(config.username(), config.password());
        this.connection.start();
        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public void publish(String address, byte[] payload) throws Exception {
        MessageProducer producer = producers.computeIfAbsent(address, addr -> {
            try {
                Destination destination = session.createTopic(addr);
                return session.createProducer(destination);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        BytesMessage msg = session.createBytesMessage();
        msg.writeBytes(payload);
        producer.send(msg);
    }

    @Override
    public AutoCloseable subscribe(String address, Consumer<byte[]> handler) throws Exception {
        Destination destination = session.createTopic(address);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(message -> {
            try {
                handler.accept(extractBytes(message));
            } catch (Exception ignored) {
            }
        });
        return consumer::close;
    }

    private static byte[] extractBytes(Message message) throws Exception {
        if (message instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            return buf;
        }
        return new byte[0];
    }

    @Override
    public String describe() {
        return "AMQP 1.0 @ " + config.uri();
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }
}
