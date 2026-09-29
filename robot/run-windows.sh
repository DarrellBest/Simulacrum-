#!/usr/bin/env bash
# Run the Robot suite against a jar on the desktop display (no Xvfb).
# Usage: bash robot/run-windows.sh <jar> <outdir> [app-flags]   ("" = globe on; default --no-globe)
set -uo pipefail

JAR="${1:?usage: run-windows.sh <jar> <outdir> [app-flags]}"
OUT="${2:?usage: run-windows.sh <jar> <outdir> [app-flags]}"
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
