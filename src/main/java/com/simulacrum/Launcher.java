package com.simulacrum;

/**
 * Separate entry point so the JAR main class does NOT extend {@link javafx.application.Application}.
 * Without this, JavaFX 21's module check aborts launches from a non-modular shadow JAR with
 * "JavaFX runtime components are missing".
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
