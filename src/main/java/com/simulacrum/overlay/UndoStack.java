package com.simulacrum.overlay;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UndoStack {
    private final Deque<OverlayCommand> undo = new ArrayDeque<>();
    private final Deque<OverlayCommand> redo = new ArrayDeque<>();

    public void push(OverlayCommand cmd, OverlayManager manager) {
        cmd.apply(manager);
        undo.push(cmd);
        redo.clear();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void undo(OverlayManager manager) {
        if (undo.isEmpty()) return;
        OverlayCommand cmd = undo.pop();
        cmd.undo(manager);
        redo.push(cmd);
    }

    public void redo(OverlayManager manager) {
        if (redo.isEmpty()) return;
        OverlayCommand cmd = redo.pop();
        cmd.apply(manager);
        undo.push(cmd);
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
