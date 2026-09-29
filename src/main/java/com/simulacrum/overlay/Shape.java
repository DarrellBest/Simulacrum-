package com.simulacrum.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A drawn overlay, independent of WorldWind. GlobePane converts these to Renderables. */
public final class Shape {
    public enum Kind { POINT, LINE, ELLIPSE, RECTANGLE, POLYGON }

    public record LatLon(double latDeg, double lonDeg) {
    }

    private final String id;
    private final Kind kind;
    private final List<LatLon> points;
    private String label;
    private String groupId;

    public Shape(Kind kind, List<LatLon> points) {
        this(UUID.randomUUID().toString(), kind, points, null, null);
    }

    public Shape(String id, Kind kind, List<LatLon> points, String label, String groupId) {
        this.id = id;
        this.kind = kind;
        this.points = new ArrayList<>(points);
        this.label = label;
        this.groupId = groupId;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public List<LatLon> points() { return Collections.unmodifiableList(points); }
    public String label() { return label; }
    public String groupId() { return groupId; }

    public void setLabel(String label) { this.label = label; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public Shape copyWithPoints(List<LatLon> newPoints) {
        return new Shape(id, kind, newPoints, label, groupId);
    }
}
