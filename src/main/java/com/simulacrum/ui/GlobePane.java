package com.simulacrum.ui;

import com.simulacrum.overlay.OverlayManager;
import com.simulacrum.overlay.Shape;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLJPanel;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.Polyline;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfacePolygon;
import javafx.embed.swing.SwingNode;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hosts a NASA WorldWind {@link WorldWindow} inside a JavaFX {@link SwingNode}, and syncs the
 * {@link OverlayManager} model into a {@link RenderableLayer}. Construction happens on the FX
 * thread, but WorldWind mutation happens on the AWT EDT via {@link SwingUtilities}.
 */
public final class GlobePane {
    private final SwingNode swingNode = new SwingNode();
    private final RenderableLayer overlayLayer = new RenderableLayer();
    private final Map<String, Renderable> renderables = new HashMap<>();
    private WorldWindowGLJPanel wwd;
    private final OverlayManager manager;

    public GlobePane(OverlayManager manager) {
        this.manager = manager;
        manager.addListener(this::onOverlayEvent);
    }

    /** Build the Swing WorldWindow. Must be invoked after FX is initialized. */
    public void initialize() {
        SwingUtilities.invokeLater(() -> {
            wwd = new WorldWindowGLJPanel();
            wwd.setPreferredSize(new java.awt.Dimension(800, 600));
            BasicModel model = new BasicModel();
            model.getLayers().add(overlayLayer);
            wwd.setModel(model);
            swingNode.setContent(wwd);
        });
    }

    public SwingNode node() {
        return swingNode;
    }

    public void dropTrackMarker(double lat, double lon, String label) {
        SwingUtilities.invokeLater(() -> {
            PointPlacemark placemark = new PointPlacemark(Position.fromDegrees(lat, lon, 0));
            placemark.setLabelText(label);
            placemark.setAttributes(new gov.nasa.worldwind.render.PointPlacemarkAttributes());
            placemark.setAltitudeMode(gov.nasa.worldwind.WorldWind.CLAMP_TO_GROUND);
            overlayLayer.addRenderable(placemark);
            if (wwd != null) wwd.redraw();
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
            if (wwd != null) wwd.redraw();
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

    public WorldWindow worldWindow() {
        return wwd;
    }
}
