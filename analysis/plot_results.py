#!/usr/bin/env python3
"""
AS-HTS result analysis: aggregates results/summary.csv (mean +- std over seeds),
writes markdown tables and draws the comparison figures used in the paper.

Usage:
    python3 analysis/plot_results.py --results results --figures results/figures

Requires: pandas, matplotlib, numpy   (pip install -r analysis/requirements.txt)
"""
import argparse
import glob
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

MAIN = ["ASHTS", "PRIORITY_ESC", "FCFS", "SJF", "GA", "RR"]
ABLATION = ["ASHTS", "ASHTS_FIXED_ALPHA", "ASHTS_NO_SWITCH", "PRIORITY_ESC"]
# Fixed categorical order (validated palette: CVD-separated adjacent pairs, see docs/RESULTS.md)
COLOR = {
    "ASHTS": "#2a78d6", "PRIORITY_ESC": "#eb6834", "FCFS": "#1baf7a",
    "SJF": "#eda100", "GA": "#e87ba4", "RR": "#008300",
    "ASHTS_FIXED_ALPHA": "#6250d6", "ASHTS_NO_SWITCH": "#e34948",
}
LABEL = {"ASHTS": "AS-HTS (proposed)", "PRIORITY_ESC": "Priority+Escalation", "FCFS": "FCFS",
         "SJF": "SJF", "GA": "Genetic", "RR": "Round Robin",
         "ASHTS_FIXED_ALPHA": "AS-HTS, fixed α", "ASHTS_NO_SWITCH": "AS-HTS, no switching"}
SCEN_ORDER = ["SHORT_BURSTY", "LONG_BATCH", "MIXED_PRIORITY", "PHASED", "GOCJ"]
SCEN_LABEL = {"SHORT_BURSTY": "Short-bursty", "LONG_BATCH": "Long-batch",
              "MIXED_PRIORITY": "Mixed-priority", "PHASED": "Phased", "GOCJ": "GoCJ"}
METRIC_LABEL = {
    "makespan": "Makespan (s)", "avg_wait": "Average waiting time (s)",
    "max_wait_LOW": "Max waiting time, LOW priority (s)", "vm_util": "VM utilisation (%)",
    "sla_rate_HIGH": "SLA violation rate, HIGH priority (%)", "sla_rate": "SLA violation rate, all tasks (%)",
    "p95_wait_LOW": "95th-pct waiting time, LOW (s)", "di_vm": "Degree of imbalance (VM busy time)",
}
PCT = {"vm_util", "sla_rate_HIGH", "sla_rate", "sla_rate_MEDIUM", "sla_rate_LOW"}

INK = "#2b2b2a"
MUTED = "#6b6b66"
GRID = "#e6e5e0"


def style():
    plt.rcParams.update({
        "figure.dpi": 130, "savefig.dpi": 160, "font.size": 9.5,
        "axes.edgecolor": MUTED, "axes.labelcolor": INK, "axes.titlesize": 10.5,
        "axes.titleweight": "bold", "axes.titlecolor": INK, "xtick.color": MUTED, "ytick.color": MUTED,
        "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.7, "axes.axisbelow": True,
        "axes.spines.top": False, "axes.spines.right": False, "legend.frameon": False,
        "text.color": INK,
    })


def load(results):
    d = pd.read_csv(os.path.join(results, "summary.csv"))
    for c in PCT:
        if c in d:
            d[c] = d[c] * 100.0
    return d


def aggregate(d):
    metrics = [c for c in d.columns if c not in ("scenario", "load", "policy", "seed")]
    g = d.groupby(["scenario", "load", "policy"])[metrics]
    agg = g.mean().add_suffix("_mean").join(g.std().fillna(0).add_suffix("_std")).reset_index()
    return agg


def grouped_bars(agg, metric, fig_path, policies=MAIN, log=False):
    loads = sorted(agg["load"].unique())
    scen = [s for s in SCEN_ORDER if s in agg["scenario"].unique()]
    fig, axes = plt.subplots(1, len(loads), figsize=(4.6 * len(loads), 3.6), sharey=log)
    axes = np.atleast_1d(axes)
    width = 0.8 / len(policies)
    for ax, L in zip(axes, loads):
        sub = agg[agg["load"] == L]
        x = np.arange(len(scen))
        for i, p in enumerate(policies):
            vals, errs = [], []
            for s in scen:
                r = sub[(sub["scenario"] == s) & (sub["policy"] == p)]
                vals.append(r[metric + "_mean"].values[0] if len(r) else np.nan)
                errs.append(r[metric + "_std"].values[0] if len(r) else 0)
            ax.bar(x + (i - (len(policies) - 1) / 2) * width, vals, width * 0.88, yerr=errs,
                   color=COLOR[p], label=LABEL[p], error_kw=dict(ecolor=MUTED, lw=0.7, capsize=1.5))
        ax.set_xticks(x)
        ax.set_xticklabels([SCEN_LABEL[s] for s in scen], rotation=20, ha="right")
        ax.set_title(f"Offered load ρ = {L:.2f}")
        if log:
            ax.set_yscale("log")
    axes[0].set_ylabel(METRIC_LABEL.get(metric, metric) + (" — log scale" if log else ""))
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=len(policies), bbox_to_anchor=(0.5, 1.04))
    fig.suptitle(METRIC_LABEL.get(metric, metric) + " (mean ± std over seeds)", y=1.10, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)


def starvation_vs_load(agg, fig_path):
    scen = [s for s in SCEN_ORDER if s in agg["scenario"].unique()]
    fig, axes = plt.subplots(1, len(scen), figsize=(3.3 * len(scen), 3.2))
    for ax, s in zip(np.atleast_1d(axes), scen):
        sub = agg[agg["scenario"] == s]
        for p in MAIN:
            r = sub[sub["policy"] == p].sort_values("load")
            ax.plot(r["load"], r["max_wait_LOW_mean"], marker="o", ms=4.5, lw=2 if p == "ASHTS" else 1.4,
                    color=COLOR[p], label=LABEL[p], zorder=3 if p == "ASHTS" else 2)
        ax.set_yscale("log")
        ax.set_title(SCEN_LABEL[s])
        ax.set_xlabel("Offered load ρ")
    np.atleast_1d(axes)[0].set_ylabel("Max wait of LOW-priority tasks (s, log)")
    h, l = np.atleast_1d(axes)[0].get_legend_handles_labels()
    fig.legend(h, l, loc="upper center", ncol=len(MAIN), bbox_to_anchor=(0.5, 1.06))
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)


def tradeoff(agg, fig_path, load):
    scen = [s for s in SCEN_ORDER if s in agg["scenario"].unique()]
    pols = [p for p in MAIN if p != "RR"]
    fig, axes = plt.subplots(1, len(scen), figsize=(3.3 * len(scen), 3.3))
    for ax, s in zip(np.atleast_1d(axes), scen):
        sub = agg[(agg["scenario"] == s) & (np.isclose(agg["load"], load))]
        for p in pols:
            r = sub[sub["policy"] == p]
            if not len(r):
                continue
            ax.scatter(r["max_wait_LOW_mean"], r["sla_rate_HIGH_mean"], s=70 if p == "ASHTS" else 46,
                       color=COLOR[p], edgecolor="white", linewidth=1.5, label=LABEL[p], zorder=3)
        ax.set_title(SCEN_LABEL[s])
        ax.set_xlabel("Max wait, LOW (s)  →  worse")
    np.atleast_1d(axes)[0].set_ylabel("SLA violations, HIGH (%)  →  worse")
    h, l = np.atleast_1d(axes)[0].get_legend_handles_labels()
    fig.legend(h, l, loc="upper center", ncol=len(pols), bbox_to_anchor=(0.5, 1.06))
    fig.suptitle(f"Fairness vs. high-priority SLA at ρ = {load:.2f} (bottom-left is best; RR omitted, off-scale)",
                 y=1.13, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)


def ablation(agg, fig_path, load):
    scen = [s for s in SCEN_ORDER if s in agg["scenario"].unique()]
    pols = [p for p in ABLATION if p in agg["policy"].unique()]
    metrics = ["max_wait_LOW", "sla_rate_HIGH", "avg_wait"]
    fig, axes = plt.subplots(1, 3, figsize=(14, 3.6))
    width = 0.8 / len(pols)
    sub = agg[np.isclose(agg["load"], load)]
    for ax, m in zip(axes, metrics):
        x = np.arange(len(scen))
        for i, p in enumerate(pols):
            vals = [sub[(sub["scenario"] == s) & (sub["policy"] == p)][m + "_mean"].mean() for s in scen]
            ax.bar(x + (i - (len(pols) - 1) / 2) * width, vals, width * 0.88, color=COLOR[p], label=LABEL[p])
        ax.set_xticks(x)
        ax.set_xticklabels([SCEN_LABEL[s] for s in scen], rotation=20, ha="right")
        ax.set_title(METRIC_LABEL.get(m, m))
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="upper center", ncol=len(pols), bbox_to_anchor=(0.5, 1.07))
    fig.suptitle(f"Ablation study at ρ = {load:.2f}", y=1.14, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)


def regime_timeline(results, fig_path, tag="PHASED_0.90"):
    tl = os.path.join(results, "timeline", f"{tag}_ASHTS.csv")
    tasks = os.path.join(results, "tasks", f"{tag}_ASHTS.csv")
    if not (os.path.exists(tl) and os.path.exists(tasks)):
        return False
    t = pd.read_csv(tl)
    k = pd.read_csv(tasks)
    order = ["SHORT_BURSTY", "LONG_BATCH", "MIXED_PRIORITY"]
    ymap = {r: i for i, r in enumerate(order)}
    fig, (a1, a2, a3) = plt.subplots(3, 1, figsize=(10, 6.2), sharex=True,
                                     gridspec_kw={"height_ratios": [1.1, 1, 1]})
    # true phases as shaded bands
    k = k.sort_values("arrival")
    shades = ["#f3f2ee", "#ffffff"]
    blocks = (k["phase"] != k["phase"].shift()).cumsum()
    for i, (_, grp) in enumerate(k.groupby(blocks)):
        for ax in (a1, a2, a3):
            ax.axvspan(grp["arrival"].min(), grp["arrival"].max(), color=shades[i % 2], zorder=0)
        a1.text((grp["arrival"].min() + grp["arrival"].max()) / 2, 2.55, "true: " + SCEN_LABEL[grp["phase"].iloc[0]],
                ha="center", va="bottom", fontsize=8, color=MUTED)
    a1.step(t["time"], t["stable_regime"].map(ymap), where="post", color=COLOR["ASHTS"], lw=2)
    a1.set_yticks(range(len(order)))
    a1.set_yticklabels(["Short-bursty → SJF mode", "Long-batch → escalation", "Mixed-priority → escalation"])
    a1.set_ylim(-0.4, 2.9)
    a1.set_title("Runtime classifier: detected regime vs. true workload phase (" + tag.replace("_", ", ρ = ") + ")")
    a2.plot(t["time"], t["window_mean_mi"] / 1000, color=INK, lw=1.2)
    a2.set_ylabel("Window mean\nlength (k MI)")
    a3.plot(t["time"], t["hi_frac"], color=COLOR["PRIORITY_ESC"], lw=1.4, label="HIGH share")
    a3.plot(t["time"], t["lo_frac"], color=COLOR["FCFS"], lw=1.4, label="LOW share")
    a3.set_ylim(0, 1)
    a3.set_ylabel("Share of window")
    a3.set_xlabel("Simulation time (s)")
    a3.legend(loc="upper right", ncol=2, fontsize=8)
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)
    return True


def class_waits(results, fig_path, tag="MIXED_PRIORITY_1.05"):
    pols = [p for p in MAIN if p != "RR"]
    data, ok = {}, False
    for p in pols:
        f = os.path.join(results, "tasks", f"{tag}_{p}.csv")
        if os.path.exists(f):
            data[p] = pd.read_csv(f)
            ok = True
    if not ok:
        return False
    classes = ["HIGH", "MEDIUM", "LOW"]
    fig, axes = plt.subplots(1, 3, figsize=(13, 3.4), sharey=True)
    for ax, c in zip(axes, classes):
        vals = [data[p][data[p]["priority"] == c]["wait"].clip(lower=0.01).values for p in pols]
        bp = ax.boxplot(vals, patch_artist=True, widths=0.6, showfliers=True,
                        flierprops=dict(marker="o", ms=2.5, alpha=0.35, markeredgecolor=MUTED),
                        medianprops=dict(color=INK, lw=1.2))
        for patch, p in zip(bp["boxes"], pols):
            patch.set_facecolor(COLOR[p])
            patch.set_edgecolor("white")
        ax.set_xticks(range(1, len(pols) + 1))
        ax.set_xticklabels([LABEL[p] for p in pols], rotation=25, ha="right")
        ax.set_yscale("log")
        ax.set_title(f"{c} priority tasks")
    axes[0].set_ylabel("Waiting time (s, log)")
    fig.suptitle(f"Waiting-time distribution by priority class ({tag.replace('_', ' ')}, seed 1)",
                 y=1.03, fontweight="bold")
    fig.tight_layout()
    fig.savefig(fig_path, bbox_inches="tight")
    plt.close(fig)
    return True


def fmt(m, s, metric):
    if metric in PCT:
        return f"{m:.1f} ± {s:.1f}"
    if m >= 100:
        return f"{m:,.0f} ± {s:,.0f}"
    return f"{m:.2f} ± {s:.2f}"


def tables(agg, out_md):
    cols = ["makespan", "avg_wait", "max_wait_LOW", "sla_rate_HIGH", "sla_rate", "vm_util", "di_vm"]
    lines = ["# AS-HTS results tables", "",
             "Mean ± standard deviation over all seeds. Times in simulated seconds; rates and utilisation in %.",
             "Lower is better for every column except VM utilisation.", ""]

    # Headline: AS-HTS vs. the starvation-aware baseline
    lines += ["## Headline: AS-HTS vs. Priority+Escalation baseline (Silver-T)", "",
              "Relative change = (AS-HTS − baseline) / baseline. Negative is an improvement.", "",
              "| Scenario | Load | Max wait LOW | p95 wait LOW | SLA-viol. HIGH (pp) | Avg wait | Makespan HIGH |",
              "|---|---|---|---|---|---|---|"]
    for s in SCEN_ORDER:
        for L in sorted(agg["load"].unique()):
            a = agg[(agg.scenario == s) & np.isclose(agg.load, L) & (agg.policy == "ASHTS")]
            b = agg[(agg.scenario == s) & np.isclose(agg.load, L) & (agg.policy == "PRIORITY_ESC")]
            if not len(a) or not len(b):
                continue
            a, b = a.iloc[0], b.iloc[0]

            def rel(m):
                return f"{100 * (a[m + '_mean'] - b[m + '_mean']) / b[m + '_mean']:+.1f}%" if b[m + "_mean"] else "n/a"
            pp = a["sla_rate_HIGH_mean"] - b["sla_rate_HIGH_mean"]
            lines.append(f"| {SCEN_LABEL[s]} | {L:.2f} | {rel('max_wait_LOW')} | {rel('p95_wait_LOW')} | "
                         f"{pp:+.1f} | {rel('avg_wait')} | {rel('makespan_HIGH')} |")
    lines.append("")

    for s in SCEN_ORDER:
        for L in sorted(agg["load"].unique()):
            sub = agg[(agg.scenario == s) & np.isclose(agg.load, L)]
            if not len(sub):
                continue
            lines += [f"## {SCEN_LABEL[s]}, load {L:.2f}", "",
                      "| Policy | " + " | ".join(METRIC_LABEL.get(c, c) for c in cols) + " |",
                      "|---" * (len(cols) + 1) + "|"]
            for p in MAIN + ["ASHTS_FIXED_ALPHA", "ASHTS_NO_SWITCH"]:
                r = sub[sub.policy == p]
                if not len(r):
                    continue
                r = r.iloc[0]
                name = f"**{LABEL[p]}**" if p == "ASHTS" else LABEL[p]
                lines.append(f"| {name} | " + " | ".join(fmt(r[c + '_mean'], r[c + '_std'], c) for c in cols) + " |")
            lines.append("")
    with open(out_md, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", default="results")
    ap.add_argument("--figures", default=None)
    a = ap.parse_args()
    figs = a.figures or os.path.join(a.results, "figures")
    os.makedirs(figs, exist_ok=True)
    style()

    d = load(a.results)
    agg = aggregate(d)
    agg.to_csv(os.path.join(a.results, "summary_aggregated.csv"), index=False)
    tables(agg, os.path.join(a.results, "summary_table.md"))

    hi_load = max(agg["load"].unique())
    made = []
    for i, (m, log) in enumerate([("makespan", False), ("avg_wait", True), ("max_wait_LOW", True),
                                  ("sla_rate_HIGH", False), ("sla_rate", False), ("vm_util", False)], 1):
        p = os.path.join(figs, f"fig{i}_{m}.png")
        grouped_bars(agg, m, p, log=log)
        made.append(p)
    p = os.path.join(figs, "fig7_starvation_vs_load.png"); starvation_vs_load(agg, p); made.append(p)
    p = os.path.join(figs, "fig8_tradeoff.png"); tradeoff(agg, p, hi_load); made.append(p)
    if "ASHTS_FIXED_ALPHA" in agg["policy"].unique():
        p = os.path.join(figs, "fig9_ablation.png"); ablation(agg, p, hi_load); made.append(p)
    tags = sorted(os.path.basename(f).replace("_ASHTS.csv", "") for f in glob.glob(os.path.join(a.results, "timeline", "PHASED_*_ASHTS.csv")))
    if tags:
        tag = "PHASED_0.90" if "PHASED_0.90" in tags else tags[0]
        p = os.path.join(figs, "fig10_regime_timeline.png")
        if regime_timeline(a.results, p, tag):
            made.append(p)
    mixed = sorted(glob.glob(os.path.join(a.results, "tasks", "MIXED_PRIORITY_*_ASHTS.csv")))
    if mixed:
        tag = os.path.basename(mixed[-1]).replace("_ASHTS.csv", "")
        p = os.path.join(figs, "fig11_wait_by_class.png")
        if class_waits(a.results, p, tag):
            made.append(p)

    print("Wrote:")
    print("  " + os.path.join(a.results, "summary_aggregated.csv"))
    print("  " + os.path.join(a.results, "summary_table.md"))
    for m in made:
        print("  " + m)


if __name__ == "__main__":
    main()
