package edu.manipal.ashts.workload;

/** The workload families used in the experiments. See docs/WORKLOADS.md. */
public enum Scenario {
    /** Many short, similar-sized tasks arriving in bursts. SJF territory. */
    SHORT_BURSTY,
    /** Large batch jobs arriving in groups, priority mostly medium/low. */
    LONG_BATCH,
    /** Bimodal lengths and a strong mix of HIGH and LOW priority. Starvation territory. */
    MIXED_PRIORITY,
    /** SHORT_BURSTY -> LONG_BATCH -> MIXED_PRIORITY -> SHORT_BURSTY in one run. Tests adaptivity. */
    PHASED,
    /** Job sizes drawn from the GoCJ (Google-Cloud-Jobs) size bands, Poisson arrivals. */
    GOCJ,
    /** Loaded from a CSV file (see WorkloadIO). */
    CUSTOM
}
