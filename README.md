# AS-HTS: Adaptive Starvation-Aware Hybrid Task Scheduler for Cloud Datacenters

**A CloudSim-based extension for priority scheduling with waiting-time escalation**

Cloud Computing Mini Project (ICT 4441), School of Computer Engineering, Manipal Institute of Technology

| Name | Reg. No. |
|---|---|
| K. V. Ananth Karantha | 230911006 |
| K. V. Aparna Karanth | 230911004 |
| Tejas K Rao | 230911009 |
| Amith U Nayak | 230911248 |

---

## 1. What this project does

The project simulates a heterogeneous cloud datacenter in **CloudSim 3.0.3**. Tasks (cloudlets) arrive over time with different lengths, priorities (HIGH / MEDIUM / LOW) and SLA deadlines. It compares **six schedulers** on identical workloads:

| Scheduler | Source | Idea |
|---|---|---|
| FCFS | CloudSim-Examples (Ravikisha) | Oldest task first |
| SJF | CloudSim-Examples (Ravikisha) | Shortest task first (non-preemptive) |
| Round Robin | CloudSim-Examples (Ravikisha) | Task *i* → VM *i mod n* |
| Genetic (GA) | CloudSim-Examples (Ravikisha) | Online batch GA that minimises estimated makespan |
| Priority + Escalation | CloudSim-Algorithm-Project (Silver-T) | `P = level × (100 + wait)`, a fixed waiting-time weight |
| **AS-HTS (proposed)** | this project | Runtime workload classifier, policy switching (SJF ↔ escalation) and a queue-depth-adaptive escalation rate |

It reports makespan, average, p95 and maximum waiting time (overall and per priority class), VM utilisation, the SLA-violation rate (overall and per class) and the degree of imbalance. It also runs an **ablation study** of AS-HTS's two components.

> **Key result (load ρ = 1.05, 5 seeds):** compared with the Silver-T starvation-aware priority baseline, AS-HTS cuts the **maximum waiting time of LOW-priority tasks by 16–24 %** in every workload. High-priority makespan stays within 5 %, and the HIGH-priority SLA-violation rate moves by −6.6 to +2.9 percentage points. In short-bursty traffic its SJF mode also cuts average waiting time by about 20 %. Full numbers and an honest discussion of the trade-offs are in [`docs/RESULTS.md`](docs/RESULTS.md).

---

## 2. Requirements

| Needed for | Requirement |
|---|---|
| Simulation (Java part) | **JDK 8 or newer** (`java -version` and `javac -version` must both work). Tested with JDK 21 and compiled to Java 8 bytecode. |
| Graphs and tables (Python part) | **Python 3.8+** with `pandas`, `matplotlib`, `numpy` |
| Maven | **Not required.** CloudSim is bundled in `lib/`. A `pom.xml` is included only for opening the project in an IDE. |
| Internet | Not required |

Install the Python packages once:

```bash
pip install -r analysis/requirements.txt
```

---

## 3. Quick start

### Linux / macOS

```bash
cd AS-HTS
chmod +x scripts/*.sh
scripts/build.sh                      # compile (a few seconds)
scripts/run.sh validate               # sanity checks: should print "All validation checks passed."
scripts/run.sh demo                   # 6 schedulers on the Phased workload, comparison table
scripts/run_all.sh                    # FULL reproduction: build + validate + 600 simulations + all graphs
```

### Windows (Command Prompt or PowerShell)

```bat
cd AS-HTS
scripts\build.bat
scripts\run.bat validate
scripts\run.bat demo
scripts\run_all.bat
```

The full experiment (600 simulations of 1,000 tasks each) takes **about 15–30 seconds**. When it finishes:

* `results/summary.csv` has one row per simulation run.
* `results/summary_table.md` has all the result tables (mean ± std), including the headline comparison.
* `results/figures/*.png` holds the 11 graphs used in the paper.

**Precomputed results are already included** in `results/`, so you can look at the graphs before running anything.

---

## 4. All commands

Run `scripts/run.sh` (or `scripts\run.bat`) with no arguments to see this help.

| Command | What it does | Example |
|---|---|---|
| `validate` | Checks that a 10,000 MI task on a 2,500 MIPS VM takes exactly 4 s, and that every policy completes a 300-task workload with correct execution times | `scripts/run.sh validate` |
| `demo` | Runs the 6 main schedulers on one workload and prints a table (good for the video) | `scripts/run.sh demo --scenario MIXED_PRIORITY --load 1.05` |
| `run` | Runs one or more schedulers on one workload and writes per-task CSVs (and the AS-HTS regime timeline) | `scripts/run.sh run --scenario GOCJ --load 0.9 --policy ASHTS,SJF` |
| `generate` | Writes a workload to CSV | `scripts/run.sh generate --scenario PHASED --load 0.9 --seed 3` |
| `experiment` | Runs the full grid from a config file | `scripts/run.sh experiment --config config/experiment.properties` |

Then draw the graphs:

```bash
python3 analysis/plot_results.py --results results --figures results/figures
```

**Options**

* `--scenario` takes `SHORT_BURSTY`, `LONG_BATCH`, `MIXED_PRIORITY`, `PHASED` or `GOCJ`.
* `--load` is the offered load ρ. 1.0 means the datacenter is exactly saturated on average.
* `--policy` takes `FCFS`, `SJF`, `RR`, `GA`, `PRIORITY_ESC`, `ASHTS`, `ASHTS_FIXED_ALPHA`, `ASHTS_NO_SWITCH` or `ALL`.
* Other options: `--seed N`, `--tasks N`, `--out DIR` and `--verbose true` (prints CloudSim's own event log).
* `--workload file.csv` runs your own workload. The columns are `id,arrival_time,length_mi,priority,deadline,phase`.
* `--gocj file.txt` uses a **real GoCJ dataset file** (one job size in MI per line) for the GOCJ scenario. GoCJ (Hussain & Aleem, 2018) is published on Mendeley Data.
* **Any parameter** can be overridden inline, e.g. `--ashts.alpha0 0.2 --dc.vm.mips 1000,1000,2000`.

**Quick smoke test** (about 3 s, writes to `results_quick/`):

```bash
scripts/run.sh experiment --config config/quick.properties
python3 analysis/plot_results.py --results results_quick
```

---

## 5. Project structure

```
AS-HTS/
├── README.md                     ← this file
├── pom.xml                       ← optional Maven/IDE build (uses the bundled jar)
├── lib/
│   └── cloudsim-3.0.3-core.jar   ← CloudSim 3.0.3 core, built from the official Cloudslab source
├── config/
│   ├── experiment.properties     ← all parameters + the full experiment grid
│   └── quick.properties          ← small grid for a quick check
├── scripts/                      ← build / run / run_all  (.sh for Linux/macOS, .bat for Windows)
├── src/main/java/edu/manipal/ashts/
│   ├── Main.java                 ← command-line interface
│   ├── core/                     ← TaskSpec, TaskRecord, VmInfo, PriorityClass, SimConfig
│   ├── datacenter/               ← DatacenterFactory: hosts + heterogeneous VMs
│   ├── workload/                 ← seeded workload generator (5 scenarios), CSV / GoCJ I/O
│   ├── broker/                   ← QueueingBroker: CloudSim DatacenterBroker with a ready queue
│   ├── policy/                   ← FCFS, SJF, RR, GA, PriorityEscalation, AS-HTS, PolicyFactory
│   ├── classifier/               ← AS-HTS runtime workload classifier
│   ├── metrics/                  ← metric computation + CSV writers
│   └── experiment/               ← single-run and grid runners
├── analysis/
│   ├── plot_results.py           ← tables + 11 figures
│   └── requirements.txt
├── docs/
│   ├── DESIGN.md                 ← architecture and algorithms (with pseudocode)
│   ├── WORKLOADS.md              ← datacenter and workload models
│   ├── METRICS.md                ← exact metric definitions
│   └── RESULTS.md                ← findings, ablation, limitations, figure guide
├── results/                      ← precomputed outputs (regenerated by run_all)
│   ├── summary.csv  summary_aggregated.csv  summary_table.md
│   ├── tasks/                    ← per-task logs (seed 1) for every scenario/load/policy
│   ├── timeline/                 ← AS-HTS classifier timelines
│   └── figures/                  ← fig1 … fig11
└── workloads/                    ← the generated workloads (seed 1) as CSV
```

---

## 6. How AS-HTS works (summary)

1. **Dynamic arrivals and a real ready queue.** Stock CloudSim binds every cloudlet to a VM at time 0, so tasks never wait at the broker and starvation can't be observed. `QueueingBroker` extends `DatacenterBroker` so that tasks arrive at their arrival times and wait in a queue. Whenever a VM (1 PE, space-shared) becomes idle, the plugged-in policy chooses which task it runs. All baselines were re-implemented as policies in this same broker, so the comparison is fair.
2. **Runtime workload classifier.** It reads a rolling window of the last 30 arrivals: mean length, coefficient of variation and the HIGH/LOW share. From these it labels the regime **SHORT_BURSTY**, **LONG_BATCH** or **MIXED_PRIORITY**. Hysteresis (8 agreeing labels) prevents flip-flopping.
3. **Policy switching.**
   * In SHORT_BURSTY it uses a priority-weighted SJF (Smith's rule, `level / length`) with an aging cap.
   * In LONG_BATCH and MIXED_PRIORITY it uses starvation-aware escalation.
4. **Adaptive escalation.** The score is `S = level + α(q) · wait / refExec`, with `α(q) = min(α0·(1 + β·q/#VMs), 10·α0)`. When the queue is short, AS-HTS behaves almost like strict priority. As the backlog `q` grows, waiting time counts for more, so LOW tasks cannot starve. Normalising by `refExec` (the typical execution time of current tasks) makes the rule work the same way for 2 s tasks and 400 s jobs.

Details, pseudocode and design rationale are in [`docs/DESIGN.md`](docs/DESIGN.md).

---

## 7. Mapping to the synopsis

| Synopsis item | Where it is |
|---|---|
| CloudSim datacenter with heterogeneous hosts and VMs | `datacenter/DatacenterFactory.java`, `config/experiment.properties` |
| Synthetic and GoCJ-like workloads with varying burst length, arrival rate and priority | `workload/WorkloadGenerator.java`, `docs/WORKLOADS.md` |
| Baselines: FCFS, SJF, RR, Genetic, priority-with-escalation | `policy/*Policy.java` |
| Runtime workload classifier | `classifier/WorkloadClassifier.java` |
| Policy-switching layer | `policy/AsHtsPolicy.java` (`selectNext`) |
| Escalation rate adaptive to queue depth | `policy/AsHtsPolicy.java` (`alpha`) |
| SLA-violation-rate and imbalance tracking per priority class | `metrics/Metrics.java`, `docs/METRICS.md` |
| Comparative graphs | `analysis/plot_results.py`, `results/figures/` |
| Starvation reduction quantified | `results/summary_table.md` (headline table), `docs/RESULTS.md` |

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `javac: command not found` | Install a JDK, not just a JRE (e.g. Temurin 17), and add it to `PATH` |
| `Permission denied` on `.sh` | `chmod +x scripts/*.sh` |
| `ModuleNotFoundError: pandas` | `pip install -r analysis/requirements.txt` (on Linux you may need `pip install --user …` or a venv) |
| `python3` not found on Windows | Use `python analysis\plot_results.py …` |
| You change Java code and nothing happens | Re-run `scripts/build.sh` (`run.sh` only builds when `build/` is missing) |
| Opening in IntelliJ / Eclipse | Import as a Maven project (`pom.xml`), or add `lib/cloudsim-3.0.3-core.jar` as a library and mark `src/main/java` as the source root. The main class is `edu.manipal.ashts.Main`, with program argument `demo`. |

---

## 9. Credits and licences

* **CloudSim 3.0.3** — CLOUDS Lab, University of Melbourne (GPL). `lib/cloudsim-3.0.3-core.jar` was compiled from the official `Cloudslab/cloudsim` tag `cloudsim-3.0.3`. The `power` package and `MathUtil` were left out because they need Apache Commons Math and this project doesn't use them.
* Baseline algorithms adapted from **Silver-T/CloudSim-Algorithm-Project** (priority escalation formula) and **Ravikisha/CloudSim-Examples** (FCFS, SJF, RR, Genetic).
* GoCJ job-size bands: A. Hussain and M. Aleem, "GoCJ: Google Cloud Jobs Dataset for Distributed and Cloud Computing Infrastructures", *Data*, 3(4), 38, 2018.
