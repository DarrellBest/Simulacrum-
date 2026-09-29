package com.simulacrum.ship;

import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 20 Hz integrator: drives ShipModel from throttle/rudder/autopilot inputs. */
public final class ShipSimulator implements AutoCloseable {
    public static final double TICK_HZ = 20.0;
    public static final double DT = 1.0 / TICK_HZ;
    public static final double ACCEL_KN_PER_S = 1.5;
    public static final double MAX_TURN_RATE_DEG_S = 6.0;

    private final ShipModel ship;
    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ship-sim");
                t.setDaemon(true);
                return t;
            });

    public ShipSimulator(ShipModel ship) {
        this.ship = ship;
    }

    public void start() {
        exec.scheduleAtFixedRate(this::safeTick, 0, (long) (1000.0 / TICK_HZ), TimeUnit.MILLISECONDS);
    }

    private void safeTick() {
        try { tick(); } catch (Throwable ignored) { }
    }

    private void tick() {
        State in = State.snapshot(ship);
        State out = advance(in, DT);
        Platform.runLater(() -> {
            ship.latitudeDeg.set(out.latDeg);
            ship.longitudeDeg.set(out.lonDeg);
            ship.headingDeg.set(out.headingDeg);
            ship.speedKn.set(out.speedKn);
            if (in.autopilot) ship.rudderDeg.set(out.rudderDeg);
        });
    }

    /** One integration step. Pure function of the inputs. */
    public static State advance(State s, double dt) {
        double targetKn = s.anchored ? 0 : (s.throttle / 100.0) * ShipModel.MAX_SPEED_KN;
        double newKn = approach(s.speedKn, targetKn, ACCEL_KN_PER_S * dt);

        double rudder = s.rudderDeg;
        if (s.autopilot && !s.anchored) {
            double err = wrap180(s.orderedHeadingDeg - s.headingDeg);
            rudder = clamp(err * 2.0, -ShipModel.MAX_RUDDER_DEG, ShipModel.MAX_RUDDER_DEG);
        }

        double speedFactor = Math.abs(newKn) / ShipModel.MAX_SPEED_KN;
        double turnRate = (rudder / ShipModel.MAX_RUDDER_DEG) * speedFactor * MAX_TURN_RATE_DEG_S;
        if (newKn < 0) turnRate = -turnRate;
        double newHeading = wrap360(s.headingDeg + turnRate * dt);

        double speedMps = newKn * 0.514444;
        double distM = speedMps * dt;
        double rad = Math.toRadians(newHeading);
        double dLat = (distM * Math.cos(rad)) / 111111.0;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(s.latDeg)));
        double dLon = (distM * Math.sin(rad)) / (111111.0 * cosLat);
        double newLat = clamp(s.latDeg + dLat, -85, 85);
        double newLon = wrap180lon(s.lonDeg + dLon);

        return new State(newLat, newLon, newHeading, newKn, s.throttle, rudder,
                s.orderedHeadingDeg, s.autopilot, s.anchored);
    }

    public static final class State {
        public final double latDeg, lonDeg, headingDeg, speedKn;
        public final double throttle, rudderDeg, orderedHeadingDeg;
        public final boolean autopilot, anchored;

        public State(double latDeg, double lonDeg, double headingDeg, double speedKn,
                     double throttle, double rudderDeg, double orderedHeadingDeg,
                     boolean autopilot, boolean anchored) {
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
            this.headingDeg = headingDeg;
            this.speedKn = speedKn;
            this.throttle = throttle;
            this.rudderDeg = rudderDeg;
            this.orderedHeadingDeg = orderedHeadingDeg;
            this.autopilot = autopilot;
            this.anchored = anchored;
        }

        static State snapshot(ShipModel m) {
            return new State(m.latitudeDeg.get(), m.longitudeDeg.get(),
                    m.headingDeg.get(), m.speedKn.get(),
                    m.throttle.get(), m.rudderDeg.get(),
                    m.orderedHeadingDeg.get(),
                    m.autopilot.get(), m.anchored.get());
        }
    }

    private static double approach(double cur, double tgt, double step) {
        if (cur < tgt) return Math.min(tgt, cur + step);
        if (cur > tgt) return Math.max(tgt, cur - step);
        return cur;
    }

    private static double wrap360(double d) {
        d = d % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    private static double wrap180(double d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private static double wrap180lon(double d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}
