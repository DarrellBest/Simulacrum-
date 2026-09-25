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

// Extra classpath for the debloat task only. ArtusCmd's distribution lib/
// is missing log4j-core/api, which its startup calls directly. We resolve
// these from Maven Central without polluting the shadow jar.
configurations {
    create("artusRuntime") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

dependencies {
    "artusRuntime"("org.apache.logging.log4j:log4j-core:2.24.1")
    "artusRuntime"("org.apache.logging.log4j:log4j-api:2.24.1")
    "artusRuntime"("org.apache.logging.log4j:log4j-iostreams:2.24.1")

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

// ArtusCmd is a licensed tool and is NOT checked in. Point at its lib/ directory with
//   -PartusLib=/path/to/ArtusCmd/lib   or   ARTUS_LIB=/path/to/ArtusCmd/lib
// Only the debloat* tasks need it; every other task works without ArtusCmd.
val artusLibDir: String = (project.findProperty("artusLib") as String?)
        ?: System.getenv("ARTUS_LIB")
        ?: "$projectDir/artuscmd/ArtusCmd/lib"
val debloatedJar = layout.buildDirectory.file("libs/simulacrum-all-debloated.jar")

tasks.register<JavaExec>("debloat") {
    description = "Runs ArtusCmd 13.2.0 to debloat the shadow jar (aggressiveness=keeppublic)."
    group = "build"
    dependsOn("shadowJar")
    val inputJar = layout.buildDirectory.file("libs/simulacrum-all.jar").get().asFile
    val outputJar = debloatedJar.get().asFile
    inputs.file(inputJar)
    outputs.file(outputJar)
    classpath = files("$artusLibDir").asFileTree.matching { include("*.jar") } + configurations["artusRuntime"]
    mainClass.set("com.pjrcorp.artus.cmd.ArtusCmdMain")
    // Work from build/libs so we can hand ArtusCmd bare filenames — it URI-ifies
    // anything that looks absolute, and the Windows drive-letter colon blows up
    // Java's URI parser ("Illegal character [:] in path at index 4: ///C:/...").
    workingDir = inputJar.parentFile
    args = listOf(
            "process",
            "-a", "keeppublic",
            "-jv", "21",
            "-fo",
            "-o", outputJar.name,
            inputJar.name
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
    classpath = files("$artusLibDir").asFileTree.matching { include("*.jar") } + configurations["artusRuntime"]
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

// Reproducible source distribution. Produces build/distributions/simulacrum-src-<version>.zip
// containing everything a fresh user needs to unpack and run bootstrap.{ps1,sh}.
// Excludes build artifacts, IDE state, venvs, git metadata, and any recorded demos.
tasks.register<Zip>("packageSource") {
    description = "Zip the source tree into a self-contained distribution archive."
    group = "distribution"

    archiveBaseName.set("simulacrum-src")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    val rootName = "simulacrum-${project.version}"
    into(rootName) {
        from(projectDir) {
            exclude(
                ".git",
                ".git/**",
                ".gradle/**",
                ".idea/**",
                ".vscode/**",
                ".claude/**",
                "build/**",
                "out/**",
                "bin/**",
                "robot/.venv/**",
                "**/*.iml",
                "**/*.log",
                "**/*.mp4",
                "**/.DS_Store"
            )
        }
    }

    // Preserve the executable bit on shell scripts and the Gradle wrapper.
    filesMatching(listOf(
        "$rootName/gradlew",
        "$rootName/bootstrap.sh",
        "$rootName/robot/setup-venv.sh",
        "$rootName/robot/run-under-xvfb.sh"
    )) {
        mode = "755".toInt(8)
    }

    doLast {
        val out = archiveFile.get().asFile
        println("Source distribution: ${out.absolutePath}  (${out.length() / 1024} KB)")
    }
}
