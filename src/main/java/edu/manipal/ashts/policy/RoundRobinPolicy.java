package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.List;

/**
 * Round Robin as implemented in the CloudSim-Examples baseline (Ravikisha):
 * task i is bound to VM (i mod #VMs) as it arrives; each VM runs its queue in order.
 * It ignores task size, VM speed and priority.
 */
public final class RoundRobinPolicy extends AbstractPolicy {

    private int next = 0;

    @Override public String name() { return "RR"; }

    @Override
    public void onArrival(TaskRecord t, SchedulerContext ctx) {
        List<VmInfo> vms = ctx.vms();
        ctx.bind(t, vms.get(next % vms.size()));
        next++;
    }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        return none(central, vm);
    }
}
