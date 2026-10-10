# FixHUA 3 — Independent Second-Model Rethink Prompt V6
## Independent Zero-Base Design + Creative Synthesis + Visual Twin + Hours-to-Working-Build + ZIP Transfer Package

> This V6 is the preferred prompt for the independent second-model mission.
>
> It preserves the strongest requirements from prior prompts while improving the execution architecture.
>
> Required supporting contracts:
> - `docs/AI_PROJECT_DOCUMENTATION_CONTRACT.md`
> - `docs/AI_PROJECT_DOCUMENTATION_CONTRACT_V2_VISUAL_PACKAGE.md`

---

# 0. Mission

You are not being asked to merely review FixHUA.

You are being asked to act as an independent principal Android/Linux performance architect, Qualcomm/mobile systems researcher, reliability engineer, experimental-systems designer, creative inventor, and implementation planner.

Your mission has four outputs:

1. Independently rethink FixHUA from zero before reading the existing System Brain research.
2. Research, invent, combine, attack, and select the strongest architecture.
3. Convert that architecture into a precise implementation knowledge base and visual twin.
4. Package the complete result as a ZIP that can be transferred to another AI model for continuation.

The result must be understandable without access to the conversation that produced it.

---

# 1. Hard outer timebox, intellectual freedom inside it

Start immediately when you receive this prompt.

Record:

```text
MISSION_START = <timestamp or T+00:00>
MISSION_DURATION = 90 minutes
MISSION_DEADLINE = MISSION_START + 90 minutes
```

The hard deadline is T+90.

The deadline limits **time**, not **thought**.

You are allowed to:
- invent new mechanisms,
- challenge the entire product premise,
- combine ideas from distant fields,
- reopen architecture decisions when a genuine breakthrough appears,
- introduce a previously unconsidered subsystem,
- reject existing FixHUA research,
- and pursue an unproven idea when its causal mechanism is plausible and testable.

Do not extend beyond the deadline.

If a high-potential idea appears too late to finish, preserve it under `HIGH-POTENTIAL LATE DISCOVERIES` with the first falsification experiment.

---

# 2. Anti-anchoring: independent design must come first

Before reading the existing System Brain research, inspect only enough repository context to know:

- what the currently implemented app actually does,
- repository structure,
- verified device/platform evidence,
- the user's goal,
- hard safety and non-goal constraints.

Do **not** read `SYSTEM_BRAIN_RESEARCH_DRAFT.md` or Research Rounds 1–13 until your independent design snapshot is complete.

Create first:

`docs/project/04_INDEPENDENT_ZERO_BASE_DESIGN.md`

It must capture your pre-exposure:

- problem model,
- independent thesis,
- architecture candidates,
- strongest candidate mechanisms,
- radical ideas,
- rejected directions,
- uncertainties,
- expected high-impact interventions,
- and first executable milestone.

Do not rewrite this document later to make it agree with the final answer.

---

# 3. Creative invention mandate

Do not limit yourself to established Android optimizer patterns.

Deliberately search for new combinations and non-obvious control mechanisms.

Borrow useful structures from areas such as:

- control theory,
- robotics,
- real-time systems,
- congestion control,
- queueing theory,
- cache economics,
- distributed systems,
- aviation fault management,
- reliability engineering,
- adaptive signal processing,
- biological homeostasis,
- online experimentation,
- cybernetics,
- scheduling theory,
- portfolio/resource allocation.

For every cross-domain idea, translate it explicitly:

`foreign concept → Android mechanism → signal → control surface → predicted effect → experiment → rollback`

Do not reject an idea simply because nobody appears to be using it yet.

Instead classify it:

- `NOVEL_HYPOTHESIS`
- `PLAUSIBLE_MECHANISM`
- `PLATFORM_SUPPORTED`
- `DEVICE_CONFIRMED`

A novel idea may survive if it is mechanistically plausible, measurable, reversible or safely shadowable, and cheap enough to test.

Do not confuse novelty with value. Rank by expected real user impact.

---

# 4. Existing research exposure and collision

After freezing your independent design, read the full existing research:

- `docs/SYSTEM_BRAIN_RESEARCH_DRAFT.md`
- `docs/SYSTEM_BRAIN_RESEARCH_ROUND1.md`
- ... through `docs/SYSTEM_BRAIN_RESEARCH_ROUND13.md`

Then write:

`docs/project/05_EXISTING_RESEARCH_AUDIT.md`

For every major idea assign:

- `KEEP`
- `MODIFY`
- `DOWNGRADE`
- `REJECT`
- `DEFER`

Then collide:

```text
INDEPENDENT DESIGN
        ×
EXISTING FIXHUA RESEARCH
        ×
REAL DEVICE / PLATFORM EVIDENCE
```

Explicitly identify:

- independent convergence,
- things the existing research found that you missed,
- things you found that the existing research missed,
- direct contradictions,
- ideas both sides got wrong,
- and combination opportunities that create a stronger third idea.

Do not mechanically merge both designs.

---

# 5. Adversarial attack before acceptance

Attack the strongest design as if you want it to fail.

Try to break it through:

- measurement overhead,
- wrong causal attribution,
- thermal drift,
- Huawei/vendor policy conflicts,
- scheduler oscillation,
- zRAM/reclaim side effects,
- app survival tradeoffs,
- root-daemon failure,
- stale policies after firmware update,
- device-specific capability mismatch,
- boot safety,
- signing/update problems,
- documentation/code drift,
- and complexity that costs more than the benefit it creates.

An architecture that survives attack is stronger than one that merely looks comprehensive.

---

# 6. Visual Twin is mandatory

Do not leave the architecture only in prose.

Produce a complete visual representation of the system.

If a Figma-capable tool is available, create an editable Figma board named approximately:

`FixHUA System Brain — Architecture & Build Map`

The visual system must include at minimum:

- System Context
- Component Architecture
- Runtime Data Flow
- Privilege Boundaries
- Telemetry Pipeline
- Decision Engine
- App Preservation / Memory Flow
- Performance Incident Attribution Flow
- Intervention Lifecycle
- Failure & Rollback Flow
- Build / CI / Signing / Release Flow
- Implementation Dependency Graph
- Product/UI Flow

If Figma is unavailable, produce editable Mermaid/SVG/PlantUML/Graphviz sources inside the repository and export viewable diagrams.

Do not provide screenshots only.

Every diagram node must map to documentation and implementation via:

`VISUAL_NODE_ID → COMPONENT_CONTRACT → SOURCE_PATH → TESTS → STATUS`

Create:

`docs/project/VISUAL_TRACEABILITY.json`

The diagrams are engineering artifacts, not presentation decoration.

---

# 7. Design the documentation package as an architecture

Follow:

`docs/AI_PROJECT_DOCUMENTATION_CONTRACT_V2_VISUAL_PACKAGE.md`

Do not produce a random collection of documents.

Build a navigable **Project Knowledge Architecture** with:

- root `AGENTS.md`,
- `docs/project/00_START_HERE.md`,
- source-of-truth ownership,
- accepted/rejected ADRs,
- component contracts,
- data schemas,
- examples,
- diagrams,
- machine-readable project state,
- knowledge graph,
- implementation blueprint,
- build/release instructions,
- rollback strategy,
- experiment protocol,
- traceability matrix,
- roadmap,
- future-model work protocol.

Create:

`docs/project/KNOWLEDGE_GRAPH.json`

Do not repeat authoritative facts unnecessarily.

Every primary document must clearly distinguish:

- `IMPLEMENTED`
- `VERIFIED`
- `PROPOSED`
- `EXPERIMENTAL`
- `REJECTED`
- `UNKNOWN`

Never let a research hypothesis look like implemented behavior.

---

# 8. Implementation blueprint must be code-ready

The final package must explain not only **what** to build, but **how** to build it.

For each major component include:

- purpose,
- source path,
- class/module names,
- interface/API contract,
- inputs and outputs,
- data models,
- state machine,
- lifecycle,
- threading/process model,
- privilege requirements,
- dependencies,
- algorithm or pseudocode,
- error handling,
- timeouts,
- persistence,
- logs/telemetry,
- rollback,
- unit tests,
- integration tests,
- real-device validation,
- completion criteria.

A future coding model should not need the original conversation to reconstruct design intent.

---

# 9. Hours-to-working-system constraint

The user wants the strongest useful version produced in **hours of focused work**, not days or weeks.

Design for **minimum implementation path with maximum real impact**.

Do not optimize for architectural elegance if it adds days of work with weak user-visible benefit.

Use:

`priority = expected_user_impact × confidence × reversibility / implementation_time / risk`

Prefer:

- existing Android/AOSP mechanisms,
- existing kernel/platform telemetry,
- one-process or few-module designs,
- minimal external dependencies,
- Kotlin/Java where native code is not essential,
- read-only discovery before root tuning,
- shadow mode before automated write mode,
- session-scoped interventions before persistent ones,
- a small number of high-signal tests.

Avoid for the first usable release unless essential:

- custom kernel builds,
- custom eBPF programs,
- large native frameworks,
- cloud infrastructure,
- complicated distributed architectures,
- broad ML pipelines,
- dozens of interventions at once.

Create:

`docs/project/27_HOURS_TO_WORKING_BUILD.md`

It must contain:

1. Smallest useful final build.
2. Exact build sequence.
3. File/class/module worklist.
4. Which steps can run in parallel.
5. Estimated duration per step.
6. Minimum high-signal tests.
7. Expected artifact at each checkpoint.
8. Blockers.
9. Deferred features.
10. Criteria for `READY_FOR_REAL_DEVICE_TEST`.
11. Criteria for `READY_FOR_PERSONAL_DAILY_USE`.

Do not promise an exact hour count if unknown; provide realistic ranges and blockers.

---

# 10. Minimum steps does not mean weak safety

Reduce ceremony, duplication, and low-value testing—not critical validation.

For the first useful build, prioritize tests that catch the largest failures:

- compilation/build,
- app launch,
- read-only probe correctness,
- unsupported capability fail-closed behavior,
- no unintended root-write endpoint,
- intervention rollback,
- thermal stop condition,
- daemon/app communication failure,
- one Android emulator smoke test where meaningful,
- one physical-device measurement protocol,
- reproducible signing/update path.

A 200-test suite is not automatically better than 20 high-signal tests.

But never remove a test solely to make a deadline look shorter if it protects against a high-impact failure.

---

# 11. Radical-idea fast lane

When you invent a high-potential new mechanism, do not automatically burden it with the full production architecture.

Use this progression:

```text
IDEA
→ MECHANISM SKETCH
→ SHADOW/READ-ONLY PROBE
→ ONE SMALL FALSIFICATION TEST
→ CANARY
→ INTEGRATE OR REJECT
```

This lets unproven ideas be explored quickly without destabilizing the phone.

A radical idea that cannot be tested safely in hours belongs in the research backlog, not in the first daily-use build.

---

# 12. Final selected architecture

After research, collision, and adversarial attack, select one primary architecture.

Do not end with a menu of equally plausible systems.

Produce:

`docs/project/06_FINAL_SYSTEM_ARCHITECTURE.md`

The architecture must explicitly answer:

- What FixHUA is.
- What runs continuously.
- What runs only on incidents.
- What remains advisory.
- What requires root.
- What can work without root.
- How telemetry stays low-overhead.
- How decisions are made.
- How interventions are admitted.
- How experiments are measured.
- How rollback occurs.
- How the system prevents itself from becoming the performance problem.

---

# 13. Required package deliverables

Create the full package defined by the documentation contracts, including at least:

- `AGENTS.md`
- `docs/project/00_START_HERE.md`
- `docs/project/01_MISSION_AND_NON_GOALS.md`
- `docs/project/02_CURRENT_STATE.md`
- `docs/project/03_DEVICE_AND_PLATFORM_FACTS.md`
- `docs/project/04_INDEPENDENT_ZERO_BASE_DESIGN.md`
- `docs/project/05_EXISTING_RESEARCH_AUDIT.md`
- `docs/project/06_FINAL_SYSTEM_ARCHITECTURE.md`
- `docs/project/07_COMPONENT_CONTRACTS.md`
- `docs/project/08_DATA_STATE_AND_STORAGE_MODEL.md`
- `docs/project/09_TELEMETRY_AND_SIGNAL_MODEL.md`
- `docs/project/10_DECISION_AND_CONTROL_ENGINE.md`
- `docs/project/11_PRIVILEGE_SECURITY_AND_SAFETY_BOUNDARIES.md`
- `docs/project/12_CAPABILITY_PROBE_SPEC.md`
- `docs/project/13_INTERVENTION_CATALOG.md`
- `docs/project/14_IMPLEMENTATION_BLUEPRINT.md`
- `docs/project/15_CODEBASE_AND_MODULE_MAP.md`
- `docs/project/16_BUILD_CI_SIGNING_AND_RELEASE.md`
- `docs/project/17_TEST_AND_VALIDATION_STRATEGY.md`
- `docs/project/18_FAILURE_RECOVERY_AND_ROLLBACK.md`
- `docs/project/19_PERFORMANCE_EXPERIMENT_PROTOCOL.md`
- `docs/project/20_TRACEABILITY_MATRIX.md`
- `docs/project/21_ASSUMPTIONS_UNKNOWNS_AND_OPEN_QUESTIONS.md`
- `docs/project/22_ROADMAP_AND_EXECUTION_ORDER.md`
- `docs/project/23_BACKLOG_AND_LATE_DISCOVERIES.md`
- `docs/project/24_GLOSSARY.md`
- `docs/project/25_FUTURE_MODEL_WORK_PROTOCOL.md`
- `docs/project/26_DOCUMENTATION_MAINTENANCE.md`
- `docs/project/27_HOURS_TO_WORKING_BUILD.md`
- `docs/project/PROJECT_STATE.json`
- `docs/project/KNOWLEDGE_GRAPH.json`
- `docs/project/VISUAL_TRACEABILITY.json`
- ADRs
- schemas
- examples
- editable diagrams
- final mission report
- `PACKAGE_MANIFEST.md`

Do not create filler files. If a document is genuinely unnecessary, record that explicitly in `00_START_HERE.md` and explain why.

---

# 14. ZIP transfer package is mandatory

At the end, create:

`FixHUA-System-Brain-Knowledge-Package.zip`

The ZIP must contain the complete knowledge package and visual assets.

If your environment supports file/artifact generation, **actually generate the ZIP**.

Return:

- exact file/artifact path,
- file size if available,
- checksum if practical,
- repository commit/branch used,
- list of major contents,
- Figma link/reference if one was created.

Do not merely say “ZIP created” unless the file actually exists.

If your environment cannot create binary files, state that explicitly and provide the exact folder structure plus a one-command packaging instruction for the environment that will create it.

The purpose of the ZIP is to allow the user to send the entire package to another model for independent analysis.

---

# 15. Transfer-readiness simulation

Before declaring success, simulate a future model entering the repository with no conversation history.

That model must be able to determine quickly:

- what the project is,
- what is actually implemented,
- what is proposed only,
- what architecture was selected,
- why it was selected,
- how components connect,
- what to build next,
- exact source files/modules involved,
- which capabilities are device-dependent,
- what is unsafe,
- what tests are mandatory,
- how rollback works,
- and where the visual system map is.

If hidden conversational context is still required, the package is incomplete.

---

# 16. Definition of success

Success is not:

- the longest report,
- the most components,
- the largest test suite,
- the most impressive terminology,
- or the most radical tweak.

Success is:

- a stronger independent architecture,
- genuinely new high-impact ideas,
- a short path to a working build,
- clear separation of fact vs hypothesis,
- measurable mechanisms,
- reversible high-risk actions,
- a visual twin that makes the design inspectable,
- a complete project memory future models can trust,
- and an actual ZIP transfer artifact when tooling permits.

---

# Final instruction

Start immediately.

Think independently before reading the existing System Brain research.

Be creatively aggressive and technically skeptical.

Do not fear proposing ideas that have not been tried before when they have a plausible mechanism and can be falsified cheaply.

Search for combinations that produce disproportionately large impact from small implementation effort.

Prefer a few powerful mechanisms over dozens of weak tweaks.

Design the project so its first useful version can be built and validated in hours of focused work.

Make the architecture visible.
Make the implementation path explicit.
Make every important assumption traceable.
Make the project understandable to the next model without this conversation.

Then package the complete result into `FixHUA-System-Brain-Knowledge-Package.zip` and return it to the user.