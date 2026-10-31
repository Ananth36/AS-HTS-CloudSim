package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/**
 * A task scheduling policy plugged into {@link edu.manipal.ashts.broker.QueueingBroker}.
 *
 * <p>Two styles are supported, so classic CloudSim baselines and queue-based
 * schedulers can be compared on identical, dynamically arriving workloads:</p>
 * <ul>
 *   <li><b>Pull (queue) style</b> - FCFS, SJF, priority escalation, AS-HTS: tasks stay in
 *       the central queue and {@link #selectNext} picks one whenever a VM becomes idle.</li>
 *   <li><b>Binding style</b> - Round Robin, Genetic: tasks are bound to a VM's local queue
 *       in {@link #onArrival} or {@link #beforeDispatch}; each VM then runs its own queue FIFO.</li>
 * </ul>
 */
public interface SchedulingPolicy {

    String name();

    /** Called once before the simulation starts. */
    void init(SchedulerContext ctx);

    /** Called when a task arrives (it has already been appended to the central queue). */
    void onArrival(TaskRecord t, SchedulerContext ctx);

    /** Called at the start of every dispatch round (after arrivals / completions). */
    void beforeDispatch(SchedulerContext ctx);

    /**
     * Choose the next task from the (non-empty) central queue for the given idle VM.
     * Return null to leave the VM idle.
     */
    TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx);

    /** Label of the policy's current internal mode (AS-HTS regime); "-" for static policies. */
    String currentMode();
}
