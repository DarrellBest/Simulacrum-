package com.simulacrum.data;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.function.Consumer;

/** Worked example of a {@link DataSource}: reads NMEA 0183 sentences from any {@link Reader}. */
public final class NmeaDataSource implements DataSource {
    private final String name;
    private final Reader reader;
    private Thread worker;
    private volatile boolean running;

    public NmeaDataSource(String name, Reader reader) {
        this.name = name;
        this.reader = reader;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void start(Consumer<Fix> consumer) {
        running = true;
        worker = new Thread(() -> drain(consumer), "nmea-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    private void drain(Consumer<Fix> consumer) {
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while (running && (line = br.readLine()) != null) {
                Fix fix = NmeaParser.parse(line);
                if (fix != null) consumer.accept(fix);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
