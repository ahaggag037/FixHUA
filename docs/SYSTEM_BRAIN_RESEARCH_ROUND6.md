# FixHUA 3 — System Brain Research Round 6

## Theme
Black-box tracing, sustained performance, energy-aware optimization, and safe experimental control.

## 1. The best diagnostic is a trace from before the lag

A common problem with performance tools is that they start collecting after the user notices a freeze. The causal event may already be gone.

Perfetto supports ring-buffer tracing and snapshotting. This enables a lightweight always-rolling history that can be cloned when an event occurs.

### Candidate component: FixHUA Black Box
Maintain a bounded ring buffer containing only selected low-overhead signals. When a trigger occurs, snapshot the previous N seconds plus a short post-event window.

Possible triggers:
- FrameTimeline jank burst
- PSI memory full/some threshold
- I/O PSI spike
- LMK event
- foreground app disappears unexpectedly
- ANR/crash event
- thermal transition
- user manually taps "It just lagged"

Possible captured signals:
- CPU scheduling and runnable delay
- CPU frequency/idle
- GPU frequency if traceable
- devfreq/interconnect counters if exposed
- FrameTimeline
- LMK + oom_score_adj
- memory/process counters
- thermal events
- battery current/charge where supported

The Black Box should have strict size/time limits and auto-expire old traces.

## 2. New idea: Event Signature Library

Instead of only storing raw traces, extract a compact signature from each incident:

Example signature:
- foreground app: X
- jank type: AppDeadlineMissed
- CPU runnable delay: high
- CPU frequency ramp: late
- memory PSI: low
- I/O PSI: low
- thermal: healthy
- GPU frequency: normal

Label: probable `CPU_RAMP_OR_SCHEDULING`.

Over time, cluster recurring signatures. If the same app repeatedly produces the same signature, its App DNA gains confidence in a specific bottleneck class.

## 3. Peak performance is not the same as sustained performance

Android's sustained-performance guidance explicitly recognizes that maximum clocks may produce high performance briefly and then cause thermal throttling. A lower but sustainable ceiling can produce better long-session consistency.

### Candidate component: Sustained Performance Brain
Track performance over time, not just first 10 seconds.

For long sessions:
- measure frame/jank trend
- measure thermal headroom trend
- detect frequency throttling
- compare first-minute vs later-minute latency

If an aggressive policy improves minute 1 but degrades minute 10, classify it as a failed optimization.

### New metric: Performance Decay
`decay = late_session_latency / early_session_latency`

Use a richer implementation in code, but conceptually this detects policies that burn thermal headroom too quickly.

## 4. Thermal headroom is a resource budget

Instead of binary "hot/not hot", treat headroom as a consumable budget.

### Thermal Credit policy
Every optional boost has:
- expected latency benefit
- expected heat cost
- maximum duration
- cooldown

The Brain spends thermal credit only on high-value events such as user-initiated app launch or interaction bursts, not background maintenance.

When headroom trends toward severe throttling:
- cancel boosts
- defer background compilation/maintenance
- reduce experimental activity
- favor consistent performance

## 5. Energy must be part of the objective function

Perfetto can read battery current/charge counters on supported devices via Android power data sources. Power-rail counters are device dependent and may not be available on Huawei, so capability probing is required.

### New metric: Benefit per Energy Cost
For an optimization experiment, record:
- launch/resume latency delta
- jank delta
- battery current/energy delta where measurable
- thermal delta

Reject changes whose tiny performance benefit has a disproportionate energy/thermal cost.

## 6. Root does not replace Android's Power HAL

Modern Android already has a device-specific power/performance layer. PerformanceHintManager is app-scoped, while OEM Power HAL implementations can map higher-level hints to CPU/GPU/platform policies.

FixHUA should inspect existing vendor behavior before fighting it.

Research/probe targets:
- Power HAL version/service presence
- supported modes/hints discoverable through dumpsys/service metadata
- Huawei Performance Mode state if present
- whether launch/interaction already causes measurable CPU/GPU ramps

### Candidate idea: Vendor Policy Comparator
Capture the system response to the same workload in:
- normal mode
- Huawei Performance Mode (if available)
- FixHUA experimental mode

If Huawei already applies an effective launch boost, FixHUA should avoid stacking another one.

## 7. New safety mode: Shadow Brain

Before the Brain gets permission to write system settings, run it in **Shadow Mode**.

In Shadow Mode it:
- observes
- classifies bottlenecks
- predicts an action
- records what it *would* have changed
- measures what actually happened without intervention

After enough incidents, compare predictions to outcomes.

Only actions with sufficiently strong evidence graduate to active experiments.

This reduces risk from false attribution.

## 8. Experiment lifecycle

Each optimization experiment should use a state machine:

`PROPOSED → ELIGIBLE → ACTIVE → VERIFYING → KEPT | REVERTED | QUARANTINED`

### Eligibility checks
- capability present
- device not thermally constrained
- battery above configured floor or charging, depending on action
- no concurrent conflicting experiment
- baseline data available
- rollback path verified

### Quarantine
If an action causes:
- crash/ANR increase
- boot instability
- repeated write failures
- thermal regression
- severe battery regression
- unexplained system behavior
then quarantine it for the current device build until explicitly reviewed.

## 9. Multi-objective optimization, not a single speed score

FixHUA should optimize a vector:
- perceived latency
- jank
- app state preservation
- stability
- battery
- thermal sustainability

A single "performance score" hides tradeoffs.

### Proposed policy
Use hard safety constraints first:
- no severe thermal regression
- no crash/ANR regression
- no boot/system instability

Then optimize latency/jank within those constraints.

## 10. Resource conflict graph

Actions can conflict.

Examples:
- preserving more apps vs memory pressure
- aggressive CPU boost vs thermal budget
- background dexopt vs foreground I/O latency
- high refresh rate vs battery/thermal
- app background exemption vs idle power

Create an explicit conflict graph so the policy engine cannot activate incompatible actions simultaneously.

## 11. System Brain scheduler

Separate work into priority classes:

### Interactive critical
- user touch / app launch / resume
- frame-jank recovery

### Stability critical
- LMK storm response
- thermal safety
- crash/ANR evidence capture

### Maintenance
- ART compile experiments
- trace processing
- database compaction
- model/profile updates

Maintenance should yield immediately to interactive work and preferably run while charging/idle.

## 12. Next probe additions

The capability probe should also report:
- Perfetto availability/version
- ability to create local ring-buffer traces
- accessible ftrace categories/events
- FrameTimeline support
- CPU frequency and idle trace events
- GPU frequency trace event availability
- battery counter availability
- Power HAL/service metadata
- sustained performance support
- Huawei Performance Mode discoverability

## Round-6 conclusion

The System Brain should behave more like a flight recorder plus conservative controller than a traditional Android booster.

The most important new feature is **FixHUA Black Box**: continuously retain a small rolling history of the system so every real lag event can be analyzed from its lead-up, giving the Brain evidence to learn device-specific causes and only then apply reversible optimizations.
