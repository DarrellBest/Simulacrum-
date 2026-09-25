#!/usr/bin/env bash
# Windows-friendly Robot Framework runner (no xvfb — uses the real desktop display).
# Boots a Simulacrum jar with --no-globe, waits for the test-control endpoint on
# :17355, runs robot/smoke.robot, then tears the app down. Exits non-zero on failure.
#
# Usage: bash robot/run-windows.sh <path-to-jar> <output-dir>
set -uo pipefail

JAR="${1:?usage: run-windows.sh <jar> <outdir> [app-flags]}"
OUT="${2:?usage: run-windows.sh <jar> <outdir> [app-flags]}"
# Optional 3rd arg: app launch flags. Defaults to --no-globe (headless-safe).
# Pass "" (empty) to launch the FULL UI with the WorldWind 3D globe.
APP_FLAGS="${3---no-globe}"
HERE="$(cd "$(dirname "$0")" && pwd)"
BASE="http://127.0.0.1:17355"

if [[ ! -f "$JAR" ]]; then
    echo "Jar not found: $JAR" >&2
    exit 1
fi

mkdir -p "$OUT"
APP_LOG="$OUT/app.log"

echo "Launching: $JAR  (flags: ${APP_FLAGS:-<none / full globe>})"
java -jar "$JAR" $APP_FLAGS >"$APP_LOG" 2>&1 &
APP_PID=$!

cleanup() {
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "Waiting for $BASE/health ..."
ready=0
for i in $(seq 1 60); do
    if curl -fsS "$BASE/health" >/dev/null 2>&1; then
        ready=1
        break
    fi
    # bail early if the app already died
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "App process exited before becoming ready. Log:" >&2
        cat "$APP_LOG" >&2
        exit 2
    fi
    sleep 1
done

if [[ "$ready" -ne 1 ]]; then
    echo "Endpoint never came up. App log:" >&2
    cat "$APP_LOG" >&2
    exit 2
fi
echo "Endpoint is up."

python -m robot.run --outputdir "$OUT" "$HERE/smoke.robot"
RC=$?
echo "Robot exit code: $RC  (output in $OUT)"
exit $RC
