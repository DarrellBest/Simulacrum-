package com.simulacrum.overlay;

import java.util.List;

public final class MoveShapeCommand implements OverlayCommand {
    private final String shapeId;
    private final List<Shape.LatLon> before;
    private final List<Shape.LatLon> after;

    public MoveShapeCommand(String shapeId, List<Shape.LatLon> before, List<Shape.LatLon> after) {
        this.shapeId = shapeId;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    @Override
    public void apply(OverlayManager manager) {
        Shape s = manager.get(shapeId);
        if (s != null) manager.replace(s.copyWithPoints(after));
    }

    @Override
    public void undo(OverlayManager manager) {
        Shape s = manager.get(shapeId);
        if (s != null) manager.replace(s.copyWithPoints(before));
    }

    @Override
    public String description() {
        return "Move shape";
    }
}
