# Simulacrum

JavaFX desktop app for exercising SWFTS-style messaging: a bridge control pane on the
left, a NASA WorldWind globe on the right, an embedded ActiveMQ Artemis broker
(AMQP 1.0) and a Qpid JMS publisher/subscriber in between.

## Requirements

- JDK 21 on `PATH`. Gradle 8.14 will not start on JDK 25.
- Internet on the first build only, for the Gradle distribution. Every dependency is
  vendored under `offline-repo/`, so `./gradlew --offline` works after that.
- For the Robot suite: Python 3.9+, and `xvfb-run` on Linux without a display.

## Build and run

```bash
./gradlew --offline build          # compile, protobuf, 27 unit tests
./gradlew --offline shadowJar      # build/libs/simulacrum-all.jar (~60 MB)
java -jar build/libs/simulacrum-all.jar
```

`bootstrap.sh` / `bootstrap.ps1` do the build, create `robot/.venv`, run the Robot
suite and launch the app in one go (`--no-tests`, `--no-launch`).

| Flag | Effect |
|---|---|
| `--headless-check` | Start broker and transport, print readiness, exit 0. No UI. |
| `--no-globe` | UI without the WorldWind pane. Use under Xvfb without GL. |

If the AMQP connection fails the app falls back to an in-process loopback transport
and shows a banner. `--headless-check` prints which transport it got.

## Layout

```
src/main/java/com/simulacrum/
  App.java, Launcher.java     entry points
  ui/                         ControlsPane, GlobePane (WorldWind in a SwingNode), MainView
  amqp/                       EmbeddedBroker, QpidJmsTransport, LoopbackTransport
  ship/                       ShipModel + 20 Hz integrator
  overlay/                    shapes, undo/redo, KML 2.2 export
  data/                       DataSource + NMEA 0183 parser
  testctl/                    HTTP test-control server on :17355
src/main/proto/simulacrum.proto
src/test/java/                JUnit 5; integration and gui tags run separately
robot/                        Robot Framework smoke suite and runners
debloat/                      ArtusCmd harness (see below)
offline-repo/                 vendored Maven repository
```

## Tests

```bash
./gradlew test                # unit tests
./gradlew integrationTest     # spawns the shadow jar, drives it over HTTP
./gradlew guiTest             # clicks the live UI with java.awt.Robot; needs a display
```

### Robot suite

Robot Framework is a Python package, not a jar. `robot/smoke.robot` sends HTTP requests
to the running app on `127.0.0.1:17355` and checks the responses: 17 tests covering
health, publisher, subscriber, draw/KML, ship controls, signals and UI introspection.

```bash
bash robot/setup-venv.sh                                   # once; .ps1 on Windows
bash robot/run-under-xvfb.sh                               # Linux, --no-globe under Xvfb
bash robot/run-windows.sh build/libs/simulacrum-all.jar out ""   # any desktop, globe on
.\robot\run-on-windows.ps1 [-WithGlobe] [-PaceSeconds 1.5]
```

Endpoints:

```
GET /health                      ok
GET /pub/start?hz=N  /pub/stop  /pub/count  /sub/count
GET /draw/sample     /kml
GET /ship/throttle?v=  /ship/rudder?v=  /ship/heading?v=  /ship/autopilot?v=  /ship/anchor?v=
GET /ship/state                  lat= lon= hdg= spd= thr= rud=
GET /signal/heartbeat  /signal/sensor?kind=SONAR|RADAR|AIS|EW|MOB|DISTRESS
GET /ui/list  /ui/locate?id=  /ui/focus
```

To add a test: expose the behaviour as a GET route in `TestControlServer.java` and
`App.java` if it is not already there, then add a case to `smoke.robot` that checks the
side effect, not just the `ok`.

## Debloating with ArtusCmd

ArtusCmd is licensed and not in this repository. `ArtusCmd*/`, `artuscmd/` and
`.artus/` are git-ignored. It needs its own JDK 25; the app stays on JDK 21.

```bash
ARTUS_LIB=/path/to/ArtusCmd/lib ARTUS_JAVA=/path/to/jdk-25/bin/java bash debloat/sweep.sh
```

`sweep.sh` tests the untouched jar, runs `stats`, `preflight` and `scan`, then runs
`process` with one flag added at a time in this order:

```
-rdb  -rdc  -rmr  -re  -rej  -ruc  -rum
```

Each output jar goes through `debloat/test-jar.sh`: `--headless-check` must exit 0 and
report the AMQP transport, then the UI is started with the globe on and the Robot suite
must pass with no linkage errors in the log. A flag whose jar fails is dropped and the
sweep continues. No `-a`/`--aggressiveness` preset is used. Results land in
`build/debloat/`, the best jar as `simulacrum-all-debloated.jar`, with a summary table,
every Artus report, and the Robot logs per step.

`./gradlew debloat` runs `process` once with the flag set the sweep verified
(`-rdb -rdc -rmr -re -rej -ruc`). `integrationTestDebloated` and `guiTestDebloated` run
the JUnit suites against that jar. See `DEBLOAT_REPORT.md` for numbers and why `-rum`
is excluded.

## Globe

WorldWind 2.2.1 with JOGL 2.6.0. Natives for Linux x64 and aarch64, Windows x64, and
macOS x64 and arm64 are in the jar. The globe needs a display with OpenGL; under Xvfb
use `--no-globe`. Base imagery is bundled; the NASA WMS servers WorldWind queries for
higher zoom levels are offline, and those retrieval errors in the log are expected.

## Branches

`master` builds and passes the unit and Robot suites. Day-to-day work lands on `develop`.
