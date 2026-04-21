package com.simulacrum.amqp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Drives a repeated publish at a configurable rate (Hz). Supports single-shot and burst. */
public final class RateController implements AutoCloseable {
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "simulacrum-rate");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> future;

    public synchronized void start(double hz, Runnable emit) {
        stop();
        long periodNanos = Math.max(1, (long) (1_000_000_000.0 / hz));
        future = scheduler.scheduleAtFixedRate(emit, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    public synchronized void burst(int count, Runnable emit) {
        for (int i = 0; i < count; i++) {
            emit.run();
        }
    }

    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public synchronized boolean isRunning() {
        return future != null && !future.isCancelled();
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
