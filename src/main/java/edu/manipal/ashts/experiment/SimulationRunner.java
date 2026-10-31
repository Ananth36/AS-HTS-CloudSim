package edu.manipal.ashts.experiment;

import edu.manipal.ashts.broker.QueueingBroker;
import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.TaskSpec;
import edu.manipal.ashts.core.VmInfo;
import edu.manipal.ashts.datacenter.DatacenterFactory;
import edu.manipal.ashts.metrics.Metrics;
import edu.manipal.ashts.policy.AsHtsPolicy;
import edu.manipal.ashts.policy.PolicyFactory;
import edu.manipal.ashts.policy.SchedulingPolicy;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Runs one CloudSim simulation: one workload x one policy. */
public final class SimulationRunner {

    /** Everything produced by a run. */
    public static final class Output {
        public final String policy;
        public final List<TaskRecord> records;
        public final List<VmInfo> vms;
        public final Map<String, Double> metrics;
        public final SchedulingPolicy policyObj;

        Output(String policy, List<TaskRecord> records, List<VmInfo> vms, Map<String, Double> metrics,
               SchedulingPolicy policyObj) {
            this.policy = policy;
            this.records = records;
            this.vms = vms;
            this.metrics = metrics;
            this.policyObj = policyObj;
        }

        public AsHtsPolicy asHts() {
            return policyObj instanceof AsHtsPolicy ? (AsHtsPolicy) policyObj : null;
        }
    }

    private SimulationRunner() {}

    public static Output run(List<TaskSpec> workload, String policyName, SimConfig cfg, long seed,
                             boolean verbose) throws Exception {
        if (verbose) Log.enable(); else Log.disable();

        // 0.001 s minimum gap between datacenter update events (CloudSim default is 0.1 s,
        // which would add rounding error to short tasks).
        CloudSim.init(1, Calendar.getInstance(), false, 0.001);

        DatacenterFactory.createDatacenter("Datacenter_0", cfg);

        SimConfig pcfg = PolicyFactory.configFor(policyName, cfg);
        SchedulingPolicy policy = PolicyFactory.create(policyName);
        QueueingBroker broker = new QueueingBroker("Broker_" + policy.name(), workload, policy, pcfg, seed);
        broker.submitVmList(DatacenterFactory.createVms(broker.getId(), cfg));

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        if (broker.finishedCount() != workload.size()) {
            throw new IllegalStateException("Only " + broker.finishedCount() + " of " + workload.size()
                    + " tasks finished for policy " + policyName);
        }
        int switches = (policy instanceof AsHtsPolicy) ? ((AsHtsPolicy) policy).classifier().switches() : 0;
        Map<String, Double> m = Metrics.compute(broker.records(), broker.vms(), switches);
        Log.enable();
        return new Output(policy.name(), broker.records(), broker.vms(), m, policy);
    }
}
