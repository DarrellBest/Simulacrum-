pluginManagement {
    repositories {
        maven { url = uri(file("offline-repo").toURI()) }
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
        maven { url = uri(file("offline-repo").toURI()) }
    }
}

rootProject.name = "simulacrum"
