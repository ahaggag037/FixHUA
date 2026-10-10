# FixHUA 3 — System Brain Research Round 3

Date: 2026-10-10
Focus: interaction smoothness, graphics, Binder latency, storage I/O, and display refresh behavior.

## 1. Smoothness is a pipeline, not a CPU number

On Android, a visible frame can miss its deadline even when average CPU use looks acceptable. Android 12 FrameTimeline records expected vs actual presentation timing, and Perfetto can collect CPU scheduling, GPU frequency/counters, Binder activity, memory and I/O evidence on the same timeline.

This suggests a new FixHUA subsystem: **Interaction Latency Brain**.

Its job is not to maximize FPS blindly. Its job is to explain missed interaction deadlines.

Sources:
- FrameTimeline: https://perfetto.dev/docs/data-sources/frametimeline
- Perfetto GPU tracing: https://perfetto.dev/docs/data-sources/gpu
- Perfetto CPU scheduling: https://perfetto.dev/docs/data-sources/cpu-scheduling

## 2. New invention: Jank Attribution Graph

For every significant jank burst, build a causal candidate graph across the same time window:

- app main/render thread runnable but not scheduled → scheduler contention;
- app thread running but blocked on Binder → service/dependency latency;
- GPU queue/render stage exceeds deadline → GPU/render bottleneck;
- CPU/GPU frequency collapses while thermal evidence rises → thermal limitation;
- I/O PSI rises while app waits → storage contention;
- memory PSI/refault/LMK activity rises → reclaim/thrash;
- refresh-rate transition occurs around the event → display/frame-pacing interaction;
- no system bottleneck → likely app-local work or network wait.

The Brain should never apply a CPU boost to a GPU- or Binder-limited jank event.

## 3. Display refresh policy is itself dynamic

Huawei nova 9 SE supports up to 90 Hz. Android's DisplayManager/SurfaceFlinger chooses refresh rate using system policy, active layers, app frame-rate requests, battery saver, touch and idle heuristics. Therefore 'force maximum refresh forever' is not automatically optimal.

New idea: **Refresh Stability Monitor**.

Measure whether visible jank clusters around refresh-rate transitions or an app repeatedly oscillates between frame-rate regimes. If so, recommend or experimentally stabilize a compatible refresh mode only for that workload, then compare frame-overrun and energy cost.

Source:
- Android multiple refresh rate: https://source.android.com/docs/core/graphics/multiple-refresh-rate

## 4. GPU must be observed independently

Perfetto can collect GPU frequency, GPU memory and, when the producer supports it, hardware GPU counters and render stages.

New idea: **CPU↔GPU Misallocation Detector**.

Examples:
- high CPU boost + GPU saturation = wasted CPU thermal budget;
- GPU low utilization + render deadline miss + CPU runnable delay = CPU/scheduler issue;
- both CPU and GPU below saturation + thermal cap = thermal/power controller issue;
- both low + Binder wait = dependency latency.

No GPU frequency or governor write should be automated until the exact KGSL/devfreq controls and thermal interaction are probed on the device.

Source:
- Perfetto GPU tracing: https://perfetto.dev/docs/data-sources/gpu

## 5. Binder latency can masquerade as 'slow CPU'

Android apps frequently block waiting on framework/system services through Binder. Android performance documentation identifies Binder scheduling/priority interactions as a potential source of latency.

New idea: **Dependency Wait Score**.

When an app feels slow, measure how much of the interaction window was:
- actually running;
- runnable but not scheduled;
- blocked on Binder;
- blocked on I/O;
- sleeping/waiting for another event.

This prevents FixHUA from boosting compute resources when the task is actually waiting on another service.

Sources:
- Android performance testing / Binder priority inversion: https://source.android.com/docs/core/tests/vts/performance
- Perfetto standard library startup breakdowns: https://perfetto.dev/docs/analysis/stdlib-docs

## 6. Storage optimization should target latency, not 'cleaning'

Linux I/O schedulers explicitly trade throughput, fairness and latency. Android devices may use vendor-specific or multi-queue configurations, so FixHUA should not switch schedulers blindly.

New idea: **Interactive I/O Shield**.

If I/O PSI shows user-visible stalls while a known background maintenance/download/indexing workload is active, the Brain can consider lowering the background workload's I/O priority or deferring FixHUA-owned maintenance rather than changing the global storage scheduler.

This is safer than cache cleaning or global I/O-scheduler replacement.

Sources:
- Linux block I/O priorities: https://docs.kernel.org/block/ioprio.html
- BFQ documentation as a latency/throughput design reference: https://docs.kernel.org/block/bfq-iosched.html

## 7. New invention: User-Perceived Latency Ledger

For each app/session store distributions, not single averages:

- launch P50/P90/P95;
- resume P50/P90/P95;
- frame overrun P90/P95/P99;
- jank burst count;
- time runnable-but-unscheduled;
- Binder wait time;
- I/O stall time;
- memory stall time;
- thermal-limited time;
- app reload count.

The Brain optimizes tail latency because occasional long freezes are often more annoying than a slightly slower average.

## 8. New invention: Phase-aware control

An app moves through different workload phases:

1. LAUNCH
2. INTERACTIVE_BURST
3. STEADY_INTERACTIVE
4. MEDIA/GAME_RENDER
5. BACKGROUND_ACTIVE
6. BACKGROUND_IDLE
7. DEVICE_IDLE_MAINTENANCE

Each phase should have different priorities. For example:
- launch may justify a short validated latency burst;
- steady interaction prioritizes jank stability;
- background active prioritizes survival with bounded resource use;
- device idle is where heavy diagnostics/dexopt/maintenance belong.

## 9. New invention: Optimization conflict resolver

Several optimizations can fight each other. Example:
- CPU boost reduces launch time but consumes thermal headroom;
- depleted thermal headroom later causes frame throttling;
- app protection increases memory pressure and triggers other reloads.

Every candidate action should therefore declare:
- intended metric;
- resources consumed;
- known conflicts;
- maximum duration;
- cooldown;
- rollback trigger.

The Brain then chooses a portfolio, not independent tweaks.

## 10. Research conclusion after Round 3

A strong System Brain needs to distinguish at least six latency causes before acting: CPU scheduling, memory/reclaim, GPU/render, Binder/dependency, storage I/O, and thermal/power limitation. The first privileged probe should collect enough synchronized evidence to attribute these causes on the user's actual Huawei device.
