# FixHUA — Blind Genesis Mission Prompt V7
## Zero-Prior-Design Independent Invention + Deep Engineering Package + Visual Twin + Fast-Build Transfer ZIP

> **Standalone prompt.**
>
> Do not read or rely on any previous FixHUA architecture, research notes, design rounds, prior prompts, previous component names, or earlier proposed mechanisms.
>
> You are being given a **problem and an outcome**, not a predecessor design.

---

# 0. Role and standard of work

Act as an independent principal mobile-systems architect, Android/Linux engineer, performance and reliability researcher, experimental-systems designer, product systems thinker, and creative inventor.

Treat this mission as consequential engineering work, not as a brainstorming exercise.

Read the entire prompt carefully before committing to a direction.

Every instruction in this prompt is part of the acceptance criteria unless it explicitly says otherwise.

Do not skim, collapse multiple requirements into vague summaries, or substitute generic best practices for direct execution.

At the same time, do **not** interpret rigor as conservatism.

You are encouraged to invent new mechanisms, combine ideas from distant fields, challenge conventional Android optimization approaches, and propose techniques that may not yet exist as standard products—as long as you can explain why they could work and how to test them safely.

The desired combination is:

**maximum seriousness + maximum intellectual freedom + engineering reality.**

---

# 1. The problem

We want to create a powerful Android application/project named **FixHUA** for a Huawei-class Android device.

The user wants the phone to feel materially better in real daily use:

- smoother,
- faster to respond,
- less prone to stutter or long pauses,
- more stable under multitasking,
- less prone to avoidable app exits or reloads,
- more predictable during heavy use,
- and intelligently managed rather than controlled by a static collection of internet tweaks.

The project may use normal Android capabilities, assisted/privileged mechanisms, or root where justified and available.

Root is a capability, not a goal.

The product should use the strongest architecture you can derive from first principles.

You are **not** being told whether the right solution is:

- an optimizer,
- a controller,
- a diagnostic system,
- an adaptive runtime,
- a policy manager,
- a hybrid,
- or something else entirely.

Determine that yourself.

Do not assume the product category in advance.

---

# 2. What success means

The objective is not to make impressive benchmark charts.

The objective is to improve **real user-perceived device quality**.

Success must ultimately be expressed in observable outcomes such as:

- interaction latency,
- smoothness,
- responsiveness,
- app-resume quality,
- avoidable reload/exit frequency,
- stability,
- sustained performance,
- thermal behavior,
- battery/energy tradeoffs,
- and recovery from bad operating states.

You may define a better success model if you can justify it.

Do not optimize a metric merely because it is easy to read.

---

# 3. Important non-goals and safety boundaries

The project must not depend on:

- fake RAM-cleaner theater,
- blindly killing background applications,
- disabling thermal protection,
- destroying core Android stability mechanisms,
- bypassing account security, DRM, banking protections, integrity systems, or anti-tamper protections,
- arbitrary unrestricted root-shell endpoints,
- destructive persistent changes with no rollback,
- or unsupported claims that an Android device can be made incapable of killing applications.

If privileged behavior is eventually proposed, it must have a defensible safety model.

Do not interpret these boundaries as a ban on ambitious engineering. They are simply constraints on unsafe or misleading approaches.

---

# 4. Knowledge isolation rule

You must develop this design independently.

Do not search for, inspect, or consume previous FixHUA design work, previous FixHUA research documents, prior architecture diagrams, old component descriptions, prior AI prompts, or historical design discussions.

Do not search the existing FixHUA repository for design ideas.

If you are given a clean workspace for this mission, use only the neutral brief and artifacts explicitly designated for Blind Genesis work.

You may research Android, Linux, Huawei behavior, mobile systems, hardware/software interaction, operating-system design, performance engineering, reliability, academic work, open-source projects, and other external sources normally.

The isolation rule exists to preserve independent invention—not to limit technical research.

---

# 5. Exact device facts may be incomplete

Assume that some exact device, kernel, firmware, hardware, or vendor-policy facts may initially be unknown or contradictory.

Do not solve uncertainty by silently guessing.

If the final design depends on a device-specific fact that is not known, define the smallest observation, diagnostic, or experiment needed to establish it.

You are free to decide whether device discovery should be a major subsystem, a temporary engineering tool, or something else.

Do not let missing facts prevent architectural thinking; instead isolate what depends on them.

---

# 6. Hard outer mission clock

The mission starts immediately when this prompt is received.

Record at the top of your mission report:

```text
MISSION_START = <wall-clock timestamp if available, otherwise T+00:00>
MISSION_DURATION = 90 minutes maximum
MISSION_DEADLINE = MISSION_START + 90 minutes
```

The deadline is a hard outer boundary.

It limits **how long you work**, not **how widely you are allowed to think**.

There are no rigid internal time slices.

Manage the 90 minutes intelligently based on information value.

If a new high-impact idea appears late, you may pursue it if the remaining time allows responsible evaluation.

If it cannot be completed before the deadline, preserve it clearly as a high-potential unresolved idea with the shortest next test.

Do not continue indefinitely because more research remains possible.

Do not artificially fill the 90 minutes if the work is genuinely complete earlier.

---

# 7. Requirement Ledger — do not lose user intent

Before deep work, create a compact `REQUIREMENT_LEDGER` for yourself.

Translate this prompt into concrete obligations.

Each important user requirement must eventually map to one or more deliverables, decisions, diagrams, tests, or implementation instructions.

The ledger exists to prevent accidental omission—not to prescribe architecture.

Before finalizing, audit every requirement and mark it:

- satisfied,
- partially satisfied with reason,
- blocked by missing evidence/tooling,
- or intentionally rejected with justification.

Do not silently drop requirements.

---

# 8. Depth Gate — generic advice is not a deliverable

Do not stop at statements such as:

- “monitor performance,”
- “optimize memory,”
- “use AI,”
- “improve CPU scheduling,”
- “use root carefully,”
- “add telemetry,”
- “test thoroughly,”
- “create a modular architecture.”

Those are category labels, not engineering designs.

For every retained major mechanism, drive the explanation down until another competent engineer or coding model can answer:

- What exact problem is being changed?
- What causes it?
- What signal or evidence reveals it?
- What code/process/component owns the response?
- What action or policy changes the situation?
- What operating-system or hardware layer is involved?
- What can conflict with the action?
- What state must be recorded?
- What happens on failure?
- How is success measured?
- How is harm detected?
- How is the action reversed or disabled?
- How much implementation effort is required?

If you cannot answer these yet, label the mechanism as a hypothesis or research item instead of pretending it is implementation-ready.

---

# 9. Independent research

Research the real problem deeply before locking the architecture.

Do not start from common “Android optimization tips.”

Start from first principles:

- where perceived latency and instability actually originate,
- how mobile operating systems allocate and reclaim resources,
- how application lifecycle behavior interacts with the system,
- how hardware and software policy interact,
- how vendor behavior can alter platform behavior,
- how sustained use differs from short benchmarks,
- how one intervention can improve one workload while hurting another,
- and which control points are realistically accessible on production devices.

You decide which technical domains deserve investigation.

Prefer authoritative sources for consequential mechanisms when possible.

Use community discoveries when useful, but distinguish them from platform facts.

Do not accumulate sources for appearance. Research should change decisions.

---

# 10. Creative Invention Mandate

You are explicitly encouraged to go beyond existing optimizer conventions.

Search for mechanisms, combinations, abstractions, and control strategies that may be non-obvious or new.

You may borrow useful structures from unrelated fields, including but not limited to:

- control theory,
- robotics,
- real-time systems,
- distributed systems,
- queueing theory,
- congestion control,
- reliability engineering,
- fault-tolerant aviation systems,
- adaptive signal processing,
- online experimentation,
- cybernetics,
- economics/resource allocation,
- biological regulation,
- cache theory,
- anomaly detection,
- predictive control,
- and other disciplines you discover.

Do not import fashionable terminology as decoration.

When a foreign concept becomes promising, translate it into a concrete mobile-system mechanism:

```text
source concept
→ mobile/Android interpretation
→ observable evidence
→ possible action or policy
→ predicted user-visible effect
→ falsification test
→ safety/rollback story
```

Do not reject a mechanism merely because it is unfamiliar or not widely deployed.

Novel ideas are allowed to survive as hypotheses when:

- the causal mechanism is plausible,
- the idea is testable,
- the experiment can be bounded safely,
- and the expected impact justifies the effort.

Do not promote novelty merely because it is novel.

---

# 11. Search for combinations, not only features

Actively investigate whether multiple observations or mechanisms can be combined into a stronger abstraction.

Look for opportunities where:

- prediction is more useful than reaction,
- timing an intervention is more important than making it stronger,
- reducing work is better than allocating more resources,
- preserving useful state is better than preserving execution,
- a single causal model can replace many static tweaks,
- an incident can teach the system something reusable,
- one subsystem can explain several apparently unrelated symptoms,
- conflicting resource goals can be coordinated rather than independently tuned,
- or the correct solution is to avoid intervention entirely under certain conditions.

You are free to discover entirely different patterns.

---

# 12. Multiple architecture hypotheses before commitment

Do not marry the first plausible architecture.

Generate materially different solution theses and challenge them.

They do not need to share the same product category.

Compare them on:

- likely user impact,
- implementation time,
- observability,
- controllability,
- privilege requirements,
- runtime overhead,
- safety,
- reversibility,
- device portability,
- and ability to learn from real use.

Converge only after a credible comparison.

There is no required number of alternatives.

Explore enough to avoid first-solution bias, then stop when additional alternatives cease to improve the decision.

---

# 13. Adversarial attack your own winner

Before finalizing the architecture, try to break it.

Ask whether it could:

- become a source of lag itself,
- oscillate between policies,
- misattribute correlation as causation,
- fight Android or Huawei control loops,
- increase heat or battery drain,
- create instability under rare states,
- rely on unavailable privileges,
- become too complex to build in hours,
- generate data that cannot support the decisions it claims to make,
- fail after firmware/app updates,
- or become impossible for future models to maintain safely.

If the architecture survives only by hand-waving, redesign it.

---

# 14. Reality labels

Throughout the work, distinguish at least:

- `VERIFIED_FACT`
- `PLATFORM_EVIDENCE`
- `INFERENCE`
- `HYPOTHESIS`
- `UNKNOWN`

You may improve this vocabulary.

Do not present a hypothesis as a device fact.

Do not convert a platform capability into a claim that the target device definitely exposes it without evidence.

---

# 15. Fast-build objective: hours, not weeks

The user wants the smallest path from architecture to a useful installable result.

Treat **implementation compression** as a first-class design objective.

The target is to make the strongest useful version achievable in **hours of focused work**, not days, weeks, or months, whenever the platform allows it.

This does **not** mean:

- skipping critical safety checks,
- claiming untested code works,
- or building a toy that cannot produce meaningful value.

It means aggressively eliminating low-value complexity.

For every subsystem ask:

- Is this required for the first useful version?
- Can an existing platform capability replace custom infrastructure?
- Can a read-only or advisory version prove value first?
- Can we use a simpler mechanism without losing most of the impact?
- Can the architecture preserve a clean upgrade path while keeping v1 small?
- Can this step be parallelized?
- Can one test cover several high-risk assumptions?

Prefer architecture with **high impact per implementation hour**.

---

# 16. Fewest meaningful tests, not fewest tests at any cost

Design a compact validation strategy.

We want the minimum set of tests and experiments that provides maximum confidence.

Avoid redundant test matrices and ceremonial testing.

But do not remove a test if it is the only protection against:

- device instability,
- irreversible state,
- dangerous privileged behavior,
- misleading performance conclusions,
- broken build/install behavior,
- or regression of a core function.

Explain why each critical test exists.

---

# 17. First usable version and full vision

Produce both:

## A. First usable build
A sharply reduced version that can realistically be built and validated in hours.

## B. Full product architecture
The stronger long-term system that the first build can evolve into without throwing away its foundations.

The first build must not be a random prototype disconnected from the final architecture.

It should be the shortest credible path into the final system.

---

# 18. Implementation detail requirement

For the selected design, explain the construction in enough detail that another capable coding model can implement it.

Include, where knowable:

- repository/module structure,
- Android project structure,
- processes/services/workers,
- important classes/interfaces,
- state ownership,
- data models,
- inter-component communication,
- privilege boundaries,
- lifecycle behavior,
- background execution model,
- concurrency/threading model,
- persistence model,
- configuration model,
- error handling,
- timeouts,
- recovery behavior,
- logging/telemetry,
- APIs/system interfaces,
- build configuration,
- test structure,
- CI/release flow,
- installation/upgrade behavior,
- and first-device validation.

Use pseudocode, schemas, state machines, sequence diagrams, or interface definitions where they reduce ambiguity.

Do not invent device-specific filenames, paths, services, or capabilities merely to make the plan look complete.

---

# 19. Visual Twin

Create a visual representation of the final system so the user and future models can **see the architecture**, not merely read it.

You decide what diagrams are necessary based on your architecture.

The visual set should make it possible to understand at a glance:

- the whole product,
- major components,
- data movement,
- runtime control flow,
- external/platform boundaries,
- privilege boundaries,
- failure paths,
- recovery paths,
- build/release flow,
- and implementation dependencies.

If a connected Figma-like tool is available, create an editable architecture board.

If not, produce editable repository-native diagrams such as Mermaid, PlantUML, Graphviz/DOT, or structured SVG sources.

Do not create decoration-only diagrams.

Every major visual node should map back to a document/component identifier.

---

# 20. Design the project knowledge architecture

The final deliverable is not one giant report.

Design a **project knowledge architecture** that future AI models can navigate.

Use the accompanying neutral contract:

`BLIND_GENESIS_DOCUMENTATION_CONTRACT.md`

The contract defines quality and transfer requirements but deliberately does not prescribe your product architecture.

Create your own documentation topology and terminology.

The knowledge package must preserve:

- the selected thesis,
- rejected alternatives,
- facts,
- assumptions,
- open questions,
- design decisions,
- component contracts,
- implementation blueprint,
- schemas,
- visual diagrams,
- build instructions,
- validation strategy,
- risks,
- recovery rules,
- and future-model working protocol.

---

# 21. Future-model precision requirement

Imagine a strong AI coding model receives only your final ZIP six months from now.

It must be able to determine:

- what the project is,
- why it exists,
- what architecture was chosen,
- what alternatives were rejected,
- what is actually implemented versus merely proposed,
- what needs to be built next,
- where each component belongs,
- how components connect,
- what data they exchange,
- what invariants must hold,
- what tests guard those invariants,
- what can be changed safely,
- what needs new evidence before modification,
- and how to keep the documentation synchronized with code.

If your package cannot answer these, it is not complete.

---

# 22. Source-of-truth and anti-confusion design

Create an explicit method for resolving contradictions among:

- code,
- tests,
- build artifacts,
- device evidence,
- architecture decisions,
- planning documents,
- and research hypotheses.

Do not allow documentation to become an alternate fictional implementation.

Future models should know when a document is stale and how to repair it.

---

# 23. Innovation archive

Preserve strong ideas that are not selected for the first build.

Do not let implementation compression erase creativity.

For each high-potential deferred idea record:

- idea,
- mechanism,
- why it might matter,
- expected impact,
- missing evidence,
- risk,
- estimated implementation cost,
- and shortest experiment that could validate or kill it.

This allows the project to remain ambitious while the initial build remains small.

---

# 24. Do not let documentation consume the product

The documentation package must be excellent, but documentation itself is not the objective.

Avoid writing hundreds of pages when a precise schema, diagram, contract, or table would communicate the same thing better.

Optimize documentation for:

- information density,
- retrieval speed,
- implementation clarity,
- and future-model reliability.

Write deeply where detail prevents mistakes.

Write compactly where repetition adds no value.

---

# 25. Deliverable: transfer ZIP

At the end of the mission, produce a real transfer package named approximately:

`FixHUA-Blind-Genesis-Knowledge-Package.zip`

It must contain all final documents, machine-readable state/index files, diagrams and their editable sources, schemas/examples, design decisions, implementation blueprint, fast-build plan, validation plan, future-model instructions, innovation archive, and mission report.

If your environment supports file creation, create the ZIP physically and provide the exact path or artifact reference.

If your environment cannot produce a binary ZIP, do not pretend that it did. Produce the complete directory tree and manifest, clearly state the limitation, and package everything possible.

---

# 26. Mission report

Include a concise top-level mission report containing:

```text
MISSION_START:
MISSION_DEADLINE:
MISSION_END:
TIMEBOX_STATUS:

FINAL_PRODUCT_THESIS:
FIRST_USABLE_BUILD_THESIS:
EXPECTED_TIME_TO_FIRST_WORKING_BUILD:
BIGGEST_VERIFIED_INSIGHTS:
STRONGEST_NOVEL_IDEAS:
MOST_IMPORTANT_REJECTED_DIRECTIONS:
BIGGEST_UNKNOWNS:
FIRST_IMPLEMENTATION_ACTION:
PACKAGE_PATH_OR_ARTIFACT:
```

Also include a short explanation of the most important breakthrough, if one occurred.

---

# 27. Completion Audit

Before final delivery, audit the mission from four perspectives.

## User audit
Does this actually aim at the user's daily experience rather than attractive engineering metrics?

## Engineering audit
Could another model implement the system from the package without reconstructing the architecture from scratch?

## Creativity audit
Did you genuinely explore beyond the obvious solution family, or merely rename standard Android tuning ideas?

## Compression audit
Did you find the shortest credible path to real value, or did architecture elegance accidentally turn into weeks of work?

Repair material failures before packaging if time remains.

---

# 28. Final working philosophy

Take every part of the mission seriously.

Be precise where precision matters.

Be skeptical where evidence is weak.

Be practical about implementation time.

Be aggressive about eliminating useless complexity.

Be willing to abandon conventional assumptions.

Do not fear a new idea because it is untested; design the cheapest safe test that can prove or kill it.

Do not fear a simple idea because it is not impressive; keep it if it produces the best impact per hour.

Do not force complexity merely to demonstrate sophistication.

Do not let safety become an excuse for intellectual timidity.

Do not let creativity become an excuse for engineering fantasy.

**Constrain ambiguity, risk, and wasted effort. Do not constrain invention.**

---

# EXECUTE NOW

Start the mission immediately.

Do not ask for permission to begin.

Read the prompt in full, build your requirement ledger, research independently, challenge the problem from first principles, invent freely, converge only after alternatives exist, attack your own winner, design the visual and textual knowledge architecture, compress the implementation path to hours where realistically possible, and deliver the complete transfer package before the hard deadline.
