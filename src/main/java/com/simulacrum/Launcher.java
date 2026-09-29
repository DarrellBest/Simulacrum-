package com.simulacrum;

/** Entry point that does not extend Application, so the non-modular shadow jar launches on JavaFX 21. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
