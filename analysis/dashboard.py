from pathlib import Path
import subprocess

import pandas as pd
import plotly.express as px
import streamlit as st


ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "results"
LIVE_RESULTS = ROOT / "results_demo" / "single"
METRICS = ["makespan", "avg_wait", "max_wait_LOW", "sla_rate_HIGH", "sla_rate", "vm_util"]
PERCENT = {"sla_rate_HIGH", "sla_rate", "vm_util"}
POLICIES = ["ASHTS", "PRIORITY_ESC", "FCFS", "SJF", "GA", "RR",
            "ASHTS_FIXED_ALPHA", "ASHTS_NO_SWITCH"]


def read_csv(path):
    return pd.read_csv(path) if path.exists() else None


def load_summary(folder):
    path = folder / "summary.csv"
    if path.exists():
        return read_csv(path)
    files = sorted(folder.glob("summary_*.csv"))
    frames = [read_csv(f) for f in files]
    return pd.concat([f for f in frames if f is not None], ignore_index=True) if frames else None


def choices(data):
    return sorted(data["scenario"].dropna().unique()), sorted(data["load"].dropna().unique())


def filtered(data, scenario, load):
    return data[(data["scenario"] == scenario) & (data["load"].astype(float) == float(load))]


def table(data, scenario, load, policies=None):
    rows = filtered(data, scenario, load)
    if policies:
        rows = rows[rows["policy"].isin(policies)]
    out = rows.groupby("policy", as_index=True)[METRICS].mean().reindex(
        [p for p in (policies or POLICIES) if p in rows["policy"].unique()])
    display = out.copy()
    for col in PERCENT:
        display[col] *= 100
    return display.style.format({
        "makespan": "{:,.1f}", "avg_wait": "{:,.1f}", "max_wait_LOW": "{:,.1f}",
        "sla_rate_HIGH": "{:.1f}%", "sla_rate": "{:.1f}%", "vm_util": "{:.1f}%"
    }).apply(lambda row: ["background-color: #dbeafe; font-weight: bold"
                           if row.name == "ASHTS" else "" for _ in row], axis=1)


def charts(data, scenario, load, policies=None, title=""):
    rows = filtered(data, scenario, load)
    if policies:
        rows = rows[rows["policy"].isin(policies)]
    agg = rows.groupby("policy", as_index=False)[METRICS].mean()
    for col in PERCENT:
        agg[col] *= 100
    labels = {"max_wait_LOW": "LOW maximum wait", "sla_rate_HIGH": "HIGH SLA violations",
              "avg_wait": "Average wait"}
    for metric in ("max_wait_LOW", "sla_rate_HIGH", "avg_wait"):
        fig = px.bar(agg, x="policy", y=metric, color="policy", title=f"{title}{labels[metric]}")
        st.plotly_chart(fig, use_container_width=True)
    fig = px.scatter(agg, x="max_wait_LOW", y="sla_rate_HIGH", text="policy",
                     color="policy", title=f"{title}Fairness vs HIGH-priority SLA")
    st.plotly_chart(fig, use_container_width=True)


def selectors(data, key):
    scenarios, loads = choices(data)
    scenario = st.selectbox("Scenario", scenarios, key=f"{key}_scenario")
    load = st.selectbox("Load", loads, format_func=lambda x: f"{float(x):.2f}", key=f"{key}_load")
    return scenario, load


def show_results(data, scenario, load, policies=None, title=""):
    st.subheader(f"{title} Summary")
    st.dataframe(table(data, scenario, load, policies), use_container_width=True)
    charts(data, scenario, load, policies, title)


st.set_page_config(page_title="AS-HTS Dashboard", layout="wide")
st.title("AS-HTS CloudSim Dashboard")
st.caption("Explore reproducible Java CloudSim scheduling experiments and live runs.")

stored = load_summary(RESULTS)
if stored is None:
    st.warning("No stored results found. Run the experiment first or use the Live run tab.")
    stored = pd.DataFrame(columns=["scenario", "load", "policy"] + METRICS)

results_tab, live_tab, timeline_tab, ablation_tab = st.tabs(
    ["Results explorer", "Live run", "Regime timeline", "Ablation"])

with results_tab:
    if stored.empty:
        st.info("Stored results are unavailable.")
    else:
        scenario, load = selectors(stored, "stored")
        show_results(stored, scenario, load)

with live_tab:
    scenario = st.selectbox("Scenario", ["SHORT_BURSTY", "LONG_BATCH", "MIXED_PRIORITY", "PHASED", "GOCJ"],
                            key="live_scenario")
    load = st.selectbox("Load", [0.70, 0.90, 1.05], format_func=lambda x: f"{x:.2f}", key="live_load")
    seed = st.selectbox("Seed", [1, 2, 3, 4, 5], key="live_seed")
    if st.button("Run simulation"):
        LIVE_RESULTS.mkdir(parents=True, exist_ok=True)
        command = ["scripts\\run.bat", "run", "--scenario", scenario, "--load", str(load),
                   "--seed", str(seed), "--out", str(LIVE_RESULTS)]
        with st.spinner("Running Java CloudSim simulation..."):
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, shell=True)
        if result.returncode:
            st.error("The simulation failed.")
            st.code(result.stderr or result.stdout)
        else:
            st.session_state["live_summary"] = load_summary(LIVE_RESULTS)
            st.session_state["live_summary"]["scenario"] = scenario
            st.success("Simulation completed.")
            if result.stdout:
                st.code(result.stdout)
    live = st.session_state.get("live_summary")
    if live is not None:
        show_results(live, scenario, load, title="Live run")
        rows = filtered(live, scenario, load).set_index("policy")
        if {"ASHTS", "PRIORITY_ESC"}.issubset(rows.index):
            base, proposed = rows.loc["PRIORITY_ESC"], rows.loc["ASHTS"]
            st.subheader("AS-HTS vs PRIORITY_ESC")
            st.write({
                "max_wait_LOW change": f"{(proposed.max_wait_LOW / base.max_wait_LOW - 1) * 100:.1f}%",
                "SLA-HIGH change": f"{(proposed.sla_rate_HIGH - base.sla_rate_HIGH) * 100:.1f} percentage points",
                "avg_wait change": f"{(proposed.avg_wait / base.avg_wait - 1) * 100:.1f}%",
            })

with timeline_tab:
    source = st.session_state.get("live_summary", stored)
    if source.empty:
        st.info("Timeline choices are unavailable.")
    else:
        scenario, load = selectors(source, "timeline")
        live_mode = "live_summary" in st.session_state
        seeds = sorted(source[source.scenario == scenario].seed.unique()) if live_mode else [1]
        seed = st.selectbox("Seed", seeds, key="timeline_seed")
        live_path = LIVE_RESULTS / f"{scenario}_{float(load):.2f}_seed{seed}_ASHTS_timeline.csv"
        path = live_path if live_path.exists() else (
            RESULTS / "timeline" / f"{scenario}_{float(load):.2f}_ASHTS.csv")
        if path.exists():
            timeline = pd.read_csv(path)
            regime_fig = px.scatter(timeline, x="time", y="stable_regime", color="stable_regime",
                                    title="AS-HTS stable regime over time")
            st.plotly_chart(regime_fig, use_container_width=True)
            mean_fig = px.line(timeline, x="time", y="window_mean_mi",
                               title="Rolling mean task length")
            st.plotly_chart(mean_fig, use_container_width=True)
            st.dataframe(timeline, use_container_width=True)
        else:
            st.info(f"No timeline file found: {path.name}")

with ablation_tab:
    if stored.empty:
        st.info("Stored results are unavailable.")
    else:
        scenario, load = selectors(stored, "ablation")
        policies = ["ASHTS", "ASHTS_FIXED_ALPHA", "ASHTS_NO_SWITCH", "PRIORITY_ESC"]
        show_results(stored, scenario, load, policies, "Ablation")
