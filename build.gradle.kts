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
// WorldWind 2.2.1 is vendored in offline-repo (not on Maven Central). JOGL 2.2.4 only works on Windows.
val worldwindVersion = "2.2.1"
val joglVersion = "2.6.0"

// ArtusCmd's lib/ lacks log4j; the debloat task adds it.
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

    // WorldWind + JOGL
    implementation("gov.nasa:worldwind:$worldwindVersion")
    implementation("org.jogamp.jogl:jogl-all:$joglVersion")
    implementation("org.jogamp.gluegen:gluegen-rt:$joglVersion") { exclude(group = "antlr") }
    for (platform in listOf("linux-amd64", "linux-aarch64", "windows-amd64", "macosx-universal")) {
        runtimeOnly("org.jogamp.jogl:jogl-all:$joglVersion:natives-$platform")
        runtimeOnly("org.jogamp.gluegen:gluegen-rt:$joglVersion:natives-$platform") { exclude(group = "antlr") }
    }

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

// ArtusCmd is not checked in: -PartusLib=/path/to/ArtusCmd/lib or ARTUS_LIB=...
val artusLibDir: String = (project.findProperty("artusLib") as String?)
        ?: System.getenv("ARTUS_LIB")
        ?: "$projectDir/artuscmd/ArtusCmd/lib"
val debloatedJar = layout.buildDirectory.file("libs/simulacrum-all-debloated.jar")

tasks.register<JavaExec>("debloat") {
    description = "Debloat the shadow jar with ArtusCmd using the verified flag set (see debloat/sweep.sh)."
    group = "build"
    dependsOn("shadowJar")
    val inputJar = layout.buildDirectory.file("libs/simulacrum-all.jar").get().asFile
    val outputJar = debloatedJar.get().asFile
    inputs.file(inputJar)
    outputs.file(outputJar)
    classpath = files("$artusLibDir").asFileTree.matching { include("*.jar") } + configurations["artusRuntime"]
    mainClass.set("com.pjrcorp.artus.cmd.ArtusCmdMain")
    // Bare file names: ArtusCmd rejects Windows drive-letter paths.
    workingDir = inputJar.parentFile
    args = listOf(
            "process", "-jv", "21", "-fo",
            "-rdb", "-rdc", "-rmr", "-re", "-rej", "-ruc",
            "-o", outputJar.name,
            inputJar.name
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

// build/distributions/simulacrum-src-<version>.zip: the source tree, ready for bootstrap.{sh,ps1}.
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

    filesMatching(listOf(
        "$rootName/gradlew",
        "$rootName/bootstrap.sh",
        "$rootName/robot/setup-venv.sh",
        "$rootName/robot/run-under-xvfb.sh",
        "$rootName/debloat/test-jar.sh",
        "$rootName/debloat/sweep.sh"
    )) {
        mode = "755".toInt(8)
    }

    doLast {
        val out = archiveFile.get().asFile
        println("Source distribution: ${out.absolutePath}  (${out.length() / 1024} KB)")
    }
}
