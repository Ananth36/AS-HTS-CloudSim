package edu.manipal.ashts;

import edu.manipal.ashts.core.PriorityClass;
import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskSpec;
import edu.manipal.ashts.experiment.ExperimentRunner;
import edu.manipal.ashts.experiment.SimulationRunner;
import edu.manipal.ashts.metrics.ResultWriter;
import edu.manipal.ashts.policy.PolicyFactory;
import edu.manipal.ashts.workload.Scenario;
import edu.manipal.ashts.workload.WorkloadGenerator;
import edu.manipal.ashts.workload.WorkloadIO;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line entry point. Run without arguments for help.
 */
public final class Main {

    private static final String HELP = String.join("\n",
            "AS-HTS: Adaptive Starvation-Aware Hybrid Task Scheduler (CloudSim 3.0.3)",
            "",
            "Usage: run.sh <command> [--option value ...]      (Windows: run.bat)",
            "",
            "Commands:",
            "  validate     Sanity-check the CloudSim setup (execution times, task completion).",
            "  demo         Run every scheduler on one workload and print a comparison table.",
            "               Options: --scenario PHASED --load 0.9 --seed 1 --tasks 1000",
            "  run          Run one or more schedulers on one workload, write per-task CSVs.",
            "               Options: --scenario S | --workload file.csv, --load L, --seed N,",
            "                        --policy P|ALL, --tasks N, --out results/single, --verbose true",
            "  generate     Write a workload CSV. Options: --scenario --load --seed --tasks --out file.csv",
            "  experiment   Run the full grid from the config file (all scenarios/loads/seeds/policies).",
            "               Options: --config config/experiment.properties",
            "",
            "Global options:",
            "  --config FILE         properties file with parameter overrides",
            "  --gocj FILE           real GoCJ dataset file (one job size in MI per line) for scenario GOCJ",
            "  --<any.config.key> V  override any config key, e.g. --ashts.alpha0 2.0",
            "",
            "Scenarios: SHORT_BURSTY LONG_BATCH MIXED_PRIORITY PHASED GOCJ",
            "Policies:  FCFS SJF RR GA PRIORITY_ESC ASHTS ASHTS_FIXED_ALPHA ASHTS_NO_SWITCH");

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].startsWith("-h") || args[0].equals("help")) {
            System.out.println(HELP);
            return;
        }
        String cmd = args[0];
        Map<String, String> opt = parse(args);
        SimConfig cfg = SimConfig.load(opt.get("config"));
        for (Map.Entry<String, String> e : opt.entrySet()) {
            if (e.getKey().contains(".")) cfg.set(e.getKey(), e.getValue());
        }
        if (opt.containsKey("tasks")) cfg.set("wl.tasks", opt.get("tasks"));
        long[] gocj = opt.containsKey("gocj") ? WorkloadIO.readGoCJ(new File(opt.get("gocj"))) : null;

        if (cmd.equals("validate")) validate(cfg);
        else if (cmd.equals("demo")) demo(cfg, opt, gocj);
        else if (cmd.equals("run")) run(cfg, opt, gocj);
        else if (cmd.equals("generate")) generate(cfg, opt, gocj);
        else if (cmd.equals("experiment")) ExperimentRunner.runGrid(cfg, gocj);
        else {
            System.err.println("Unknown command: " + cmd + "\n");
            System.out.println(HELP);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ commands

    private static void validate(SimConfig cfg) throws Exception {
        System.out.println("== Validation 1: one 10,000 MI cloudlet on an idle datacenter ==");
        List<TaskSpec> one = new ArrayList<TaskSpec>();
        one.add(new TaskSpec(0, 0.0, 10000, PriorityClass.HIGH, 1e9, "VALIDATE"));
        SimulationRunner.Output o = SimulationRunner.run(one, "FCFS", cfg, 1, false);
        double exec = o.records.get(0).executionTime();
        double mips = o.vms.get(o.records.get(0).vmId).mips;
        System.out.printf(Locale.US, "   ran on VM %d (%.0f MIPS): execution time %.3f s, expected %.3f s  -> %s%n",
                o.records.get(0).vmId, mips, exec, 10000 / mips,
                Math.abs(exec - 10000 / mips) < 0.05 ? "OK" : "MISMATCH");

        System.out.println("== Validation 2: every policy completes a 300-task MIXED workload ==");
        cfg.set("wl.tasks", "300");
        List<TaskSpec> wl = new WorkloadGenerator(cfg, 42, null).generate(Scenario.MIXED_PRIORITY, 0.9, 300);
        boolean ok = true;
        for (String p : PolicyFactory.ALL) {
            SimulationRunner.Output r = SimulationRunner.run(wl, p, cfg, 42, false);
            double dev = r.metrics.get("exec_dev_max");
            boolean good = r.records.size() == 300 && dev < 0.05;
            ok &= good;
            System.out.printf(Locale.US, "   %-18s finished %d/300, max |exec - length/MIPS| = %.4f s  -> %s%n",
                    p, r.records.size(), dev, good ? "OK" : "CHECK");
        }
        System.out.println(ok ? "All validation checks passed." : "Some checks FAILED.");
    }

    private static void demo(SimConfig cfg, Map<String, String> opt, long[] gocj) throws Exception {
        Scenario sc = Scenario.valueOf(get(opt, "scenario", "PHASED"));
        double load = Double.parseDouble(get(opt, "load", "0.9"));
        long seed = Long.parseLong(get(opt, "seed", "1"));
        int n = cfg.i("wl.tasks");
        List<TaskSpec> wl = new WorkloadGenerator(cfg, seed, gocj).generate(sc, load, n);
        System.out.printf(Locale.US, "Workload: %s, load %.2f, %d tasks, seed %d, %d VMs%n%n",
                sc, load, n, seed, cfg.vmMips().length);
        System.out.printf(Locale.US, "%-18s %10s %9s %10s %12s %9s %9s %7s %8s%n", "Policy", "Makespan",
                "AvgWait", "MaxWait", "MaxWaitLOW", "SLA-HIGH", "SLA-all", "VMutil", "Switches");
        String[] pols = {"FCFS", "SJF", "RR", "GA", "PRIORITY_ESC", "ASHTS"};
        for (String p : pols) {
            Map<String, Double> m = SimulationRunner.run(wl, p, cfg, seed, false).metrics;
            System.out.printf(Locale.US, "%-18s %10.1f %9.1f %10.1f %12.1f %8.1f%% %8.1f%% %6.1f%% %8.0f%n", p,
                    m.get("makespan"), m.get("avg_wait"), m.get("max_wait"), m.get("max_wait_LOW"),
                    100 * m.get("sla_rate_HIGH"), 100 * m.get("sla_rate"), 100 * m.get("vm_util"),
                    m.get("regime_switches"));
        }
        System.out.println("\n(times in simulated seconds)");
    }

    private static void run(SimConfig cfg, Map<String, String> opt, long[] gocj) throws Exception {
        long seed = Long.parseLong(get(opt, "seed", "1"));
        double load = Double.parseDouble(get(opt, "load", "0.9"));
        String label;
        List<TaskSpec> wl;
        if (opt.containsKey("workload")) {
            wl = WorkloadIO.read(new File(opt.get("workload")));
            label = new File(opt.get("workload")).getName().replace(".csv", "");
        } else {
            Scenario sc = Scenario.valueOf(get(opt, "scenario", "MIXED_PRIORITY"));
            wl = new WorkloadGenerator(cfg, seed, gocj).generate(sc, load, cfg.i("wl.tasks"));
            label = sc + String.format(Locale.US, "_%.2f_seed%d", load, seed);
        }
        String pol = get(opt, "policy", "ALL");
        String[] pols = pol.equalsIgnoreCase("ALL") ? PolicyFactory.ALL : pol.split(",");
        File out = new File(get(opt, "out", "results/single"));
        File summary = new File(out, "summary_" + label + ".csv");
        if (summary.exists()) summary.delete();
        boolean verbose = Boolean.parseBoolean(get(opt, "verbose", "false"));
        for (String p : pols) {
            SimulationRunner.Output o = SimulationRunner.run(wl, p.trim(), cfg, seed, verbose);
            ResultWriter.writeTasks(o.records, new File(out, label + "_" + o.policy + "_tasks.csv"));
            ResultWriter.appendSummary(summary, label, load, o.policy, seed, o.metrics);
            if (o.asHts() != null) {
                ResultWriter.writeTimeline(o.asHts().classifier().timeline(),
                        new File(out, label + "_" + o.policy + "_timeline.csv"));
            }
            System.out.printf(Locale.US, "%-18s makespan=%.1f avgWait=%.1f maxWaitLOW=%.1f slaHIGH=%.1f%% util=%.1f%%%n",
                    o.policy, o.metrics.get("makespan"), o.metrics.get("avg_wait"), o.metrics.get("max_wait_LOW"),
                    100 * o.metrics.get("sla_rate_HIGH"), 100 * o.metrics.get("vm_util"));
        }
        System.out.println("Results written to " + out.getPath());
    }

    private static void generate(SimConfig cfg, Map<String, String> opt, long[] gocj) throws Exception {
        Scenario sc = Scenario.valueOf(get(opt, "scenario", "PHASED"));
        double load = Double.parseDouble(get(opt, "load", "0.9"));
        long seed = Long.parseLong(get(opt, "seed", "1"));
        List<TaskSpec> wl = new WorkloadGenerator(cfg, seed, gocj).generate(sc, load, cfg.i("wl.tasks"));
        File f = new File(get(opt, "out", "workloads/" + sc + String.format(Locale.US, "_%.2f_seed%d.csv", load, seed)));
        WorkloadIO.write(wl, f);
        System.out.println("Wrote " + wl.size() + " tasks to " + f.getPath());
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String k = args[i].substring(2);
                String v = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }

    private static String get(Map<String, String> m, String k, String def) {
        return m.containsKey(k) ? m.get(k) : def;
    }
}
