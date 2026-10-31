package edu.manipal.ashts.policy;

import edu.manipal.ashts.core.SimConfig;

/**
 * Creates policies by name. Besides the six main schedulers, two ablation variants
 * of AS-HTS are available for the paper's ablation study:
 * <ul>
 *   <li>ASHTS_FIXED_ALPHA - classifier + switching, but constant escalation weight</li>
 *   <li>ASHTS_NO_SWITCH   - adaptive escalation only, never switches to SJF</li>
 * </ul>
 * Ablation variants modify a copy of the config, which the broker passes to init().
 */
public final class PolicyFactory {

    private PolicyFactory() {}

    public static final String[] ALL = {"FCFS", "SJF", "RR", "GA", "PRIORITY_ESC", "ASHTS",
            "ASHTS_FIXED_ALPHA", "ASHTS_NO_SWITCH"};

    public static SchedulingPolicy create(String name) {
        String n = name.trim().toUpperCase();
        if (n.equals("FCFS")) return new FcfsPolicy();
        if (n.equals("SJF")) return new SjfPolicy();
        if (n.equals("RR") || n.equals("ROUND_ROBIN")) return new RoundRobinPolicy();
        if (n.equals("GA") || n.equals("GENETIC")) return new GeneticPolicy();
        if (n.equals("PRIORITY_ESC") || n.equals("PRIORITY")) return new PriorityEscalationPolicy();
        if (n.startsWith("ASHTS") || n.equals("AS-HTS")) return new AsHtsPolicy(n.replace("AS-HTS", "ASHTS"));
        throw new IllegalArgumentException("Unknown policy " + name + ". Known: " + java.util.Arrays.toString(ALL));
    }

    /** Applies the config changes an ablation variant needs (returns a modified copy). */
    public static SimConfig configFor(String name, SimConfig base) {
        String n = name.trim().toUpperCase();
        SimConfig c = base.copy();
        if (n.equals("ASHTS_FIXED_ALPHA")) c.set("ashts.adaptiveAlpha", "false");
        if (n.equals("ASHTS_NO_SWITCH")) c.set("ashts.switching", "false");
        return c;
    }
}
