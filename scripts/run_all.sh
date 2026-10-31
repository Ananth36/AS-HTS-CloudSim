#!/usr/bin/env bash
# One-shot reproduction: build -> validate -> full experiment grid -> plots.
set -e
cd "$(dirname "$0")/.."
scripts/build.sh
scripts/run.sh validate
scripts/run.sh experiment --config config/experiment.properties
python3 analysis/plot_results.py --results results --figures results/figures
echo
echo "Done. Open results/figures/ for the graphs and results/summary_table.md for the tables."
