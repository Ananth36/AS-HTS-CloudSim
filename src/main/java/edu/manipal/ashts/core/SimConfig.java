package edu.manipal.ashts.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * All tunable parameters in one place. Defaults are the values used for the
 * results shipped with the project; any of them can be overridden in
 * config/experiment.properties or on the command line (--key value).
 */
public final class SimConfig {
    private final Properties p = new Properties();

    public SimConfig() {
        // ---------------- Datacenter ----------------
        set("dc.hosts", "4");
        set("dc.host.pes", "4");
        set("dc.host.pe.mips", "3000");
        set("dc.host.ram", "65536");
        // Heterogeneous VMs (1 PE each). Comma separated MIPS ratings.
        set("dc.vm.mips", "500,750,1000,1000,1500,1500,2000,2500");

        // ---------------- Workload ----------------
        set("wl.tasks", "1000");
        // SLA slack factor per class: deadline = arrival + slack * (length / meanVmMips)
        set("sla.slack.HIGH", "4");
        set("sla.slack.MEDIUM", "8");
        set("sla.slack.LOW", "16");

        // ---------------- Baseline priority escalation (Silver-T) ----------------
        // P = level * (base + w * waitSeconds); Silver-T uses base=100, w=1/s
        set("prio.base", "100");
        set("prio.w", "1.0");

        // ---------------- Genetic Algorithm baseline ----------------
        set("ga.population", "30");
        set("ga.generations", "40");
        set("ga.mutation", "0.1");
        set("ga.crossover", "0.9");
        set("ga.batch", "16");

        // ---------------- AS-HTS ----------------
        set("ashts.window", "30");             // rolling window of arrivals inspected by the classifier
        set("ashts.hysteresis", "8");          // consecutive agreeing labels before a regime switch
        set("ashts.short.meanMI", "30000");    // mean length below this => "short"
        set("ashts.short.cv", "1.0");          // and coefficient of variation below this => bursty-homogeneous
        set("ashts.mixed.hiFrac", "0.25");     // >= this share of HIGH ...
        set("ashts.mixed.loFrac", "0.25");     // ... and >= this share of LOW => mixed-priority
        set("ashts.alpha0", "0.1");            // base escalation weight (per typical execution time waited)
        set("ashts.beta", "1.0");              // queue-depth sensitivity of the escalation weight
        set("ashts.alphaMaxFactor", "10.0");   // alpha is capped at alpha0 * this
        set("ashts.agingCap", "10.0");         // in SJF mode, tasks waiting > agingCap * refExec jump the queue
        set("ashts.adaptiveAlpha", "true");    // false => ablation with fixed alpha
        set("ashts.switching", "true");        // false => ablation: always escalation mode

        // ---------------- Experiment grid ----------------
        set("exp.scenarios", "SHORT_BURSTY,LONG_BATCH,MIXED_PRIORITY,PHASED,GOCJ");
        set("exp.loads", "0.7,0.9,1.05");
        set("exp.policies", "FCFS,SJF,RR,GA,PRIORITY_ESC,ASHTS,ASHTS_FIXED_ALPHA,ASHTS_NO_SWITCH");
        set("exp.seeds", "1,2,3,4,5");
        set("exp.out", "results");
    }

    public static SimConfig load(String path) throws IOException {
        SimConfig c = new SimConfig();
        if (path != null) {
            InputStream in = new FileInputStream(path);
            try {
                Properties f = new Properties();
                f.load(in);
                for (String k : f.stringPropertyNames()) c.set(k, f.getProperty(k).trim());
            } finally {
                in.close();
            }
        }
        return c;
    }

    public void set(String k, String v) { p.setProperty(k, v); }
    public String str(String k) {
        String v = p.getProperty(k);
        if (v == null) throw new IllegalArgumentException("Missing config key " + k);
        return v;
    }
    public int i(String k) { return Integer.parseInt(str(k)); }
    public double d(String k) { return Double.parseDouble(str(k)); }
    public boolean b(String k) { return Boolean.parseBoolean(str(k)); }
    public String[] list(String k) {
        String[] parts = str(k).split(",");
        for (int j = 0; j < parts.length; j++) parts[j] = parts[j].trim();
        return parts;
    }
    public double[] vmMips() {
        String[] s = list("dc.vm.mips");
        double[] m = new double[s.length];
        for (int j = 0; j < s.length; j++) m[j] = Double.parseDouble(s[j]);
        return m;
    }
    public double meanVmMips() {
        double[] m = vmMips();
        double s = 0;
        for (double x : m) s += x;
        return s / m.length;
    }
    public double totalVmMips() {
        return meanVmMips() * vmMips().length;
    }
    public SimConfig copy() {
        SimConfig c = new SimConfig();
        for (String k : p.stringPropertyNames()) c.set(k, p.getProperty(k));
        return c;
    }
}
