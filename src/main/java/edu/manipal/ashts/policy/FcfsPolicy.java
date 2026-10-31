package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/** First-Come-First-Served: the oldest waiting task goes to the next idle VM. */
public final class FcfsPolicy extends AbstractPolicy {

    @Override public String name() { return "FCFS"; }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        TaskRecord best = null;
        for (TaskRecord t : central) {
            if (best == null || earlier(t, best)) best = t;
        }
        return best;
    }
}
