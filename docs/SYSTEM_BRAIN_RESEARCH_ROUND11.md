# FixHUA 3 — System Brain Research Round 11

## Theme
Device-specific **Capability Probe Specification** for JLN-class Huawei hardware and Android 12/EMUI-era systems.

The probe is deliberately **read-only**. Its purpose is to discover the actual control and telemetry surfaces available on the device before any optimization policy is allowed to write anything.

## 1. Capability state model

Every discovered capability must be represented with explicit state instead of a boolean.

Suggested state fields:

- `present`: node/service/property exists.
- `readable`: current value can be read.
- `writable`: write permission appears available. This alone does **not** mean FixHUA may write.
- `reversible`: a write has a defined, verified inverse or restore path.
- `volatile`: change resets on reboot / service restart.
- `persistent`: change survives reboot or is restored by vendor framework.
- `framework_owned`: Android framework manages the value and may overwrite external writes.
- `vendor_owned`: Huawei/Qualcomm service or HAL appears to manage the value.
- `kernel_owned`: direct kernel control surface.
- `risk`: `READ_ONLY`, `SAFE_REVERSIBLE`, `EXPERIMENTAL`, `DANGEROUS`, `DO_NOT_TOUCH`.
- `confidence`: `CONFIRMED_DEVICE`, `CONFIRMED_PLATFORM`, `LIKELY`, `UNKNOWN`.
- `source`: framework API, procfs, sysfs, service, HAL, property, config file, Perfetto data source, etc.

The Brain Engine must never infer `writable => safe`.

## 2. Identity and platform truth layer

The earlier device report contained conflicting Android `Build.*` identity versus Huawei/EMUI properties. Therefore the probe needs a multi-source identity model.

Read and compare:
- Android API level / release / security patch.
- kernel release and architecture.
- device-tree compatible/model where exposed.
- SoC identifiers exposed by `/sys/devices/soc0/*` or equivalent.
- Huawei/EMUI properties that are readable.
- CPU topology/capacity/frequency policy count.
- GPU driver identity.
- memory size and swap/zRAM topology.

Output should distinguish:
- **marketing identity**
- **Android property identity**
- **kernel/platform identity**
- **vendor ROM identity**

No spoofing or modification belongs in this component.

## 3. CPU / scheduler probe

### Read-only inventory

For every `/sys/devices/system/cpu/cpufreq/policy*`:
- `related_cpus`
- `affected_cpus`
- `scaling_driver`
- `scaling_governor`
- `scaling_available_governors`
- `scaling_available_frequencies` when present
- `scaling_cur_freq`
- `scaling_min_freq`
- `scaling_max_freq`
- `cpuinfo_min_freq`
- `cpuinfo_max_freq`

Why: Linux CPUFreq policy objects are the authoritative abstraction; `scaling_cur_freq` may reflect the last requested P-state rather than exact instantaneous hardware frequency, so FixHUA must not over-interpret it.

### Scheduler/cgroup inventory

Probe:
- cgroup version/mount layout.
- `/system/etc/task_profiles.json`
- `/system/etc/task_profiles/task_profiles_<API>.json`
- `/vendor/etc/task_profiles.json`
- corresponding cgroup JSON files.
- cpuset groups and task-profile actions.
- schedtune nodes if present.
- uclamp nodes only if actually exposed.

Android 12 makes `task_profiles` the preferred abstraction for task migration. Vendor definitions can override API-level and default profiles, so hardcoded paths are forbidden in future write mode.

### New component: `TaskProfileDictionary`

For each profile:
- profile name
- referenced controllers
- target groups
- action type
- underlying node
- current state
- ownership (system/vendor)
- whether applying the profile would collide with Power HAL or Huawei policy

## 4. PSI / pressure probe

Check availability and readability of:
- `/proc/pressure/cpu`
- `/proc/pressure/memory`
- `/proc/pressure/io`

Record:
- `some.avg10/60/300`
- `full.avg10/60/300` where meaningful
- cumulative `total`

If the kernel supports PSI trigger registration, mark `event_trigger_capable=true`; the future Stall Sentinel can use poll/epoll instead of high-frequency polling.

Important: on older kernels CPU `full` may be unavailable/zero; absence must not be treated as an error.

## 5. Memory / reclaim / LMKD probe

Read:
- selected `/proc/meminfo` fields
- selected `/proc/vmstat` counters
- swap devices from `/proc/swaps`
- workingset/refault counters where exposed
- `vm.swappiness`, dirty/writeback parameters as **read-only facts**
- memory cgroup layout and limits

Read Android LMKD-related properties where accessible, including:
- PSI use flag
- partial/complete stall thresholds
- thrashing limit and decay
- swap-free / swap-util thresholds

Do not alter LMKD properties during capability discovery.

### Kill-report capability

Record whether Android reports low-memory kills accurately using `ActivityManager.isLowMemoryKillReportSupported()`.

For process-death attribution, FixHUA should model:
- LOW_MEMORY
- SIGNALED / SIGKILL fallback
- ANR
- Java/native crash
- initialization failure
- dependency death
- excessive resource use
- user/package state changes
- unknown/other

Cross-package historical exit reasons require privileged access; the root agent may be able to read them later, but the capability must first be verified rather than assumed.

## 6. zRAM capability probe

For each zRAM device, inventory only what exists:
- `disksize`
- `mem_limit`
- `mem_used_total`
- `orig_data_size`
- `compr_data_size`
- `comp_algorithm`
- `mm_stat`
- `bd_stat`
- `writeback`-related nodes if present
- `idle` tracking if present
- recompression/multi-compression nodes only if present

Critical rule: modern kernel documentation includes features that may not exist on a 4.19-derived vendor kernel. FixHUA must not infer support from documentation for newer kernels.

### Derived read-only metrics

Compute:
- compression ratio
- physical zRAM memory cost
- occupancy ratio
- swap-in/swap-out delta
- major page-fault delta
- reclaim/refault pressure

A high swap occupancy alone is **not** evidence of active thrashing.

## 7. Qualcomm DDR / interconnect / devfreq probe

Qualcomm Khaje reference device trees expose several memory-bandwidth and latency components: CPU-to-DDR bandwidth monitoring, memory-latency monitors, compute monitors, and DDR operating points.

Probe `/sys/class/devfreq/*` and record per device:
- `name`
- `governor`
- `cur_freq`
- `target_freq`
- `available_frequencies`
- `available_governors`
- `min_freq`
- `max_freq`
- `trans_stat` if available
- related CPUs where applicable

Classify likely devices by names/paths into:
- CPU↔DDR bandwidth
- memlat
- GPU core
- GPU bus/interconnect
- other vendor devfreq domains

### New idea: `Bandwidth Starvation Signature`

A lag event should be marked as possible bus/DDR starvation only when:
- foreground runnable latency or frame delay rises,
- CPU is not clearly saturated,
- devfreq/interconnect remains at low state or transitions late,
- memory pressure is not the dominant cause.

This prevents FixHUA from blindly boosting CPU for a bus bottleneck.

## 8. GPU probe

On Qualcomm devices, KGSL commonly supplies Adreno power management and uses devfreq governors such as `msm-adreno-tz` and GPU bandwidth monitoring where compiled.

Probe read-only:
- GPU driver/device identity
- KGSL sysfs/debugfs surfaces that are accessible
- GPU devfreq governor and current/available frequencies
- GPU busy/idle counters if exposed
- GPU bus/interconnect devfreq domain if exposed
- Perfetto GPU-frequency capability

Never assume all KGSL debug nodes are stable ABI.

### New component: `CPU-GPU Balance Analyzer`

For jank bursts classify:
- CPU-bound
- GPU-bound
- mixed
- memory/bus-bound
- unknown

No CPU or GPU policy change should be admitted if attribution confidence is low.

## 9. Thermal / LMH probe

Inventory `/sys/class/thermal/thermal_zone*`:
- type
- temperature
- trip points when readable
- policy / available policies when exposed
- linked cooling devices

Also capture Android thermal status/API if available.

Qualcomm Khaje/Bengal references include LMH-DCVS hardware-limit blocks tied to CPU frequency domains. Therefore:
- CPU frequency ceilings may come from hardware thermal/current limits.
- a requested frequency not being reached does not necessarily mean scheduler/governor failure.

### New idea: `Throttle Provenance`

When performance drops, distinguish:
1. software governor lowered demand
2. framework/Power HAL applied a cap
3. thermal framework cooling device applied a cap
4. Qualcomm LMH hardware imposed a limit
5. unknown

Future Brain interventions must never attempt to defeat hardware thermal protection.

## 10. Power and energy telemetry probe

Check:
- Android battery counters
- current/voltage/charge data availability
- PowerStats HAL presence
- Perfetto `android.power` source support
- entity-state residency and power rails only when the device actually exposes them

Power rails are optional and vendor-dependent; absence is normal.

### New metric: `Benefit per mWh`

An optimization that reduces launch latency but costs disproportionate energy should lose score unless explicitly requested by the user.

## 11. Storage / I/O probe

Determine actual data filesystem and block device topology.

For relevant block queues, read:
- current/available scheduler
- read-ahead
- rotational flag
- request-affinity setting
- write-cache mode
- writeback-throttling latency node if present

Read-only first. Queue parameters are not generic optimization knobs; the active vendor configuration may already be tuned for UFS/eMMC characteristics.

Combine with:
- I/O PSI
- diskstats deltas
- major faults
- app-launch trace windows

### New component: `Interactive I/O Contention Detector`

Identify whether foreground latency coincides with background writeback/package maintenance/cache activity instead of changing the scheduler globally.

## 12. Perfetto / tracing capability probe

The probe should query which data sources and ftrace events are actually available on this build.

High-value targets:
- sched switch/wakeup
- CPU frequency/idle
- thermal events
- FrameTimeline
- memory/process counters
- LMK / oom adjustment traces
- GPU frequency
- devfreq where exported
- battery/power counters

The future Black Box should use a bounded ring buffer and snapshot only on an incident trigger.

## 13. Cached-app freezer probe

Do **not** toggle the feature during probe.

Read:
- DeviceConfig value for cached-app freezer where accessible
- cgroup v2 controller list
- presence of `cgroup.freeze` in relevant child groups
- framework dumps showing frozen apps if permitted

Classify:
- framework enabled
- kernel freezer available
- Binder-aware framework path available
- unsupported/unknown

Raw PID freezing remains rejected as an optimization strategy.

## 14. Huawei OEM policy probe

Map Huawei-specific policy surfaces without modifying them:
- App Launch management state if discoverable
- auto-launch / secondary launch / run-in-background state where accessible
- battery optimization state
- power saving / ultra power saving
- recent-task lock is UI state and may not be programmatically queryable
- vendor services/packages involved in power/background management

Official Huawei support confirms that background survival can depend on these layers, so OEM policy must be treated as a separate causal domain rather than folded into LMKD.

## 15. Root-agent capability boundary

The first privileged component remains read-only.

Allowed classes for v0 probe:
- read files/properties
- enumerate services/nodes
- query dumpsys/service state
- query Perfetto capabilities
- collect bounded telemetry

Explicitly excluded from the first root build:
- writing governor/min/max frequencies
- changing task profiles
- changing LMKD thresholds
- changing zRAM configuration
- changing thermal trip points/governors
- disabling OEM services
- changing storage scheduler
- freezer toggles
- boot/property spoofing

## 16. Output schema

Recommended per-capability record:

```json
{
  "id": "cpu.policy0.scaling_governor",
  "domain": "CPU",
  "path": "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor",
  "present": true,
  "readable": true,
  "writable": true,
  "reversible": "UNVERIFIED",
  "owner": "KERNEL",
  "risk": "READ_ONLY",
  "confidence": "CONFIRMED_DEVICE",
  "value": "schedutil",
  "notes": "Writable does not authorize writes"
}
```

## 17. Probe success criteria

The first device run is successful if it can answer, without changing state:

1. What kernel/platform are we actually running?
2. Which CPU policies and scheduler/cgroup mechanisms really exist?
3. Is PSI present and event-trigger capable?
4. What zRAM features exist on this kernel?
5. Is Android LMKD using PSI, and what thresholds are configured?
6. Which GPU and DDR/devfreq domains are visible?
7. Which thermal zones and limits can be observed?
8. Which PowerStats/Perfetto sources are available?
9. Is the cached-app freezer supported/enabled?
10. Which Huawei background/power policies appear active?
11. Which signals can be correlated in a Black Box trace?
12. Which surfaces are writable but must remain prohibited until separately validated?

## Provisional conclusion

The highest-value next artifact is a **read-only Root Capability Probe**. It should produce a machine-readable device map and a human-readable report. No optimization engine should be allowed to write to the system until this map exists and the Brain can prove which bottleneck class actually precedes a user-visible problem.
