# Metric Definitions (`metrics/Metrics.java`)

All times are in simulated seconds, measured from the moment the VMs are ready. For task *i*: `a_i` is its arrival, `s_i` its dispatch (= execution start), `f_i` its finish and `d_i` its deadline.

| Column in `summary.csv` | Definition | Notes |
|---|---|---|
| `makespan` | max *f_i* − min *a_i* | Total time to clear the workload |
| `avg_wait` | mean (*s_i* − *a_i*) | Waiting at the scheduler |
| `p95_wait` / `max_wait` | 95th percentile / maximum of the waiting time | |
| `avg_turnaround` | mean (*f_i* − *a_i*) | |
| `avg_wait_{HIGH,MEDIUM,LOW}` | mean wait within a class | |
| `max_wait_{HIGH,MEDIUM,LOW}` | max wait within a class | **`max_wait_LOW` is the starvation metric** from the synopsis |
| `p95_wait_LOW` | 95th-percentile wait of LOW tasks | A more robust starvation measure |
| `avg_turnaround_HIGH` | mean turnaround of HIGH tasks | |
| `makespan_HIGH` | max *f_i* − min *a_i* over HIGH tasks | "High-priority makespan" from the synopsis's objective |
| `sla_rate` | share of tasks with *f_i* > *d_i* | |
| `sla_rate_{HIGH,MEDIUM,LOW}` | the same, per class | `sla_rate_HIGH` is the key cost measure for any fairness mechanism |
| `vm_util` | Σ VM busy time / (#VMs × makespan) | |
| `di_vm` | (max − min) / mean of per-VM busy time | Degree of imbalance (DI) across VMs |
| `wait_ratio_low_high` | avg_wait_LOW / avg_wait_HIGH | Priority imbalance: how much more LOW tasks wait than HIGH |
| `regime_switches` | number of stable-regime changes made by the AS-HTS classifier | 0 for static policies |
| `exec_dev_max` | max \|(*f_i* − *s_i*) − length_i / MIPS\| | Simulator self-check; at most 0.011 s in all 600 runs (CloudSim event-time rounding) |

`analysis/plot_results.py` converts rates and utilisation to percentages. It reports mean ± sample standard deviation over the 5 seeds.
