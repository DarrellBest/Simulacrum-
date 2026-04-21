package com.simulacrum.overlay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayManagerTest {

    private static Shape somePolygon() {
        return new Shape(Shape.Kind.POLYGON, List.of(
                new Shape.LatLon(0, 0),
                new Shape.LatLon(0, 1),
                new Shape.LatLon(1, 1),
                new Shape.LatLon(1, 0)));
    }

    @Test
    void addAndUndoRemoveSymmetry() {
        OverlayManager mgr = new OverlayManager();
        UndoStack undo = new UndoStack();
        Shape shape = somePolygon();

        undo.push(new AddShapeCommand(shape), mgr);
        assertEquals(1, mgr.shapes().size());
        assertNotNull(mgr.get(shape.id()));

        undo.undo(mgr);
        assertEquals(0, mgr.shapes().size());

        undo.redo(mgr);
        assertEquals(1, mgr.shapes().size());
    }

    @Test
    void removeThenUndoRestores() {
        OverlayManager mgr = new OverlayManager();
        UndoStack undo = new UndoStack();
        Shape shape = somePolygon();
        mgr.add(shape);

        undo.push(new RemoveShapeCommand(shape), mgr);
        assertFalse(mgr.shapes().contains(shape));

        undo.undo(mgr);
        assertTrue(mgr.shapes().contains(shape));
    }

    @Test
    void moveUpdatesPoints() {
        OverlayManager mgr = new OverlayManager();
        UndoStack undo = new UndoStack();
        Shape shape = somePolygon();
        mgr.add(shape);

        List<Shape.LatLon> after = List.of(
                new Shape.LatLon(10, 10),
                new Shape.LatLon(10, 11),
                new Shape.LatLon(11, 11),
                new Shape.LatLon(11, 10));

        undo.push(new MoveShapeCommand(shape.id(), shape.points(), after), mgr);
        assertEquals(10.0, mgr.get(shape.id()).points().get(0).latDeg(), 1e-9);

        undo.undo(mgr);
        assertEquals(0.0, mgr.get(shape.id()).points().get(0).latDeg(), 1e-9);
    }

    @Test
    void groupAssignsSharedId() {
        OverlayManager mgr = new OverlayManager();
        Shape a = somePolygon();
        Shape b = somePolygon();
        mgr.add(a);
        mgr.add(b);
        String gid = mgr.groupOf(List.of(a, b));
        assertEquals(gid, a.groupId());
        assertEquals(gid, b.groupId());
    }
}
