# FixHUA 3 — System Brain Research Round 12

## Theme
**Causal Intervention Gate, experimental admission, hysteresis, rollback, and optimization scoring.**

The root-capable System Brain must be more conservative than a normal optimizer because a privileged mistake can create instability that looks like the problem it is trying to solve.

## 1. The Brain is not allowed to optimize a symptom

An observed symptom such as:
- slow launch
- scrolling jank
- app reload
- background exit
- hot device
- network stall

is not itself an intervention target.

The Brain must first map the incident to a causal domain with confidence:
- CPU scheduling / runnable delay
- GPU rendering
- memory pressure / reclaim
- swap/zRAM thrashing
- DDR/interconnect latency
- storage I/O contention
- thermal/LMH limit
- Huawei/OEM background policy
- app crash/ANR/bug
- network path
- unknown

If confidence is `UNKNOWN`, only observation/recommendation is allowed.

## 2. Causal Intervention Gate

A candidate write action is admitted only if all conditions pass:

1. **Capability confirmed** on the current device.
2. **Root cause confidence** is above the domain threshold.
3. **Target metric** is named before the action.
4. **Counter-metrics** are named before the action.
5. **Old state is captured** and validated.
6. **Rollback path exists** and is independently executable.
7. **Action bounds** are finite and device-derived.
8. **Time-to-live** is specified unless the action is inherently session-scoped.
9. **No conflicting owner** is actively controlling the same surface, or conflict handling is known.
10. **Thermal safety** is clear.
11. **Boot safety** is clear.
12. **Experiment budget** allows another change.

Fail any condition → no write.

## 3. Experimental states

Every intervention moves through:

`PROPOSED → SHADOW → CANARY → VERIFIED_SESSION → DEVICE_ACCEPTED`

### PROPOSED
Rule exists but never writes.

### SHADOW
Brain predicts an action and records what it *would* have done. Used to verify that classification is stable without changing the phone.

### CANARY
One bounded real intervention on one target session/app.

### VERIFIED_SESSION
The intervention produced measurable benefit in that session without counter-metric regression.

### DEVICE_ACCEPTED
Benefit repeats across enough comparable incidents and conditions.

A device-accepted action may still be revoked when firmware, kernel, thermal environment, app version, or workload changes.

## 4. Optimization score

A single metric must never dominate.

Proposed utility function concept:

`utility = responsiveness_gain + survival_gain + smoothness_gain - energy_cost - thermal_cost - instability_cost - background_cost`

Each term is normalized against the device's own baseline, not another phone.

### Responsiveness
- app cold/warm/resume latency
- runnable latency
- input-to-frame delay if traceable

### Survival
- avoided expensive reloads
- preserved app state
- fewer unnecessary LMK/background-policy exits

### Smoothness
- FrameTimeline jank distribution
- long-frame bursts
- sustained rather than average FPS

### Cost
- battery current/energy where available
- increased thermal pressure
- extra CPU/GPU residency
- I/O stalls
- impact on unrelated foreground/background apps

## 5. Hysteresis and anti-oscillation

A smart controller can become unstable if it reacts too quickly.

Rules:
- Do not flip modes on a single noisy sample.
- Require entry threshold > exit threshold.
- Apply minimum dwell time after a policy transition.
- Rate-limit repeated interventions in the same domain.
- Cool down after rollback before retrying.
- Never allow two competing experiments on the same control surface simultaneously.

Example state logic:

`NORMAL → MEMORY_WATCH → MEMORY_PRESSURE → RECOVERY → NORMAL`

The threshold to enter `MEMORY_PRESSURE` should be stronger than the threshold required to remain there.

## 6. Domain admission matrix

### CPU / scheduler

Potential future action classes:
- existing task-profile application
- bounded scheduler boost for a foreground transition
- temporary min-frequency floor if explicitly proven necessary

Evidence required:
- foreground runnable delay is high
- CPU capacity is the dominant bottleneck
- GPU/memory/I/O pressure do not better explain the event
- thermal headroom exists

Automatic rejection:
- CPU already thermally capped
- GPU-bound trace
- I/O or memory full-stall dominates
- requested frequency is already high but latency remains

### GPU

Potential future actions should be much more restricted than CPU actions.

Evidence required:
- GPU frame/completion delay dominates
- CPU has headroom
- thermal headroom exists
- Qualcomm KGSL/devfreq surface is confirmed

Rejected:
- permanent maximum GPU frequency
- disabling GPU thermal limits
- undocumented register writes

### DDR / interconnect

Potential future actions:
- prefer vendor-defined performance/task profile if one already coordinates bus policy
- bounded existing devfreq/profile hints only after capability validation

Evidence required:
- CPU not saturated
- memory pressure not dominant
- devfreq/memlat behavior correlates with stalls
- repeated signature

Rejected:
- arbitrary min/max DDR locking
- copying values from another Snapdragon device

### Memory / LMKD

Potential future actions:
- app-preservation recommendation/profile
- bounded policy only after studying OEM LMKD configuration

Evidence required:
- repeated low-memory or SIGKILL evidence
- PSI/reclaim/refault pattern precedes reload
- target app has high resume/reconstruction cost

Rejected:
- globally disabling LMKD
- protecting every app
- using free-RAM target as success metric

### zRAM

Potential future actions are experimental and require the strongest gate.

Evidence required:
- active swap/reclaim behavior is measured
- sustained PSI/refault pattern exists
- hardware/storage path for any writeback feature is understood
- test window is controlled

Rejected:
- universal swappiness value
- resizing zRAM without rollback/reboot model
- changing compressor while device is initialized when kernel forbids it
- assuming new-kernel recompression features exist

### Cached-app preservation/freezer

Preferred strategy:
- observe Android framework behavior
- let framework own Binder-aware freezing
- tune only through supported/system mechanisms if a future capability proves safe

Rejected:
- direct PID freezer writes as a general feature
- globally forcing freezer without device compatibility evidence

### Storage I/O

Potential future action:
- defer FixHUA's own maintenance
- identify background writers
- use existing workload/profile mechanisms where available

Rejected by default:
- changing queue scheduler globally
- changing write-cache semantics
- disabling writeback throttling

### Thermal

Thermal domain is a **constraint**, not a target to bypass.

Allowed:
- read thermal zones/status
- reduce FixHUA interventions
- select sustainable profile

Rejected:
- changing critical trip points
- disabling thermal services
- defeating LMH hardware limits
- forcing maximum CPU/GPU during thermal pressure

### Huawei/OEM background policy

Potential future action:
- guided per-app policy configuration
- privileged automation only if exact setting is discovered, reversible, and stable on this ROM

Evidence required:
- app exit is not explained by LMK/crash/ANR
- Huawei policy is demonstrably restricting target app
- user has marked the app as important/background-required

Rejected:
- globally exempting all apps
- deleting/disabling Huawei power services

## 7. Conflict detector

Before writing any surface, detect whether another layer owns it.

Possible owners:
- Android Power HAL
- thermal HAL / thermal daemon
- Qualcomm perf/perfd/powerhint implementation
- Huawei iAware/PowerGenie-like services
- init post-boot scripts
- task profile transitions
- kernel governor
- user/root module from another optimizer

### New idea: `Policy Churn Detector`

Observe a candidate node before any experiment. If its value changes by itself repeatedly, treat it as framework/vendor-owned.

If FixHUA writes a value and another component restores it immediately, do **not** fight the component in a write loop. Mark the surface `CONFLICTED` and stop.

This avoids the classic root-tweak failure mode where multiple daemons continuously overwrite each other and waste CPU/battery.

## 8. Rollback hierarchy

Every experiment needs multiple recovery layers:

### Layer A — In-memory rollback
Restore old value at TTL expiry or on counter-metric regression.

### Layer B — Daemon crash recovery
On daemon restart, inspect unfinished transactions and restore pre-state before accepting new work.

### Layer C — Reboot recovery
Experimental writes should default to volatile. The system's own defaults after reboot are preferred unless a policy reached `DEVICE_ACCEPTED`.

### Layer D — Safe Mode
A user-visible safe mode starts the app/root agent with all write-capable experiments disabled.

### Layer E — Module disable/removal
Boot integration must not be required for Android to boot normally.

## 9. Transaction journal

Every privileged action produces an append-only local record:

- action ID
- timestamp
- trigger incident/signature
- causal confidence
- target package/session
- capability ID
- old state
- requested state
- observed state
- expected metric
- counter-metrics
- TTL
- verification result
- rollback result
- final verdict

No secret/user-content capture belongs in this log.

## 10. Firmware/app invalidation

Accepted device knowledge becomes stale when:
- ROM/EMUI version changes
- kernel changes
- security patch materially changes framework behavior
- app version changes significantly
- root framework changes
- task profile/vendor configuration changes

On such events:
- retain history
- downgrade learned interventions to `SHADOW`
- re-probe capabilities
- do not blindly replay old writes

## 11. New concept: `Optimization Debt`

Every accepted tweak creates maintenance cost because firmware and workload evolve.

FixHUA should assign optimization debt:
- low: framework-supported profile/hint
- medium: stable kernel sysfs with rollback
- high: vendor-specific undocumented node
- unacceptable: boot-critical/thermal/integrity/security bypass

The Brain should prefer a slightly smaller improvement with lower debt over a fragile vendor-specific hack.

## 12. User modes become policy budgets, not tweak packs

Suggested modes:

### Balanced
- conservative experiment budget
- energy and thermal cost weighted strongly

### Smoothness
- prioritizes UI/resume/jank reduction
- allows short bounded performance assistance

### Multitasking
- prioritizes expensive-state preservation
- stronger memory-survival weighting

### Battery
- accepts somewhat higher resume cost to reduce background/energy load

### Diagnostic
- no automated writes
- maximum telemetry/Black Box detail within privacy limits

Modes change the scoring weights; they do not load hardcoded lists of sysctl values.

## 13. First real-world experiment order

After the read-only Capability Probe is built and run on the device:

1. Establish 24–48h observational baseline.
2. Build incident signatures.
3. Select the single highest-frequency user-visible bottleneck.
4. Run SHADOW recommendations first.
5. Admit one CANARY intervention with a short TTL.
6. Compare against matched baseline sessions.
7. Roll back on any instability/thermal/energy regression.
8. Only then consider a second domain.

## Provisional conclusion

Root increases FixHUA's capability, but the strongest innovation is not root access itself. It is the **Causal Intervention Gate** that stops root from turning the project into another static tweak pack. The System Brain should earn write privileges one control surface at a time through observation, attribution, canary testing, and rollback evidence.
