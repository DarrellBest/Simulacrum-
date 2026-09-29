#!/usr/bin/env bash
# Build the shadow jar, set up robot/.venv, run the Robot suite, launch the app.
# Usage: bash bootstrap.sh [--no-tests] [--no-launch]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

NO_TESTS=0
NO_LAUNCH=0
for arg in "$@"; do
    case "$arg" in
        --no-tests)  NO_TESTS=1 ;;
        --no-launch) NO_LAUNCH=1 ;;
        -h|--help)
            sed -n '2,3p' "$0"; exit 0 ;;
        *) echo "Unknown flag: $arg" >&2; exit 2 ;;
    esac
done

echo "==> Checking JDK 21"
if ! command -v java >/dev/null; then
    echo "Java not found on PATH. Install JDK 21 and re-run." >&2
    exit 1
fi
VER_LINE="$(java -version 2>&1 | head -n1)"
echo "    $VER_LINE"
if ! echo "$VER_LINE" | grep -Eq '"21\.|version 21'; then
    echo "Warning: Java is not 21." >&2
fi

echo "==> Building shadow jar (./gradlew shadowJar)"
chmod +x ./gradlew
./gradlew shadowJar --console=plain
JAR="$ROOT/build/libs/simulacrum-all.jar"
[[ -f "$JAR" ]] || { echo "Build succeeded but jar missing at $JAR" >&2; exit 3; }
SIZE_MB="$(du -m "$JAR" | awk '{print $1}')"
echo "    Built: $JAR  (${SIZE_MB} MB)"

if [[ "$NO_TESTS" -eq 0 ]]; then
    echo "==> Setting up Robot Framework venv at robot/.venv"
    bash "$ROOT/robot/setup-venv.sh"

    echo "==> Running Robot Framework smoke suite (under xvfb-run)"
    if ! command -v xvfb-run >/dev/null; then
        echo "xvfb-run not on PATH; install xvfb (e.g. 'sudo apt install xvfb') or re-run with --no-tests." >&2
        exit 4
    fi
    bash "$ROOT/robot/run-under-xvfb.sh"
else
    echo "==> Skipping Robot Framework suite (--no-tests)"
fi

if [[ "$NO_LAUNCH" -eq 0 ]]; then
    echo "==> Launching Simulacrum (close the window to exit)"
    java -jar "$JAR"
else
    echo
    echo "Bootstrap complete. Launch the app manually with:"
    echo "    java -jar \"$JAR\""
fi
