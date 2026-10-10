# FixHUA 3 — System Brain Research Draft

> Status: living research draft. This document records hypotheses, candidate mechanisms, rejected shortcuts, validation questions, and evidence targets before implementation.

## Mission

Turn FixHUA from a GBox-oriented helper into a system-wide adaptive runtime manager for Huawei/Android devices. The objective is not to make synthetic benchmark numbers look better. The objective is to improve experienced smoothness, app survival, resume latency, stability, thermal behavior, and responsiveness while preserving rollback and avoiding destructive tweaks.

## Core design principle

Every optimization must follow:

**Observe → Classify → Decide → Act → Measure → Keep/Revert**

No permanent tweak is accepted merely because it is popular online. A change should survive only if the device's own measurements show that it helped the target metric without causing unacceptable regression elsewhere.

## Proposed architecture

### 1. FixHUA App
- Human-readable dashboard and controls.
- Safe Mode that works without root.
- App profiles / App DNA.
- Session history and before/after comparisons.
- Recovery UI and explicit rollback.

### 2. Brain Engine
- State classifier: NORMAL, MEMORY_PRESSURE, THERMAL_PRESSURE, BATTERY_SENSITIVE, NETWORK_UNSTABLE, STORAGE_PRESSURE, APP_INSTABILITY.
- Policy engine selects the smallest justified intervention.
- Per-device learning instead of universal magic values.
- Rate limits and hysteresis to prevent oscillation.

### 3. Privileged Root Agent
- Optional root daemon with a narrow command allowlist, not arbitrary shell execution from the UI.
- Reads kernel/system telemetry and performs only predefined reversible actions.
- Records before-state, requested action, result, timeout, and rollback data.
- Fail-closed: if a capability is unsupported or ambiguous, do nothing.

### 4. Optional root integration layer
- Prefer systemless mechanisms such as Magisk/KernelSU where supported.
- Never depend on bypassing Play Integrity, DRM, banking checks, or account security.
- Root availability must be detected at runtime; unsupported operations must be hidden or disabled.

## Candidate intelligence domains

### Memory Brain
Research targets:
- PSI memory pressure (`/proc/pressure/memory`) when exposed.
- `/proc/meminfo`, swap/zRAM utilization, page faults, reclaim behavior.
- LMKD events / process exit reasons where accessible.
- Per-app PSS/RSS and foreground/background transitions.
- zRAM compressor, disksize, writeback/recompression support if kernel exposes them.
- Correlation between pressure spikes and app reloads / user-visible stalls.

Candidate idea: **Pressure Forecasting**. Learn what memory-pressure trajectory usually precedes a target app being reclaimed, then intervene before the critical point rather than running a periodic cleaner.

Candidate idea: **Selective Survival Budget**. Assign a small protected budget to a user-selected important app only when evidence shows reloads are harming the session. Avoid globally pinning all apps.

Candidate idea: **Adaptive zRAM policy**. Never apply a fixed swappiness or zRAM size copied from another phone. Evaluate supported controls, change one parameter within bounded limits, measure PSI/reload/latency/battery/temperature, then keep or revert.

### Scheduler / CPU Brain
Research targets:
- cpufreq policies and governors actually exposed by the device kernel.
- schedutil behavior, uclamp, cpusets, cgroups/task profiles.
- foreground/top-app/background grouping.
- frequency residency and thermal throttling.

Candidate idea: **Latency Burst**. Short, bounded assistance for app launch/resume based on detected foreground transition, followed by immediate return to normal policy. Avoid locking maximum frequencies.

Candidate idea: **App-specific scheduling fingerprints**. Learn whether a target app benefits from short CPU bursts, background CPU limits, or no intervention at all.

### Thermal Brain
Research targets:
- Android thermal status / Thermal HAL signals.
- thermal zones exposed in sysfs.
- frequency throttling and battery temperature trends.

Policy: never disable thermal protection. Thermal headroom is a feedback constraint, not an obstacle to bypass.

Candidate idea: **Thermal Budget Controller**. Allow short responsiveness boosts only when headroom exists; progressively reduce intervention as thermal pressure rises.

### Background Survival Brain
Research targets:
- Huawei App Launch / auto-launch / secondary launch / run-in-background behavior.
- Android Doze/App Standby/background restrictions.
- per-package standby buckets/AppOps where readable.
- process exit reasons and repeated background deaths.

Candidate idea: **Evidence-based exemption**. Recommend or apply exceptions only to apps that demonstrably suffer background termination, instead of making every app unrestricted.

### Storage / I/O Brain
Research targets:
- filesystem type, free space thresholds, I/O pressure, dirty/writeback behavior where exposed.
- package cache state and app launch I/O patterns.
- ART/Dex compilation state and idle maintenance behavior.

Candidate idea: **Idle Maintenance Window**. Move expensive maintenance/optimization to charging+idle windows; never run heavy cleanup while the user is actively interacting.

### App Launch Brain
Metrics:
- cold launch, warm launch, hot resume latency.
- page faults and memory pressure during launch.
- whether app is re-created or resumed.

Candidate idea: **Launch Readiness Predictor**. Detect frequently used apps and optimize only the path that is measurable on this device, avoiding fake cache cleaners.

### Network Brain
Research targets:
- validated connectivity, Wi-Fi/cellular transitions, DNS/connection failures observable without collecting browsing content.
- background network restrictions.

Candidate idea: **Session Connectivity Guard**. Detect an unstable transition around a critical app session and recommend/apply only reversible network-policy actions that Android permits.

### App DNA
Per-app profile may contain:
- launch/resume latency distribution.
- typical PSS/RSS.
- reload frequency.
- exit-reason distribution.
- background requirement.
- thermal cost.
- network dependency.
- successful/failed optimization experiments.
- preferred system policy and rollback history.

This should become a learned device-local profile, not a cloud fingerprint.

## Anti-patterns currently rejected

- Repeated `drop_caches` as a "speed boost".
- Killing all cached/background apps.
- Holding CPU at maximum frequency.
- Disabling thermal throttling.
- Disabling LMKD globally.
- Universal `swappiness` values.
- Universal zRAM size copied from another device.
- Permanent aggressive background exemptions for every app.
- Deleting Huawei services merely because online tweak lists recommend it.
- Treating more free RAM as inherently better.
- Claiming "zero app exits" as achievable.

## Safety / reliability rules for root mode

1. No arbitrary shell command endpoint from UI to root daemon.
2. Every write action requires a known capability probe first.
3. Snapshot old value before changing it.
4. Every action defines a rollback path.
5. Use bounded values and timeouts.
6. Stop interventions on overheating, instability, or repeated failures.
7. Never modify boot-critical partitions automatically.
8. Never bypass integrity, DRM, banking, or account-security mechanisms.
9. Keep a recovery-safe mode that starts with all experimental policies disabled.
10. Store an append-only local action log for postmortem analysis.

## Metrics that matter

Primary:
- app exit/reload rate.
- cold/warm/resume latency.
- frame/jank indicators if accessible.
- PSI memory/IO/CPU stall time where available.
- time spent under thermal throttling.
- ANR/crash counts.
- battery cost per active session.

Secondary:
- available RAM only as context, not a success metric.
- swap/zRAM occupancy only in relation to stalls and reloads.
- CPU frequency only in relation to latency and heat.

## Highest-value research questions next

1. Which PSI, LMKD, zRAM, MGLRU and scheduler interfaces are likely to exist on the user's Android 12 Huawei ROM/kernel, and which require root?
2. What can Magisk and KernelSU reliably provide for a persistent root service without modifying `/system`?
3. Which Huawei background/power components are documented, which are community-inferred, and which should not be touched?
4. Which Android task profiles/cgroups/uclamp controls can be safely observed vs modified on Android 12?
5. What telemetry can distinguish "app killed by memory pressure" from crash, ANR, dependency death or user/system stop?
6. What root-level controls are reversible enough for automated experimentation?
7. How should hysteresis, experiment duration, and rollback thresholds be designed to avoid policy thrashing?
8. Can FixHUA use eBPF/perf tracing safely/portably on this device, or should that remain an optional expert diagnostic layer?
9. Which measures provide actual user-perceived smoothness instead of benchmark-only gains?
10. What exact capability-probe APK/root-agent should be built before any optimization engine?

## Current provisional thesis

The strongest version of FixHUA is not a cleaner, booster, or static tweak pack. It is a **device-local adaptive controller** that learns the relationship between workload, memory pressure, scheduling, thermal state, background policy, and app exits, then performs small reversible interventions only when evidence predicts benefit.

The next research session should try to falsify this thesis as aggressively as it tries to support it.
