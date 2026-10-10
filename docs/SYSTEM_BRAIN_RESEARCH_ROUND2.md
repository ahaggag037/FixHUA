# FixHUA 3 — System Brain Research Round 2

Date: 2026-10-10
Focus: Qualcomm platform behavior, root feasibility, bottleneck attribution, and control architecture.

## 1. Probable SoC family is Qualcomm Khaje / Snapdragon 680

Huawei's official nova 9 SE specs identify Snapdragon 680. Qualcomm's public Android device-tree material for the `khaje` platform shows hardware CPU frequency domains, LMH-DCVS links, DDR dynamic clock/voltage scaling and memory-latency monitoring structures.

This matters because user-visible latency is not controlled by CPU frequency alone.

Potential bottleneck classes:
- CPU scheduling / CPU frequency response.
- DDR/memory-bandwidth scaling.
- memory reclaim / swap / page refault.
- GPU/render pipeline.
- storage I/O.
- hardware thermal/power limiting.

Source examples:
- Huawei nova 9 SE specs: https://consumer.huawei.com/jo-ar/phones/nova9-se/specs/
- Qualcomm Khaje public device tree: https://android.googlesource.com/kernel/msm-extra/devicetree/+/refs/heads/android-msm-redbull-4.19-android13-qpr2-beta/qcom/khaje.dtsi

## 2. New core invention: Bottleneck Attribution Engine

Before FixHUA changes anything, it should answer:

> What resource actually delayed the user's interaction?

A candidate inference model:

- Rising memory PSI + refaults + LMKs → MEMORY_RECLAIM bottleneck.
- High CPU runnable delay + frequency lag without thermal pressure → CPU_SCHED/DVFS bottleneck.
- CPU busy but memory-latency / DDR indicators rise → MEMORY_BANDWIDTH bottleneck.
- FrameTimeline jank with GPU/frequency pressure but low CPU stall → GPU/RENDER bottleneck.
- I/O PSI/full pressure + storage activity → STORAGE bottleneck.
- Frequency collapse while thermal severity / LMH limits rise → THERMAL_LIMIT bottleneck.
- None of the above → app-local work, network, binder dependency, or unknown.

This makes FixHUA fundamentally different from static tweak packs: an optimization is selected from the detected bottleneck class rather than applied globally.

## 3. Hardware thermal limiting must be treated as an independent controller

Qualcomm LMH-DCVS is hardware designed to react quickly to thermal changes by requesting clock/voltage limits. Fighting it with software frequency writes can produce unstable control loops and worse sustained performance.

Therefore:
- never disable LMH or thermal protection;
- detect when frequency is being capped by thermal/power logic;
- when hardware mitigation is active, stop performance boosting and enter a cooling/stability policy;
- distinguish 'CPU governor chose low frequency' from 'hardware limit forced low frequency'.

Source:
- Qualcomm LMH-DCVS binding: https://android.googlesource.com/kernel/msm/+/f382bd87e7398d1d55b1b84211de3d44ae88cf11/Documentation/devicetree/bindings/thermal/qcom-lmh-dcvs.txt

## 4. DDR/memory latency deserves first-class telemetry

Khaje reference device trees expose DDR DCVS and memory-latency monitoring. This suggests a useful direction: some perceived slowness can come from memory/bus scaling rather than raw CPU utilization.

New idea: **Latency Path Score**.

Correlate:
- CPU scheduler delay;
- CPU frequency;
- memory PSI/refaults;
- DDR/devfreq state when readable;
- frame jank;
- launch/resume latency.

If CPU frequency is already high but interaction remains slow while memory pressure/bandwidth evidence rises, FixHUA should not waste thermal budget by boosting CPU further.

## 5. Root technology choice must be capability-based

Current KernelSU documentation says official support primarily targets GKI 5.10+ devices. Older/non-GKI kernels can require custom kernel integration and are not a universal supported path. If this phone really runs a vendor 4.19 kernel, KernelSU must NOT be assumed to be the easy root foundation.

Magisk remains architecturally attractive because its module system can run late-start `service.sh` scripts and operate systemlessly after the device is already rooted/unlocked appropriately.

Important: FixHUA should not choose Magisk vs KernelSU until the device capability probe confirms the exact kernel, boot image structure, root solution already available, and module environment.

Sources:
- KernelSU FAQ: https://kernelsu.org/guide/faq.html
- KernelSU non-GKI integration notes: https://kernelsu.org/guide/how-to-integrate-for-non-gki.html
- Magisk installation: https://topjohnwu.github.io/Magisk/install.html
- Magisk module developer guide: https://topjohnwu.github.io/Magisk/guides.html

## 6. Root is primarily an observation upgrade

A key research correction: root is valuable even if FixHUA performs very few writes.

With privileged observation, the Brain can potentially access more of:
- `/proc/<pid>` process state;
- system-wide process memory and swap;
- PSI triggers;
- cgroups/cpusets/schedtune values;
- cpufreq/devfreq nodes;
- thermal zones and limits;
- Perfetto/ftrace data sources;
- package/process importance and LMK history;
- power and battery diagnostics.

This may improve decisions more than aggressive tuning itself.

## 7. New operating model: Observer-first Root Agent

Phase A — READ ONLY:
- discover capabilities;
- build baseline distributions;
- classify bottlenecks;
- produce recommendations only.

Phase B — REVERSIBLE ACTIONS:
- enable one small policy change;
- measure fixed window;
- compare against baseline;
- automatically revert if no repeatable benefit.

Phase C — LEARNED POLICY:
- only after repeated wins may the Brain automatically reuse an action for the same workload state.

No direct transition from 'root granted' to 'tweak everything'.

## 8. New invention: Policy Confidence Levels

Every possible root action gets a confidence class:

- **OBSERVED**: interface exists and value was read.
- **REVERSIBLE**: old value can be restored immediately.
- **TESTED**: action completed without instability.
- **BENEFICIAL**: repeated A/B windows showed improvement.
- **AUTOMATABLE**: safe enough to reuse under matching conditions.

The Brain may never auto-apply an action solely because it is OBSERVED or REVERSIBLE.

## 9. New invention: Two-controller model

**Fast Controller** (milliseconds/seconds)
- reacts to PSI trigger, app launch/resume, jank burst, thermal transition.
- only performs low-cost actions that are already validated.

**Slow Controller** (minutes/hours/days)
- analyzes histories and A/B experiments.
- updates App DNA and policy confidence.
- schedules idle maintenance.
- changes long-term thresholds gradually.

This separation prevents a learning algorithm from making frequent unstable system changes during live interaction.

## 10. New invention: Device Truth Map

Because Build properties on this phone appear spoofed/mixed, FixHUA should create a local map with confidence per identity signal:

- SoC model / hardware platform.
- kernel version.
- Android API level.
- EMUI/Huawei properties.
- device tree compatible strings when readable.
- cgroup/task profile layout.
- display refresh capabilities.
- memory/zRAM layout.
- filesystem/storage type.
- root implementation.

No subsystem-specific policy is enabled until the relevant identity signals agree strongly enough.

## 11. Research decision after Round 2

The first privileged deliverable should NOT be a performance-tuning module.

It should be a **FixHUA Capability & Bottleneck Probe** that is read-only and answers:
- what this phone actually is;
- which kernel mechanisms really exist;
- which telemetry is readable;
- which scheduler/memory/thermal controls exist;
- what causes representative slowdowns.

Only then should System Brain v3 receive write capabilities.
