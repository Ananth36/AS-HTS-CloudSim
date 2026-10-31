package edu.manipal.ashts.core;

/**
 * Three user-facing priority classes. Each maps onto the 1..10 "priority level"
 * scale used by the Silver-T CloudSim-Algorithm-Project baseline, so the
 * baseline escalation formula can be reproduced faithfully.
 */
public enum PriorityClass {
    HIGH(8),
    MEDIUM(5),
    LOW(2);

    /** Priority level on the 1..10 scale (higher = more important). */
    public final int level;

    PriorityClass(int level) {
        this.level = level;
    }

    public static PriorityClass parse(String s) {
        String t = s.trim().toUpperCase();
        if (t.startsWith("H")) return HIGH;
        if (t.startsWith("M")) return MEDIUM;
        if (t.startsWith("L")) return LOW;
        throw new IllegalArgumentException("Unknown priority class: " + s);
    }
}
