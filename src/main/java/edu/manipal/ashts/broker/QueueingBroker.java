package edu.manipal.ashts.broker;

import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.TaskSpec;
import edu.manipal.ashts.core.VmInfo;
import edu.manipal.ashts.policy.SchedulerContext;
import edu.manipal.ashts.policy.SchedulingPolicy;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudSim {@link DatacenterBroker} extended with a global ready queue and dynamic arrivals.
 *
 * <p>Stock CloudSim binds every cloudlet to a VM at time 0, so tasks never "wait" at the
 * broker and starvation cannot be observed. This broker instead:</p>
 * <ol>
 *   <li>schedules a TASK_ARRIVAL event for every task at its arrival time;</li>
 *   <li>keeps arrived tasks in a central queue (and optional per-VM local queues);</li>
 *   <li>whenever a VM is idle, asks the plugged-in {@link SchedulingPolicy} which task to run
 *       and submits exactly that cloudlet to the VM (1 PE, space-shared => one task at a time);</li>
 *   <li>on CLOUDLET_RETURN records finish time and dispatches again.</li>
 * </ol>
 * Waiting time = dispatch time - arrival time, which is what all starvation metrics use.
 *
 * <p>All times exposed to policies and metrics are relative to the moment the VMs became
 * ready (t0), so they line up exactly with the arrival times in the workload file.</p>
 */
public class QueueingBroker extends DatacenterBroker implements SchedulerContext {

    /** Custom event tags (outside CloudSim's reserved range). */
    private static final int TASK_ARRIVAL = 990_001;
    private static final int DISPATCH = 990_002;

    private final List<TaskSpec> workload;
    private final SchedulingPolicy policy;
    private final SimConfig cfg;
    private final long seed;

    private final List<TaskRecord> records = new ArrayList<TaskRecord>();
    private final Map<Integer, TaskRecord> byCloudlet = new HashMap<Integer, TaskRecord>();
    private final List<TaskRecord> central = new ArrayList<TaskRecord>();
    private final List<VmInfo> vmInfos = new ArrayList<VmInfo>();
    private final Map<Integer, VmInfo> vmById = new HashMap<Integer, VmInfo>();

    private double t0 = 0;
    private int finished = 0;
    private boolean dispatchPending = false;
    private boolean started = false;

    public QueueingBroker(String name, List<TaskSpec> workload, SchedulingPolicy policy,
                          SimConfig cfg, long seed) throws Exception {
        super(name);
        this.workload = workload;
        this.policy = policy;
        this.cfg = cfg;
        this.seed = seed;
    }

    // ------------------------------------------------------------------ CloudSim hooks

    /** Called by the parent class once all VMs have been created. */
    @Override
    protected void submitCloudlets() {
        if (started) return;
        started = true;
        t0 = CloudSim.clock();

        List<Vm> created = getVmsCreatedList();
        for (Vm vm : created) {
            VmInfo v = new VmInfo(vm.getId(), vm.getMips());
            vmInfos.add(v);
            vmById.put(v.id, v);
        }
        Collections.sort(vmInfos, new Comparator<VmInfo>() {
            @Override public int compare(VmInfo a, VmInfo b) { return Integer.compare(a.id, b.id); }
        });

        policy.init(this);

        UtilizationModel full = new UtilizationModelFull();
        for (TaskSpec s : workload) {
            Cloudlet c = new Cloudlet(s.id, s.lengthMI, 1, 300, 300, full, full, full);
            c.setUserId(getId());
            TaskRecord r = new TaskRecord(s, c);
            records.add(r);
            byCloudlet.put(s.id, r);
            schedule(getId(), Math.max(0.0, s.arrivalTime), TASK_ARRIVAL, r);
        }
        if (workload.isEmpty()) {
            clearDatacenters();
            finishExecution();
        }
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev.getTag() == TASK_ARRIVAL) {
            TaskRecord r = (TaskRecord) ev.getData();
            central.add(r);
            policy.onArrival(r, this);
            requestDispatch();
        } else if (ev.getTag() == DISPATCH) {
            dispatchPending = false;
            dispatch();
        } else {
            super.processOtherEvent(ev);
        }
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet c = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(c);
        cloudletsSubmitted--;

        TaskRecord r = byCloudlet.get(c.getCloudletId());
        r.finishTime = now();
        VmInfo vm = vmById.get(r.vmId);
        vm.busyTime += r.finishTime - r.dispatchTime;
        vm.running = null;
        finished++;

        if (finished == records.size()) {
            clearDatacenters();
            finishExecution();
        } else {
            requestDispatch();
        }
    }

    // ------------------------------------------------------------------ dispatching

    /**
     * Dispatch is deferred to a zero-delay event so that all tasks arriving at the same
     * instant (batch arrivals) are in the queue before the policy makes its choice.
     */
    private void requestDispatch() {
        if (!dispatchPending) {
            dispatchPending = true;
            schedule(getId(), 0.0, DISPATCH);
        }
    }

    private void dispatch() {
        policy.beforeDispatch(this);

        List<VmInfo> idle = new ArrayList<VmInfo>();
        for (VmInfo v : vmInfos) if (v.isIdle()) idle.add(v);
        // Fastest idle VM gets to pick first (same rule for every policy).
        Collections.sort(idle, new Comparator<VmInfo>() {
            @Override public int compare(VmInfo a, VmInfo b) {
                int c = Double.compare(b.mips, a.mips);
                return c != 0 ? c : Integer.compare(a.id, b.id);
            }
        });

        for (VmInfo vm : idle) {
            TaskRecord t = null;
            if (!vm.localQueue.isEmpty()) {
                t = vm.localQueue.pollFirst();
            } else if (!central.isEmpty()) {
                t = policy.selectNext(central, vm, this);
                if (t != null) central.remove(t);
            }
            if (t != null) submitTo(t, vm);
        }
    }

    private void submitTo(TaskRecord t, VmInfo vm) {
        t.vmId = vm.id;
        t.dispatchTime = now();
        t.dispatchRegime = policy.currentMode();
        vm.running = t;
        vm.tasksRun++;
        Cloudlet c = t.cloudlet;
        c.setVmId(vm.id);
        sendNow(getVmsToDatacentersMap().get(vm.id), CloudSimTags.CLOUDLET_SUBMIT, c);
        cloudletsSubmitted++;
        getCloudletSubmittedList().add(c);
    }

    // ------------------------------------------------------------------ SchedulerContext

    @Override public double now() { return CloudSim.clock() - t0; }
    @Override public List<VmInfo> vms() { return vmInfos; }
    @Override public List<TaskRecord> centralQueue() { return central; }
    @Override public SimConfig config() { return cfg; }
    @Override public long seed() { return seed; }

    @Override
    public int waitingCount() {
        int n = central.size();
        for (VmInfo v : vmInfos) n += v.localQueue.size();
        return n;
    }

    @Override
    public void bind(TaskRecord t, VmInfo vm) {
        central.remove(t);
        vm.localQueue.addLast(t);
    }

    // ------------------------------------------------------------------ results

    public List<TaskRecord> records() { return records; }
    public SchedulingPolicy policy() { return policy; }
    public int finishedCount() { return finished; }
}
