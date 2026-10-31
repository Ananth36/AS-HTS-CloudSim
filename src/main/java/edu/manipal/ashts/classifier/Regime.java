package edu.manipal.ashts.classifier;

/** Workload regimes recognised by the AS-HTS runtime classifier. */
public enum Regime {
    /** Short, similar-sized tasks: SJF is near-optimal and starvation risk is low. */
    SHORT_BURSTY,
    /** Long and/or highly variable task sizes: SJF would starve the big jobs. */
    LONG_BATCH,
    /** Substantial shares of both HIGH and LOW priority: starvation of LOW is the main risk. */
    MIXED_PRIORITY
}
