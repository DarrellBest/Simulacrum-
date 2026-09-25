# Simulacrum — SWFTS Simulator & Messaging Sandbox

A lightweight Java desktop app for simulating and exercising SWFTS-adjacent
infrastructure: JavaFX UI on the left, a NASA WorldWind globe on the right,
and an AMQP publisher/subscriber in the middle. Intended as a developer-grade
MVP, not a production system.

## Quick start

You need only one prerequisite: **JDK 21** on your PATH (Microsoft OpenJDK 21
or Eclipse Temurin 21 are both fine).

> **Have a newer JDK as your default?** The bundled Gradle 8.14 wrapper does
> not run on JDK 25. Point `JAVA_HOME` at a JDK 21 before invoking any
> `gradlew` command, for example on Windows:
>
> ```powershell
> $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
> ```

**Windows (PowerShell):**

```powershell
.\bootstrap.ps1
```

**Linux / macOS / WSL:**

```bash
bash bootstrap.sh
```

The bootstrap script downloads dependencies, builds a ~50 MB self-contained
jar with the Gradle wrapper, sets up a project-local Python venv for the
Robot Framework test suite, runs all 17 smoke tests, then launches the
JavaFX desktop app. First run takes 3–5 minutes depending on network speed;
subsequent runs are incremental.

Flags: `-NoTests` / `--no-tests` skips the Robot suite, `-NoLaunch` /
`--no-launch` builds only. Examples and more detail in the rest of this
README.

**Want to ship the source to someone else?**

```powershell
.\gradlew.bat packageSource          # Windows
./gradlew packageSource              # Linux/macOS
```

Drops a self-contained `simulacrum-src-<version>.zip` into
`build/distributions/`. Unzip on any machine with a JDK 21 and run the
bootstrap script in the root — no other setup required.

## What's in here

- **JavaFX 21** split-screen shell (`src/main/java/com/simulacrum/ui/`)
- **NASA WorldWind Java** globe via `SwingNode` (`GlobePane`)
- **Embedded Apache ActiveMQ Artemis** broker speaking AMQP 1.0, plus an
  **Apache Qpid JMS** client (`src/main/java/com/simulacrum/amqp/`)
- **Google Protocol Buffers** payload schemas (`src/main/proto/simulacrum.proto`)
- **NMEA 0183** parser as a worked `DataSource` (`NmeaParser`, `NmeaDataSource`)
- **Overlay model** with undo/redo/group and a **KML 2.2** writer
- **HTTP test-control endpoint** on `:17355` that Robot Framework drives
- **Robot Framework** smoke suite wrapped by `xvfb-run`
- **JUnit 5** tests for parsers, loopback transport, overlay commands,
  KML export, and the test-control server

## Architecture

```
simulacrum/
├── bootstrap.ps1 / bootstrap.sh     # One-shot build + test + launch
├── build.gradle.kts                 # Gradle + JavaFX + Protobuf + ShadowJar
├── settings.gradle.kts
├── src/main/java/com/simulacrum/
│   ├── App.java                     # JavaFX entrypoint + --headless-check
│   ├── ui/                          # JavaFX + WorldWind SwingNode host
│   ├── amqp/                        # Artemis embedded broker, Qpid JMS, loopback
│   ├── data/                        # DataSource + NmeaDataSource
│   ├── overlay/                     # Shapes, undo/redo, KML
│   ├── ship/                        # Ship model + 20 Hz physics integrator
│   ├── services/                    # Stubs for LDAP/DNS/NTP/TACLAN (config only)
│   └── testctl/                     # HTTP test-control endpoint
├── src/main/proto/simulacrum.proto  # TrackUpdate, SensorReport, Heartbeat
├── src/main/resources/
│   ├── fxml/main.fxml
│   └── styles/app.css
├── src/test/java/                   # JUnit 5 unit tests
├── robot/
│   ├── ADD-TEST-PROMPT.md           # Template for adding new Robot tests
│   ├── smoke.robot                  # 17 tests covering every endpoint
│   ├── requirements.txt
│   ├── setup-venv.{ps1,sh}          # Idempotent venv setup
│   ├── run-on-windows.ps1           # Windows runner
│   ├── run-windows.sh               # Windows runner (Git Bash)
│   └── run-under-xvfb.sh            # Linux runner (under Xvfb)
└── offline-repo/                    # Vendored Maven repo for offline builds
```

## Prerequisites

- **JDK 21** (tested with Microsoft OpenJDK 21.0.12). The Gradle toolchain
  refuses older JDKs, and the Gradle 8.14 wrapper itself will not start on
  JDK 25, so `JAVA_HOME` must point at a 21.
- **Internet access on first build only**, so the Gradle wrapper can
  fetch its distribution zip from `services.gradle.org` (~137 MB,
  cached into `GRADLE_USER_HOME` after the first run). Every subsequent
  build runs fully offline.
- *(Optional, for the smoke suite)* **Python 3.9+**, **`pip`**, and
  **`xvfb-run`** (`apt install xvfb` on Debian/Ubuntu).

**All Gradle plugins and every runtime/test dependency are vendored**
under `offline-repo/` (a flattened Maven layout, ~100 MB), including
the platform-specific JavaFX, JOGL and `protoc` artifacts for Linux,
Windows and macOS (x64 and Apple Silicon). After the wrapper has its
distribution cached, every build accepts `--offline` and resolves zero
artifacts from the internet. `settings.gradle.kts` searches
`offline-repo/` first and falls back to Maven Central only for anything
not vendored.

No external RabbitMQ is required: the app launches its own embedded
Artemis broker on `localhost:5672`.

## Build & run

The repo ships a Gradle wrapper; you don't need a system Gradle install.
All commands below assume you're in the repo root.

### 1. Compile, generate protobuf, run unit tests

```bash
./gradlew --offline build
```

> Everything below accepts `--offline`. Drop the flag only if you
> explicitly want to hit the internet — but the build does not need it.

Runs the 27 JUnit 5 unit tests in `src/test/java/` (NMEA parser, loopback
transport, overlay/undo/redo, KML export, ship model and physics, HTTP
test-control server, controls pane). Tests tagged `integration` and `gui`
are excluded here; see section 7.

### 2. Package a runnable fat jar

```bash
./gradlew shadowJar
# → build/libs/simulacrum-all.jar   (~50 MB, all deps bundled)
```

### 3. Launch the desktop app

```bash
java -jar build/libs/simulacrum-all.jar
```

Opens maximized (1280×800 initial scene size) with the controls pane on
the left and the WorldWind 3D globe on the right. Click **Send once** to publish a
`TrackUpdate`, **Start stream** to publish at N Hz, or **Draw sample
polygon** to drop a shape and exercise undo/redo and KML export.

> **Requires a real X11 session with hardware GL.** Xvfb's software
> GL crashes the JavaFX ↔ Swing ↔ JOGL native stack — use `--no-globe`
> (below) for headless environments.

### 4. Headless boot check (CI / smoke test)

```bash
java -jar build/libs/simulacrum-all.jar --headless-check
```

Starts the embedded Artemis broker, opens a Qpid JMS connection over
AMQP 1.0, logs `UI ready (headless transport=AMQP 1.0 @ …)`, shuts
everything down, exits 0. Does not require a display.

### 5. UI shell without the globe

```bash
java -jar build/libs/simulacrum-all.jar --no-globe
```

Full JavaFX UI, AMQP publisher/subscriber, and test-control HTTP
endpoint come up; the right-hand `SwingNode` is replaced with a
placeholder. Use this in Xvfb or other headless displays.

### 6. Robot Framework smoke suite

**How it fits together.** There is no Robot Framework jar. Robot is a
Python package (`robotframework` + `robotframework-requests`, pinned in
`robot/requirements.txt`). The only jar involved is the app's own shadow
jar. When the app starts it opens a small HTTP **test-control server** on
`127.0.0.1:17355` (`src/main/java/com/simulacrum/testctl/TestControlServer.java`).
`robot/smoke.robot` drives every one of those routes with plain HTTP GETs
and asserts on the responses: 17 tests covering health, publisher,
subscriber, draw/KML, ship throttle/rudder/heading/autopilot/anchor,
heartbeat and sensor signals, and UI node introspection. While the suite
runs, the UI shows a `ROBOT: <route>` banner and flashes the button each
request maps to, so a recording of the run is self-explanatory.

Every runner below does the same four things: start the jar, poll
`/health` until it answers, run `smoke.robot`, then kill the app.

**One-time setup** (project-local venv so nothing touches system Python):

```powershell
.\robot\setup-venv.ps1            # Windows
```

```bash
bash robot/setup-venv.sh          # Linux/macOS
```

Both scripts are idempotent. Re-run after a `requirements.txt` bump. If you
would rather use your own interpreter, `pip install -r robot/requirements.txt`
is all that is needed.

**Windows, PowerShell:**

```powershell
.\robot\run-on-windows.ps1                       # --no-globe, 8 s hold at the end so you can see the UI
.\robot\run-on-windows.ps1 -HoldSeconds 0        # CI mode
.\robot\run-on-windows.ps1 -WithGlobe -PaceSeconds 1.5   # full 3D globe, slowed down for demos
```

**Windows, Git Bash** (takes an explicit jar and output directory, so it
works for the debloated jar too):

```bash
bash robot/run-windows.sh build/libs/simulacrum-all.jar build/robot-out
bash robot/run-windows.sh build/libs/simulacrum-all.jar build/robot-out ""   # "" = full UI with globe
```

**Linux, under Xvfb:**

```bash
bash robot/run-under-xvfb.sh
```

**Running Robot by hand** against an app you already started:

```bash
python -m robot.run --outputdir build/robot-out robot/smoke.robot
python -m robot.run --variable PACE:1.5s robot/smoke.robot   # pause between tests
```

Robot writes `log.html`, `report.html` and `output.xml` into the output
directory. Open `log.html` for the per-keyword trace of a failing test.

**Adding a new test:** see [robot/ADD-TEST-PROMPT.md](robot/ADD-TEST-PROMPT.md)
for a copy-paste prompt template that briefs Claude (or any other coding
assistant) with everything it needs to add a new smoke test correctly in
one shot.

### 7. Gradle tasks

```bash
./gradlew test                # unit tests only (excludes integration + gui)
./gradlew integrationTest     # builds the shadow jar, spawns it, drives it over HTTP (JUnit)
./gradlew guiTest             # builds the shadow jar, drives the live JavaFX UI with java.awt.Robot
./gradlew generateProto       # regenerate Java from simulacrum.proto
./gradlew run                 # compile + launch without shadowJar
./gradlew packageSource       # build/distributions/simulacrum-src-<version>.zip
./gradlew clean               # wipe build/
./gradlew tasks               # list everything
```

`guiTest` needs a real desktop session: it moves the mouse and clicks.
Note the name clash: that task uses `java.awt.Robot`, which has nothing
to do with Robot Framework.

### 8. Debloating the jar with ArtusCmd (optional)

ArtusCmd is a licensed tool and is **not** in this repository. Every
`artuscmd/`, `ArtusCmd*/` and `.artus/` path is git-ignored so an
unpacked copy inside the checkout can never be committed. Tell the build
where it lives with either a Gradle property or an environment variable:

```bash
./gradlew debloat -PartusLib=/path/to/ArtusCmd/lib
ARTUS_LIB=/path/to/ArtusCmd/lib ./gradlew debloat
```

| Task | What it does |
|---|---|
| `debloat` | `shadowJar`, then ArtusCmd with `-a keeppublic`, output `build/libs/simulacrum-all-debloated.jar` |
| `debloatMax` | Same with `-a max` (more cutting, more risk), output `simulacrum-all-debloated-max.jar` |
| `integrationTestDebloated` | The integration suite against the debloated jar |
| `guiTestDebloated` | The `java.awt.Robot` UI suite against the debloated jar |

`debloat-test.sh` is the end-to-end version: it builds, runs the Robot
suite against the plain jar, debloats with a conservative flag set, runs
the suite again against the debloated jar and prints a size and pass/fail
summary for both:

```bash
bash debloat-test.sh --artus-lib /path/to/ArtusCmd/lib
bash debloat-test.sh --artus-tgz /path/to/ArtusCmd-13.2.0.tar.gz --globe
```

`package-debloat-kit.sh` tars up the scripts, suite and report for
hand-off. See [DEBLOAT_REPORT.md](DEBLOAT_REPORT.md) for results and the
known caveat that `keeppublic` strips a JavaFX JNI callback the app needs.

## Runtime knobs

| Flag / env | Effect |
|---|---|
| `--headless-check` | Boot the broker + transport, log readiness, exit 0. No UI. |
| `--no-globe` | Start the JavaFX UI without the WorldWind `SwingNode`. Safe under Xvfb. |
| *(default)* | Full UI with 3D globe. |

The HTTP test-control endpoint on `http://127.0.0.1:17355` accepts:

- `GET /health` → `ok`
- `GET /pub/start?hz=<double>` → start publishing `TrackUpdate`s at N Hz
- `GET /pub/stop` → stop the publisher
- `GET /pub/count` → messages published since launch
- `GET /sub/count` → messages received by the in-app subscriber
- `GET /draw/sample` → add a sample polygon to the overlay
- `GET /kml` → export all overlay shapes as KML 2.2
- `GET /ship/throttle?v=<-100..100>`, `/ship/rudder?v=<deg>`, `/ship/heading?v=<deg>` → `ok`
- `GET /ship/autopilot?v=<bool>`, `/ship/anchor?v=<bool>` → `ok`
- `GET /ship/state` → `lat=.. lon=.. hdg=.. spd=.. thr=.. rud=..`
- `GET /signal/heartbeat`, `/signal/sensor?kind=SONAR|RADAR|AIS|EW|MOB|DISTRESS` → publish one message
- `GET /ui/list` → comma-separated registered node ids (`btn.heartbeat`, `btn.allAheadFull`, ...)
- `GET /ui/locate?id=<node id>` → `x,y,w,h` on screen, or empty if unknown
- `GET /ui/focus` → bring the window to the front

Every request also fires a `ROBOT: <route>` banner in the controls pane
and flashes the matching button, if the route maps to one.

## AMQP behavior

- On startup the app launches an **embedded Artemis broker** on `localhost:5672`.
- The Qpid JMS client connects to that broker and publishes `TrackUpdate`
  protobufs when **Send once** or **Start stream** is clicked.
- If the AMQP connection fails for any reason, the app transparently falls
  back to an in-process `LoopbackTransport` and displays a banner so the demo
  keeps working with zero external state.

## Branches

- `master` is the integration branch and always builds and passes the
  unit and Robot suites.
- `develop` is where day-to-day work lands before it is merged to `master`.

## Scope note

This is an MVP. Real SWFTS integrations — OpenLDAP, secure LDAP, CoreDNS, NTP,
TACLAN, OTH-GOLD, NCOM, HYCOM, WW3, NITF, MrSID, RPF, CADRG, CIB, DTED,
AIRMAR, AIS RADAR — are intentionally **out of scope**. The `DataSource`
interface and `ServiceConfig` make the extension points visible without
pretending to ship the servers or parsers.
