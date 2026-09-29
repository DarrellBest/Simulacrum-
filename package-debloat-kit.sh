#!/usr/bin/env bash
# Tar up source, Robot suite, debloat scripts and report. ArtusCmd is not included.
set -uo pipefail
cd "$(dirname "$0")"

name="simulacrum-debloat-kit"
stage="dist/$name"
rm -rf "$stage" && mkdir -p "$stage"

cp -r src build.gradle.kts settings.gradle.kts gradle gradlew gradlew.bat \
      robot debloat DEBLOAT_REPORT.md README.md "$stage/"

tar -czf "dist/$name.tar.gz" -C dist "$name"
rm -rf "$stage"

echo "wrote dist/$name.tar.gz"
echo "use: tar -xzf $name.tar.gz && cd $name && ./gradlew shadowJar && ARTUS_LIB=/path/to/ArtusCmd/lib bash debloat/sweep.sh"
