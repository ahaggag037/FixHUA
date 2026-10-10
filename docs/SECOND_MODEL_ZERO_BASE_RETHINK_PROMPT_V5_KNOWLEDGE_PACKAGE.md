# FixHUA 3 — Independent Second-Model Zero-Base Rethink Prompt V5
## Independent First Pass + Creative Collision + Full Project Knowledge Package

> This V5 is the preferred execution prompt.
>
> It supersedes the **workflow architecture** of earlier prompt versions while preserving their strongest technical, creative, adversarial, safety, and timebox requirements.
>
> Required supporting contract:
> - `docs/AI_PROJECT_DOCUMENTATION_CONTRACT.md`
>
> The mission is not complete until the independent rethink and the documentation package are both delivered.

---

# 0. PRIMARY OBJECTIVE

Re-evaluate FixHUA from first principles and determine the strongest realistic architecture for materially improving real user-perceived Android/Huawei responsiveness, smoothness, app survival, stability, and efficiency.

Then leave behind a complete, implementation-ready repository knowledge base so a future model can continue the project without relying on this conversation.

Two goals must coexist:

1. **Think independently, creatively, and critically.**
2. **Leave a precise engineering memory for the project.**

Do not sacrifice either goal for the other.

---

# 1. HARD OUTER TIMEBOX

Start immediately when this prompt is received.

Record:

```text
MISSION_START = <timestamp or T+00:00>
MISSION_DURATION = 90 minutes
MISSION_DEADLINE = MISSION_START + 90 minutes
```

The deadline is hard.

The timebox limits how long you work, **not how widely you are allowed to think**.

If a valuable new idea appears late, evaluate it. If it cannot be completed responsibly before the deadline, record it under `HIGH-POTENTIAL LATE DISCOVERIES` and the project backlog.

Do not extend the mission because the topic remains interesting.

---

# 2. CRITICAL ANTI-ANCHORING RULE

## Phase I must happen BEFORE reading the existing FixHUA System Brain research.

This is mandatory.

At the beginning, you may inspect only enough repository context to establish:
- the current shipped/implemented application state,
- repository structure,
- device facts already directly verified,
- the original user goal,
- hard safety/non-goal constraints.

Do **not** read `SYSTEM_BRAIN_RESEARCH_DRAFT.md` or Research Rounds 1–13 before completing your independent architecture pass.

The purpose is to prevent the existing research from anchoring your design.

---

# 3. PHASE I — INDEPENDENT ZERO-BASE DESIGN

Start from the problem itself.

Assume no obligation to preserve:
- the current System Brain concept,
- the current proposed component list,
- the current telemetry model,
- the current root architecture,
- the current intervention model,
- the current learning/control loop.

Research independently from authoritative sources.

Build your own answer to:
- What is the real bottleneck problem the product should solve?
- What should FixHUA actually be?
- Which interventions can genuinely change user-perceived performance?
- Which parts should be observation-only?
- Which parts require root, if any?
- What should remain advisory/manual?
- What should never be automated?
- What is the smallest architecture capable of meaningful impact?

You are explicitly allowed to conclude that the current System Brain idea is wrong, too large, or pointed at the wrong abstraction.

Before moving to Phase II, freeze a snapshot of your independent thinking in:

`docs/project/04_INDEPENDENT_ZERO_BASE_DESIGN.md`

This file must contain:
- independent problem model
- independent thesis
- independent architecture
- top candidate interventions
- strongest new mechanisms
- rejected directions
- uncertainties
- assumptions
- first proposed executable milestone

Do not rewrite this file later to make it agree with the final architecture.

---

# 4. CREATIVE INVENTION MODE

During the independent pass and later synthesis, deliberately search beyond standard Android tweak culture.

Use cross-domain transfer where it produces real mechanisms.

Possible source fields include:
- control theory
- congestion control
- real-time systems
- cache economics
- distributed systems
- queueing theory
- robotics
- aviation fault management
- reliability engineering
- adaptive signal processing
- online experimentation
- cybernetics
- biological homeostasis
- resource allocation

For every borrowed idea map:

`foreign concept → Android mechanism → observable signal → possible action → expected user effect → measurable test → rollback`

Do not import terminology merely because it sounds sophisticated.

Actively search for:
- new causal signals
- timing-based interventions instead of magnitude-based tuning
- mechanisms that prevent bad states before they occur
- combinations where two weak ideas create a stronger mechanism
- contradictory resource policies that can be reconciled
- ways to reduce work rather than allocate more resources
- ways to protect app state without keeping unnecessary execution alive
- ways to distinguish latency caused by the app from latency caused by a dependency
- methods that make the optimizer itself low-overhead and self-correcting

There is no fixed ceiling on ideas.

Novelty alone is insufficient. Every retained idea must have a plausible causal mechanism and a test.

---

# 5. PHASE II — EXISTING RESEARCH EXPOSURE

Only after the independent design snapshot is complete, read the existing System Brain research in full:

- `docs/SYSTEM_BRAIN_RESEARCH_DRAFT.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND1.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND2.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND3.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND4.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND5.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND6.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND7.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND8.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND9.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND10.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND11.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND12.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND13.md`

Also read prior second-model prompt material when useful, but do not treat it as evidence.

Audit the research rather than absorbing it uncritically.

Write the audit to:

`docs/project/05_EXISTING_RESEARCH_AUDIT.md`

For every major idea assign one:
- `KEEP`
- `MODIFY`
- `DOWNGRADE`
- `REJECT`
- `DEFER`

Explain why.

---

# 6. PHASE III — COLLISION

Now collide three systems:

```text
INDEPENDENT DESIGN
        ×
EXISTING FIXHUA RESEARCH
        ×
REAL PLATFORM / DEVICE EVIDENCE
```

Do not merely merge features.

Explicitly identify:

## Independent convergence
Ideas both approaches reached separately.

## Existing-research advantage
Ideas the earlier FixHUA research found that your independent pass missed.

## Independent advantage
Ideas you found that the earlier research missed.

## Direct conflicts
Cases where the two designs disagree about architecture, mechanism, priority, or safety.

## Both wrong
Ideas rejected by stronger evidence even if both designs liked them.

## Combination opportunities
Pairs or groups of ideas whose synthesis creates a stronger mechanism than either design alone.

This collision phase is where the most important new inventions may appear.

---

# 7. PHASE IV — ADVERSARIAL ATTACK

Before choosing the final architecture, attack the strongest candidate.

Try to break it through:
- telemetry overhead
- root daemon overhead
- policy oscillation
- framework/vendor ownership conflicts
- Huawei policy interference
- false causal attribution
- noisy measurements
- thermal drift
- battery cost
- swap/I/O regressions
- stale rules after firmware/app updates
- boot instability
- root/module failure
- corrupted local state
- non-repeatable experiments
- limited data for learning
- duplicated functionality already handled better by Android

If a simpler architecture survives better, choose the simpler one.

---

# 8. PHASE V — FINAL ARCHITECTURE SYNTHESIS

Select one primary architecture.

You may preserve:
- one fallback architecture
- a bounded research backlog

Do not finish with several equally preferred designs.

The final architecture must define:
- product identity
- privilege layers
- components
- trust boundaries
- runtime data flow
- observation model
- attribution model
- decision/control model
- intervention model
- rollback model
- telemetry tiers
- persistent state
- safety invariants
- firmware/device compatibility behavior
- what is automated
- what is advisory
- what is expert-only
- what is forbidden

The final architecture may be completely different from the existing System Brain proposal if evidence supports that.

---

# 9. REQUIRED TECHNICAL RESEARCH DOMAINS

Investigate only to the depth necessary to make consequential decisions, but cover the relevant mechanisms.

At minimum consider:

## Memory and app survival
- PSI
- LMKD
- oom_score_adj
- reclaim/refault/thrashing
- zRAM/swap
- cached processes
- app reconstruction/reload cost
- cached app freezer and Binder interaction
- ApplicationExitInfo
- Huawei background policy

## CPU / scheduler
- CPUFreq policy model
- Qualcomm cpufreq-hw where relevant
- Android task profiles
- cgroups/cpusets
- schedtune/uclamp depending actual device capability
- runnable latency

## GPU / display
- KGSL/devfreq where available
- GPU memory
- FrameTimeline
- SurfaceFlinger/display pipeline
- dynamic refresh behavior
- CPU/GPU bottleneck separation

## DDR / interconnect
- devfreq/bwmon/memlat concepts
- memory bus scaling
- platform observability limits

## Binder/dependency latency
- synchronous Binder critical paths
- client/server scheduling delay
- service dependency chains

## Storage / I/O
- PSI I/O
- block-device stats
- app launch I/O
- dirty/writeback behavior
- storage pressure

## Thermal / power
- Thermal HAL
- Qualcomm LMH/DCVS where relevant
- thermal headroom
- PowerStats when available
- battery/current context

## ART / startup
- ART profile behavior
- dex optimization
- startup modes
- profile-guided compilation

## Telemetry
- low-overhead continuous signals
- Perfetto
- ring buffers
- incident snapshots
- eBPF opportunities already supplied by the platform
- overhead control

## Privileged execution
- normal app
- Shizuku/ADB-assisted mode if justified
- Magisk/systemless services
- KernelSU if actually compatible
- least-privilege root agent
- boot safety
- rollback

Never provide or depend on security/integrity/DRM/banking/account bypasses.

---

# 10. EVIDENCE DISCIPLINE

Label consequential claims as:
- `CONFIRMED_DEVICE`
- `CONFIRMED_PLATFORM`
- `SUPPORTED_BY_PRIMARY_SOURCE`
- `COMMUNITY_EVIDENCE_ONLY`
- `INFERENCE`
- `UNKNOWN`

Do not silently turn:
- reference-kernel evidence into device evidence
- platform capability into ROM availability
- writable into safe
- proposed into implemented
- correlation into causation

If uncertain, design a probe.

---

# 11. INTERVENTION ADMISSION RULE

No automated privileged intervention may enter the final design unless it has:

1. target user-visible problem
2. observable trigger/evidence
3. confirmed capability requirement
4. plausible causal mechanism
5. bounded action
6. named owner-conflict risks
7. success metric
8. harm/counter metric
9. TTL/session boundary where appropriate
10. rollback path
11. safety boundary
12. test plan

If any item is missing, keep the idea observational, advisory, experimental, or rejected.

---

# 12. ROOT ARCHITECTURE DISCIPLINE

If root is retained:

- do not expose arbitrary unrestricted shell execution from the UI
- use an allowlisted action protocol
- default to read-only capability discovery
- snapshot old values before writes
- require rollback for automated writes
- prefer session-scoped/volatile experiments first
- fail closed on unsupported capability
- isolate privileged code from UI/business logic
- preserve a safe mode where experimental policies are disabled
- do not disable thermal protections
- do not disable integrity/security controls
- do not treat root as proof that an intervention is wise

---

# 13. REQUIRED FIRST EXECUTABLE MILESTONE

Independently decide what the first executable milestone should be.

The existing project currently suspects a read-only Device Intelligence / Capability Probe may be the right first milestone.

Do not preserve that choice automatically.

If you keep it, explain why it beats alternatives.

If you replace it, explain why.

The first milestone must prove meaningful information or value without requiring broad risky tuning.

---

# 14. FULL PROJECT KNOWLEDGE PACKAGE — MANDATORY

The mission is not complete after writing an analysis report.

You must create the complete handoff package defined in:

`docs/AI_PROJECT_DOCUMENTATION_CONTRACT.md`

Read and follow that contract exactly.

The package is intended for future models and engineers who have **no access to this chat**.

At minimum it must establish:
- current truth
- target architecture
- independent design history
- audit of prior research
- component contracts
- data/state contracts
- telemetry semantics
- control logic
- privilege and safety boundaries
- capability-probe design
- intervention catalog
- implementation blueprint
- code/module map
- build/CI/signing/release process
- testing strategy
- failure/recovery/rollback design
- physical-device experiment protocol
- traceability matrix
- assumptions/unknowns ledger
- roadmap/backlog
- glossary
- future-model work protocol
- ADRs
- machine-readable project state

No critical project knowledge should live only in the final prose response.

---

# 15. IMPLEMENTATION BLUEPRINT DEPTH

The implementation documentation must be sufficiently detailed that a future model can implement one stage without inventing the architecture.

For every stage specify:
- objective
- exact module/component affected
- intended source tree location
- interfaces/classes/services to introduce
- data models
- state transitions
- algorithms or pseudocode
- concurrency model
- privilege level
- allowed side effects
- error handling
- timeout/retry policy
- logging/telemetry
- edge cases
- tests
- CI gates
- real-device validation
- entry criteria
- exit criteria
- rollback
- deferred work

Where exact code cannot yet be responsibly specified because a device capability is unknown, say so and make that capability a blocking probe result.

Never invent a platform path or API merely to make the plan look complete.

---

# 16. FUTURE MODEL ERROR-PREVENTION REQUIREMENTS

The documentation system must actively prevent common future-model failures.

Design the package so future agents cannot easily confuse:
- research with implementation
- device facts with platform facts
- proposed architecture with current app state
- write permission with safe intervention
- temporary CI signing with stable release signing
- a test plan with a passing test
- a capability known from AOSP with one verified on the Huawei device

Use:
- explicit status labels
- accepted ADRs
- current-state document
- traceability matrix
- component contracts
- machine-readable project state
- invariants
- negative requirements
- tests mapped to invariants

If two artifacts disagree, the future model must have a documented conflict-resolution procedure.

---

# 17. DOCUMENTATION CHANGE DISCIPLINE

A future implementation change is incomplete unless affected documentation changes with it.

Define triggers such as:
- architecture changed → ADR + architecture doc + component contract
- interface changed → component contract + code map + tests
- schema changed → schema + examples + migration rules
- intervention added → intervention catalog + tests + traceability matrix
- capability behavior changed → capability spec + current state
- CI/release changed → build/release doc
- verified device fact changed → platform facts + project state

The second model must document these rules explicitly.

---

# 18. WRITE-BACK BEHAVIOR

If repository write access is available:

- create a dedicated research/handoff branch
- do not merge into `main`
- create the documentation package directly in the repository
- do not modify production code during this rethink unless explicitly authorized
- commit coherent documentation groups
- report branch and commit SHAs

If write access is unavailable:

- output each required document separately with its exact intended path
- preserve file boundaries
- do not collapse everything into a single response

---

# 19. TIME MANAGEMENT WITH DOCUMENTATION

The 90-minute deadline applies to the full mission, including documentation.

Do not spend the entire window researching and leave five minutes for handoff.

As soon as major decisions stabilize, write them into their destination documents.

Think of documentation as **state persistence during reasoning**, not clerical work at the end.

If time becomes tight:
- preserve correctness and source-of-truth documents first
- mark incomplete sections explicitly
- do not fabricate detail to fill templates
- record unfinished areas under `UNKNOWN` / backlog

A smaller accurate document is better than a complete-looking false one.

---

# 20. END-OF-MISSION CONSISTENCY AUDIT

Before stopping, perform the consistency gate from `AI_PROJECT_DOCUMENTATION_CONTRACT.md`.

Additionally verify:

- Could a fresh model identify exactly what is implemented today?
- Could it identify exactly what remains research?
- Could it locate the architecture decision governing a subsystem?
- Could it implement the next stage without inventing interfaces?
- Could it determine which tests must pass?
- Could it identify whether a device assumption is verified?
- Could it safely refuse an unsupported root write?
- Could it roll back an experimental intervention?
- Could it build the project without needing this chat?
- Could it determine the next correct task from the repository alone?

If not, fix the missing handoff information or mark it explicitly unresolved.

---

# 21. REQUIRED FINAL RESPONSE

Return a compact mission summary, not a duplicate of the documentation package.

Include:

```text
MISSION_START:
MISSION_DEADLINE:
MISSION_END:
TIMEBOX_STATUS:

INDEPENDENT_THESIS:
FINAL_THESIS:
MAJOR_CHANGES_AFTER_COLLISION:
PRIMARY_ARCHITECTURE:
FIRST_EXECUTABLE_MILESTONE:
STRONGEST_NEW_INVENTIONS:
MAJOR_EXISTING_IDEAS_REJECTED_OR_CHANGED:
BIGGEST_REMAINING_UNKNOWNS:
HIGH_POTENTIAL_LATE_DISCOVERIES:

DOCUMENTATION_BRANCH:
DOCUMENTATION_COMMITS:
KNOWLEDGE_PACKAGE_STATUS:
NEXT_RECOMMENDED_TASK:
```

Then list any files that remain incomplete.

---

# FINAL EXECUTION INSTRUCTION

Start immediately.

First think independently.
Do not read the prior System Brain research until your zero-base architecture is frozen into its own document.

Then expose yourself to the existing research and force the two designs to collide.

Search creatively for non-obvious combinations and new mechanisms.
Let strong evidence overturn both designs.

Attack your own final architecture before accepting it.

Then convert the result into a precise, cross-linked, implementation-ready Project Knowledge Package for future models.

The project should not depend on this conversation after you finish.

**Independent thinking first. Evidence collision second. Durable engineering memory last.**
