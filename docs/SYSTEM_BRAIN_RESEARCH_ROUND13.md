# FixHUA 3 — System Brain Research Round 13

## Theme
Low-overhead always-on telemetry, Binder latency attribution, eBPF opportunities, and tiered tracing.

## 1. FixHUA should not run a heavy trace continuously

The System Brain needs continuous situational awareness without becoming a source of CPU, I/O, battery, or thermal load itself.

Proposed telemetry tiers:

### Tier 0 — Always-on micro telemetry
Very low-overhead signals sampled or event-driven:
- PSI trigger events
- selected `/proc/vmstat` deltas
- memory/swap summaries
- CPU policy/frequency state at coarse interval
- thermal transitions
- foreground app transitions
- process exit events where available
- battery current/temperature at low rate

### Tier 1 — Black Box ring buffer
Enabled continuously only if measured overhead is acceptable:
- scheduler events
- CPU frequency/idle
- selected binder events
- FrameTimeline where available
- LMK/oom adjustment events
- bounded process stats
- selected thermal/devfreq counters

Ring buffer overwrites old data and is snapshotted on incident.

### Tier 2 — Deep incident trace
Short-lived, triggered after repeated/important incidents:
- richer Binder tracing
- GPU/devfreq counters
- perf counters/callstacks if supported
- higher-frequency process/memory counters
- additional ftrace categories

The Brain must record tracing overhead and automatically downgrade if telemetry itself affects performance.

## 2. Binder latency is a separate bottleneck domain

Android UI/app latency can come from synchronous Binder calls rather than CPU saturation.

Perfetto's standard library can decompose Binder transactions and associate delay with:
- client side
- server side
- scheduling/runnable delay
- blocked kernel functions
- server execution time

This suggests a dedicated causal class:

`BINDER_WAIT`

Subclasses:
- `BINDER_SERVER_BUSY`
- `BINDER_CLIENT_SCHED_DELAY`
- `BINDER_SERVER_SCHED_DELAY`
- `BINDER_KERNEL_BLOCK`
- `BINDER_DEPENDENCY_CHAIN`

### New idea: Dependency Wait Score

For each foreground incident, compute how much of the critical-path latency is spent waiting for another process/service.

If Binder dependency wait dominates, CPU boosting the app process itself should be rejected unless trace evidence shows the server is CPU starved and the intervention targets the correct process/group.

## 3. Binder dependency graph

Perfetto can construct incoming/outgoing Binder graphs between processes and AIDL interfaces/methods where metadata exists.

Potential use:
- identify `system_server` dependency stalls
- identify WebView/provider/service bottlenecks
- identify whether a GBox-hosted app is waiting on a host/container service
- distinguish app-local jank from system-service latency

Privacy rule: store aggregate interface/timing signatures where possible, not message contents.

## 4. eBPF on Android 12 is potentially useful but must be capability-gated

AOSP uses eBPF for several platform functions. Examples relevant to FixHUA research include:
- network traffic accounting
- CPU time-in-state
- Android 12 GPU memory accounting (`gpu_mem`)

This proves that Android 12 systems may already contain useful BPF programs/maps even on older kernels with the needed backports/configuration.

### Probe targets

Read-only discovery:
- kernel BPF support indicators
- `/sys/fs/bpf` mount/pinned maps accessibility
- Android bpfloader state/log evidence
- whether existing platform maps expose useful aggregate data through supported services
- whether Perfetto can access equivalent information without FixHUA loading any BPF program

### Conservative policy

The first FixHUA release should **not load custom eBPF programs** merely because root is available.

Reasons:
- verifier/kernel-version compatibility
- SELinux/bpfloader ownership
- potential kernel stability impact
- vendor backport differences
- unnecessary complexity if Perfetto/platform maps already provide data

Custom eBPF remains an optional expert research path after device capability mapping.

## 5. GPU memory as an incident signal

Android 12 AOSP includes a `gpu_mem` eBPF program for GPU memory profiling.

If available on the device, correlate:
- foreground app GPU memory growth
- LMK/memory pressure
- frame jank
- KGSL reclaim behavior

This could identify a class where total RAM pressure is partly driven by graphics allocations rather than Java/native heap alone.

### New signature: `GPU_MEMORY_PRESSURE`

Do not assume GPU memory is independently reclaimable. Treat it as another contributor to system memory pressure until device behavior is verified.

## 6. Perf events / hardware counters

Linux exposes hardware performance counters through `perf_event_open()` when supported and permitted. Access depends on kernel configuration and `perf_event_paranoid`/capabilities.

Potential expert-only metrics:
- instructions
- CPU cycles
- cache misses
- branch misses

These can help differentiate:
- CPU-bound compute
- cache/memory-latency-heavy workload
- scheduler wait

However high-rate perf sampling can be expensive. Therefore:
- do not use perf counters in always-on Tier 0
- capability-probe first
- use only in bounded Deep Trace sessions
- record sampling overhead

## 7. Network telemetry should be attribution-focused, not packet-content-focused

Android uses eBPF-based per-UID network accounting on compatible kernels/launch versions. FixHUA does not need packet payload inspection.

Useful signals:
- per-UID traffic deltas where platform APIs/services permit
- foreground/background network state
- validated/unvalidated transitions
- Doze/power-save network restriction state
- repeated connection-loss timing around app incidents

Privacy rule:
- no browsing content
- no packet payloads
- no account/token capture
- aggregate bytes/state/timing only

## 8. Black Box trigger hierarchy

Incident triggers should be cheap and layered:

### Immediate triggers
- memory PSI full threshold
- severe I/O PSI
- thermal severity transition
- foreground process disappears unexpectedly
- LMK/exit event

### Soft triggers
- burst of long FrameTimeline frames
- high runnable delay
- repeated Binder dependency waits
- GPU frequency saturation with frame delay

### Manual trigger
User taps: `It just lagged`.

Manual trigger is valuable because user perception can reveal issues not captured by automatic thresholds.

## 9. Incident window model

For each event snapshot:
- pre-window: enough history to see cause build-up
- event timestamp
- short post-window to observe recovery

The exact window should be learned from trace size/overhead rather than hardcoded universally.

## 10. Trace reduction pipeline

Raw traces are too large for the long-term Brain memory.

Pipeline:

`raw trace → SQL extraction → incident feature vector → signature → optional trace expiry`

Feature vector may include:
- CPU runnable delay percentiles
- top CPU consumer
- CPU frequency residency
- memory PSI some/full delta
- I/O PSI delta
- swap/refault deltas
- LMK/oom adjustment event
- GPU frequency/busy indicator
- Binder wait time
- thermal state
- battery current delta
- foreground package

## 11. New concept: Causal Evidence Weight

Evidence sources have different reliability:

Highest confidence:
- kernel event directly identifies cause
- ApplicationExitInfo explicitly reports supported reason
- trace shows direct dependency blocking critical path

Medium:
- strong temporal correlation across multiple signals

Low:
- resource utilization alone
- single snapshot
- vendor property inference

The Brain should require stronger evidence for higher-risk interventions.

## 12. Telemetry overhead budget

FixHUA must measure itself.

Track:
- daemon CPU time
- daemon RSS/PSS
- wakeups
- trace buffer memory
- bytes written per hour
- battery current difference when telemetry enabled/disabled

If the monitoring stack produces material jank or battery cost, automatically reduce sampling/tracing.

## 13. Why this matters for the user's target device

A Snapdragon 680-class phone has finite CPU/GPU/memory bandwidth and runs a vendor-modified power/background stack. A heavy optimizer can easily consume the resources it is trying to save.

The strongest design is therefore a mostly dormant controller that wakes on kernel/framework signals, captures bounded evidence, and intervenes rarely.

## Provisional conclusion

FixHUA's always-on intelligence should be **event-driven and tiered**. PSI and lightweight state transitions provide the heartbeat; the Black Box provides context; Perfetto/Binder/perf/eBPF capabilities provide deep evidence only when necessary. This architecture gives the Brain better causal visibility without turning monitoring into another source of lag.
