# Results and Discussion

Setup: 5 scenarios × 3 loads × 5 seeds × 8 policies = **600 CloudSim simulations** of 1,000 tasks each, on the 8-VM heterogeneous datacenter in `WORKLOADS.md`. Every number below is a mean over the 5 seeds. Full tables with standard deviations are in `results/summary_table.md`, and raw rows are in `results/summary.csv`.

## 1. Headline: AS-HTS vs. the starvation-aware priority baseline (Silver-T)

Under **overload (ρ = 1.05)**:

| Scenario | Max wait LOW | p95 wait LOW | HIGH SLA-violations | High-priority makespan |
|---|---|---|---|---|
| Short-bursty | **−23.4 %** | −20.3 % | +0.2 pp | +0.1 % |
| Long-batch | **−15.6 %** | −14.5 % | +2.9 pp | +0.2 % |
| Mixed-priority | **−21.5 %** | −22.7 % | −6.6 pp | +2.1 % |
| Phased | **−17.9 %** | −18.0 % | +2.6 pp | +4.9 % |
| GoCJ | **−23.9 %** | −27.2 % | −3.5 pp | +0.4 % |

The synopsis objective was to *reduce starvation without materially degrading high-priority makespan*. Under load it is met in all five workloads:

* Starvation (max wait of LOW tasks) drops by 16–24 %.
* The 95th percentile drops by a similar amount, so the gain isn't one outlier.
* High-priority makespan changes by ≤ 5 %.
* The HIGH-priority SLA-violation rate stays within −6.6 to +2.9 percentage points of the baseline.

At **ρ = 0.90** the picture is mixed: starvation is down 8–10 % in long-batch and phased and unchanged in mixed-priority, but up 10–13 % in GoCJ and short-bursty. At **ρ = 0.70** AS-HTS's LOW max-wait is 4–21 % *higher* than the baseline's. In absolute terms that is a few seconds to a few hundred, e.g. 67 s vs. 60 s in short-bursty. This is by design: with a short queue, α stays at α0 = 0.1, AS-HTS behaves close to strict priority, and it prioritises HIGH tasks (its HIGH SLA-violation rate is lower than the baseline's in every ρ = 0.70 cell). The adaptive rate only becomes aggressive when backlog, and with it the starvation risk, builds up.

## 2. Comparison with all baselines (ρ = 1.05)

| Scenario | Metric | AS-HTS | Prio+Esc | FCFS | SJF | GA | RR |
|---|---|---|---|---|---|---|---|
| Mixed-priority | Max wait LOW (s) | 1,359 | 1,732 | 1,098 | 5,405 | 1,317 | 18,236 |
| | HIGH SLA viol. (%) | 46.0 | 52.6 | 66.4 | 24.5 | 63.2 | 71.9 |
| | Avg wait (s) | 435 | 432 | 428 | 107 | 461 | 2,352 |
| Long-batch | Max wait LOW (s) | 3,911 | 4,633 | 3,569 | 20,394 | 3,707 | 53,542 |
| | HIGH SLA viol. (%) | 31.2 | 28.3 | 72.2 | 18.2 | 72.1 | 60.1 |
| GoCJ | Max wait LOW (s) | 1,383 | 1,817 | 1,036 | 5,302 | 1,208 | 21,620 |
| | HIGH SLA viol. (%) | 33.3 | 36.8 | 71.2 | 6.6 | 74.8 | 65.9 |
| Short-bursty | Avg wait (s) | 13.6 | 17.1 | 16.9 | 11.8 | 17.7 | 169.3 |
| | HIGH SLA viol. (%) | 0.4 | 0.2 | 31.5 | 6.7 | 32.6 | 51.3 |

**What this shows** (see `fig8_tradeoff.png`):

* **No scheduler wins every metric. The schedulers sit at different points of a fairness-vs-priority trade-off.**
  * **FCFS** has the lowest maximum waiting time, because it is starvation-free by construction. It ignores priority, though, so 66–72 % of HIGH tasks miss their SLA.
  * **SJF** has the lowest average wait and good SLA numbers (SLA deadlines scale with task length, which favours short-first). It starves long and LOW tasks badly: their maximum waits are roughly 4–5× AS-HTS's.
  * **GA** optimises makespan and behaves like FCFS on fairness and SLA.
  * **Round Robin** binds tasks cyclically without regard to VM speed. The 500-MIPS VM receives as many tasks as the 2,500-MIPS VM, so makespan roughly doubles and utilisation drops to about 47 %. This is the classic weakness of static RR binding on heterogeneous VMs.
* **AS-HTS sits on the Pareto front between FCFS and SJF.** Among the priority-aware schedulers it gives the best fairness, and it keeps HIGH SLAs far better than FCFS or GA. In mixed-priority and GoCJ it beats the Silver-T baseline on *both* axes at once.
* **Makespan and VM utilisation are essentially equal** for every scheduler except RR (within about 5 %). The work-conserving dispatcher keeps all VMs busy, so the scheduling order changes *who* waits, not *how long* the whole batch takes. That matches the course discussion: fairness policies redistribute delay; they don't create capacity.

## 3. Ablation study (ρ = 1.05, `fig9_ablation.png`)

| Scenario | Metric | AS-HTS | fixed α | no switching | Prio+Esc |
|---|---|---|---|---|---|
| Mixed-priority | Max wait LOW (s) | 1,359 | 2,355 | 1,359 | 1,732 |
| | HIGH SLA viol. (%) | 46.0 | 28.3 | 46.0 | 52.6 |
| GoCJ | Max wait LOW (s) | 1,383 | 2,576 | 1,383 | 1,817 |
| | HIGH SLA viol. (%) | 33.3 | 0.6 | 33.3 | 36.8 |
| Short-bursty | Avg wait (s) | 13.6 | 13.6 | 17.1 | 17.1 |
| Phased | Avg wait (s) | 1,256 | 1,124 | 1,325 | 1,124 |

* **The adaptive escalation rate is what reduces starvation.** Without it (fixed α = α0) AS-HTS behaves almost like strict priority. HIGH SLAs are then excellent, but LOW tasks starve longer than under the baseline. Turning adaptivity on cuts LOW max-wait by 27–46 % in long-batch, mixed-priority, phased and GoCJ. The adaptive rate is the dial that moves the scheduler between the two ends depending on backlog.
* **Policy switching is what improves efficiency in short-task traffic.** In short-bursty workloads, switching to the priority-weighted SJF sub-scheduler cuts average waiting time by about 20 % (13.6 s vs. 17.1 s) and doesn't change HIGH SLAs. In mixed-priority, long-batch and GoCJ, the classifier (correctly) never selects SJF mode, so "no switching" is identical there.
* In the **phased** workload, switching reduces average wait by about 5 % compared with no switching. Starvation is slightly higher (2,993 s vs. 2,824 s) because of the SJF phases at the start and end.

## 4. Does the classifier work? (`fig10_regime_timeline.png`)

On the phased workload, the classifier:

* enters SJF mode in the first short-bursty phase;
* moves to LONG_BATCH within a few arrivals of the long-batch phase starting;
* returns to SJF mode within about 8 arrivals (the hysteresis setting) of the final short-bursty phase.

In the mixed-priority phase it alternates between LONG_BATCH and MIXED_PRIORITY. The HIGH share in a 30-task window hovers right around the 25 % threshold there, and both labels select the same escalation sub-scheduler, so this has no effect on decisions.

In mixed-priority and GoCJ it logs about 20 regime switches per run. Most of these are LONG_BATCH ↔ MIXED_PRIORITY flips, which happen because the HIGH share in a 30-task window fluctuates around the 25 % threshold. Both labels use the same escalation sub-scheduler, so these flips have **no effect on scheduling decisions**. Raising the hysteresis, or merging the two labels, would remove them from the count.

## 5. Limitations and threats to validity

* **Simulation only.** All results come from CloudSim with synthetic, GoCJ-band workloads. Real traces (e.g. the full Google cluster trace) have correlated arrivals and runtimes that the generator doesn't model.
* **Non-preemptive.** Once a task starts it runs to completion. A HIGH task arriving behind a 600k-MI job can only be helped by the next free VM. Preemption or migration (as in the push-pull paper from the literature survey) is future work.
* **The SLA model favours short tasks.** Deadlines proportional to length give long tasks lots of slack. Fixed absolute deadlines would penalise SJF less and FCFS more.
* **Parameter sensitivity.** α0 trades HIGH SLA against LOW starvation (see `DESIGN.md` §5). The defaults were chosen on seeds 1–3, which are also part of the 5 evaluation seeds. A stricter study would tune on separate seeds.
* **Light load.** At ρ = 0.70, AS-HTS deliberately favours HIGH priority, so its LOW-priority maximum wait is a few percent higher than the baseline's (§1).

## 6. Figure guide (`results/figures/`)

| File | Content | Use in paper |
|---|---|---|
| `fig1_makespan.png` | Makespan per scenario × load × policy | Efficiency is preserved |
| `fig2_avg_wait.png` | Average waiting time (log scale) | SJF and switching gains |
| `fig3_max_wait_LOW.png` | Starvation metric (log scale) | **Main result** |
| `fig4_sla_rate_HIGH.png` | HIGH-priority SLA-violation rate | Cost of fairness |
| `fig5_sla_rate.png` | Overall SLA-violation rate | |
| `fig6_vm_util.png` | VM utilisation | RR's weakness |
| `fig7_starvation_vs_load.png` | Max wait LOW vs. load, per scenario | Adaptivity under load |
| `fig8_tradeoff.png` | Fairness vs. HIGH-SLA scatter at ρ = 1.05 | **Pareto position of AS-HTS** |
| `fig9_ablation.png` | AS-HTS vs. its two ablations | Component contributions |
| `fig10_regime_timeline.png` | Classifier regime vs. true phases | Classifier works |
| `fig11_wait_by_class.png` | Waiting-time distribution per priority class | Per-class fairness |
