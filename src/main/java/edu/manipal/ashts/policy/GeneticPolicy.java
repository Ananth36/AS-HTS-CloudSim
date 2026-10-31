package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskRecord;
import edu.manipal.ashts.core.VmInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Online batch Genetic Algorithm mapper, adapted from the CloudSim-Examples
 * (Ravikisha) GeneticAlgorithm.
 *
 * <p>Whenever enough tasks have accumulated in the central queue (ga.batch), or a VM
 * is idle with nothing queued, the GA maps the whole central queue onto VMs.
 * Chromosome = VM index per task. Fitness = estimated makespan of the batch,
 * using each VM's current ready time and MIPS (the original used task length only
 * and ignored VM speed - fixed here so the baseline is not artificially weak).
 * Tournament selection, uniform crossover, random-reset mutation, 1-elitism.</p>
 */
public final class GeneticPolicy extends AbstractPolicy {

    private int popSize, generations, batch;
    private double pMut, pCross;
    private Random rnd;

    @Override public String name() { return "GA"; }

    @Override
    public void init(SchedulerContext ctx) {
        SimConfig c = ctx.config();
        popSize = c.i("ga.population");
        generations = c.i("ga.generations");
        pMut = c.d("ga.mutation");
        pCross = c.d("ga.crossover");
        batch = c.i("ga.batch");
        rnd = new Random(ctx.seed() * 7919L + 17);
    }

    @Override
    public void beforeDispatch(SchedulerContext ctx) {
        List<TaskRecord> central = ctx.centralQueue();
        if (central.isEmpty()) return;
        boolean idleAndEmpty = false;
        for (VmInfo v : ctx.vms()) {
            if (v.isIdle() && v.localQueue.isEmpty()) { idleAndEmpty = true; break; }
        }
        if (central.size() < batch && !idleAndEmpty) return;

        List<TaskRecord> tasks = new ArrayList<TaskRecord>(central);
        Collections.sort(tasks, new Comparator<TaskRecord>() {
            @Override public int compare(TaskRecord a, TaskRecord b) {
                return earlier(a, b) ? -1 : (earlier(b, a) ? 1 : 0);
            }
        });
        List<VmInfo> vms = ctx.vms();
        int n = tasks.size(), m = vms.size();
        double now = ctx.now();
        double[] ready = new double[m];
        double[] mips = new double[m];
        for (int j = 0; j < m; j++) {
            ready[j] = vms.get(j).estimatedReadyTime(now);
            mips[j] = vms.get(j).mips;
        }
        long[] len = new long[n];
        for (int i = 0; i < n; i++) len[i] = tasks.get(i).spec.lengthMI;

        // --- initial population (one greedy seed + random) ---
        int[][] pop = new int[popSize][];
        pop[0] = greedy(len, ready, mips);
        for (int k = 1; k < popSize; k++) {
            pop[k] = new int[n];
            for (int i = 0; i < n; i++) pop[k][i] = rnd.nextInt(m);
        }
        double[] fit = new double[popSize];
        for (int k = 0; k < popSize; k++) fit[k] = makespan(pop[k], len, ready, mips);

        for (int g = 0; g < generations; g++) {
            int[][] next = new int[popSize][];
            int bestIdx = argMin(fit);
            next[0] = pop[bestIdx].clone(); // elitism
            for (int k = 1; k < popSize; k++) {
                int[] a = pop[tournament(fit)];
                int[] b = pop[tournament(fit)];
                int[] child = a.clone();
                if (rnd.nextDouble() < pCross) {
                    for (int i = 0; i < n; i++) if (rnd.nextBoolean()) child[i] = b[i];
                }
                for (int i = 0; i < n; i++) if (rnd.nextDouble() < pMut / Math.max(1, n / 10.0)) child[i] = rnd.nextInt(m);
                next[k] = child;
            }
            pop = next;
            for (int k = 0; k < popSize; k++) fit[k] = makespan(pop[k], len, ready, mips);
        }
        int[] best = pop[argMin(fit)];
        for (int i = 0; i < n; i++) ctx.bind(tasks.get(i), vms.get(best[i]));
    }

    @Override
    public TaskRecord selectNext(List<TaskRecord> central, VmInfo vm, SchedulerContext ctx) {
        return none(central, vm);
    }

    private int[] greedy(long[] len, double[] ready, double[] mips) {
        double[] r = ready.clone();
        int[] a = new int[len.length];
        for (int i = 0; i < len.length; i++) {
            int bj = 0;
            double bf = Double.MAX_VALUE;
            for (int j = 0; j < r.length; j++) {
                double f = r[j] + len[i] / mips[j];
                if (f < bf) { bf = f; bj = j; }
            }
            a[i] = bj;
            r[bj] = bf;
        }
        return a;
    }

    private static double makespan(int[] a, long[] len, double[] ready, double[] mips) {
        double[] r = ready.clone();
        for (int i = 0; i < a.length; i++) r[a[i]] += len[i] / mips[a[i]];
        double mx = 0;
        for (double x : r) mx = Math.max(mx, x);
        return mx;
    }

    private int tournament(double[] fit) {
        int a = rnd.nextInt(fit.length), b = rnd.nextInt(fit.length);
        return fit[a] <= fit[b] ? a : b;
    }

    private static int argMin(double[] f) {
        int b = 0;
        for (int i = 1; i < f.length; i++) if (f[i] < f[b]) b = i;
        return b;
    }
}
