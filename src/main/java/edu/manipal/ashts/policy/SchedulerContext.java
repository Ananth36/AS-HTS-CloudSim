package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/** What a scheduling policy is allowed to see and do. Implemented by the broker. */
public interface SchedulerContext {
    /** Current simulation clock (seconds). */
    double now();

    /** All VMs, in id order. */
    List<VmInfo> vms();

    /** The global ready queue (tasks that arrived and are not yet bound to a VM). */
    List<TaskRecord> centralQueue();

    /** Number of tasks waiting anywhere (central queue + all VM local queues). */
    int waitingCount();

    /** Move a task from the central queue to the tail of a VM's local queue. */
    void bind(TaskRecord t, VmInfo vm);

    SimConfig config();

    /** Seed of the current run (for policies that use randomness). */
    long seed();
}
