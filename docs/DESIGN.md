# Design and Implementation

## 1. Why a custom broker?

In stock CloudSim (`DatacenterBroker`), every cloudlet is submitted and bound to a VM as soon as the VMs are created, at time ≈ 0. Each VM's `CloudletScheduler` then works through its share. The scheduling "algorithms" in most CloudSim example repositories, including both baselines of this project, only **re-order or re-bind the list before submission**. That has two consequences:

* There are no arrivals over time, so there is no ready queue and no meaningful *waiting time at the scheduler*.
* Starvation, which is about a task waiting indefinitely while others are preferred, can't occur or be measured.

AS-HTS is about starvation, so the first piece of work is a broker that behaves like a real cloud scheduler.

### `QueueingBroker` (package `broker`)

`QueueingBroker extends DatacenterBroker` and overrides three hooks:

| Hook | Behaviour |
|---|---|
| `submitCloudlets()` (called once all VMs exist) | Builds a `TaskRecord` + `Cloudlet` for every task and schedules a **`TASK_ARRIVAL` event at the task's arrival time**. Nothing is sent to a VM yet. |
| `processOtherEvent()` | On `TASK_ARRIVAL`, adds the task to the **central ready queue**, notifies the policy (`onArrival`) and requests a dispatch round. On `DISPATCH`, runs a dispatch round. |
| `processCloudletReturn()` | Records the finish time and VM busy time, frees the VM and requests a dispatch round. When all tasks are done, it shuts down the datacenter. |

**Dispatch round.** The policy may first move tasks into per-VM queues (`beforeDispatch`). Then every idle VM is visited, fastest first, the same rule for every policy. Each VM takes the head of its local queue if it has one; otherwise it asks the policy's `selectNext(centralQueue, vm)`. The chosen cloudlet is sent with `CLOUDLET_SUBMIT` to the datacenter hosting that VM.

**One task per VM at a time.** Each VM has 1 PE and `CloudletSchedulerSpaceShared`, and the broker never sends a VM more than one cloudlet. All queueing therefore happens in the broker, where the policy controls it. As a result:

```
waiting time  = dispatch time − arrival time
execution     = length / VM MIPS              (checked by `validate`; max error 0.011 s over all runs)
turnaround    = finish time − arrival time
```

**Batch arrivals.** Dispatch runs in a zero-delay `DISPATCH` event instead of directly inside the arrival handler. CloudSim processes same-time events in insertion order, so every task in a batch arriving at time *t* is in the queue before the policy picks. Without this, the first task of a batch would always win.

**Timing precision.** CloudSim's default minimum time between datacenter updates is 0.1 s. `SimulationRunner` initialises CloudSim with 0.001 s instead, so short tasks aren't rounded.

## 2. Policy interface

```java
interface SchedulingPolicy {
    void init(SchedulerContext ctx);
    void onArrival(TaskRecord t, SchedulerContext ctx);          // may bind t to a VM
    void beforeDispatch(SchedulerContext ctx);                    // may bind many tasks (batch mappers)
    TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx);
    String currentMode();                                         // AS-HTS regime, "-" otherwise
}
```

The interface supports both styles found in the baselines:

* **Pull / queue style** (FCFS, SJF, Priority-Escalation, AS-HTS) picks from the central queue when a VM is free.
* **Binding style** (Round Robin, GA) assigns tasks to VM-local queues, and each VM runs its queue in FIFO order.

## 3. Baselines

| Policy | Rule | Notes |
|---|---|---|
| `FcfsPolicy` | Earliest arrival | Starvation-free by construction; ignores priority |
| `SjfPolicy` | Smallest length (MI) | Non-preemptive; minimises average wait, starves long tasks |
| `RoundRobinPolicy` | Task *i* → VM *i mod n* at arrival | As in Ravikisha/CloudSim-Examples; ignores VM speed, so slow VMs build long queues |
| `GeneticPolicy` | Batch GA over the central queue | Runs when ≥ `ga.batch` tasks are queued or a VM is idle with nothing queued. Chromosome = VM per task. Fitness = estimated makespan using each VM's ready time **and MIPS** (the original ignored MIPS, which we fixed so the baseline isn't artificially weak). Tournament selection, uniform crossover, mutation, elitism; seeded with a greedy solution. |
| `PriorityEscalationPolicy` | Max of `P = level × (100 + w·wait)` | **Exactly the Silver-T formula** (`priority = level*100 + waitMs*level/1000`, with w = 1 per second). The escalation rate is *proportional to the level*, so LOW tasks (level 2) age 4× slower than HIGH tasks (level 8). |

Priority classes map onto Silver-T's 1–10 level scale: HIGH = 8, MEDIUM = 5, LOW = 2.

## 4. AS-HTS

### 4.1 Runtime workload classifier (`classifier/WorkloadClassifier`)

On every arrival it updates a rolling window of the last **W = 30** tasks and computes:

* the mean length (MI) and the coefficient of variation (`cv = std / mean`) of lengths;
* `hiFrac`, the share of HIGH tasks, and `loFrac`, the share of LOW tasks.

```
raw = MIXED_PRIORITY   if hiFrac ≥ 0.25 and loFrac ≥ 0.25
      SHORT_BURSTY     else if meanLen ≤ 30,000 MI and cv ≤ 1.0
      LONG_BATCH       otherwise

stable regime changes only after K = 8 consecutive raw labels agree on the new value (hysteresis)
```

The cv condition matters. A window of mostly short tasks with a few huge ones has a high cv, and SJF would starve the huge ones, so that window is not treated as SJF-safe. Every observation is logged, and the timeline is written to `results/timeline/` and plotted in `fig10`.

The classifier is deliberately **rule-based and lightweight** rather than learned (cf. the RL/LSTM approach in the literature survey). It runs in O(W) per arrival, is easy to explain, and every threshold is a config key.

### 4.2 Policy-switching layer (`policy/AsHtsPolicy.selectNext`)

```
if regime == SHORT_BURSTY:   SJF sub-scheduler (with aging cap)
else:                        starvation-aware escalation sub-scheduler
```

**SJF sub-scheduler.** It uses **priority-weighted SJF (Smith's WSPT rule)**: pick the task with the largest `level / length`. With equal priorities this is plain SJF. A HIGH task counts as if it were 4× shorter than an equally long LOW task, so HIGH tasks aren't penalised during short bursts.

**Aging cap.** Any task that has waited more than `agingCap × refExec` (default 10 typical execution times) is served first, oldest first. Long jobs therefore can't starve even while SJF mode is active.

### 4.3 Adaptive escalation rate

```
refExec = (window mean length) / (mean VM MIPS)          typical execution time right now
q       = number of tasks waiting (central + local queues)
α(q)    = min( α0 · (1 + β · q / #VMs),  α0 · alphaMaxFactor )      α0 = 0.1, β = 1, max = 1.0

score(task) = level + α(q) · wait / refExec              highest score is dispatched
```

There are three deliberate differences from the Silver-T baseline:

1. **The weight adapts to queue depth.** With an empty queue, α = 0.1. A LOW task then needs to wait 60 typical execution times before it overtakes a fresh HIGH task, so behaviour is close to strict priority and HIGH SLAs are protected. Under heavy backlog, α rises to 1.0 and the LOW task overtakes after 6. Starvation protection comes in when starvation risk is real.
2. **Escalation is independent of the level.** All classes age at the same rate, while the baseline lets LOW age 4× slower than HIGH.
3. **Wait is normalised by `refExec`.** This makes the rule scale-free. A 60 s wait means a lot for 2 s tasks and very little for 400 s jobs, and the baseline's raw-seconds formula can't tell the difference.

### 4.4 Pseudocode

```
on arrival(t):
    queue.add(t)
    regime  ← classifier.observe(t)
    refExec ← classifier.windowMeanLength / meanVmMips
    request dispatch

on dispatch:
    for each idle VM v, fastest first:
        if queue empty: break
        if regime == SHORT_BURSTY:
            starved ← {x ∈ queue : wait(x) > agingCap·refExec}
            t ← starved ≠ ∅ ? highest-priority-then-oldest(starved)
                            : argmax_x level(x)/length(x)
        else:
            α ← min(α0(1 + β·|waiting|/#VMs), α0·alphaMaxFactor)
            t ← argmax_x level(x) + α·wait(x)/refExec
        queue.remove(t); submit t to v

on completion(t): free its VM; request dispatch
```

Each decision is O(|queue|), which costs microseconds at the queue sizes simulated.

### 4.5 Ablation variants

| Variant | What changes |
|---|---|
| `ASHTS_FIXED_ALPHA` | `ashts.adaptiveAlpha=false`, so α = α0 always (isolates the adaptive-rate contribution) |
| `ASHTS_NO_SWITCH` | `ashts.switching=false`, so it never enters SJF mode (isolates the switching contribution) |

## 5. Parameter choice

The AS-HTS defaults (`α0 = 0.1`, `β = 1`, `alphaMaxFactor = 10`) were chosen with a small sweep over α0 ∈ {0.05, 0.1, 0.2, 0.4} on seeds 1–3. We picked the value that cut LOW-priority maximum waiting under load without raising the HIGH-priority SLA-violation rate above the baseline's in most scenarios. Larger α0 gives lower starvation but more HIGH SLA violations, smaller α0 the reverse. The ablation (fixed α) shows the two ends of that dial. All classifier thresholds are round numbers chosen from the workload definitions, not fitted.

## 6. Reproducibility

* Workload generation is seeded (`java.util.Random(seed)`). The GA has its own seeded RNG, and every tie-break is deterministic (arrival, then id). The same command therefore always gives the same numbers.
* Every policy in a given (scenario, load, seed) cell receives **the identical task list**.
