#!/usr/bin/env bash
# Compiles AS-HTS against the bundled CloudSim jar. Needs only a JDK (8 or newer).
set -e
cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes
find src/main/java -name "*.java" > build/sources.txt
javac -encoding UTF-8 -nowarn -cp "lib/cloudsim-3.0.3-core.jar" -d build/classes @build/sources.txt
echo "Build OK -> build/classes"
