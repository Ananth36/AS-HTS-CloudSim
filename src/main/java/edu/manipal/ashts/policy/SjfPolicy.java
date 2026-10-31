package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/** Non-preemptive Shortest-Job-First: the shortest waiting task (by MI) goes next. */
public final class SjfPolicy extends AbstractPolicy {

    @Override public String name() { return "SJF"; }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        return shortest(central);
    }

    static TaskRecord shortest(List<TaskRecord> central) {
        TaskRecord best = null;
        for (TaskRecord t : central) {
            if (best == null
                    || t.spec.lengthMI < best.spec.lengthMI
                    || (t.spec.lengthMI == best.spec.lengthMI && earlier(t, best))) {
                best = t;
            }
        }
        return best;
    }
}
