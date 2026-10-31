package edu.manipal.ashts.metrics;

import edu.manipal.ashts.classifier.WorkloadClassifier;
import edu.manipal.ashts.core.TaskRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** CSV output helpers. */
public final class ResultWriter {

    private ResultWriter() {}

    public static void writeTasks(List<TaskRecord> recs, File f) throws IOException {
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "UTF-8");
        try {
            w.println("id,phase,priority,length_mi,arrival,dispatch,finish,wait,turnaround,vm,deadline,sla_violated,regime");
            for (TaskRecord r : recs) {
                w.println(String.format(Locale.US, "%d,%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%.3f,%d,%s",
                        r.spec.id, r.spec.phase, r.spec.priority, r.spec.lengthMI, r.spec.arrivalTime,
                        r.dispatchTime, r.finishTime, r.waitingTime(), r.turnaroundTime(), r.vmId,
                        r.spec.deadline, r.slaViolated() ? 1 : 0, r.dispatchRegime));
            }
        } finally {
            w.close();
        }
    }

    public static void writeTimeline(List<WorkloadClassifier.Sample> tl, File f) throws IOException {
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "UTF-8");
        try {
            w.println("time,raw_regime,stable_regime,window_mean_mi,window_cv,hi_frac,lo_frac");
            for (WorkloadClassifier.Sample s : tl) {
                w.println(String.format(Locale.US, "%.3f,%s,%s,%.1f,%.4f,%.4f,%.4f",
                        s.time, s.raw, s.stable, s.meanLen, s.cv, s.hiFrac, s.loFrac));
            }
        } finally {
            w.close();
        }
    }

    /** Appends one row to the summary CSV (writes the header if the file is new). */
    public static synchronized void appendSummary(File f, String scenario, double load, String policy,
                                                  long seed, Map<String, Double> m) throws IOException {
        boolean fresh = !f.exists();
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8"));
        try {
            if (fresh) {
                StringBuilder h = new StringBuilder("scenario,load,policy,seed");
                for (String c : Metrics.COLUMNS) h.append(',').append(c);
                w.println(h);
            }
            StringBuilder row = new StringBuilder(String.format(Locale.US, "%s,%.2f,%s,%d", scenario, load, policy, seed));
            for (String c : Metrics.COLUMNS) row.append(',').append(String.format(Locale.US, "%.4f", m.get(c)));
            w.println(row);
        } finally {
            w.close();
        }
    }
}
