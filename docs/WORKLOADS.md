# Datacenter and Workload Models

## Datacenter (`config/experiment.properties`)

| Item | Value |
|---|---|
| Physical hosts | 4 × (4 PEs × 3,000 MIPS, 64 GB RAM), `VmSchedulerTimeShared`, `VmAllocationPolicySimple` |
| VMs | 8 single-PE VMs: **500, 750, 1000, 1000, 1500, 1500, 2000, 2500 MIPS** (total 10,750 MIPS, mean 1,343.75) |
| VM cloudlet scheduler | `CloudletSchedulerSpaceShared`, so one task at a time; the broker feeds it |

The 5× spread in VM speed is deliberate. It makes VM choice matter (it penalises Round Robin and rewards GA and fastest-idle-first dispatch), and it makes utilisation and imbalance metrics meaningful.

## Load model

Each workload is generated for a target **offered load ρ**:

```
λ (tasks/s) = ρ × total VM MIPS / mean task length
```

At ρ = 1.0 the datacenter is exactly saturated on average. The experiments use ρ = 0.70 (moderate), 0.90 (heavy) and 1.05 (overload, where the backlog grows and starvation shows up). Every run has 1,000 tasks.

## Scenarios (`workload/WorkloadGenerator`)

| Scenario | Task length (MI) | Arrival process | Priority mix H / M / L |
|---|---|---|---|
| **SHORT_BURSTY** | Uniform 2k–20k | Bursts: burst size 1 + Geometric(mean ≈ 8), tight gaps inside a burst (exp., mean 2 % of a service time), exponential gaps between bursts | 20 / 50 / 30 % |
| **LONG_BATCH** | Uniform 80k–600k | Batches of 5–15 jobs submitted at the same instant, exponential gaps between batches | 10 / 40 / 50 % |
| **MIXED_PRIORITY** | Bimodal: 70 % 2k–20k, 30 % 80k–600k | Poisson | 30 / 30 / 40 % |
| **PHASED** | 4 consecutive phases of 250 tasks each: SHORT_BURSTY → LONG_BATCH → MIXED_PRIORITY → SHORT_BURSTY, each at load ρ | per phase | per phase |
| **GOCJ** | GoCJ size bands (below), or a real GoCJ file via `--gocj` | Poisson | 25 / 40 / 35 % |

**GoCJ size bands** (Hussain & Aleem, 2018): small 15k–55k MI (20 %), medium 59k–99k (40 %), large 101k–135k (30 %), extra-large 150k–337.5k (4 %), huge 525k–900k (6 %). GoCJ ships job sizes only, so arrivals and priorities are generated.

**PHASED** is the key scenario for the synopsis's adaptivity claim. No single static policy fits all four phases.

## SLA deadlines

```
deadline = arrival + slack(class) × length / mean VM MIPS
slack: HIGH = 4, MEDIUM = 8, LOW = 16
```

A task violates its SLA if it finishes after its deadline. HIGH tasks have the tightest deadlines. That's why the HIGH-priority SLA-violation rate is the measure of whether a fairness mechanism hurts important work.

## Workload CSV format

The format is used by `generate` (output) and by `run --workload` (input):

```
id,arrival_time,length_mi,priority,deadline,phase
0,4.940,15155,MEDIUM,95.165,SHORT_BURSTY
```

The workloads used for seed 1 are saved in `workloads/`.
