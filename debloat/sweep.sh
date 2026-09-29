#!/usr/bin/env bash
# Debloat build/libs/simulacrum-all.jar with ArtusCmd one flag at a time.
#
# Usage: debloat/sweep.sh [--artus-lib DIR] [--artus-java JAVA] [--jar FILE] [--out DIR] [--no-globe]
#   --artus-lib   ArtusCmd/lib directory        (default: $ARTUS_LIB)
#   --artus-java  java binary for ArtusCmd, must be JDK 25+   (default: $ARTUS_JAVA, else java)
#   --jar         input jar                     (default: build/libs/simulacrum-all.jar)
#   --out         output directory              (default: build/debloat)
#   --no-globe    run the UI without WorldWind (Xvfb without GL, CI)
#
# Steps: baseline test, stats/preflight/scan, then process with each flag added in turn:
#   -rdb -rdc -rmr -re -rej -ruc -rum
# A flag whose output fails debloat/test-jar.sh is dropped and the sweep continues.
# -ruc and -rum use the entry-point and do-not-remove lists that scan produces.
# No -a/--aggressiveness preset is used. The best passing jar is copied to
# <out>/simulacrum-all-debloated.jar. Requires JDK 21 on PATH for the app.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ARTUS_LIB="${ARTUS_LIB:-}"
ARTUS_JAVA="${ARTUS_JAVA:-java}"
JAR=build/libs/simulacrum-all.jar
OUT=build/debloat
APP_FLAGS=""
while [ $# -gt 0 ]; do
    case "$1" in
        --artus-lib) ARTUS_LIB="$2"; shift 2;;
        --artus-java) ARTUS_JAVA="$2"; shift 2;;
        --jar) JAR="$2"; shift 2;;
        --out) OUT="$2"; shift 2;;
        --no-globe) APP_FLAGS="--no-globe"; shift;;
        -h|--help) sed -n '2,16p' "$0"; exit 0;;
        *) echo "unknown arg: $1" >&2; exit 1;;
    esac
done
[ -f "$ARTUS_LIB/ArtusCmd.jar" ] || { echo "ArtusCmd.jar not found in '$ARTUS_LIB' (set ARTUS_LIB or --artus-lib)" >&2; exit 1; }
[ -f "$JAR" ] || { echo "jar not found: $JAR (run ./gradlew shadowJar)" >&2; exit 1; }
mkdir -p "$OUT"
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"
OUT="$(cd "$OUT" && pwd)"

artus() { "$ARTUS_JAVA" -Xms2g -Xmx16g -cp "$ARTUS_LIB/*" com.pjrcorp.artus.cmd.ArtusCmdMain "$@"; }
strip() { sed 's/\x1b\[[0-9;]*m//g'; }
size() { wc -c < "$1" | tr -d ' '; }

echo "## baseline"
mkdir -p "$OUT/baseline"
debloat/test-jar.sh "$JAR" "$OUT/baseline" $APP_FLAGS | tee "$OUT/baseline/test.log" | tail -3

echo "## artus stats / preflight / scan"
cd "$(dirname "$JAR")"
artus -rf decorated,json stats all "$(basename "$JAR")" -p "$OUT/stats.txt,$OUT/stats.json" 2>&1 | strip | grep -E 'filesize|classes|methods'
artus -rf decorated,json preflight -rd -jv 21 "$(basename "$JAR")" -p "$OUT/preflight.txt,$OUT/preflight.json" >/dev/null 2>&1
artus -rf decorated,json scan -rd -jv 21 -fo -o "$OUT/scan" "$(basename "$JAR")" -p "$OUT/scan.txt,$OUT/scan.json" 2>&1 | strip | grep -A1 Recommended
cd "$ROOT"
ENTRY="$OUT/scan/runtime_entrypoints.txt"
DNR="$OUT/scan/dnr_classlist.txt"
# scan does not list main(); without it -rum removes the launcher.
printf '<com.simulacrum.Launcher: void main(java.lang.String[])>\n<com.simulacrum.App: void main(java.lang.String[])>\n' >> "$ENTRY"
REACH="-e $ENTRY -dnr $DNR"

kept=""
best="$JAR"
n=0
printf '\n%-10s %-32s %12s  %s\n' step flags bytes result | tee "$OUT/summary.txt"
printf '%-10s %-32s %12s  %s\n' baseline "(none)" "$(size "$JAR")" "$(grep -o 'RESULT: .*' "$OUT/baseline/test.log" | tail -1)" | tee -a "$OUT/summary.txt"
for flag in -rdb -rdc -rmr -re -rej -ruc -rum; do
    n=$((n+1))
    step=$(printf 'step-%02d%s' $n "$flag")
    dir="$OUT/$step"; mkdir -p "$dir"
    extra=""; case $flag in -ruc|-rum) extra="$REACH";; esac
    echo "## $step: $kept $flag"
    ( cd "$(dirname "$JAR")" && artus process -jv 21 -fo -rd -rf decorated,json $extra $kept $flag \
        -o "$dir/simulacrum-all.jar" "$(basename "$JAR")" -p "$dir/process.txt,$dir/process.json" ) >"$dir/artus.log" 2>&1
    if [ ! -f "$dir/simulacrum-all.jar" ]; then
        echo "artus failed"; strip < "$dir/artus.log" | tail -10
        printf '%-10s %-32s %12s  %s\n' "$step" "$kept $flag" - "FAIL (artus)" | tee -a "$OUT/summary.txt"; continue
    fi
    debloat/test-jar.sh "$dir/simulacrum-all.jar" "$dir/test" $APP_FLAGS | tee "$dir/test.log" | tail -4
    result=$(grep -o 'RESULT: .*' "$dir/test.log" | tail -1)
    printf '%-10s %-32s %12s  %s\n' "$step" "$kept $flag" "$(size "$dir/simulacrum-all.jar")" "$result" | tee -a "$OUT/summary.txt"
    if [ "$result" = "RESULT: PASS" ]; then kept="$kept $flag"; best="$dir/simulacrum-all.jar"; fi
done

cp "$best" "$OUT/simulacrum-all-debloated.jar"
echo
echo "## final flags:$kept"
echo "## $OUT/simulacrum-all-debloated.jar  $(size "$JAR") -> $(size "$best") bytes"
( cd "$(dirname "$JAR")" && artus -rf decorated,json diff "$(basename "$JAR")" "$OUT/simulacrum-all-debloated.jar" -p "$OUT/diff.txt,$OUT/diff.json" ) 2>&1 | strip | grep -E 'filesize|classes|methods'
