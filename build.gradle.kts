import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    application
    id("com.google.protobuf") version "0.9.4"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.simulacrum"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Repositories are declared centrally in settings.gradle.kts so the whole build
// resolves from the in-tree offline-repo/ directory with no network access.

javafx {
    version = "21.0.5"
    modules = listOf(
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.swing"
    )
}

val protobufVersion = "3.25.5"
val artemisVersion = "2.37.0"
val qpidJmsVersion = "2.6.1"
val junitVersion = "5.11.3"
val slf4jVersion = "2.0.16"
// WorldWind 2.0.0 was built against JOGL 2.2.x (javax.media.opengl namespace).
// JOGL 2.3.x moved that package, which breaks WorldWindowGLJPanel at link time.
val joglVersion = "2.2.4"

dependencies {
    // Protobuf (payload schemas)
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")

    // Embedded AMQP broker (Artemis) + AMQP 1.0 client (Qpid JMS)
    implementation("org.apache.activemq:artemis-server:$artemisVersion")
    implementation("org.apache.activemq:artemis-amqp-protocol:$artemisVersion")
    implementation("org.apache.qpid:qpid-jms-client:$qpidJmsVersion")
    implementation("jakarta.jms:jakarta.jms-api:3.1.0")

    // NASA WorldWind Java (Maven Central release) + JOGL natives for OpenGL
    implementation("gov.nasa:worldwind:2.0.0")
    implementation("org.jogamp.jogl:jogl-all:$joglVersion")
    implementation("org.jogamp.gluegen:gluegen-rt:$joglVersion")
    runtimeOnly("org.jogamp.jogl:jogl-all:$joglVersion:natives-linux-amd64")
    runtimeOnly("org.jogamp.gluegen:gluegen-rt:$joglVersion:natives-linux-amd64")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    // Test
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

application {
    mainClass.set("com.simulacrum.Launcher")
    applicationDefaultJvmArgs = listOf(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED"
    )
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
    systemProperty("java.awt.headless", "true")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.simulacrum.Launcher"
    }
}

tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    archiveBaseName.set("simulacrum")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}
