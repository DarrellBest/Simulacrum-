#!/usr/bin/env bash
# Build Simulacrum, robot-test the shadow jar, debloat it with ArtusCmd, robot-test again.
# Supply ArtusCmd with --artus-lib DIR or --artus-tgz FILE (it is not bundled).
set -uo pipefail
cd "$(dirname "$0")"

ARTUS_LIB="${ARTUS_LIB:-}"
ARTUS_TGZ=""
JV=21
APP_FLAGS="--no-globe"   # --globe brings up the WorldWind 3D view too
BASE="http://127.0.0.1:17355"
SAFE_FLAGS="-rdb -rdc -rmr -rej -ruc -re"

while [ $# -gt 0 ]; do
    case "$1" in
        --artus-lib) ARTUS_LIB="$2"; shift 2;;
        --artus-tgz) ARTUS_TGZ="$2"; shift 2;;
        --jv) JV="$2"; shift 2;;
        --globe) APP_FLAGS=""; shift;;
        -h|--help) echo "usage: bash debloat-test.sh [--artus-lib DIR | --artus-tgz FILE] [--jv N] [--globe]"; exit 0;;
        *) echo "unknown arg: $1" >&2; exit 1;;
    esac
done

die() { echo "ERROR: $*" >&2; exit 1; }
size() { wc -c < "$1" | tr -d ' '; }

# The unix gradlew mis-resolves a Windows JAVA_HOME under MSYS, so use the .bat there.
GRADLE=./gradlew
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) GRADLE=./gradlew.bat;; esac

if command -v robot >/dev/null 2>&1; then ROBOT="robot"; else ROBOT="python -m robot.run"; fi

if [ -n "$ARTUS_TGZ" ]; then
    # Normalize to a /c/... path; tar reads a "C:/..." arg as a remote host otherwise.
    ARTUS_TGZ="$(cd "$(dirname "$ARTUS_TGZ")" && pwd)/$(basename "$ARTUS_TGZ")"
    rm -rf .artus && mkdir .artus
    tar -xzf "$ARTUS_TGZ" -C .artus || die "cannot extract $ARTUS_TGZ"
    ARTUS_LIB="$(find .artus -type d -name lib -path '*ArtusCmd*' | head -1)"
fi
[ -f "$ARTUS_LIB/ArtusCmd.jar" ] || die "ArtusCmd.jar not found in '$ARTUS_LIB' (use --artus-lib or --artus-tgz)"

artus() { java -Xmx16g -cp "$ARTUS_LIB/*" com.pjrcorp.artus.cmd.ArtusCmdMain "$@"; }

# Launch a jar, wait for its test-control endpoint, run the suite, tear down.
# Echoes the robot exit code (0 = pass), or 2 if the app never came up.
run_suite() {
    local jar="$1" outdir="$2" pid ready=""
    mkdir -p "$outdir"
    java -jar "$jar" $APP_FLAGS >"$outdir/app.log" 2>&1 &
    pid=$!
    for _ in $(seq 1 60); do
        curl -fsS "$BASE/health" >/dev/null 2>&1 && { ready=1; break; }
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
    done
    if [ -z "$ready" ]; then
        echo "  app never became ready:"; sed 's/\x1b\[[0-9;]*m//g' "$outdir/app.log" | tail -n 12
        kill "$pid" 2>/dev/null; return 2
    fi
    $ROBOT --outputdir "$outdir" robot/smoke.robot
    local rc=$?
    kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
    return $rc
}

OUT=build/debloat-kit

echo "## build"
$GRADLE clean build --console=plain || die "build failed"
JAR=build/libs/simulacrum-all.jar
[ -f "$JAR" ] || die "shadow jar missing: $JAR"

echo "## robot: baseline"
run_suite "$JAR" "$OUT/robot-baseline"; base_rc=$?

echo "## scan"
artus scan -jv "$JV" -fo -rd -o "$OUT/scan" "$JAR" 2>&1 | grep -v stty: | tail -n 20
dnr=""; [ -f "$OUT/scan/dnr_classlist.txt" ] && dnr="-dnr $OUT/scan/dnr_classlist.txt"

echo "## debloat: $SAFE_FLAGS"
DJAR=build/libs/simulacrum-all-debloated.jar
artus process -jv "$JV" -fo $SAFE_FLAGS $dnr -o "$DJAR" "$JAR" 2>&1 | grep -v stty: | grep -A8 "Summary Results"
[ -f "$DJAR" ] || die "debloated jar missing: $DJAR"

echo "## robot: debloated"
run_suite "$DJAR" "$OUT/robot-debloated"; deb_rc=$?

verdict() { case "$1" in 0) echo PASS;; 2) echo "FAIL (did not start)";; *) echo FAIL;; esac; }
reduction=$(awk "BEGIN{printf \"%.1f%%\", (1-$(size "$DJAR")/$(size "$JAR"))*100}")

echo
echo "## summary"
printf "  baseline   %10s bytes   %s\n" "$(size "$JAR")"  "$(verdict $base_rc)"
printf "  debloated  %10s bytes   %s   (-%s)\n" "$(size "$DJAR")" "$(verdict $deb_rc)" "$reduction"
echo "  reports: $OUT/robot-baseline , $OUT/robot-debloated"
exit "$deb_rc"
