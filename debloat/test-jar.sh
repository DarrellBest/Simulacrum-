#!/usr/bin/env bash
# Boot a Simulacrum jar, run the Robot suite against it, report PASS/FAIL.
# Usage: debloat/test-jar.sh <jar> <outdir> [--no-globe]
# Checks: --headless-check exits 0 and reports the AMQP transport (not the loopback
# fallback), the UI comes up, and robot/smoke.robot passes. Globe is on unless --no-globe.
# Linux without a DISPLAY runs under xvfb-run; macOS and Windows use the desktop.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${1:?usage: test-jar.sh <jar> <outdir> [--no-globe]}"
OUT="${2:?usage: test-jar.sh <jar> <outdir> [--no-globe]}"
APP_FLAGS="${3:-}"
BASE=http://127.0.0.1:17355
mkdir -p "$OUT"

if [ -x "$ROOT/robot/.venv/bin/robot" ]; then ROBOT="$ROOT/robot/.venv/bin/robot"
elif [ -x "$ROOT/robot/.venv/Scripts/robot.exe" ]; then ROBOT="$ROOT/robot/.venv/Scripts/robot.exe"
else ROBOT=robot; fi

java -jar "$JAR" --headless-check >"$OUT/headless.log" 2>&1
rc=$?
transport=$(grep -o 'headless transport=[^)]*' "$OUT/headless.log" | head -1)
echo "headless-check exit=$rc $transport"
if [ $rc -ne 0 ] || ! echo "$transport" | grep -q 'AMQP 1.0'; then
    echo "RESULT: FAIL (headless-check or AMQP transport lost)"; tail -20 "$OUT/headless.log"; exit 1
fi

launch=(java -jar "$JAR" $APP_FLAGS)
if [ "$(uname -s)" = Linux ] && [ -z "${DISPLAY:-}" ]; then
    launch=(xvfb-run -a --server-args="-screen 0 1600x1000x24" "${launch[@]}")
fi
"${launch[@]}" >"$OUT/app.log" 2>&1 &
pid=$!
trap 'kill $pid 2>/dev/null; wait $pid 2>/dev/null' EXIT
for _ in $(seq 1 90); do
    curl -fsS "$BASE/health" >/dev/null 2>&1 && break
    kill -0 $pid 2>/dev/null || break
    sleep 1
done
if ! curl -fsS "$BASE/health" >/dev/null 2>&1; then
    echo "RESULT: FAIL (app never became healthy)"; tail -30 "$OUT/app.log"; exit 2
fi
[ -z "$APP_FLAGS" ] && sleep 8
curl -fsS "$BASE/ui/focus" >/dev/null 2>&1

"$ROBOT" --outputdir "$OUT/robot" "$ROOT/robot/smoke.robot" >"$OUT/robot.log" 2>&1
rc=$?
echo "robot: $(grep -c '| PASS |' "$OUT/robot.log") passed, $(grep -c '| FAIL |' "$OUT/robot.log") failed"
grep '| FAIL |' "$OUT/robot.log"
kill -0 $pid 2>/dev/null || { echo "RESULT: FAIL (app died during suite)"; exit 3; }
if grep -qE 'NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsatisfiedLinkError' "$OUT/app.log"; then
    echo "RESULT: FAIL (linkage error in app.log)"
    grep -E 'NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsatisfiedLinkError' "$OUT/app.log" | head -5
    exit 4
fi
[ $rc -eq 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL (robot rc=$rc)"
exit $rc
