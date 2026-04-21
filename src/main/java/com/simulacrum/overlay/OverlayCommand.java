package com.simulacrum.overlay;

/** Undoable edit. {@code apply} and {@code undo} must be mutual inverses on {@link OverlayManager}. */
public interface OverlayCommand {
    void apply(OverlayManager manager);
    void undo(OverlayManager manager);
    String description();
}
