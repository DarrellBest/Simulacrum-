package com.simulacrum.overlay;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Minimal OGC KML 2.2 writer that covers the shape kinds Simulacrum produces. WorldWind bundles
 * a richer {@code KMLDocumentBuilder}, but using our own keeps the unit tests free of WorldWind's
 * Swing/JOGL initialization and makes KML a stable wire format regardless of WorldWind version.
 */
public final class KmlExporter {
    private KmlExporter() {
    }

    public static String toKml(Collection<Shape> shapes) {
        StringWriter sw = new StringWriter();
        try {
            write(shapes, sw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

    public static void export(Collection<Shape> shapes, Path target) throws IOException {
        Files.writeString(target, toKml(shapes), StandardCharsets.UTF_8);
    }

    private static void write(Collection<Shape> shapes, Writer w) throws IOException {
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        w.write("  <Document>\n");
        w.write("    <name>Simulacrum Export</name>\n");
        for (Shape s : shapes) writePlacemark(s, w);
        w.write("  </Document>\n");
        w.write("</kml>\n");
    }

    private static void writePlacemark(Shape s, Writer w) throws IOException {
        w.write("    <Placemark>\n");
        if (s.label() != null) {
            w.write("      <name>");
            w.write(xmlEscape(s.label()));
            w.write("</name>\n");
        }
        w.write("      <description>");
        w.write(s.kind().name());
        if (s.groupId() != null) {
            w.write(" (group ");
            w.write(s.groupId());
            w.write(")");
        }
        w.write("</description>\n");
        switch (s.kind()) {
            case POINT -> writePoint(s.points(), w);
            case LINE -> writeLineString(s.points(), w);
            case POLYGON, RECTANGLE, ELLIPSE -> writePolygon(s.points(), w);
        }
        w.write("    </Placemark>\n");
    }

    private static void writePoint(List<Shape.LatLon> pts, Writer w) throws IOException {
        if (pts.isEmpty()) return;
        Shape.LatLon p = pts.get(0);
        w.write("      <Point><coordinates>");
        w.write(coord(p));
        w.write("</coordinates></Point>\n");
    }

    private static void writeLineString(List<Shape.LatLon> pts, Writer w) throws IOException {
        w.write("      <LineString><coordinates>");
        for (Shape.LatLon p : pts) {
            w.write(coord(p));
            w.write(' ');
        }
        w.write("</coordinates></LineString>\n");
    }

    private static void writePolygon(List<Shape.LatLon> pts, Writer w) throws IOException {
        w.write("      <Polygon><outerBoundaryIs><LinearRing><coordinates>");
        for (Shape.LatLon p : pts) {
            w.write(coord(p));
            w.write(' ');
        }
        if (!pts.isEmpty()) {
            w.write(coord(pts.get(0)));
        }
        w.write("</coordinates></LinearRing></outerBoundaryIs></Polygon>\n");
    }

    private static String coord(Shape.LatLon p) {
        return p.lonDeg() + "," + p.latDeg() + ",0";
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
