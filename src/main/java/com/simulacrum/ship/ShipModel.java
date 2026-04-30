package com.simulacrum.ship;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class ShipModel {
    public final StringProperty trackId = new SimpleStringProperty("OWN-1");
    public final DoubleProperty latitudeDeg = new SimpleDoubleProperty(36.95);
    public final DoubleProperty longitudeDeg = new SimpleDoubleProperty(-76.0);
    public final DoubleProperty headingDeg = new SimpleDoubleProperty(45);
    public final DoubleProperty speedKn = new SimpleDoubleProperty(0);
    public final DoubleProperty throttle = new SimpleDoubleProperty(0);
    public final DoubleProperty rudderDeg = new SimpleDoubleProperty(0);
    public final DoubleProperty orderedHeadingDeg = new SimpleDoubleProperty(45);
    public final BooleanProperty autopilot = new SimpleBooleanProperty(false);
    public final BooleanProperty anchored = new SimpleBooleanProperty(false);

    public static final double MAX_SPEED_KN = 30.0;
    public static final double MAX_RUDDER_DEG = 35.0;
}
