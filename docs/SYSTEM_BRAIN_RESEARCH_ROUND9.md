# FixHUA 3 — System Brain Research Round 9

## Theme
Interaction latency, Perfetto Black Box, FrameTimeline, GPU/CPU/I/O correlation, and OEM background-policy attribution.

## 1. Smoothness must be measured at the frame and interaction level

On Android 12+, FrameTimeline can identify janky frames by comparing expected versus actual presentation timing. This is more useful than average FPS because short bursts of missed deadlines are exactly what users perceive as stutter.

Candidate primary UX metrics:
- janky-frame burst rate
- frame deadline miss duration
- app resume latency
- input-to-frame latency where measurable
- cold/warm launch latency

## 2. New component: Interaction Latency Brain

For every visible lag incident, correlate:
- FrameTimeline jank
- CPU scheduling / runnable delay
- CPU frequency / idle state
- GPU frequency / render stage if available
- memory PSI / reclaim
- I/O PSI
- Binder/system-service waits where traceable
- thermal transition
- foreground-app lifecycle transitions

The goal is not only "lag happened" but "what resource delayed the critical path?"

## 3. FixHUA Black Box should use bounded ring buffers

Perfetto uses in-memory ring buffers and can combine ftrace, process stats, memory counters, GPU traces and Android tracks.

Proposed trace tiers:

### Tier A — always-on lightweight
- selected scheduler wakeup/switch signals
- CPU frequency/idle
- process/lifecycle markers
- low-rate process memory counters
- selected thermal/system events

### Tier B — incident escalation
Enabled briefly after a trigger:
- denser scheduling data
- FrameTimeline
- GPU frequency/render stages if supported
- additional memory counters
- selected Binder/atrace categories

### Tier C — manual forensic trace
Short user-requested capture for hard-to-reproduce incidents.

This avoids permanent heavy tracing.

## 4. New component: Event Signature Library

After each incident, reduce raw telemetry into a compact signature such as:
- FRAME_GPU_BOUND
- FRAME_CPU_RUNQUEUE
- FRAME_MEMORY_RECLAIM
- FRAME_IO_STALL
- FRAME_BINDER_WAIT
- APP_LMK_RELOAD
- APP_OEM_BACKGROUND_KILL
- APP_CRASH
- THERMAL_CAP
- UNKNOWN

Repeated signatures become training evidence for device-local policy.

## 5. Confidence must be explicit

Every attribution should expose evidence and confidence, e.g.:

`MEMORY_RECLAIM 0.82`
- memory PSI full spike
- workingset refault burst
- major faults rose
- target app RSS dropped
- app recreated shortly afterward

A low-confidence event must not trigger an aggressive write action.

## 6. Huawei OEM policy is a separate causal domain

Huawei's official support material confirms background behavior can depend on:
- App launch automatic/manual management
- Auto-launch
- Secondary launch
- Run in background
- Battery optimization
- Power saving
- Data/background-network policy
- locking an app in recents

Therefore a process disappearance on EMUI must not automatically be labeled LMK. It may be an OEM background-policy action.

New component: **OEM Policy Auditor**
- inspect what can be read directly
- identify settings that require user-visible configuration
- store per-app policy state
- correlate policy state with exits/restarts
- recommend only the minimum exception needed

## 7. New idea: Interaction Protection Window

When the user returns to or launches a high-value app, FixHUA can enter a short observation window. Only if prior evidence shows a recurring bottleneck may it apply a small, reversible policy for that window.

Examples of possible future actions, capability permitting:
- select an existing task profile
- temporary scheduler preference
- suppress FixHUA's own background work
- delay maintenance/background experiments

The window ends quickly and restores baseline automatically.

## 8. Do not fight platform power hints blindly

Android 12 includes PerformanceHintManager, allowing apps to signal workload timing needs to the platform. OEM Power HAL behavior is hardware-specific.

FixHUA should first observe platform behavior rather than stacking permanent boosts on top of it. If the vendor already provides a launch/interaction boost, additional root boosting may only add heat.

## Provisional conclusion

The core optimization target is not resource utilization. It is the user's critical path from input to visible response. FixHUA should reconstruct that path from traces, identify the bottleneck, and only then consider a bounded intervention.

## Sources
- FrameTimeline: https://perfetto.dev/docs/data-sources/frametimeline
- CPU scheduling: https://perfetto.dev/docs/data-sources/cpu-scheduling
- GPU tracing: https://perfetto.dev/docs/data-sources/gpu
- Perfetto trace config/ring buffer: https://perfetto.dev/docs/concepts/config
- Huawei background protection: https://consumer.huawei.com/en/support/content/en-us15850065/
- Android Performance Hint API: https://source.android.com/docs/core/perf/performance-hint-api
