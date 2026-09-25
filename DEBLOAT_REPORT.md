# simulacrum debloat - what went wrong

ran ArtusCmd 13.2.0 on the simulacrum shadow jar. two things broke, and both come down to
the same root cause.

1. the keeppublic preset crashed the app at startup:
   `NoSuchMethodError: notifyThemeChanged` at `WinApplication.initIDs (Native Method)`.
   that method is protected and only called from JavaFX native code (glass.dll) over JNI.
   static analysis cannot see a native to java callback, and keeppublic only keeps public
   methods as roots, so it stripped a method the native layer needs and the jvm hard crashed.

2. the -rum pass (remove unused methods) crashed at startup:
   `ClassNotFoundException: com.simulacrum.Launcher`.
   if you pass an entrypoints file (-e) without an aggressiveness level, that file becomes the
   only set of roots and your main() is not added automatically. so -rum debloated out the
   manifest main class and the jar would not start.

root cause: -rum removes whatever the static call graph thinks is unused, and the call graph
cannot see code that is only reached at runtime (JNI callbacks, reflection, the manifest main,
etc). give it an incomplete root set and it deletes things the app actually needs.

what works: the safe flags only, no -rum (-rdb -rdc -rmr -rej -ruc -re). they do no call graph
method removal, so nothing reachable-only-at-runtime can get cut. about 13 percent smaller and
passes all 17 robot tests, headless and with the globe.
