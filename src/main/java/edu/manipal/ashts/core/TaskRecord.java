package edu.manipal.ashts.core;

import org.cloudbus.cloudsim.Cloudlet;

/**
 * Runtime bookkeeping for one task while it moves through the broker:
 * arrival -> ready queue -> dispatched to a VM -> finished.
 */
public final class TaskRecord {
    public final TaskSpec spec;
    public final Cloudlet cloudlet;

    /** VM the task ran on (-1 until dispatched). */
    public int vmId = -1;
    /** Simulated time at which the broker sent the task to its VM. */
    public double dispatchTime = -1;
    /** Simulated time at which the task finished (cloudlet returned). */
    public double finishTime = -1;
    /** Scheduler regime that was active when the task was dispatched (AS-HTS only, else "-"). */
    public String dispatchRegime = "-";

    public TaskRecord(TaskSpec spec, Cloudlet cloudlet) {
        this.spec = spec;
        this.cloudlet = cloudlet;
    }

    public double waitingTimeAt(double now) {
        return now - spec.arrivalTime;
    }

    public double waitingTime() {
        return dispatchTime - spec.arrivalTime;
    }

    public double turnaroundTime() {
        return finishTime - spec.arrivalTime;
    }

    public double executionTime() {
        return finishTime - dispatchTime;
    }

    public boolean slaViolated() {
        return finishTime > spec.deadline + 1e-9;
    }
}
