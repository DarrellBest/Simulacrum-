package com.simulacrum.amqp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackTransportTest {

    @Test
    void publishDeliversToSubscriber() throws Exception {
        try (LoopbackTransport tx = new LoopbackTransport()) {
            AtomicReference<byte[]> received = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            try (AutoCloseable sub = tx.subscribe("addr", bytes -> {
                received.set(bytes);
                latch.countDown();
            })) {
                tx.publish("addr", new byte[]{1, 2, 3});
                assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
                assertArrayEquals(new byte[]{1, 2, 3}, received.get());
            }
        }
    }

    @Test
    void unsubscribeStopsDelivery() throws Exception {
        try (LoopbackTransport tx = new LoopbackTransport()) {
            int[] count = new int[1];
            AutoCloseable sub = tx.subscribe("addr", bytes -> count[0]++);
            tx.publish("addr", new byte[]{1});
            sub.close();
            tx.publish("addr", new byte[]{2});
            assertTrue(count[0] == 1, "Only the first publish should have been delivered");
        }
    }
}
