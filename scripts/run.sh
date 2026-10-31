#!/usr/bin/env bash
# Runs the AS-HTS command-line tool. Builds first if needed.
#   scripts/run.sh validate
#   scripts/run.sh demo --scenario PHASED --load 0.9
#   scripts/run.sh experiment --config config/experiment.properties
set -e
cd "$(dirname "$0")/.."
if [ ! -d build/classes/edu ]; then scripts/build.sh; fi
exec java -cp "build/classes:lib/cloudsim-3.0.3-core.jar" edu.manipal.ashts.Main "$@"
