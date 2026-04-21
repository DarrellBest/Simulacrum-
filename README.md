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

## Build & run

```bash
./gradlew build                  # compile, generate protobuf, run unit tests
./gradlew shadowJar              # produces build/libs/simulacrum-all.jar
java -jar build/libs/simulacrum-all.jar
```

Headless boot check (no display, exits after "UI ready" log):

```bash
java -jar build/libs/simulacrum-all.jar --headless-check
```

Robot Framework smoke suite under Xvfb:

```bash
pip install -r robot/requirements.txt
bash robot/run-under-xvfb.sh
```

The smoke harness launches the jar with `--no-globe`. That flag skips the
Swing/WorldWind `SwingNode` host because Xvfb's software GL crashes the
JavaFX ↔ Swing ↔ JOGL native stack. Real desktop X servers with hardware
GL don't need the flag — launch without it to see the full 3D globe.

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
