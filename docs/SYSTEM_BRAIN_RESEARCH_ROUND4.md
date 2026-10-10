# FixHUA 3 — System Brain Research Round 4

## Theme
Privilege architecture, Android 12 task profiles, PSI eventing, thermal-aware control, and root-module lifecycle.

## 1. Android 12 task profiles are a first-class control plane

Android 12 deprecates direct `writepid`-style cgroup migration in favor of the `task_profiles` abstraction. Devices can load default, API-level, and vendor-specific definitions from `task_profiles.json` / `cgroups.json`, with vendor definitions overriding earlier layers.

Implication for FixHUA:
- Do not assume a universal cgroup filesystem layout.
- Probe `/system/etc/task_profiles*` and `/vendor/etc/task_profiles.json` first.
- Parse available profiles and attributes rather than hardcoding paths.
- Prefer invoking an existing profile when it maps cleanly to the intended effect.
- Direct sysfs/cgroup writes should be a fallback only when the capability is explicitly discovered and reversible.

AOSP task-profile definitions can expose controls such as cpusets, memory limits, memory swappiness, schedtune boost/prefer-idle, and uclamp attributes. The exact set is device/vendor dependent.

### Candidate component: Task Profile Mapper
Build a runtime inventory:
- profile name
- actions
- controller
- target path/file
- read/write support
- current membership/value
- safe read-only vs safe reversible vs unsupported

This becomes the scheduler/memory control dictionary for the device.

## 2. PSI should be event-driven, not polled blindly

Linux PSI exposes CPU, memory, and I/O stall metrics and supports threshold-triggered monitors using `poll()`/`epoll()`. This is better than sampling every few seconds because short latency spikes can be invisible in long averages.

### Candidate component: Stall Sentinel
Maintain separate event triggers for:
- memory `some` pressure
- memory `full` pressure
- I/O `some` pressure
- I/O `full` pressure
- CPU `some` pressure

Initial thresholds must be learned, not universal. The agent should start read-only, collect empirical distributions, then derive device-local alert thresholds.

When a trigger fires, create an Evidence Capsule containing a short window of:
- PSI deltas
- meminfo / swap counters
- top process memory
- scheduler state if available
- LMK / oom_score_adj events
- thermal state/headroom
- foreground package
- frame-jank signal if tracing is active

This supports causal attribution instead of generic "RAM pressure" labels.

## 3. LMK events are evidence, not failure by definition

Perfetto can trace Android LMK events and oom_score_adj changes. Cached apps normally have high oom_score_adj and can be reclaimed without the event necessarily being user-visible.

FixHUA therefore should classify an LMK by cost:
- invisible cached process kill
- cheap app reload
- expensive app reload
- important background service interruption
- repeated kill/restart storm

### Candidate metric: Reload Cost Score
Estimate the cost of a kill using:
- time until user returns to app
- cold/warm resume latency
- reloaded memory footprint
- network/session reconstruction time
- whether user-visible state was lost

Protect only high-cost cases.

## 4. Thermal headroom should gate every performance intervention

Android exposes thermal status and, on API 30+, thermal headroom estimates. The headroom API is slow-moving and should not be polled aggressively.

### Candidate component: Thermal Credit Ledger
Each optimization has a thermal cost budget.

Example policy:
- high headroom: short launch/resume assistance allowed
- moderate headroom: reduced duration/intensity
- near severe throttling: no boost; prefer stabilization
- thermal status severe+: freeze experimental tuning and roll back temporary performance changes

The controller should prefer sustainable performance over short benchmark spikes.

## 5. PerformanceHintManager is useful evidence, but not a cross-app superpower

Android 12 introduced `PerformanceHintManager`, but a hint session can only include threads belonging to the calling application's process. It therefore cannot directly optimize arbitrary third-party app threads from the FixHUA APK.

Implication:
- Do not design FixHUA around PerformanceHintManager for other apps.
- It can still be used internally for FixHUA's own periodic work if useful.
- Cross-app optimization must instead use legitimate system-level mechanisms discovered through root/cgroups/task profiles/Power HAL interfaces, where available.

## 6. Root Agent should follow least privilege even when root is available

The APK should never receive an unrestricted "run any shell command" bridge.

### Proposed privilege split

**APK / Brain Engine**
- decides intent: e.g. `READ_PSI`, `APPLY_PROFILE`, `SET_BOUNDED_SWAPPINESS`, `RESTORE_ACTION`
- cannot pass arbitrary shell text

**Root Agent**
- verifies capability ID
- validates parameters against bounds
- captures before-state
- executes a predefined action implementation
- verifies result
- emits structured outcome
- stores rollback token

### Action contract
Every action must define:
- capability probe
- parameter schema and bounds
- preconditions
- expected side effect
- verification readback
- timeout
- rollback implementation
- cooldown / rate limit
- conditions that disable the action permanently for this device

## 7. Magisk / KernelSU module lifecycle can host a persistent agent

Both Magisk and KernelSU expose module lifecycle scripts such as `service.sh` during late_start. KernelSU also offers a `boot-completed.sh` stage. This makes a small persistent root-side service feasible without modifying `/system` directly.

Design preference:
- start the agent late enough that the Android framework and vendor services are stable
- do not block boot
- remain idle until the APK connects
- if the APK is absent or crashes, the daemon must not keep experimental tuning active
- persistent modifications should be opt-in and represented by declarative policy, not shell snippets

KernelSU additionally supports per-app root profiles/capability restrictions, which conceptually matches FixHUA's least-privilege goal when KernelSU is actually supported on the device.

## 8. eBPF is promising but should remain optional

Android includes an eBPF loader and can use eBPF programs for kernel statistics/monitoring. However, portability, SELinux, kernel configuration, and boot integration differ significantly across devices, especially older/vendor kernels.

Decision:
- do not make eBPF a requirement for v3
- first prefer PSI + procfs/sysfs + Perfetto/ftrace interfaces that are already exposed
- add eBPF later only if the capability probe confirms safe support and it provides unique diagnostic value

## 9. Huawei policy management should be selective

Huawei documents separate controls for:
- Auto-launch
- Secondary launch
- Run in background
- Battery optimization
- Power saving mode
- network connectivity during sleep
- recent-task locking
- Developer option `Don't keep activities`
- Background process limit

This supports building an OEM Policy Auditor, but not globally exempting every app.

### Candidate component: Background Survival Contract
Per app, record whether it actually needs:
- auto-launch
- secondary launch
- unrestricted background execution
- persistent network during sleep
- recents lock

Then recommend/apply only the minimum needed policy.

## 10. New architectural rule: attribution before actuation

No optimization may be triggered solely by:
- free RAM
- CPU utilization
- battery level
- temperature alone
- an app being in foreground

The Brain must first assign a probable bottleneck class with confidence:
- MEMORY_RECLAIM
- SWAP_THRASH
- CPU_RUNQUEUE
- CPU_CAPACITY
- GPU_FRAME
- IO_STALL
- BINDER_WAIT
- THERMAL_LIMIT
- OEM_BACKGROUND_POLICY
- NETWORK_TRANSITION
- UNKNOWN

If confidence is low, collect evidence rather than tune.

## 11. Next capability probe requirements

The first privileged probe should collect, read-only:

### Kernel / scheduler
- `uname -a`
- kernel config availability
- cpuset/cgroup mounts
- task-profile files and vendor overrides
- schedtune/uclamp presence
- cpufreq policy directories/governors/frequency tables

### Memory
- PSI support and writable trigger capability
- zRAM devices
- compression algorithm
- disksize
- mm_stat / bd_stat if exposed
- swap priority
- swappiness
- page-cluster
- LMKD properties

### I/O / storage
- filesystem type
- block devices and queue scheduler
- I/O PSI
- read-ahead values only as telemetry initially

### Thermal / power
- thermal zones and types
- Android thermal status/headroom support
- cpufreq/devfreq throttling state where readable
- Huawei Performance Mode availability/state

### Graphics / interaction
- refresh-rate modes
- SurfaceFlinger/FrameTimeline trace capability
- GPU devfreq/sysfs discovery

### Root framework
- Magisk / KernelSU / neither
- lifecycle hook availability
- SELinux enforcing state
- safe module location

## Round-4 conclusion

The strongest near-term design is now:

**System Brain APK → bounded action protocol → root agent → discovered Android/vendor control planes**

The root agent should not be a bag of shell tweaks. It should be a reversible, capability-driven actuator behind an evidence-based controller.
