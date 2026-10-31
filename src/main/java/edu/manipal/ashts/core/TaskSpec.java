package edu.manipal.ashts.core;

/**
 * Immutable description of one task (CloudSim cloudlet) in a workload:
 * when it arrives, how big it is, how important it is and its SLA deadline.
 */
public final class TaskSpec {
    public final int id;
    /** Arrival time in simulated seconds. */
    public final double arrivalTime;
    /** Length in million instructions (MI). */
    public final long lengthMI;
    public final PriorityClass priority;
    /** Absolute SLA deadline (simulated seconds). A task finishing after this is an SLA violation. */
    public final double deadline;
    /** Free-text label of the workload phase that produced this task (used for phased workloads). */
    public final String phase;

    public TaskSpec(int id, double arrivalTime, long lengthMI, PriorityClass priority,
                    double deadline, String phase) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.lengthMI = lengthMI;
        this.priority = priority;
        this.deadline = deadline;
        this.phase = phase;
    }
}
