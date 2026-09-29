package com.simulacrum.overlay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Current set of {@link Shape}s. Mutate through {@link UndoStack}. */
public final class OverlayManager {
    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public enum EventKind { ADDED, REMOVED, UPDATED }
    public record Event(EventKind kind, Shape shape) {}

    public void addListener(Consumer<Event> listener) { listeners.add(listener); }
    public void removeListener(Consumer<Event> listener) { listeners.remove(listener); }

    public Collection<Shape> shapes() { return Collections.unmodifiableCollection(shapes.values()); }
    public Shape get(String id) { return shapes.get(id); }

    void add(Shape shape) {
        shapes.put(shape.id(), shape);
        fire(new Event(EventKind.ADDED, shape));
    }

    void remove(String id) {
        Shape s = shapes.remove(id);
        if (s != null) fire(new Event(EventKind.REMOVED, s));
    }

    void replace(Shape shape) {
        shapes.put(shape.id(), shape);
        fire(new Event(EventKind.UPDATED, shape));
    }

    private void fire(Event e) {
        for (Consumer<Event> l : listeners) l.accept(e);
    }

    /** Assign all given shapes to a new group id, returning the id. */
    public String groupOf(List<Shape> members) {
        String groupId = UUID.randomUUID().toString();
        for (Shape s : members) s.setGroupId(groupId);
        return groupId;
    }
}
