package com.simulacrum.data;

import java.util.function.Consumer;

/** A sensor feed. {@link NmeaDataSource} is the only implementation. */
public interface DataSource extends AutoCloseable {
    /** Human-readable feed name, used in the UI. */
    String name();

    /** Start pushing fixes to {@code consumer}. Non-blocking. */
    void start(Consumer<Fix> consumer);

    @Override
    void close();

    /** A single positional fix. Altitude/speed/heading are optional ({@code NaN} if absent). */
    record Fix(
            double latitudeDeg,
            double longitudeDeg,
            double altitudeM,
            double speedMps,
            double headingDeg,
            long timestampMs
    ) {
    }
}
