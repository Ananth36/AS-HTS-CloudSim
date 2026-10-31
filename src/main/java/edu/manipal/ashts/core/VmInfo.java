package edu.manipal.ashts.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Broker-side view of a VM: its speed, whether it is running something, and an
 * optional local (per-VM) queue used by binding-style schedulers (Round Robin,
 * Genetic) that decide the VM at arrival / batch time rather than at dispatch time.
 */
public final class VmInfo {
    public final int id;
    public final double mips;
    public TaskRecord running = null;
    public final Deque<TaskRecord> localQueue = new ArrayDeque<TaskRecord>();
    /** Accumulated busy time (seconds) for utilisation / imbalance metrics. */
    public double busyTime = 0.0;
    public int tasksRun = 0;

    public VmInfo(int id, double mips) {
        this.id = id;
        this.mips = mips;
    }

    public boolean isIdle() {
        return running == null;
    }

    /** Execution time of a task of the given length on this VM. */
    public double execTime(long lengthMI) {
        return lengthMI / mips;
    }

    /**
     * Estimated time (absolute) at which this VM becomes free, counting the
     * running task's remaining time and everything already in its local queue.
     */
    public double estimatedReadyTime(double now) {
        double t = now;
        if (running != null) {
            t = Math.max(now, running.dispatchTime + execTime(running.spec.lengthMI));
        }
        for (TaskRecord r : localQueue) {
            t += execTime(r.spec.lengthMI);
        }
        return t;
    }
}
