package com.simulacrum.ui;

import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.Shape;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.awt.WorldWindowGLJPanel;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.Polyline;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfacePolygon;
import javafx.animation.AnimationTimer;
import javafx.embed.swing.SwingNode;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** WorldWindow embedded in a JavaFX SwingNode, with own-ship marker, trail, and overlay sync. */
public final class GlobePane {
    private static final int TRAIL_MAX_POINTS = 600;
    private static final double MIN_EYE_ALTITUDE_M = 1500;

    private final SwingNode swingNode = new SwingNode();
    private final RenderableLayer overlayLayer = new RenderableLayer();
    private final RenderableLayer shipLayer = new RenderableLayer();
    private final Map<String, Renderable> renderables = new HashMap<>();
    private final Deque<Position> trailPoints = new ArrayDeque<>();
    private WorldWindowGLJPanel wwd;
    private boolean cameraInitialized;
    private double lastCenterLat = Double.NaN;
    private double lastCenterLon = Double.NaN;
    private PointPlacemark ownShip;
    private Polyline ownShipTrail;
    private final OverlayManager manager;
    private boolean trackOwnShip = true;
    private volatile boolean userDragging;

    public GlobePane(OverlayManager manager) {
        this.manager = manager;
        manager.addListener(this::onOverlayEvent);
    }

    public void initialize() {
        SwingUtilities.invokeLater(() -> {
            wwd = new WorldWindowGLJPanel();
            wwd.setPreferredSize(new java.awt.Dimension(800, 600));
            BasicModel model = new BasicModel();
            model.getLayers().add(overlayLayer);
            model.getLayers().add(shipLayer);
            wwd.setModel(model);
            wwd.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { userDragging = true; }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    userDragging = false;
                    lastCenterLat = Double.NaN;  // force one-shot recenter on next tick
                }
            });
            swingNode.setContent(wwd);
        });

        // Continuous redraw: works around the SwingNode initial-paint bug and animates the ship.
        AnimationTimer ticker = new AnimationTimer() {
            @Override public void handle(long now) {
                if (wwd == null) return;
                SwingUtilities.invokeLater(() -> {
                    View view = wwd.getView();
                    if (view != null) {
                        Position eye = view.getEyePosition();
                        if (eye != null && eye.getElevation() < MIN_EYE_ALTITUDE_M) {
                            view.setEyePosition(new Position(
                                    LatLon.fromDegrees(eye.getLatitude().degrees,
                                            eye.getLongitude().degrees),
                                    MIN_EYE_ALTITUDE_M));
                        }
                    }
                    wwd.redraw();
                });
            }
        };
        ticker.start();

        swingNode.boundsInLocalProperty().addListener((obs, a, b) -> {
            if (wwd != null) SwingUtilities.invokeLater(wwd::redraw);
        });
    }

    public SwingNode node() { return swingNode; }

    public void setTrackOwnShip(boolean track) { this.trackOwnShip = track; }

    public void updateOwnShip(double lat, double lon, double headingDeg, double speedKn) {
        SwingUtilities.invokeLater(() -> {
            if (wwd == null) return;
            Position pos = Position.fromDegrees(lat, lon, 0);
            if (ownShip == null) {
                ownShip = new PointPlacemark(pos);
                PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
                attrs.setImageColor(new Color(0xff, 0xa6, 0x57));
                attrs.setScale(0.8);
                attrs.setLabelMaterial(new Material(new Color(0xff, 0xa6, 0x57)));
                ownShip.setAttributes(attrs);
                ownShip.setAltitudeMode(gov.nasa.worldwind.WorldWind.CLAMP_TO_GROUND);
                shipLayer.addRenderable(ownShip);
            }
            ownShip.setPosition(pos);
            ownShip.setLabelText(String.format("OWN  hdg %03.0f  %.1f kn", headingDeg, speedKn));

            Position last = trailPoints.peekLast();
            boolean moved = last == null
                    || Math.abs(last.getLatitude().degrees - lat) > 1e-6
                    || Math.abs(last.getLongitude().degrees - lon) > 1e-6;
            if (moved) {
                trailPoints.addLast(pos);
                while (trailPoints.size() > TRAIL_MAX_POINTS) trailPoints.removeFirst();
                if (ownShipTrail != null) shipLayer.removeRenderable(ownShipTrail);
                if (trailPoints.size() >= 2) {
                    ownShipTrail = new Polyline(new ArrayList<>(trailPoints));
                    ownShipTrail.setFollowTerrain(true);
                    ownShipTrail.setColor(new Color(0xff, 0xa6, 0x57, 0xa0));
                    ownShipTrail.setLineWidth(2);
                    shipLayer.addRenderable(ownShipTrail);
                }
            }

            if (wwd.getView() != null) {
                boolean shipMoved = Double.isNaN(lastCenterLat)
                        || Math.abs(lat - lastCenterLat) > 1e-5
                        || Math.abs(lon - lastCenterLon) > 1e-5;
                boolean shouldCenter = !cameraInitialized
                        || (trackOwnShip && !userDragging && shipMoved);
                if (shouldCenter) {
                    double altitude = cameraInitialized
                            ? wwd.getView().getEyePosition().getElevation()
                            : 5_000_000;
                    wwd.getView().setEyePosition(new Position(LatLon.fromDegrees(lat, lon), altitude));
                    lastCenterLat = lat;
                    lastCenterLon = lon;
                    cameraInitialized = true;
                }
            }
        });
    }

    public void dropTrackMarker(double lat, double lon, String label) {
        SwingUtilities.invokeLater(() -> {
            if (wwd == null) return;
            PointPlacemark placemark = new PointPlacemark(Position.fromDegrees(lat, lon, 0));
            placemark.setLabelText(label);
            placemark.setAttributes(new PointPlacemarkAttributes());
            placemark.setAltitudeMode(gov.nasa.worldwind.WorldWind.CLAMP_TO_GROUND);
            overlayLayer.addRenderable(placemark);
        });
    }

    private void onOverlayEvent(OverlayManager.Event event) {
        SwingUtilities.invokeLater(() -> {
            switch (event.kind()) {
                case ADDED -> {
                    Renderable r = toRenderable(event.shape());
                    if (r != null) {
                        renderables.put(event.shape().id(), r);
                        overlayLayer.addRenderable(r);
                    }
                }
                case REMOVED -> {
                    Renderable r = renderables.remove(event.shape().id());
                    if (r != null) overlayLayer.removeRenderable(r);
                }
                case UPDATED -> {
                    Renderable old = renderables.remove(event.shape().id());
                    if (old != null) overlayLayer.removeRenderable(old);
                    Renderable r = toRenderable(event.shape());
                    if (r != null) {
                        renderables.put(event.shape().id(), r);
                        overlayLayer.addRenderable(r);
                    }
                }
            }
        });
    }

    private Renderable toRenderable(Shape shape) {
        List<LatLon> pts = toLatLons(shape.points());
        return switch (shape.kind()) {
            case POINT -> pts.isEmpty() ? null : makePoint(pts.get(0), shape.label());
            case LINE -> makeLine(pts);
            case POLYGON, RECTANGLE, ELLIPSE -> makePolygon(pts);
        };
    }

    private static List<LatLon> toLatLons(List<Shape.LatLon> pts) {
        List<LatLon> out = new ArrayList<>(pts.size());
        for (Shape.LatLon p : pts) out.add(LatLon.fromDegrees(p.latDeg(), p.lonDeg()));
        return out;
    }

    private static PointPlacemark makePoint(LatLon ll, String label) {
        PointPlacemark p = new PointPlacemark(new Position(ll, 0));
        if (label != null) p.setLabelText(label);
        p.setAltitudeMode(gov.nasa.worldwind.WorldWind.CLAMP_TO_GROUND);
        return p;
    }

    private static Polyline makeLine(List<LatLon> pts) {
        Polyline line = new Polyline();
        line.setPositions(toPositions(pts, 0));
        line.setFollowTerrain(true);
        line.setColor(new Color(0x58, 0xa6, 0xff));
        line.setLineWidth(2);
        return line;
    }

    private static SurfacePolygon makePolygon(List<LatLon> pts) {
        SurfacePolygon poly = new SurfacePolygon(pts);
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(new Color(0x7e, 0xe7, 0x87)));
        attrs.setInteriorMaterial(new Material(new Color(0x7e, 0xe7, 0x87)));
        attrs.setInteriorOpacity(0.2);
        attrs.setOutlineWidth(2);
        poly.setAttributes(attrs);
        return poly;
    }

    private static Iterable<Position> toPositions(List<LatLon> pts, double alt) {
        List<Position> positions = new ArrayList<>(pts.size());
        for (LatLon ll : pts) positions.add(new Position(ll, alt));
        return positions;
    }

    public WorldWindow worldWindow() { return wwd; }
}
