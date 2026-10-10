# FixHUA — AI Project Documentation Contract

## Purpose

This contract defines the documentation package that an independent redesign/research model must leave behind so that a future engineer or AI model can enter the FixHUA repository, reconstruct the project accurately, understand the smallest important design details, and continue work without silently inventing missing context.

The goal is **maximum continuity, traceability, and implementation precision**. It is not possible to guarantee that every future model will never make a mistake, so the package must reduce ambiguity and make mistakes detectable through explicit evidence, status labels, invariants, tests, and source-of-truth rules.

---

# 1. Core documentation rule

The final deliverable is **not one report**.

It is a versioned, cross-linked **Project Knowledge Package** committed to the repository.

Every important architectural decision, implementation dependency, control boundary, uncertainty, safety rule, test requirement, and execution step must have a stable home.

Avoid duplicating the same fact in many files. One document should own each fact; other documents should link to it.

---

# 2. Required repository structure

Create the following package under `docs/project/` unless a clearly superior structure is justified.

```text
AGENTS.md

docs/project/
  00_START_HERE.md
  01_MISSION_AND_NON_GOALS.md
  02_CURRENT_STATE.md
  03_DEVICE_AND_PLATFORM_FACTS.md
  04_INDEPENDENT_ZERO_BASE_DESIGN.md
  05_EXISTING_RESEARCH_AUDIT.md
  06_FINAL_SYSTEM_ARCHITECTURE.md
  07_COMPONENT_CONTRACTS.md
  08_DATA_STATE_AND_STORAGE_MODEL.md
  09_TELEMETRY_AND_SIGNAL_MODEL.md
  10_DECISION_AND_CONTROL_ENGINE.md
  11_PRIVILEGE_SECURITY_AND_SAFETY_BOUNDARIES.md
  12_CAPABILITY_PROBE_SPEC.md
  13_INTERVENTION_CATALOG.md
  14_IMPLEMENTATION_BLUEPRINT.md
  15_CODEBASE_AND_MODULE_MAP.md
  16_BUILD_CI_SIGNING_AND_RELEASE.md
  17_TEST_AND_VALIDATION_STRATEGY.md
  18_FAILURE_RECOVERY_AND_ROLLBACK.md
  19_PERFORMANCE_EXPERIMENT_PROTOCOL.md
  20_TRACEABILITY_MATRIX.md
  21_ASSUMPTIONS_UNKNOWNS_AND_OPEN_QUESTIONS.md
  22_ROADMAP_AND_EXECUTION_ORDER.md
  23_BACKLOG_AND_LATE_DISCOVERIES.md
  24_GLOSSARY.md
  25_FUTURE_MODEL_WORK_PROTOCOL.md
  26_DOCUMENTATION_MAINTENANCE.md

  adr/
    ADR-0001-*.md
    ADR-0002-*.md
    ...

  schemas/
    device-capabilities.schema.json
    incident.schema.json
    intervention-record.schema.json
    experiment-record.schema.json
    project-state.schema.json

  diagrams/
    architecture.md
    runtime-flows.md
    failure-and-rollback.md

  examples/
    device-capabilities.example.json
    incident.example.json
    intervention-record.example.json
    experiment-record.example.json

  PROJECT_STATE.json
```

If some files would contain no meaningful information, keep the path in `00_START_HERE.md` as `NOT YET REQUIRED` rather than inventing filler.

---

# 3. Root `AGENTS.md`

Create or update a concise root-level `AGENTS.md` intended for any future coding/research model.

It must state:

1. Read `docs/project/00_START_HERE.md` first.
2. Never treat research ideas as implemented behavior.
3. Read `docs/project/02_CURRENT_STATE.md` before changing code.
4. Read accepted ADRs relevant to the subsystem being changed.
5. Read the subsystem's Component Contract before implementation.
6. Preserve safety and rollback invariants.
7. Do not introduce new root writes without capability evidence, admission criteria, rollback, and tests.
8. Update affected documentation in the same change as code.
9. If code, CI, device evidence, and documents disagree, follow the source-of-truth procedure rather than guessing.
10. Never include private signing material, API keys, account data, or personal device data in documentation.

Keep `AGENTS.md` short enough that an agent will actually read it.

---

# 4. Mandatory metadata on every project document

Every primary document must begin with a compact metadata block:

```text
STATUS: IMPLEMENTED | VERIFIED | PROPOSED | EXPERIMENTAL | REJECTED | UNKNOWN | MIXED
DOCUMENT_VERSION:
LAST_REVIEWED_COMMIT:
LAST_REVIEWED_DATE:
OWNER_DOMAIN:
SUPERSEDES:
SUPERSEDED_BY:
SOURCE_OF_TRUTH_FOR:
DEPENDENCIES:
```

If a document contains mixed states, every major section must label its own status.

Do not use vague phrases such as "the system does X" when X is merely proposed.

---

# 5. Source-of-truth hierarchy

Future models must have an explicit conflict-resolution order.

Use this hierarchy:

## A. Execution truth

For claims about what currently runs:

1. verified device evidence / probe output for device-specific facts
2. current checked-out source code
3. current tests and CI output
4. build artifacts and release metadata
5. `02_CURRENT_STATE.md`

If documentation conflicts with verified code/CI/device evidence, **do not silently trust the document**. Mark the contradiction and repair the documentation.

## B. Architectural decision truth

For why the system is intentionally designed a certain way:

1. accepted ADRs
2. `06_FINAL_SYSTEM_ARCHITECTURE.md`
3. component contracts
4. implementation blueprint

A future model may challenge an accepted decision, but it must create a new ADR that supersedes the old one instead of silently changing direction.

## C. Planning truth

Roadmaps, backlog entries, experiments, and speculative ideas are not proof that a feature exists.

`PROPOSED`, `EXPERIMENTAL`, and `BACKLOG` must never be interpreted as `IMPLEMENTED`.

---

# 6. Required contents of each primary document

## `00_START_HERE.md`

This is the navigation and context entry point for every future model.

It must contain:
- one-paragraph project definition
- current project phase
- current stable branch/release versus research branch
- what is implemented now
- what is being designed next
- top safety invariants
- exact reading order for common tasks
- links to all project documents
- source-of-truth hierarchy summary
- "do not assume" list
- last-known major open risks

It should allow a competent model to understand where it is in under five minutes.

## `01_MISSION_AND_NON_GOALS.md`

Define:
- user-visible objectives
- measurable goals
- explicit non-goals
- prohibited success metrics such as free-RAM theater
- safety limits
- what "System Brain" means and does not mean

## `02_CURRENT_STATE.md`

This document must be brutally factual.

Include:
- repository state and important branches
- current implemented app architecture
- package/version/build state
- currently verified features
- current tests and CI gates
- known defects
- known missing components
- whether root agent exists or not
- whether capability probe exists or not
- whether each major System Brain subsystem is implemented, proposed, or absent
- last verified APK/release metadata when applicable

No future design language unless clearly separated under `PROPOSED NEXT`.

## `03_DEVICE_AND_PLATFORM_FACTS.md`

Separate:
- confirmed device facts
- platform-level facts
- likely/inferred facts
- contradictory identity signals
- unknowns requiring the Capability Probe

Each consequential claim must carry an evidence/confidence tag such as:
- `CONFIRMED_DEVICE`
- `CONFIRMED_PLATFORM`
- `SUPPORTED_BY_PRIMARY_SOURCE`
- `COMMUNITY_EVIDENCE_ONLY`
- `INFERENCE`
- `UNKNOWN`

## `04_INDEPENDENT_ZERO_BASE_DESIGN.md`

This must record the second model's architecture **before** reading FixHUA research in detail.

Purpose: preserve independent reasoning and prevent hindsight from rewriting history.

Include:
- original problem model
- independent architecture
- independent high-impact ideas
- rejected directions
- uncertainties
- assumptions

Do not rewrite this file later to make it match the final synthesis. Append a short post-comparison note if necessary.

## `05_EXISTING_RESEARCH_AUDIT.md`

After the independent pass, audit all existing FixHUA research.

For each major prior idea:
- keep
- modify
- downgrade
- reject
- defer

Explain why.

Record where the independent design converged with existing work and where it differed.

## `06_FINAL_SYSTEM_ARCHITECTURE.md`

This is the normative target architecture.

Must contain:
- architecture goals
- subsystem boundaries
- trust/privilege boundaries
- data flow
- control flow
- observation flow
- intervention flow
- rollback flow
- startup/shutdown flow
- normal APK mode
- assisted mode if retained
- root mode if retained
- failure isolation
- how Huawei/vendor ownership is respected
- what is intentionally *not* controlled

Include Mermaid diagrams where useful.

## `07_COMPONENT_CONTRACTS.md`

For every component define:
- purpose
- inputs
- outputs
- state owned
- dependencies
- allowed side effects
- forbidden side effects
- privilege level
- threading/execution context
- error semantics
- timeout behavior
- retry policy
- idempotency expectations
- logging/telemetry
- security/safety constraints
- unit/integration tests required

Examples may include:
- Capability Probe
- Signal Collector
- Stall Sentinel
- Black Box Recorder
- Incident Classifier
- Bottleneck Attribution Engine
- Policy/Decision Engine
- Intervention Executor
- Rollback Manager
- App DNA/Profile Store
- Experiment Evaluator
- UI/Report Layer
- Root Agent

Do not include a component merely because it appeared in earlier research; only document components retained in the final design.

## `08_DATA_STATE_AND_STORAGE_MODEL.md`

Define every persistent and transient state category.

Include:
- schemas
- ownership
- lifecycle
- retention
- versioning
- migrations
- corruption handling
- privacy rules
- local-only data guarantees where applicable
- what may be reset safely
- what must survive reboot

## `09_TELEMETRY_AND_SIGNAL_MODEL.md`

For every signal:
- exact meaning
- source
- units
- sampling/event model
- expected overhead
- privilege required
- reliability caveats
- interpretation hazards
- how it participates in causal attribution

Explicitly distinguish raw measurements from derived scores.

## `10_DECISION_AND_CONTROL_ENGINE.md`

Define:
- state machine / evidence model / learning model chosen
- why it was chosen
- confidence handling
- hysteresis
- intervention admission gate
- shadow mode
- canary mode
- experiment promotion/demotion
- conflict detection
- policy priority
- time-to-live
- rollback triggers
- no-action decision

Include pseudocode for the main decision loop.

## `11_PRIVILEGE_SECURITY_AND_SAFETY_BOUNDARIES.md`

Define:
- normal app permissions
- assisted privilege mode if any
- root agent trust boundary
- allowlisted commands/actions only
- IPC authentication/authorization model
- SELinux/capability expectations where relevant
- what root must never expose
- boot safety
- thermal safety
- storage-write safety
- privacy constraints
- explicit exclusions: no Play Integrity/DRM/banking/account-security bypass

Never place secrets in this document.

## `12_CAPABILITY_PROBE_SPEC.md`

This must be implementation-ready.

For every probe domain define:
- discovery paths/APIs/services
- expected data
- fallback paths
- timeout/error behavior
- normalization rules
- confidence calculation
- how the result maps to `PRESENT`, `READABLE`, `WRITABLE`, `REVERSIBLE`, ownership, persistence, and risk
- privacy constraints

The first probe implementation remains read-only unless a future accepted ADR changes this.

## `13_INTERVENTION_CATALOG.md`

For every automated or manual intervention retained in the design:
- unique intervention ID
- target problem
- required evidence
- required capability
- action
- bounds
- owner conflict risks
- success metric
- harm metric
- TTL
- rollback
- privilege level
- maturity: `PROPOSED / SHADOW / CANARY / DEVICE_ACCEPTED / REJECTED`

No intervention is allowed to exist only as an attractive idea.

## `14_IMPLEMENTATION_BLUEPRINT.md`

This is the detailed build plan from current state to target system.

For each implementation stage define:
- objective
- files/modules to add or change
- prerequisite decisions
- APIs/interfaces to create
- data structures
- algorithms/pseudocode
- edge cases
- test work
- CI changes
- device validation
- entry criteria
- exit criteria
- rollback if the stage fails
- what is explicitly deferred

The plan must be granular enough that another model can implement one stage without inventing architecture.

## `15_CODEBASE_AND_MODULE_MAP.md`

Show the intended and current repository tree.

For every important path explain:
- responsibility
- public interfaces
- owner subsystem
- permitted dependencies
- forbidden dependencies

Include a dependency-direction rule to prevent future architectural erosion.

## `16_BUILD_CI_SIGNING_AND_RELEASE.md`

Document:
- toolchain versions
- Gradle/JDK/Android SDK expectations
- local build steps
- test commands
- CI workflow stages
- APK verification
- package/version checks
- permission checks
- emulator/device smoke tests
- artifact naming
- release process
- stable signing architecture

Never include signing private keys, passwords, API keys, or secret values.

If current signing is temporary or unsuitable for updates, state that explicitly.

## `17_TEST_AND_VALIDATION_STRATEGY.md`

Define:
- unit tests
- property/state-machine tests where useful
- integration tests
- Android instrumentation tests
- root-agent protocol tests
- malformed-input tests
- privilege-boundary tests
- rollback tests
- boot/restart recovery tests
- emulator tests
- real Huawei device tests
- performance regression gates

Map every critical invariant to at least one test.

## `18_FAILURE_RECOVERY_AND_ROLLBACK.md`

For each serious failure mode define:
- detection
- containment
- rollback
- safe mode
- user recovery
- data recovery
- whether reboot is required
- how to prevent repeated failure after reboot

Include boot loop, daemon crash, stale vendor paths, failed writes, thermal escalation, policy oscillation, corrupted learning state, and firmware update invalidation.

## `19_PERFORMANCE_EXPERIMENT_PROTOCOL.md`

Define how FixHUA proves real benefit on one physical phone.

Include:
- baseline definition
- paired trials
- warm/cold state control
- battery/thermal control
- randomization where feasible
- repeated measurements
- variance handling
- user-perceived metrics
- counter-metrics
- promotion threshold
- regression threshold
- when evidence is too weak to decide

## `20_TRACEABILITY_MATRIX.md`

Create a matrix that links:

`User Goal → Requirement → Component → Signal → Intervention → Test → Source Files → Documentation → Status`

This is the fastest way for a future model to discover the complete consequence chain of a change.

## `21_ASSUMPTIONS_UNKNOWNS_AND_OPEN_QUESTIONS.md`

Maintain a numbered ledger.

Every entry needs:
- ID
- statement
- class: assumption / unknown / contradiction / research question
- impact if wrong
- current evidence
- next action to resolve
- owner document/component
- status

## `22_ROADMAP_AND_EXECUTION_ORDER.md`

Define the recommended order of implementation with dependencies and gates.

Do not use vague phases such as "optimize system".

Each stage must have a measurable deliverable.

## `23_BACKLOG_AND_LATE_DISCOVERIES.md`

Preserve creativity without contaminating current architecture.

For each item include:
- idea
- why it matters
- evidence level
- what must be true for it to become active work
- why it is not in the current build path

## `24_GLOSSARY.md`

Define every project-specific term and acronym exactly once.

## `25_FUTURE_MODEL_WORK_PROTOCOL.md`

This document must tell any future model exactly how to enter the project.

Minimum protocol:

### Before changing anything
1. Read `AGENTS.md`.
2. Read `00_START_HERE.md`.
3. Read `02_CURRENT_STATE.md`.
4. Identify the target subsystem.
5. Read its component contract.
6. Read relevant accepted ADRs.
7. Read corresponding rows in the traceability matrix.
8. Inspect actual code/tests before trusting documentation.
9. State the pre-change facts, assumptions, and unknowns.

### During work
- do not expand scope silently
- preserve component contracts or create an ADR to change them
- keep privileged changes reversible
- add tests for invariants
- do not mark proposed behavior as implemented

### Before finishing
- run relevant tests
- update `02_CURRENT_STATE.md`
- update affected component/architecture docs
- update traceability matrix
- update schemas if data contracts changed
- add/supersede ADR when a decision changed
- report what remains unverified

## `26_DOCUMENTATION_MAINTENANCE.md`

Define:
- document ownership
- update triggers
- stale-document detection
- review cadence
- how to supersede a document
- how to update commit references
- which code changes require documentation changes
- how CI may eventually lint documentation links/status metadata

---

# 7. Architecture Decision Records

Every consequential architectural choice must have an ADR.

Use a stable format:

```text
# ADR-XXXX — Title

STATUS: PROPOSED | ACCEPTED | SUPERSEDED | REJECTED
DATE:
SUPERSEDES:
SUPERSEDED_BY:

## Context
## Decision
## Alternatives considered
## Why this decision won
## Consequences
## Failure modes introduced
## Validation required
## Reversal strategy
## Evidence
```

At minimum create ADRs for decisions such as:
- product identity / System Brain thesis
- privilege architecture
- root-agent design
- telemetry tiers
- capability-probe-first strategy
- control/learning model
- intervention admission model
- storage/state model
- safety/rollback model
- any decision to use or reject Magisk/KernelSU/Shizuku mechanisms

Do not create ADRs for trivial naming or formatting choices.

---

# 8. Machine-readable project state

Create `docs/project/PROJECT_STATE.json` plus a JSON schema.

It should summarize at least:

```json
{
  "project": "FixHUA",
  "phase": "...",
  "current_release": "...",
  "stable_branch": "...",
  "research_branch": "...",
  "architecture_status": "...",
  "capability_probe": {"status": "..."},
  "root_agent": {"status": "..."},
  "brain_engine": {"status": "..."},
  "open_blockers": [],
  "verified_device_facts": [],
  "critical_unknowns": [],
  "next_recommended_task": "...",
  "last_verified_commit": "..."
}
```

Do not fabricate values. Use `UNKNOWN` when necessary.

This file is a navigation aid, not a replacement for the human-readable current-state document.

---

# 9. Documentation quality requirements

Every document must be written for a technically capable reader who has **no access to the original chat**.

Therefore:
- never say "as discussed earlier"
- never rely on memory of previous prompts
- define every important term
- use exact repository paths where known
- distinguish current versus target architecture
- distinguish platform facts versus device facts
- include preconditions for every dangerous or privileged action
- include negative requirements: what must not happen
- include examples for non-obvious schemas/contracts
- explain why choices were made, not only what was chosen
- record rejected alternatives when future developers are likely to rediscover them

Prefer precise prose and diagrams over giant code dumps.

For implementation-critical algorithms, include pseudocode and invariants.

---

# 10. Traceability and anti-drift rule

No future feature may be considered complete unless its consequence chain is traceable.

At minimum:

`Requirement → Architecture component → Implementation path → Tests → Documentation → Runtime status`

If any link is missing, the work is incomplete.

This is designed specifically to stop future models from adding a clever local change that violates a system-level invariant.

---

# 11. Documentation consistency gate

Before the second model declares the mission complete, it must perform a consistency pass:

- Are all links valid?
- Do document statuses match reality?
- Does `CURRENT_STATE` match current code/research status?
- Does the final architecture contradict any accepted ADR?
- Does the implementation blueprint match component contracts?
- Does the code/module map match the proposed architecture?
- Does every intervention have measurement and rollback?
- Does every critical invariant have a test plan?
- Are unverified device assumptions labeled?
- Are any secrets present? If yes, remove them.
- Can a fresh model determine the next correct task without reading the original conversation?

Any contradiction found must be fixed or explicitly recorded as unresolved before handoff.

---

# 12. Write-back rule

If the model has repository write access:

- create the documentation package on a dedicated research branch
- do not merge into `main` automatically
- do not modify production code during the documentation/rethink mission unless explicitly authorized
- commit the documentation package in coherent commits
- return the branch name and commit SHAs

If repository write access is unavailable:

- output every file with its exact intended path
- keep files separable so another tool/model can materialize them without rewriting content

---

# 13. Definition of documentation done

The documentation mission is complete only when a future model can answer, from the repository alone:

1. What does FixHUA currently do?
2. What is only proposed?
3. What device/platform facts are verified?
4. What is unknown?
5. What is the final target architecture?
6. Why was that architecture chosen?
7. What are the major components and their contracts?
8. What data/state exists and who owns it?
9. What signals drive decisions?
10. What privileged actions are allowed or forbidden?
11. What interventions exist and what evidence admits them?
12. How is every intervention rolled back?
13. How is the app/root system built and released?
14. How is the system tested?
15. How is real performance improvement proven?
16. What can break and how is recovery performed?
17. What should be implemented next?
18. Which ideas were rejected and why?
19. What must be updated when a future change is made?
20. Where is the source of truth when two artifacts disagree?

If those answers require the original chat, the handoff is incomplete.
