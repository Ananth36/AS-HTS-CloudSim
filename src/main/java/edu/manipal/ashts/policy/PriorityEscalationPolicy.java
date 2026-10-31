package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/**
 * Starvation-aware priority scheduling with waiting-time escalation, reproducing
 * the formula of the Silver-T "CloudSim-Algorithm-Project" baseline:
 *
 * <pre>  P = level * (base + w * waitSeconds)      (Silver-T: base = 100, w = 1 per second)</pre>
 *
 * The task with the largest P is dispatched next. Note the weighting factor is
 * <i>fixed</i> and the escalation rate is proportional to the level, so LOW tasks
 * escalate the slowest - the property AS-HTS changes.
 */
public final class PriorityEscalationPolicy extends AbstractPolicy {

    private double base;
    private double w;

    @Override public String name() { return "PRIORITY_ESC"; }

    @Override
    public void init(SchedulerContext ctx) {
        base = ctx.config().d("prio.base");
        w = ctx.config().d("prio.w");
    }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        double now = ctx.now();
        TaskRecord best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (TaskRecord t : central) {
            double p = t.spec.priority.level * (base + w * t.waitingTimeAt(now));
            if (p > bestScore || (p == bestScore && earlier(t, best))) {
                best = t;
                bestScore = p;
            }
        }
        return best;
    }
}
