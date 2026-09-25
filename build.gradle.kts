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

repositories {
    mavenCentral()
}

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
    runtimeOnly("org.jogamp.jogl:jogl-all:$joglVersion:natives-windows-amd64")
    runtimeOnly("org.jogamp.gluegen:gluegen-rt:$joglVersion:natives-windows-amd64")
    runtimeOnly("org.jogamp.jogl:jogl-all:$joglVersion:natives-macosx-universal")
    runtimeOnly("org.jogamp.gluegen:gluegen-rt:$joglVersion:natives-macosx-universal")

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
    useJUnitPlatform {
        excludeTags("integration", "gui")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
    systemProperty("java.awt.headless", "true")
}

tasks.register<Test>("integrationTest") {
    description = "Black-box tests that spawn the built shadow jar and drive it via HTTP."
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    dependsOn("shadowJar")
    shouldRunAfter("test")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
}

val artusLibDir = "C:/Users/dbest/PycharmProjects/artuscmd/extracted_13.2.0/ArtusCmd/lib"
val debloatedJar = layout.buildDirectory.file("libs/simulacrum-all-debloated.jar")

tasks.register<JavaExec>("debloat") {
    description = "Runs ArtusCmd 13.2.0 to debloat the shadow jar (aggressiveness=keeppublic)."
    group = "build"
    dependsOn("shadowJar")
    val inputJar = layout.buildDirectory.file("libs/simulacrum-all.jar").get().asFile
    val outputJar = debloatedJar.get().asFile
    inputs.file(inputJar)
    outputs.file(outputJar)
    classpath = files("$artusLibDir").asFileTree.matching { include("*.jar") }
    mainClass.set("com.pjrcorp.artus.cmd.ArtusCmdMain")
    args = listOf(
            "process",
            "-a", "keeppublic",
            "-jv", "21",
            "-fo",
            "-o", outputJar.relativeTo(projectDir).path.replace('\\', '/'),
            inputJar.relativeTo(projectDir).path.replace('\\', '/')
    )
}

tasks.register<JavaExec>("debloatMax") {
    description = "Runs ArtusCmd with aggressiveness=max (more cutting, more risk)."
    group = "build"
    dependsOn("shadowJar")
    val inputJar = layout.buildDirectory.file("libs/simulacrum-all.jar").get().asFile
    val outputJar = layout.buildDirectory.file("libs/simulacrum-all-debloated-max.jar").get().asFile
    inputs.file(inputJar)
    outputs.file(outputJar)
    classpath = files("$artusLibDir").asFileTree.matching { include("*.jar") }
    mainClass.set("com.pjrcorp.artus.cmd.ArtusCmdMain")
    args = listOf(
            "process",
            "-a", "max",
            "-jv", "21",
            "-fo",
            "-o", outputJar.relativeTo(projectDir).path.replace('\\', '/'),
            inputJar.relativeTo(projectDir).path.replace('\\', '/')
    )
}

tasks.register<Test>("guiTest") {
    description = "Drives the live JavaFX UI of the built shadow jar with java.awt.Robot."
    group = "verification"
    dependsOn("shadowJar")
    useJUnitPlatform { includeTags("gui") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
}

tasks.register<Test>("guiTestDebloated") {
    description = "Same Robot-driven GUI tests, but against the debloated jar."
    group = "verification"
    dependsOn("debloat")
    useJUnitPlatform { includeTags("gui") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("simulacrum.jar", debloatedJar.get().asFile.absolutePath)
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
}

tasks.register<Test>("integrationTestDebloated") {
    description = "Runs the integration suite against the debloated jar."
    group = "verification"
    dependsOn("debloat")
    useJUnitPlatform { includeTags("integration") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("simulacrum.jar", debloatedJar.get().asFile.absolutePath)
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
    }
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
    exclude("javafx-swt.jar")
    exclude("**/javafx-swt.jar")
}
