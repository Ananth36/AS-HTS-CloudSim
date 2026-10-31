package edu.manipal.ashts.workload;

import edu.manipal.ashts.core.PriorityClass;
import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded synthetic workload generator.
 *
 * <p>The arrival rate is derived from a target offered load rho:
 * lambda = rho * (total VM MIPS) / (mean task length), so rho = 1.0 means
 * the datacenter would be exactly saturated on average.</p>
 *
 * <p>The same (scenario, load, seed) always produces the same workload, so every
 * scheduler is compared on identical input.</p>
 */
public final class WorkloadGenerator {

    private final SimConfig cfg;
    private final Random rnd;
    private final double totalMips;
    private final double meanMips;
    /** Optional real GoCJ job sizes (MI). If null, the GoCJ size bands are sampled. */
    private final long[] gocjSizes;

    public WorkloadGenerator(SimConfig cfg, long seed, long[] gocjSizes) {
        this.cfg = cfg;
        this.rnd = new Random(seed);
        this.totalMips = cfg.totalVmMips();
        this.meanMips = cfg.meanVmMips();
        this.gocjSizes = gocjSizes;
    }

    public List<TaskSpec> generate(Scenario s, double load, int n) {
        List<TaskSpec> out = new ArrayList<TaskSpec>(n);
        switch (s) {
            case SHORT_BURSTY:   phase(out, Scenario.SHORT_BURSTY, load, n, 0.0); break;
            case LONG_BATCH:     phase(out, Scenario.LONG_BATCH, load, n, 0.0); break;
            case MIXED_PRIORITY: phase(out, Scenario.MIXED_PRIORITY, load, n, 0.0); break;
            case GOCJ:           phase(out, Scenario.GOCJ, load, n, 0.0); break;
            case PHASED: {
                Scenario[] order = {Scenario.SHORT_BURSTY, Scenario.LONG_BATCH,
                        Scenario.MIXED_PRIORITY, Scenario.SHORT_BURSTY};
                int per = n / order.length;
                double t = 0.0;
                for (int k = 0; k < order.length; k++) {
                    int count = (k == order.length - 1) ? n - out.size() : per;
                    t = phase(out, order[k], load, count, t);
                }
                break;
            }
            default:
                throw new IllegalArgumentException("Scenario " + s + " cannot be generated");
        }
        return out;
    }

    /** Appends {@code count} tasks of the given family starting at time t0; returns the last arrival time. */
    private double phase(List<TaskSpec> out, Scenario fam, double load, int count, double t0) {
        // 1) lengths and priorities
        long[] len = new long[count];
        PriorityClass[] pr = new PriorityClass[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            len[i] = sampleLength(fam);
            pr[i] = samplePriority(fam);
            sum += len[i];
        }
        double meanLen = sum / count;
        double lambda = load * totalMips / meanLen;          // tasks per second
        double meanService = meanLen / meanMips;

        // 2) arrival times
        double[] arr = new double[count];
        double t = t0;
        int i = 0;
        if (fam == Scenario.SHORT_BURSTY) {
            // Bursts: geometric burst size (mean ~8), tight gaps inside a burst,
            // exponential idle gaps between bursts chosen so the mean rate is lambda.
            while (i < count) {
                int burst = 1 + geometric(1.0 / 8.0);
                t += exp(burst / lambda);
                for (int b = 0; b < burst && i < count; b++) {
                    t += exp(0.02 * meanService);
                    arr[i++] = t;
                }
            }
        } else if (fam == Scenario.LONG_BATCH) {
            // Batches of 5..15 jobs submitted together.
            while (i < count) {
                int batch = 5 + rnd.nextInt(11);
                t += exp(batch / lambda);
                for (int b = 0; b < batch && i < count; b++) arr[i++] = t;
            }
        } else {
            // Poisson
            while (i < count) {
                t += exp(1.0 / lambda);
                arr[i++] = t;
            }
        }

        // 3) build specs
        for (int k = 0; k < count; k++) {
            double slack = cfg.d("sla.slack." + pr[k].name());
            double deadline = arr[k] + slack * (len[k] / meanMips);
            out.add(new TaskSpec(out.size(), round3(arr[k]), len[k], pr[k], round3(deadline), fam.name()));
        }
        return t;
    }

    private long sampleLength(Scenario fam) {
        switch (fam) {
            case SHORT_BURSTY:
                return uniformL(2000, 20000);
            case LONG_BATCH:
                return uniformL(80000, 600000);
            case MIXED_PRIORITY:
                return rnd.nextDouble() < 0.7 ? uniformL(2000, 20000) : uniformL(80000, 600000);
            case GOCJ:
                if (gocjSizes != null && gocjSizes.length > 0) {
                    return gocjSizes[rnd.nextInt(gocjSizes.length)];
                }
                // GoCJ size bands (Hussain & Aleem, 2018), MI:
                // small 15k-55k (20%), medium 59k-99k (40%), large 101k-135k (30%),
                // extra-large 150k-337.5k (4%), huge 525k-900k (6%)
                double u = rnd.nextDouble();
                if (u < 0.20) return uniformL(15000, 55000);
                if (u < 0.60) return uniformL(59000, 99000);
                if (u < 0.90) return uniformL(101000, 135000);
                if (u < 0.94) return uniformL(150000, 337500);
                return uniformL(525000, 900000);
            default:
                throw new IllegalStateException();
        }
    }

    private PriorityClass samplePriority(Scenario fam) {
        double h, m; // probability of HIGH and MEDIUM; LOW is the rest
        switch (fam) {
            case SHORT_BURSTY:   h = 0.20; m = 0.50; break;
            case LONG_BATCH:     h = 0.10; m = 0.40; break;
            case MIXED_PRIORITY: h = 0.30; m = 0.30; break;
            case GOCJ:           h = 0.25; m = 0.40; break;
            default: throw new IllegalStateException();
        }
        double u = rnd.nextDouble();
        if (u < h) return PriorityClass.HIGH;
        if (u < h + m) return PriorityClass.MEDIUM;
        return PriorityClass.LOW;
    }

    private long uniformL(long lo, long hi) {
        return lo + (long) (rnd.nextDouble() * (hi - lo));
    }

    private double exp(double mean) {
        return -mean * Math.log(1.0 - rnd.nextDouble());
    }

    private int geometric(double p) {
        return (int) Math.floor(Math.log(1.0 - rnd.nextDouble()) / Math.log(1.0 - p));
    }

    private static double round3(double x) {
        return Math.round(x * 1000.0) / 1000.0;
    }
}
