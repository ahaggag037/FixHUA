# FixHUA 3 — System Brain Research Round 5

## Theme
App preservation, cached-app freezer, ART/startup optimization, Qualcomm CPU/GPU/bus coordination, and the difference between keeping an app alive versus keeping it runnable.

## 1. App survival has at least three distinct states

A process can be:
1. **Alive and runnable** — consumes CPU when scheduled.
2. **Alive but frozen/cached** — retains memory state while receiving effectively no CPU.
3. **Killed** — memory is reclaimed; next use requires reconstruction/relaunch.

FixHUA should stop treating "background app present" as a binary state.

### New model: Preservation Tier
Per app, learn the cheapest tier that preserves acceptable user experience:
- T0: killable / cheap restart
- T1: cache only
- T2: cache + freezer-friendly
- T3: background-execution required
- T4: user-critical persistent service

A messaging app, browser tab process, game, music player, and calculator should not receive the same policy.

## 2. Android cached-app freezer is highly relevant

Android 11+ supports a cached apps freezer. Its goal is to keep cached apps in RAM while preventing them from consuming CPU. This can reduce cold starts without letting cached processes waste resources.

This is conceptually aligned with FixHUA, but raw process freezing is unsafe.

### Why raw freezing is dangerous
Android's own implementation coordinates cgroup freezing with Binder state. Synchronous Binder calls into a frozen process can cause the remote process to be killed rather than block indefinitely. Asynchronous transactions can accumulate and overflow buffers.

AOSP's `CachedAppOptimizer` explicitly freezes/unfreezes Binder traffic and inspects frozen Binder state before restoring a process.

Therefore:
- Do not implement "freeze any PID" by writing directly to cgroup freezer as an optimization feature.
- First inspect whether the framework freezer is supported/enabled on the device.
- Prefer observing and cooperating with Android's own freezer policy.
- If any manual freezing is ever considered, it must be a later expert-only feature with Binder-aware safeguards and strong exclusions.

### Candidate component: Preservation Auditor
For each target app, determine:
- cached/freezer eligibility
- current process importance / oom_score_adj
- whether framework freezer is enabled
- whether app requires background callbacks/network/audio/location/service behavior
- whether past freezing correlates with app exits or session loss

## 3. New idea: Warmth Efficiency Score

The objective is not "keep the maximum number of apps alive". It is to maximize useful warm state per unit cost.

Possible score inputs:
- probability user returns within N minutes
- relaunch cost (TTID/TTFD)
- memory footprint
- background CPU cost
- background network cost
- historical kill frequency
- state-loss penalty
- freeze compatibility

An app with a 500 MB footprint and 150 ms restart may be less valuable to preserve than a 150 MB app with a 4-second authenticated session reconstruction.

## 4. Cached-app count should not be increased blindly

Modern Android can keep many apps cached, but increasing cached-process limits without understanding device RAM and vendor policy can increase memory pressure and LMKs.

FixHUA should never use a universal `max_cached_processes` tweak.

Instead, learn whether the device is losing expensive app state because of:
- memory pressure
- OEM cleanup
- app-specific background restriction
- freezer/Binder incompatibility
- app crash

Then target the actual cause.

## 5. ART compilation is a real performance axis

Android Runtime uses profile-guided compilation. Baseline Profiles and runtime profiles can significantly improve startup and runtime performance by avoiding interpretation/JIT on important code paths.

For third-party apps, FixHUA cannot manufacture a correct Baseline Profile that the developer did not ship. However, root/shell-level ART tooling may expose the current compilation state and allow carefully scheduled package compilation where supported.

### Candidate component: ART Readiness Auditor
Read-only first:
- package compiler filter / dexopt status where available
- whether an app shipped a profile
- last update/install time
- cold/warm startup distributions

### Candidate idea: Idle Compile Advisor
Do not force-compile every app. Identify apps that:
- are used frequently
- have expensive startup
- show signs of unoptimized code paths after install/update

Then, only if the device's Android version and ART tooling support a safe operation, schedule compilation during charging + idle, and measure before/after startup.

Important: broad/full compilation may increase storage and I/O and is not inherently better. Any compile intervention must be A/B measured.

## 6. Qualcomm performance is a coupled system

Qualcomm kernel sources for the Khaje family show:
- hardware cpufreq domains
- LMH-DCVS thermal/current/reliability limits
- Adreno GPU devfreq governors
- GPU bandwidth voting
- memory/bus frequency mechanisms on related Qualcomm platforms

This means CPU frequency alone is an incomplete optimization target.

### New component: Coupled Resource Attribution
When a frame or app launch is slow, classify:
- CPU compute bound
- CPU runqueue/scheduling delay
- GPU bound
- GPU bandwidth bound
- DDR/memory-latency bound
- memory reclaim/swap bound
- I/O bound
- thermal/LMH-limited
- Binder/system-service wait

Only then pick an intervention.

## 7. GPU policy should remain governor-aware

Qualcomm KGSL commonly uses a TrustZone-based Adreno governor plus GPU-bandwidth voting. Hard-locking GPU max frequency can waste thermal headroom and reduce sustained performance.

Initial FixHUA behavior should be:
- observe GPU busy/frequency/devfreq state if exposed
- correlate GPU frequency with jank and thermal state
- detect whether governor ramp-up is too slow for a workload
- avoid global fixed-frequency tuning

### Candidate idea: GPU Ramp Deficit Detector
If FrameTimeline shows GPU-side jank while:
- CPU pressure is low,
- thermal headroom is healthy,
- GPU frequency ramps after the missed frame rather than before,
then mark a possible GPU-ramp deficit.

Do not automatically override the governor until a reversible device-specific mechanism is validated.

## 8. Bus / DDR may be the hidden source of "CPU looks fine but phone stutters"

Qualcomm platforms can couple CPU activity to memory/interconnect frequency through devfreq/memlat/bandwidth voting.

FixHUA should probe:
- `/sys/class/devfreq/*`
- current/min/max frequencies
- governor names
- available frequencies if exposed
- device names associated with DDR/interconnect/GPU bus

### Candidate metric: Memory-Latency Suspicion Score
Raise suspicion when:
- CPU utilization is moderate,
- CPU PSI is low,
- memory PSI is not severe,
- app thread spends significant time runnable or blocked around memory-heavy work,
- bus/devfreq is low before a latency event and rises after it.

This is a hypothesis to test, not an assumption.

## 9. New component: Evidence Capsule v2

Every user-visible lag event should be correlated across layers:
- timestamp
- foreground package/activity
- FrameTimeline jank class
- CPU sched delay
- CPU cluster frequency
- GPU busy/frequency
- devfreq/bus state
- PSI CPU/memory/I/O deltas
- swap/zRAM counters
- LMK/oom_score_adj changes
- thermal headroom/status
- Binder wait indicators if traceable

The system then produces a ranked bottleneck explanation rather than a single resource chart.

## 10. New optimization principle: preserve expensive state, not processes

The true objective is minimizing **user-visible reconstruction cost**.

Examples:
- A killed browser helper process may be cheap.
- A killed editor with unsaved reconstruction may be expensive.
- A frozen app that later dies due to Binder misuse is worse than allowing a clean restart.

Therefore FixHUA should optimize a per-app **State Preservation Value** rather than raw process lifetime.

## 11. Build implication

The first privileged probe should add read-only checks for:
- cached apps freezer enabled/supported state
- cgroup freezer nodes
- relevant `DeviceConfig` values
- `dumpsys activity` cached/frozen information if accessible
- package dexopt/compiler status
- GPU KGSL/devfreq nodes
- all devfreq devices and governors
- current cpufreq policy topology

No writes yet.

## Round-5 conclusion

A potentially powerful FixHUA feature is not "never kill apps". It is an **App Preservation Engine** that learns which app state is expensive to lose and chooses the cheapest preservation mechanism compatible with Android/Huawei behavior.

This can combine with Bottleneck Attribution so that memory retention never steals resources from the foreground workload without measurable benefit.
