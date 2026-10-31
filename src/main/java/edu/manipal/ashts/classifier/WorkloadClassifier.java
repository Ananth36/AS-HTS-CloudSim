package edu.manipal.ashts.classifier;

import edu.manipal.ashts.core.PriorityClass;
import edu.manipal.ashts.core.SimConfig;
import edu.manipal.ashts.core.TaskSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Lightweight rule-based runtime workload classifier (AS-HTS component 1).
 *
 * <p>Keeps a rolling window of the last W arrivals and computes:</p>
 * <ul>
 *   <li>mean task length (MI) and coefficient of variation (CV = std/mean) of lengths,</li>
 *   <li>share of HIGH and of LOW priority tasks.</li>
 * </ul>
 * Rules (evaluated in order):
 * <pre>
 *   if hiFrac >= H_T and loFrac >= L_T             -> MIXED_PRIORITY
 *   else if meanLen <= SHORT_T and cv <= CV_T      -> SHORT_BURSTY
 *   else                                           -> LONG_BATCH
 * </pre>
 * <p>Hysteresis: the stable regime only changes after the same new raw label has been
 * produced K times in a row, preventing rapid flip-flopping between sub-schedulers.</p>
 */
public final class WorkloadClassifier {

    /** One point of the regime timeline (written to CSV for plotting). */
    public static final class Sample {
        public final double time;
        public final Regime raw;
        public final Regime stable;
        public final double meanLen, cv, hiFrac, loFrac;

        Sample(double time, Regime raw, Regime stable, double meanLen, double cv, double hiFrac, double loFrac) {
            this.time = time; this.raw = raw; this.stable = stable;
            this.meanLen = meanLen; this.cv = cv; this.hiFrac = hiFrac; this.loFrac = loFrac;
        }
    }

    private final int window, hysteresis;
    private final double shortMean, shortCv, hiT, loT;

    private final Deque<TaskSpec> win = new ArrayDeque<TaskSpec>();
    private Regime stable = null;
    private Regime candidate = null;
    private int candidateCount = 0;
    private int switches = 0;
    private double meanLen = 0;
    private final List<Sample> timeline = new ArrayList<Sample>();

    public WorkloadClassifier(SimConfig c) {
        window = c.i("ashts.window");
        hysteresis = c.i("ashts.hysteresis");
        shortMean = c.d("ashts.short.meanMI");
        shortCv = c.d("ashts.short.cv");
        hiT = c.d("ashts.mixed.hiFrac");
        loT = c.d("ashts.mixed.loFrac");
    }

    /** Feed one arrival; returns the (stable) regime after this observation. */
    public Regime observe(TaskSpec t, double now) {
        win.addLast(t);
        if (win.size() > window) win.removeFirst();

        int n = win.size();
        double sum = 0, sq = 0;
        int hi = 0, lo = 0;
        for (TaskSpec s : win) {
            sum += s.lengthMI;
            sq += (double) s.lengthMI * s.lengthMI;
            if (s.priority == PriorityClass.HIGH) hi++;
            else if (s.priority == PriorityClass.LOW) lo++;
        }
        meanLen = sum / n;
        double var = Math.max(0, sq / n - meanLen * meanLen);
        double cv = meanLen > 0 ? Math.sqrt(var) / meanLen : 0;
        double hiFrac = hi / (double) n, loFrac = lo / (double) n;

        Regime raw;
        if (hiFrac >= hiT && loFrac >= loT) raw = Regime.MIXED_PRIORITY;
        else if (meanLen <= shortMean && cv <= shortCv) raw = Regime.SHORT_BURSTY;
        else raw = Regime.LONG_BATCH;

        if (stable == null) {
            // Warm-up: adopt the first label once the window has a few samples.
            if (n >= Math.min(window, 10)) { stable = raw; candidate = raw; }
        } else if (raw != stable) {
            if (raw == candidate) candidateCount++;
            else { candidate = raw; candidateCount = 1; }
            if (candidateCount >= hysteresis) {
                stable = raw;
                switches++;
                candidateCount = 0;
            }
        } else {
            candidate = raw;
            candidateCount = 0;
        }
        Regime current = stable == null ? raw : stable;
        timeline.add(new Sample(now, raw, current, meanLen, cv, hiFrac, loFrac));
        return current;
    }

    public Regime current() {
        return stable == null ? Regime.MIXED_PRIORITY : stable;
    }

    /** Mean length (MI) of the tasks in the current window. */
    public double windowMeanLength() { return meanLen; }

    public int switches() { return switches; }

    public List<Sample> timeline() { return timeline; }
}
