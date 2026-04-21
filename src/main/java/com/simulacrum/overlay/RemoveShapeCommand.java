package com.simulacrum.overlay;

public final class RemoveShapeCommand implements OverlayCommand {
    private final Shape shape;

    public RemoveShapeCommand(Shape shape) {
        this.shape = shape;
    }

    @Override
    public void apply(OverlayManager manager) {
        manager.remove(shape.id());
    }

    @Override
    public void undo(OverlayManager manager) {
        manager.add(shape);
    }

    @Override
    public String description() {
        return "Remove " + shape.kind();
    }
}
