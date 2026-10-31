package edu.manipal.ashts.metrics;

import edu.manipal.ashts.core.PriorityClass;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes all evaluation metrics of one simulation run. See docs/METRICS.md for definitions.
 */
public final class Metrics {

    private Metrics() {}

    /** Ordered list of metric names (= CSV columns after the run identifiers). */
    public static final String[] COLUMNS = {
            "tasks", "makespan", "avg_wait", "p95_wait", "max_wait", "avg_turnaround",
            "avg_wait_HIGH", "avg_wait_MEDIUM", "avg_wait_LOW",
            "max_wait_HIGH", "max_wait_MEDIUM", "max_wait_LOW", "p95_wait_LOW",
            "avg_turnaround_HIGH", "makespan_HIGH",
            "sla_rate", "sla_rate_HIGH", "sla_rate_MEDIUM", "sla_rate_LOW",
            "vm_util", "di_vm", "wait_ratio_low_high", "regime_switches", "exec_dev_max"
    };

    public static Map<String, Double> compute(List<TaskRecord> recs, List<VmInfo> vms, int regimeSwitches) {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        int n = recs.size();
        double minArr = Double.MAX_VALUE, maxFin = 0;
        List<Double> waits = new ArrayList<Double>();
        double sumWait = 0, sumTat = 0, maxWait = 0, viol = 0, execDev = 0;
        for (TaskRecord r : recs) {
            minArr = Math.min(minArr, r.spec.arrivalTime);
            maxFin = Math.max(maxFin, r.finishTime);
            double w = r.waitingTime();
            waits.add(w);
            sumWait += w;
            sumTat += r.turnaroundTime();
            maxWait = Math.max(maxWait, w);
            if (r.slaViolated()) viol++;
            for (VmInfo v : vms) {
                if (v.id == r.vmId) {
                    execDev = Math.max(execDev, Math.abs(r.executionTime() - v.execTime(r.spec.lengthMI)));
                }
            }
        }
        double makespan = maxFin - minArr;
        m.put("tasks", (double) n);
        m.put("makespan", makespan);
        m.put("avg_wait", n == 0 ? 0 : sumWait / n);
        m.put("p95_wait", percentile(waits, 0.95));
        m.put("max_wait", maxWait);
        m.put("avg_turnaround", n == 0 ? 0 : sumTat / n);

        Map<PriorityClass, ClassStats> cs = new LinkedHashMap<PriorityClass, ClassStats>();
        for (PriorityClass p : PriorityClass.values()) cs.put(p, new ClassStats());
        for (TaskRecord r : recs) cs.get(r.spec.priority).add(r);

        for (PriorityClass p : PriorityClass.values()) m.put("avg_wait_" + p, cs.get(p).avgWait());
        for (PriorityClass p : PriorityClass.values()) m.put("max_wait_" + p, cs.get(p).maxWait);
        m.put("p95_wait_LOW", percentile(cs.get(PriorityClass.LOW).waits, 0.95));
        ClassStats hi = cs.get(PriorityClass.HIGH);
        m.put("avg_turnaround_HIGH", hi.n == 0 ? 0 : hi.sumTat / hi.n);
        m.put("makespan_HIGH", hi.n == 0 ? 0 : hi.maxFin - hi.minArr);

        m.put("sla_rate", n == 0 ? 0 : viol / n);
        for (PriorityClass p : PriorityClass.values()) m.put("sla_rate_" + p, cs.get(p).slaRate());

        double busy = 0, bmax = 0, bmin = Double.MAX_VALUE;
        for (VmInfo v : vms) {
            busy += v.busyTime;
            bmax = Math.max(bmax, v.busyTime);
            bmin = Math.min(bmin, v.busyTime);
        }
        double bavg = vms.isEmpty() ? 0 : busy / vms.size();
        m.put("vm_util", makespan > 0 ? busy / (vms.size() * makespan) : 0);
        m.put("di_vm", bavg > 0 ? (bmax - bmin) / bavg : 0);
        double wh = cs.get(PriorityClass.HIGH).avgWait();
        double wl = cs.get(PriorityClass.LOW).avgWait();
        m.put("wait_ratio_low_high", wh > 1e-9 ? wl / wh : 0);
        m.put("regime_switches", (double) regimeSwitches);
        m.put("exec_dev_max", execDev);
        return m;
    }

    static double percentile(List<Double> xs, double q) {
        if (xs.isEmpty()) return 0;
        List<Double> s = new ArrayList<Double>(xs);
        Collections.sort(s);
        int idx = (int) Math.ceil(q * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, idx)));
    }

    private static final class ClassStats {
        int n = 0;
        double sumWait = 0, maxWait = 0, sumTat = 0, viol = 0;
        double minArr = Double.MAX_VALUE, maxFin = 0;
        final List<Double> waits = new ArrayList<Double>();

        void add(TaskRecord r) {
            n++;
            double w = r.waitingTime();
            waits.add(w);
            sumWait += w;
            maxWait = Math.max(maxWait, w);
            sumTat += r.turnaroundTime();
            if (r.slaViolated()) viol++;
            minArr = Math.min(minArr, r.spec.arrivalTime);
            maxFin = Math.max(maxFin, r.finishTime);
        }

        double avgWait() { return n == 0 ? 0 : sumWait / n; }
        double slaRate() { return n == 0 ? 0 : viol / n; }
    }
}
