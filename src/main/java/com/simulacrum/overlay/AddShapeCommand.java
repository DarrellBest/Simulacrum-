package com.simulacrum.overlay;

public final class AddShapeCommand implements OverlayCommand {
    private final Shape shape;

    public AddShapeCommand(Shape shape) {
        this.shape = shape;
    }

    @Override
    public void apply(OverlayManager manager) {
        manager.add(shape);
    }

    @Override
    public void undo(OverlayManager manager) {
        manager.remove(shape.id());
    }

    @Override
    public String description() {
        return "Add " + shape.kind();
    }
}
