package com.simulacrum.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShipModelTest {

    @Test
    void defaults_areReasonableNorfolkStart() {
        ShipModel m = new ShipModel();
        assertEquals("OWN-1", m.trackId.get());
        assertEquals(36.95, m.latitudeDeg.get(), 1e-6);
        assertEquals(-76.0, m.longitudeDeg.get(), 1e-6);
        assertEquals(45, m.headingDeg.get(), 1e-6);
        assertEquals(0, m.speedKn.get());
        assertEquals(0, m.throttle.get());
        assertEquals(0, m.rudderDeg.get());
        assertEquals(45, m.orderedHeadingDeg.get(), 1e-6);
        assertFalse(m.autopilot.get());
        assertFalse(m.anchored.get());
    }

    @Test
    void physicalLimitsAreSensibleForSurfaceShip() {
        assertEquals(30.0, ShipModel.MAX_SPEED_KN);
        assertEquals(35.0, ShipModel.MAX_RUDDER_DEG);
    }
}
