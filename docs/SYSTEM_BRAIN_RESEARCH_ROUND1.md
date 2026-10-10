# FixHUA 3 — System Brain Research Round 1

Date: 2026-10-10
Status: evidence-backed research notes; implementation not started.

## 1. Device identity: probable real platform

The diagnostic report exposed Huawei properties `JLN-L21` / `JLN-L21 13.0.0.319(C185...)` while generic Android Build fields presented a Samsung identity. Huawei's official nova 9 SE specifications identify the JLN family and confirm Qualcomm Snapdragon 680, 8 GB-class RAM configurations, 90 Hz display, and EMUI. Independent device databases report the JLN platform as qcom and commonly show a 4.19.157-perf+ kernel. The exact kernel version remains UNVERIFIED on the user's phone until a privileged capability probe reads `uname -a` and kernel config/interfaces directly.

Implication: do not trust `Build.MODEL`, `Build.MANUFACTURER`, or fingerprint alone when selecting system tuning. FixHUA must derive a hardware/runtime identity from multiple independent signals.

Sources:
- Huawei nova 9 SE specifications: https://consumer.huawei.com/jo-ar/phones/nova9-se/specs/
- Android kernel overview: https://source.android.com/docs/core/architecture/kernel

## 2. Kernel generation changes the scheduler plan

Upstream utilization clamping (uclamp) arrived after Linux 4.19. Android 4.19 kernels include the older SchedTune mechanism, which exposes per-cgroup boosting and prefer-idle semantics. Android also supports API/vendor-specific task profile definitions, and vendor task profile files can override platform defaults.

Therefore the Brain must never assume uclamp. Capability discovery should check, in order:
- kernel version and config exposure;
- cgroup v1/v2 layout;
- `/dev/stune` / schedtune interfaces;
- cpusets and task profile files;
- vendor overrides;
- cpufreq governor and policy nodes;
- only then any newer uclamp interface if present by vendor backport.

Sources:
- Android cgroup abstraction/task profiles: https://source.android.com/docs/core/perf/cgroups
- Android 4.19 SchedTune documentation in common kernel: https://android.googlesource.com/kernel/common/+/refs/heads/deprecated/android-4.19/
- Upstream uclamp documentation: https://docs.kernel.org/scheduler/sched-util-clamp.html

## 3. PSI should become the core pressure signal

Pressure Stall Information measures time workloads are actually stalled by CPU, memory, or I/O contention. It exposes `some` and `full` pressure and supports event triggers. Android's modern `lmkd` can use PSI because it tracks user-visible memory pressure better than noisy vmpressure signals.

This leads to a new FixHUA primitive: **Stall Budget**.

Instead of targeting a fixed free-RAM number, FixHUA can maintain acceptable budgets for:
- memory `some/full` pressure;
- I/O `some/full` pressure;
- CPU pressure;
- app frame-jank / launch latency.

An intervention is justified only when a stall budget is being exceeded and the selected action has previously improved that state on this device.

Sources:
- Linux PSI: https://docs.kernel.org/accounting/psi.html
- Android LMKD / PSI: https://source.android.com/docs/core/perf/lmkd

## 4. Perfetto can be the evidence recorder

Perfetto can collect system and per-process memory counters, RSS, swap, `oom_score_adj`, LMK events, scheduler events, CPU frequency, and FrameTimeline jank on Android 12+. This provides a much stronger measurement backbone than custom polling alone.

New idea: **Evidence Capsule**. When FixHUA detects a bad event (freeze, LMK storm, heavy jank, app reload), it records a short rolling trace window with only performance metadata. The Brain then classifies what happened before deciding on a policy change.

New idea: **LMK Storm Detector**. A single cached-app kill can be normal. Multiple kills in a short interval plus rising memory pressure is different. FixHUA should score the event by user impact, process importance and clustering rather than counting every kill equally.

Sources:
- Perfetto memory counters / LMK: https://perfetto.dev/docs/data-sources/memory-counters
- Perfetto CPU scheduling: https://perfetto.dev/docs/data-sources/cpu-scheduling
- Perfetto FrameTimeline: https://perfetto.dev/docs/data-sources/frametimeline

## 5. zRAM is promising, but only capability-driven

zRAM can expose compressor selection, memory statistics, idle marking, optional writeback, and on newer kernels optional recompression. These features depend on kernel configuration. Writeback also has flash-wear implications and therefore requires strict budgets.

For this device, the initial diagnostic report already showed substantial swap use. That does NOT prove swap is harmful; it proves it is important enough to measure against PSI, page faults and app reloads.

New idea: **Swap Quality Score** instead of Swap Used MB. Score whether swap activity is helping preserve expensive apps or causing refault/thrash latency.

Potential actions if supported:
- observe compressor and zRAM stats;
- detect repeated refault/thrash patterns;
- use bounded experiments around swappiness only if writable and reversible;
- use recompression only if exposed;
- never enable aggressive writeback without a verified backing setup and daily write budget.

Sources:
- Linux zRAM: https://www.kernel.org/doc/html/latest/admin-guide/blockdev/zram.html
- Android 17 memory-management daemon as a design reference, not an Android-12 feature: https://source.android.com/docs/core/perf/mmd

## 6. Modern Android memory ideas cannot be blindly backported conceptually

Multi-Gen LRU can improve reclaim efficiency, but it requires kernel support (`CONFIG_LRU_GEN`). The user's probable 4.19 vendor kernel may not have it. The same warning applies to newer Android mmd features and newer zRAM recompression interfaces.

Rule: modern Android features are useful as architectural inspiration, but FixHUA must probe the device rather than emulate unsupported kernel features with shell hacks.

Source:
- MGLRU: https://docs.kernel.org/mm/multigen_lru.html

## 7. Thermal control must be closed-loop, never disabled

Android's Thermal HAL represents mitigation severity and cooling-device state. AOSP explicitly warns not to disable thermal mitigation. FixHUA should treat thermal headroom as a hard control constraint.

New idea: **Thermal Credits**. Short latency boosts may spend a limited thermal budget. The budget recharges during cool periods and shrinks aggressively as thermal severity rises. This avoids the classic tweak-module failure mode of boosting until throttling makes sustained performance worse.

Source:
- Android thermal mitigation: https://source.android.com/docs/core/power/thermal-mitigation

## 8. Huawei background survival is multi-layered and documented

Huawei's own support material confirms that background reliability can depend on App Launch/manual management, Auto-launch, Secondary launch, Run in background, Battery Optimization, Power Saving, staying connected while asleep, Don't keep activities and Background process limit. Huawei also recommends maintaining free storage headroom.

FixHUA therefore needs an **OEM Policy Auditor**, not a single battery-whitelist check.

Important: community sites recommend removing PowerGenie on some older EMUI versions, but that is not strong enough evidence for destructive automation. FixHUA should detect actual policy behavior first and prefer supported settings or reversible changes. PowerGenie/iAware removal is NOT approved as an automatic optimization.

Sources:
- Huawei background apps: https://consumer.huawei.com/en/support/content/en-us00428704/
- Huawei app launch behavior: https://consumer.huawei.com/eg-en/support/content/en-gb00414587/

## 9. App smoothness must be measured directly

Android 12 FrameTimeline can identify janky frames, and Macrobenchmark defines cold/warm/hot startup and frame-overrun metrics. That suggests two core user-facing scores:

**Resume Cost** = median and tail latency of returning to an app, including whether the process had to be recreated.

**Interaction Stability** = jank/frame-overrun distribution during representative user interactions.

New idea: **Selective Survival Budget** should prioritize an app partly by its measured Resume Cost. An app that cold-starts in 250 ms may not deserve memory protection; an app that takes several seconds and reconstructs heavy state might.

Sources:
- FrameTimeline: https://perfetto.dev/docs/data-sources/frametimeline
- Macrobenchmark metrics: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics

## 10. ART optimization is a maintenance problem, not a constant booster

ART uses profile-guided compilation such as `speed-profile`, and background dexopt is designed as maintenance work. FixHUA should never constantly force compilation while the user is active.

Candidate: **Idle Maintenance Planner**. If package compilation state is suboptimal and the device is cool, charging and idle, queue a bounded maintenance job. This remains experimental until Android-12-specific package compiler behavior is validated on the actual ROM.

Sources:
- ART configuration: https://source.android.com/docs/core/runtime/configure
- ART service background dexopt reference for newer Android versions: https://source.android.com/docs/core/runtime/configure/art-service

## 11. Power cost can be measured, not guessed

Android's PowerStats HAL can expose rail-level energy and subsystem residency when the OEM implements it. Perfetto can correlate this with CPU activity. If unavailable, Batterystats provides approximate per-UID/system attribution.

New idea: **Performance-per-Energy Gate**. A policy that cuts launch latency by a tiny amount but materially increases thermal load or energy use should be rejected automatically unless the user explicitly selected a temporary performance mode.

Sources:
- Android PowerStats HAL: https://source.android.com/docs/core/power/power-stats-hal
- Android dumpsys/batterystats: https://developer.android.com/tools/dumpsys

## 12. Root architecture becomes practical with systemless modules

Magisk and KernelSU both support late-start module services. KernelSU can additionally inject init service definitions in supported modes. This is sufficient for a small privileged FixHUA agent without modifying `/system` directly.

Recommended direction:
- APK owns UX, policy and data visualization.
- Root Agent owns privileged reads/writes only.
- Communication protocol has a strict typed allowlist.
- Root module starts non-blocking in late-start/boot-completed stage.
- A safe-mode flag prevents experimental policies from applying after a problematic boot.

Sources:
- Magisk developer guides: https://topjohnwu.github.io/Magisk/guides.html
- KernelSU module guide: https://kernelsu.org/guide/module.html

## New candidate inventions from Round 1

1. **Stall Budget Controller** — optimize user-visible stall time rather than free RAM.
2. **Evidence Capsule** — rolling short Perfetto snapshots around bad events.
3. **LMK Storm Detector** — distinguish normal cached kills from destructive kill storms.
4. **Resume Cost Model** — memory protection value based partly on the cost of recreating an app.
5. **Thermal Credits** — bounded performance interventions constrained by real thermal state.
6. **Swap Quality Score** — judge swap by refault/stall/reload effects, not occupancy alone.
7. **OEM Policy Auditor** — understand Huawei's multiple background policy layers per app.
8. **Performance-per-Energy Gate** — reject optimizations whose energy/thermal cost is disproportionate.
9. **Workload Phase Detector** — classify launch, interactive, steady-state, background and idle phases and apply different policies.
10. **Counterfactual Tuning Windows** — A/B small reversible changes and keep only statistically repeatable gains.

## Next research targets

- Determine exact kernel/cgroup/schedtune/cpufreq nodes on the user's JLN-L21 via a read-only capability probe.
- Research Qualcomm Snapdragon 680 / vendor Power HAL and scheduler behavior without assuming generic Linux controls.
- Map EMUI 13's actual App Launch/iAware/PowerGenie components and package/service names on this ROM.
- Establish what Perfetto data sources are available to a root daemon on this build.
- Determine whether zRAM writeback/recompression/MGLRU exist on this exact kernel.
- Define a safe root transaction protocol and rollback journal.
- Research GPU/devfreq and SurfaceFlinger metrics before considering graphics tuning.
- Research I/O scheduler/UFS behavior before any storage policy changes.

## Provisional conclusion after Round 1

The best optimization opportunity is not one magical root tweak. It is **closed-loop control based on stall, jank, reload, thermal and energy evidence**. Root is valuable mainly because it gives FixHUA better observation and a carefully bounded set of reversible controls.
