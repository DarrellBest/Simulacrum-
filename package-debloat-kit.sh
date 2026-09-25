#!/usr/bin/env bash
# Bundle the Simulacrum source + debloat harness + report into one tarball for handoff.
# ArtusCmd and build output are intentionally left out.
set -uo pipefail
cd "$(dirname "$0")"

name="simulacrum-debloat-kit"
stage="dist/$name"
rm -rf "$stage" && mkdir -p "$stage"

cp -r src build.gradle.kts settings.gradle.kts gradle gradlew gradlew.bat \
      robot debloat-test.sh DEBLOAT_REPORT.md README.md "$stage/"

tar -czf "dist/$name.tar.gz" -C dist "$name"
rm -rf "$stage"

echo "wrote dist/$name.tar.gz"
echo "use: tar -xzf $name.tar.gz && cd $name && bash debloat-test.sh --artus-tgz /path/to/ArtusCmd-13.2.0.tar.gz"
