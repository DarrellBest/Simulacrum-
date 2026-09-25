# Simulacrum — SWFTS Simulator & Messaging Sandbox

A lightweight Java desktop app for simulating and exercising SWFTS-adjacent
infrastructure: JavaFX UI on the left, a NASA WorldWind globe on the right,
and an AMQP publisher/subscriber in the middle. Intended as a developer-grade
MVP, not a production system.

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
├── build.gradle.kts                 # Gradle + JavaFX + Protobuf + ShadowJar
├── settings.gradle.kts
├── src/main/java/com/simulacrum/
│   ├── App.java                     # JavaFX entrypoint + --headless-check
│   ├── ui/                          # JavaFX + WorldWind SwingNode host
│   ├── amqp/                        # Artemis embedded broker, Qpid JMS, loopback
│   ├── data/                        # DataSource + NmeaDataSource
│   ├── overlay/                     # Shapes, undo/redo, KML
│   ├── services/                    # Stubs for LDAP/DNS/NTP/TACLAN (config only)
│   └── testctl/                     # HTTP test-control endpoint
├── src/main/proto/simulacrum.proto  # TrackUpdate, SensorReport, Heartbeat
├── src/main/resources/
│   ├── fxml/main.fxml
│   └── styles/app.css
├── src/test/java/                   # JUnit 5 unit tests
└── robot/
    ├── smoke.robot
    ├── requirements.txt
    └── run-under-xvfb.sh
```

## Prerequisites

- **JDK 21** (tested with OpenJDK 21.0.10). The Gradle toolchain will
  refuse to build on older JDKs.
- **Internet access** on first build so Gradle can resolve JavaFX 21,
  Artemis 2.37, Qpid JMS 2.6, WorldWind 2.0, JOGL 2.2.4, and Protobuf
  3.25 from Maven Central.
- *(Optional, for the smoke suite)* **Python 3.9+**, **`pip`**, and
  **`xvfb-run`** (`apt install xvfb` on Debian/Ubuntu).

No external RabbitMQ is required — the app launches its own embedded
Artemis broker on `localhost:5672`.

## Build & run

The repo ships a Gradle wrapper; you don't need a system Gradle install.
All commands below assume you're in the repo root.

### 1. Compile, generate protobuf, run unit tests

```bash
./gradlew build
```

Runs the 13 JUnit 5 tests in `src/test/java/` (NMEA parser, loopback
transport, overlay/undo/redo, KML export, HTTP test-control server).

### 2. Package a runnable fat jar

```bash
./gradlew shadowJar
# → build/libs/simulacrum-all.jar   (~50 MB, all deps bundled)
```

### 3. Launch the desktop app

```bash
java -jar build/libs/simulacrum-all.jar
```

Opens a 1280×800 window with the controls pane on the left and the
WorldWind 3D globe on the right. Click **Send once** to publish a
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

The suite exercises every route exposed by the test-control server
(health, publisher, subscriber, draw/KML, ship controls, signal
buttons, and UI introspection — 16 tests total).

**One-time setup** — create a project-local venv so the toolchain
stays out of your system Python:

```powershell
.\robot\setup-venv.ps1            # Windows
```

```bash
bash robot/setup-venv.sh          # Linux/macOS
```

Both scripts are idempotent — re-run after a `requirements.txt` bump.

**Linux (under Xvfb):**

```bash
bash robot/run-under-xvfb.sh
```

**Windows (PowerShell, native, no Xvfb needed):**

```powershell
.\robot\run-on-windows.ps1                  # default 8s hold so you can see the UI
.\robot\run-on-windows.ps1 -HoldSeconds 0   # CI mode, no hold
```

Either runner boots the jar with `--no-globe`, waits for
`http://127.0.0.1:17355/health` to respond, then runs `robot/smoke.robot`.
Robot output (HTML log and report) lands in a temp directory that the
script prints at the end.

**Adding a new test:** see [robot/ADD-TEST-PROMPT.md](robot/ADD-TEST-PROMPT.md)
for a copy-paste prompt template that briefs Claude (or any other coding
assistant) with everything it needs to add a new smoke test correctly in
one shot.

### 7. Individual Gradle tasks

```bash
./gradlew test              # unit tests only
./gradlew generateProto     # regenerate Java from simulacrum.proto
./gradlew run               # compile + launch without shadowJar
./gradlew clean             # wipe build/
./gradlew tasks             # list everything
```

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

## AMQP behavior

- On startup the app launches an **embedded Artemis broker** on `localhost:5672`.
- The Qpid JMS client connects to that broker and publishes `TrackUpdate`
  protobufs when **Send once** or **Start stream** is clicked.
- If the AMQP connection fails for any reason, the app transparently falls
  back to an in-process `LoopbackTransport` and displays a banner so the demo
  keeps working with zero external state.

## Scope note

This is an MVP. Real SWFTS integrations — OpenLDAP, secure LDAP, CoreDNS, NTP,
TACLAN, OTH-GOLD, NCOM, HYCOM, WW3, NITF, MrSID, RPF, CADRG, CIB, DTED,
AIRMAR, AIS RADAR — are intentionally **out of scope**. The `DataSource`
interface and `ServiceConfig` make the extension points visible without
pretending to ship the servers or parsers.
