#!/usr/bin/env bash
# Run the Robot suite against build/libs/simulacrum-all.jar under Xvfb.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
JAR="$ROOT/build/libs/simulacrum-all.jar"

if [[ ! -f "$JAR" ]]; then
    echo "Jar not found at $JAR. Run './gradlew shadowJar' first." >&2
    exit 1
fi

if ! command -v xvfb-run >/dev/null; then
    echo "xvfb-run not found on PATH." >&2
    exit 1
fi

if ! command -v robot >/dev/null; then
    echo "'robot' not found on PATH. Install with: pip install -r $HERE/requirements.txt" >&2
    exit 1
fi

LOG_DIR="$(mktemp -d)"
APP_LOG="$LOG_DIR/app.log"

echo "Launching Simulacrum under Xvfb, log: $APP_LOG"
xvfb-run -a --server-args="-screen 0 1280x800x24" \
    java -Dprism.order=sw -Dsun.java2d.xrender=false \
    -jar "$JAR" --no-globe >"$APP_LOG" 2>&1 &
APP_PID=$!

cleanup() {
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "Waiting for test control endpoint..."
for i in $(seq 1 60); do
    if curl -fsS http://127.0.0.1:17355/health >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

if ! curl -fsS http://127.0.0.1:17355/health >/dev/null; then
    echo "Test control endpoint never came up. App log:" >&2
    cat "$APP_LOG" >&2
    exit 2
fi

robot --outputdir "$LOG_DIR" "$HERE/smoke.robot"
echo "Robot output in $LOG_DIR"
