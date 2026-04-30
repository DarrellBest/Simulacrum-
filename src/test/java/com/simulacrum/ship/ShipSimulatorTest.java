package com.simulacrum.ship;

import com.simulacrum.ship.ShipSimulator.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSimulatorTest {

    private static State stationary() {
        return new State(36.95, -76.0, 45, 0, 0, 0, 45, false, false);
    }

    @Test
    void stationaryShipDoesNotMove() {
        State s = stationary();
        State after = ShipSimulator.advance(s, 1.0);
        assertEquals(s.latDeg, after.latDeg, 1e-9);
        assertEquals(s.lonDeg, after.lonDeg, 1e-9);
        assertEquals(0, after.speedKn, 1e-9);
    }

    @Test
    void throttleAcceleratesShipTowardCommandedSpeed() {
        State s = new State(0, 0, 90, 0, 100, 0, 90, false, false);
        for (int i = 0; i < 1200; i++) s = ShipSimulator.advance(s, 0.05);
        assertEquals(30.0, s.speedKn, 0.5,
                "throttle=100 should approach MAX_SPEED_KN within 60s");
    }

    @Test
    void shipMovesEastOnHeading090() {
        State s = new State(0, 0, 90, 30, 100, 0, 90, false, false);
        State after = ShipSimulator.advance(s, 1.0);
        assertTrue(after.lonDeg > s.lonDeg, "moving east should increase longitude");
        assertEquals(0, after.latDeg, 1e-3, "lat should not drift on heading 090");
    }

    @Test
    void shipMovesNorthOnHeading000() {
        State s = new State(0, 0, 0, 30, 100, 0, 0, false, false);
        State after = ShipSimulator.advance(s, 1.0);
        assertTrue(after.latDeg > s.latDeg, "heading 000 increases latitude");
        assertEquals(0, after.lonDeg, 1e-3);
    }

    @Test
    void rudderTurnsTheShipWhenMoving() {
        State s = new State(0, 0, 0, 30, 100, ShipModel.MAX_RUDDER_DEG, 0, false, false);
        State after = ShipSimulator.advance(s, 1.0);
        assertNotEquals(0, after.headingDeg, "full rudder at full speed should turn");
        assertTrue(after.headingDeg > 0 && after.headingDeg < 90,
                "starboard rudder yields heading drift to right (0..90)");
    }

    @Test
    void rudderHasNoEffectAtZeroSpeed() {
        State s = new State(0, 0, 45, 0, 0, ShipModel.MAX_RUDDER_DEG, 45, false, false);
        State after = ShipSimulator.advance(s, 1.0);
        assertEquals(45, after.headingDeg, 1e-9);
    }

    @Test
    void anchoredShipDecelerates() {
        State s = new State(0, 0, 0, 20, 100, 0, 0, false, true);
        for (int i = 0; i < 600; i++) s = ShipSimulator.advance(s, 0.05);
        assertEquals(0, s.speedKn, 0.5, "anchored should drive speed to 0");
    }

    @Test
    void autopilotDrivesHeadingTowardOrdered() {
        State s = new State(0, 0, 0, 20, 100, 0, 90, true, false);
        for (int i = 0; i < 1200; i++) s = ShipSimulator.advance(s, 0.05);
        assertEquals(90, s.headingDeg, 2.0, "autopilot should converge to ordered heading");
    }

    @Test
    void asternThrottleProducesNegativeSpeed() {
        State s = new State(0, 0, 0, 0, -50, 0, 0, false, false);
        for (int i = 0; i < 400; i++) s = ShipSimulator.advance(s, 0.05);
        assertTrue(s.speedKn < -10, "back 1/2 should reach negative speed");
    }
}
