pluginManagement {
    repositories {
        maven { url = uri(file("offline-repo").toURI()) }
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.google.protobuf" ->
                    useModule("com.google.protobuf:protobuf-gradle-plugin:${requested.version}")
                "org.openjfx.javafxplugin" ->
                    useModule("org.openjfx:javafx-plugin:${requested.version}")
                "com.gradleup.shadow" ->
                    useModule("com.gradleup.shadow:shadow-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Vendored artifacts first (Linux-classifier JavaFX/JOGL, all plugins,
        // every platform-neutral jar). Maven Central is the fallback for anything
        // not vendored, e.g. the Windows/macOS JavaFX and JOGL native jars.
        maven { url = uri(file("offline-repo").toURI()) }
        mavenCentral()
    }
}

rootProject.name = "simulacrum"
