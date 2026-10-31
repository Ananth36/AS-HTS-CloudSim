package edu.manipal.ashts.experiment;

import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskSpec;
import edu.manipal.ashts.metrics.ResultWriter;
import edu.manipal.ashts.workload.Scenario;
import edu.manipal.ashts.workload.WorkloadGenerator;
import edu.manipal.ashts.workload.WorkloadIO;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Runs the full experiment grid: scenarios x loads x seeds x policies.
 * Every policy sees exactly the same workload for a given (scenario, load, seed).
 *
 * Outputs (under exp.out, default results/):
 *   summary.csv                         one row per run, all metrics
 *   tasks/SCENARIO_LOAD_POLICY.csv      per-task log for the first seed
 *   timeline/SCENARIO_LOAD_ASHTS.csv    classifier regime timeline for the first seed
 *   ../workloads/SCENARIO_LOAD_seedS.csv   the generated workloads (first seed)
 */
public final class ExperimentRunner {

    private ExperimentRunner() {}

    public static void runGrid(SimConfig cfg, long[] gocj) throws Exception {
        String[] scenarios = cfg.list("exp.scenarios");
        String[] loads = cfg.list("exp.loads");
        String[] policies = cfg.list("exp.policies");
        String[] seeds = cfg.list("exp.seeds");
        int n = cfg.i("wl.tasks");
        File out = new File(cfg.str("exp.out"));
        out.mkdirs();
        File summary = new File(out, "summary.csv");
        if (summary.exists() && !summary.delete()) throw new IllegalStateException("Cannot overwrite " + summary);

        int total = scenarios.length * loads.length * seeds.length * policies.length;
        int done = 0;
        long start = System.currentTimeMillis();
        System.out.printf(Locale.US, "Running %d simulations (%d tasks each)...%n", total, n);

        for (String sc : scenarios) {
            Scenario scenario = Scenario.valueOf(sc);
            for (String ls : loads) {
                double load = Double.parseDouble(ls);
                for (int si = 0; si < seeds.length; si++) {
                    long seed = Long.parseLong(seeds[si]);
                    List<TaskSpec> wl = new WorkloadGenerator(cfg, seed, gocj).generate(scenario, load, n);
                    String tag = sc + "_" + String.format(Locale.US, "%.2f", load);
                    if (si == 0) WorkloadIO.write(wl, new File("workloads", tag + "_seed" + seed + ".csv"));

                    for (String pol : policies) {
                        SimulationRunner.Output o = SimulationRunner.run(wl, pol, cfg, seed, false);
                        ResultWriter.appendSummary(summary, sc, load, o.policy, seed, o.metrics);
                        if (si == 0) {
                            ResultWriter.writeTasks(o.records, new File(out, "tasks/" + tag + "_" + o.policy + ".csv"));
                            if (o.asHts() != null && o.policy.equals("ASHTS")) {
                                ResultWriter.writeTimeline(o.asHts().classifier().timeline(),
                                        new File(out, "timeline/" + tag + "_ASHTS.csv"));
                            }
                        }
                        done++;
                        if (done % 20 == 0 || done == total) {
                            System.out.printf(Locale.US, "  %4d/%d done  (%.0f s elapsed)%n", done, total,
                                    (System.currentTimeMillis() - start) / 1000.0);
                        }
                    }
                }
            }
        }
        System.out.println("Summary written to " + summary.getPath());
    }
}
