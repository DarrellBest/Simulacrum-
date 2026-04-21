package com.simulacrum.overlay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KmlExporterTest {

    @Test
    void writesKmlWrapperAndShapes() {
        Shape point = new Shape(Shape.Kind.POINT, List.of(new Shape.LatLon(37.78, -122.42)));
        Shape line = new Shape(Shape.Kind.LINE, List.of(
                new Shape.LatLon(0, 0), new Shape.LatLon(1, 1)));
        Shape poly = new Shape(Shape.Kind.POLYGON, List.of(
                new Shape.LatLon(0, 0), new Shape.LatLon(0, 1),
                new Shape.LatLon(1, 1), new Shape.LatLon(1, 0)));

        String kml = KmlExporter.toKml(List.of(point, line, poly));
        assertTrue(kml.contains("<kml"));
        assertTrue(kml.contains("<Point>"));
        assertTrue(kml.contains("<LineString>"));
        assertTrue(kml.contains("<Polygon>"));
        assertTrue(kml.contains("-122.42,37.78"));
    }
}
