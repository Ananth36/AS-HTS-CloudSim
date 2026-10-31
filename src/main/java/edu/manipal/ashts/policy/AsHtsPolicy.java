package edu.manipal.ashts.policy;

import edu.manipal.ashts.classifier.Regime;
import edu.manipal.ashts.classifier.WorkloadClassifier;
import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/**
 * AS-HTS: Adaptive Starvation-Aware Hybrid Task Scheduler.
 *
 * <ol>
 *   <li><b>Runtime workload classifier</b> ({@link WorkloadClassifier}) labels the
 *       current regime from a rolling window of arrivals.</li>
 *   <li><b>Policy-switching layer</b>: SHORT_BURSTY -> SJF sub-scheduler (with an aging cap);
 *       LONG_BATCH / MIXED_PRIORITY -> starvation-aware priority-escalation sub-scheduler.</li>
 *   <li><b>Adaptive escalation rate</b>: the waiting-time weight grows with queue depth
 *       <pre>alpha(q) = min(alpha0 * (1 + beta * q / #VMs), alphaMax)</pre></li>
 * </ol>
 *
 * Escalation score (higher runs first):
 * <pre>  S = level + alpha(q) * (wait / refExec)</pre>
 * where refExec = (window mean length) / (mean VM MIPS) is the typical execution time of
 * current tasks. Normalising the wait makes escalation scale-free (it works the same for
 * 2-second and 400-second jobs), and making the escalation term independent of the level
 * means LOW tasks age as fast as HIGH ones - unlike the Silver-T baseline, where LOW tasks
 * age 4x slower than HIGH tasks. With levels HIGH=8, LOW=2 a LOW task overtakes a
 * freshly-arrived HIGH task after waiting 6/alpha typical execution times: 60 when the queue
 * is empty (alpha = alpha0 = 0.1, near-strict priority) shrinking to 6 under heavy backlog
 * (alpha = alphaMax = 1.0), i.e. starvation protection kicks in exactly when it is needed.
 *
 * SJF aging cap: in SJF mode any task that has waited more than agingCap * refExec is served
 * first (oldest such task), so long jobs cannot starve during a short-bursty phase either.
 */
public final class AsHtsPolicy extends AbstractPolicy {

    private final String name;
    private WorkloadClassifier classifier;
    private double alpha0, beta, alphaMax, agingCap, meanMips;
    private boolean adaptive, switching;
    private double refExec = 1.0;

    public AsHtsPolicy(String name) {
        this.name = name;
    }

    @Override public String name() { return name; }

    @Override
    public void init(SchedulerContext ctx) {
        SimConfig c = ctx.config();
        classifier = new WorkloadClassifier(c);
        alpha0 = c.d("ashts.alpha0");
        beta = c.d("ashts.beta");
        alphaMax = alpha0 * c.d("ashts.alphaMaxFactor");
        agingCap = c.d("ashts.agingCap");
        adaptive = c.b("ashts.adaptiveAlpha");
        switching = c.b("ashts.switching");
        meanMips = c.meanVmMips();
    }

    @Override
    public void onArrival(TaskRecord t, SchedulerContext ctx) {
        classifier.observe(t.spec, ctx.now());
        refExec = Math.max(1e-6, classifier.windowMeanLength() / meanMips);
    }

    @Override
    public String currentMode() {
        return classifier == null ? "-" : classifier.current().name();
    }

    public WorkloadClassifier classifier() { return classifier; }

    /** Current escalation weight (exposed for logging). */
    public double alpha(SchedulerContext ctx) {
        if (!adaptive) return alpha0;
        double q = ctx.waitingCount();
        return Math.min(alphaMax, alpha0 * (1.0 + beta * q / ctx.vms().size()));
    }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        boolean sjfMode = switching && classifier.current() == Regime.SHORT_BURSTY;
        return sjfMode ? sjfWithAging(central, ctx.now()) : escalation(central, ctx);
    }

    private TaskRecord sjfWithAging(List<TaskRecord> central, double now) {
        double cap = agingCap * refExec;
        TaskRecord oldestStarved = null;
        for (TaskRecord t : central) {
            if (t.waitingTimeAt(now) > cap && (oldestStarved == null || higherThenEarlier(t, oldestStarved))) {
                oldestStarved = t;
            }
        }
        if (oldestStarved != null) return oldestStarved;
        // Priority-weighted SJF (Smith's WSPT rule): largest level / length first.
        // With equal priorities this is exactly SJF; a HIGH task (level 8) is treated as if it
        // were 4x shorter than an equally long LOW task (level 2).
        TaskRecord best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (TaskRecord t : central) {
            double s = t.spec.priority.level / (double) t.spec.lengthMI;
            if (s > bestScore || (s == bestScore && earlier(t, best))) {
                best = t;
                bestScore = s;
            }
        }
        return best;
    }

    private static boolean higherThenEarlier(TaskRecord a, TaskRecord b) {
        if (a.spec.priority.level != b.spec.priority.level) return a.spec.priority.level > b.spec.priority.level;
        return earlier(a, b);
    }

    private TaskRecord escalation(List<TaskRecord> central, SchedulerContext ctx) {
        double now = ctx.now();
        double a = alpha(ctx);
        TaskRecord best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (TaskRecord t : central) {
            double s = t.spec.priority.level + a * (t.waitingTimeAt(now) / refExec);
            if (s > bestScore || (s == bestScore && earlier(t, best))) {
                best = t;
                bestScore = s;
            }
        }
        return best;
    }
}
