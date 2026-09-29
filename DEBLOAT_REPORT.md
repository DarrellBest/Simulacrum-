# Debloat report

ArtusCmd 14.7.0 on `build/libs/simulacrum-all.jar` (WorldWind 2.2.1, JOGL 2.6.0), run with
`debloat/sweep.sh` on macOS arm64, JDK 21, globe on. Each step: `--headless-check` must
report the AMQP transport, then the Robot suite (17 tests) must pass against the live UI.

| step | flags | jar bytes | classes | methods | result |
|---|---|---|---|---|---|
| baseline | none | 59,636,331 | 30,592 | 299,089 | pass |
| 1 | -rdb | 52,561,619 | 30,592 | 299,089 | pass |
| 2 | + -rdc | 52,561,619 | 30,592 | 299,089 | pass (no duplicates) |
| 3 | + -rmr | 51,041,796 | 29,491 | 290,111 | pass |
| 4 | + -re | 50,939,582 | 29,285 | 290,111 | pass |
| 5 | + -rej | 50,939,582 | 29,285 | 290,111 | pass (single jar) |
| 6 | + -ruc | 50,786,110 | 29,189 | 289,321 | pass |
| 7 | + -rum | 47,984,238 | 27,588 | 259,874 | fail |

Final flags: `-rdb -rdc -rmr -re -rej -ruc`. 59.6 MB to 50.8 MB on disk (14.8%),
uncompressed 20.2%, 1,403 classes and 9,768 methods removed. No `-a` preset.

## Why -rum is excluded

`-rum` removes methods the static call graph cannot reach. Qpid JMS sets its provider
options through reflection, so those setters look unused and get cut. The provider then
fails to start:

```
Not all provider options could be set on the found factory ... providerScheme=amqp, transportScheme=tcp
```

and the app silently falls back to the in-process loopback transport. The Robot suite still
passes, because every route works over loopback. Only the `--headless-check` transport line
shows the loss, which is why `debloat/test-jar.sh` fails on anything but `AMQP 1.0`.

Passing scan's `runtime_entrypoints.txt` with `-e` does not help; it does not include the
reflective setters. An earlier run on an older Artus with `-a keeppublic` also broke startup
by stripping a JavaFX JNI callback (`notifyThemeChanged`), which is another case of runtime
reachability the analysis cannot see.

## Globe

The previous WorldWind 2.0.0 / JOGL 2.2.4 build could only render the globe on Windows:
JOGL 2.2.4 needs a `libjawt` symbol version that JDK 9+ dropped on Linux, creates its
NSWindow off the main thread on macOS, and has no arm64 code. With WorldWind 2.2.1 and
JOGL 2.6.0 the globe renders on macOS arm64 natively and the debloated jar above was
verified with it on. The 52 classes removed under `gov.nasa`, `com.jogamp` and `jogamp`
are constant-only interfaces, SWT and applet glue, and the GDAL loader; none is referenced
by a surviving class.
