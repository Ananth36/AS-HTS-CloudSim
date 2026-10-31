package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/** No-op defaults so each concrete policy only implements what it needs. */
public abstract class AbstractPolicy implements SchedulingPolicy {

    @Override public void init(SchedulerContext ctx) {}
    @Override public void onArrival(TaskRecord t, SchedulerContext ctx) {}
    @Override public void beforeDispatch(SchedulerContext ctx) {}
    @Override public String currentMode() { return "-"; }

    /** Deterministic tie-break: earlier arrival, then lower id. */
    protected static boolean earlier(TaskRecord a, TaskRecord b) {
        if (a.spec.arrivalTime != b.spec.arrivalTime) return a.spec.arrivalTime < b.spec.arrivalTime;
        return a.spec.id < b.spec.id;
    }

    /** Default for binding-style policies: never pull from the central queue. */
    protected TaskRecord none(List<TaskRecord> central, VmInfo vm) {
        return null;
    }
}
