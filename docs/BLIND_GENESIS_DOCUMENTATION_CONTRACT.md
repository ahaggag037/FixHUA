# Blind Genesis — Project Knowledge Package Contract

## Purpose

This contract defines the output package for an independent model that is given only the product problem, user goals, verified device facts, safety boundaries, timebox, and delivery expectations.

The model must not be given prior FixHUA architecture, prior research rounds, prior component names, prior proposed mechanisms, or any earlier design thesis.

The purpose is to obtain a genuinely independent design and a complete engineering memory that can later be compared against another design without contamination or anchoring.

---

# 1. Core principle

The final result must be a **coherent project knowledge system**, not a pile of notes.

The model is free to invent its own architecture, terminology, modules, workflows, control model, data model, visual language, and implementation strategy.

The documentation contract constrains only the **quality, traceability, completeness, and transferability** of the result. It must not constrain the conceptual solution space.

---

# 2. Required qualities of the package

The package must be:

- self-contained,
- understandable without the original conversation,
- explicit about facts versus hypotheses,
- implementation-oriented,
- visually inspectable,
- cross-linked,
- machine-navigable where practical,
- precise enough for a future AI model to continue coding without inventing missing context,
- honest about uncertainty,
- and compact enough that a future model can reconstruct the project rapidly.

The package must make it difficult to confuse:

- an idea with an implemented feature,
- an inference with a verified fact,
- an experiment with a production rule,
- a future possibility with the current architecture,
- or a research hypothesis with a confirmed device capability.

---

# 3. Design the documentation architecture yourself

Do **not** force a predetermined document list if a better knowledge architecture can be created.

Before writing the package, design the documentation system itself.

Produce a short document called:

`KNOWLEDGE_ARCHITECTURE.md`

It must explain:

- what the documentation graph looks like,
- which documents own which facts,
- what the canonical entry point is,
- how future models should navigate it,
- how design decisions are recorded,
- how implementation state is tracked,
- how visual diagrams map to source documents,
- how uncertainty is represented,
- and how contradictions are resolved.

You may use Markdown, JSON, YAML, diagrams, ADRs, schemas, or other repository-friendly formats.

The structure is yours to invent.

---

# 4. Mandatory semantic states

Regardless of document structure, every consequential statement about project state must be classifiable as one of:

- `VERIFIED_FACT`
- `IMPLEMENTED`
- `TESTED`
- `PROPOSED`
- `EXPERIMENTAL`
- `HYPOTHESIS`
- `REJECTED`
- `DEFERRED`
- `UNKNOWN`

You may define a richer state model, but these distinctions must remain possible.

A future model must never be able to read a speculative mechanism and mistake it for current implementation.

---

# 5. Mandatory knowledge domains

Your documentation architecture may name and organize these differently, but the package must preserve enough information for a future model to reconstruct all of the following:

- the problem definition,
- user-visible objectives,
- success criteria,
- non-goals,
- verified device/platform facts,
- unknowns and assumptions,
- final product thesis,
- architecture,
- subsystem responsibilities,
- component interfaces,
- data model,
- state model,
- privilege model,
- runtime flows,
- decision rules or algorithms,
- safety constraints,
- failure handling,
- rollback behavior,
- testing strategy,
- measurement strategy,
- build process,
- release process,
- signing/secrets boundaries,
- implementation order,
- code/module mapping,
- unresolved questions,
- deferred ideas,
- and future-model operating instructions.

---

# 6. Future-model entry point

Create a short root-level AI handoff file such as `AGENTS.md`, `AI_START_HERE.md`, or a better equivalent.

It must tell any future model:

1. what to read first,
2. where the current state lives,
3. where architectural decisions live,
4. where code ownership and component contracts live,
5. how to distinguish implemented from proposed work,
6. what invariants must not be violated,
7. how to update documentation when code changes,
8. what evidence must exist before changing privileged behavior,
9. how to handle contradictions,
10. and what not to guess.

Keep this entry file short enough that another model will actually read it before acting.

---

# 7. Machine-readable project state

Create a machine-readable state file such as:

`PROJECT_STATE.json`

or a better equivalent.

It should expose at least:

- project phase,
- selected architecture identifier,
- implemented subsystems,
- unimplemented subsystems,
- currently blocked work,
- verified device facts,
- critical unknowns,
- active risks,
- current build/release state,
- test state,
- and next recommended implementation milestone.

The model may design the schema itself.

---

# 8. Knowledge graph

Produce a machine-readable graph/index that lets another model map relationships among:

- requirements,
- decisions,
- components,
- data structures,
- diagrams,
- source files,
- tests,
- risks,
- and implementation stages.

The graph may be JSON, YAML, GraphML, or another practical repository format.

The important property is navigability, not format.

---

# 9. Visual Twin

The project must have a visual twin that mirrors the architecture and runtime behavior.

You must decide the correct diagram set based on your own architecture.

At minimum, the visual package must make it possible to see:

- the whole system at a glance,
- how major parts connect,
- how data moves,
- where privilege boundaries exist,
- what happens during normal runtime,
- what happens during a performance/stability incident,
- what happens when something fails,
- how recovery/rollback works,
- how the software is built and released,
- and what order the implementation should follow.

Do not create decorative diagrams. Every visual object must map back to a named architectural entity, document, or implementation unit.

---

# 10. Figma or equivalent

If an editable visual-design tool is available, create an editable architecture board.

Figma is acceptable but not mandatory.

If no such tool is available, create repository-native editable diagrams such as:

- Mermaid,
- PlantUML,
- Graphviz/DOT,
- SVG generated from structured sources,
- or another editable format.

Prefer source-controlled visuals over screenshots.

If possible, also generate rendered previews.

---

# 11. Visual traceability

Create a mapping file such as:

`VISUAL_TRACEABILITY.json`

Each visual node should map to at least some of:

- component identifier,
- responsibility,
- source document,
- implementation location,
- related tests,
- current state,
- dependency relationships.

The purpose is to prevent diagrams from drifting away from implementation reality.

---

# 12. Design-decision memory

Every major architecture decision must be recorded in a durable form.

Use ADRs or an equivalent decision-log mechanism.

Each important decision should capture:

- the problem,
- alternatives considered,
- why the decision was chosen,
- evidence level,
- expected benefit,
- tradeoffs,
- failure modes,
- reversal conditions,
- and what future evidence would justify replacing it.

Do not hide rejected alternatives; preserve enough context to avoid repeating the same dead ends later.

---

# 13. Component contracts

For every major implementation unit that survives the final design, document:

- purpose,
- public interface,
- inputs,
- outputs,
- state ownership,
- dependencies,
- concurrency/threading assumptions,
- privilege requirements,
- failure behavior,
- timeout behavior,
- observability,
- safety invariants,
- rollback behavior,
- tests,
- and expected source/module location.

These contracts should be concrete enough that a future coding model can implement the component without re-deriving its intended semantics.

---

# 14. Data contracts

For important records/messages/state objects, define schemas or equivalent precise contracts.

Include examples.

A future model should know field meaning, units, allowed states, optionality, versioning, and compatibility expectations.

Do not store personal content, credentials, signing secrets, or account-sensitive data in examples.

---

# 15. Implementation blueprint

Produce an implementation blueprint that translates architecture into concrete work.

For each build stage, specify as much as is known:

- files/modules to create or change,
- classes/interfaces/services/processes,
- data structures,
- control flow,
- algorithms or pseudocode where useful,
- dependencies,
- privilege level,
- platform APIs or OS interfaces,
- error handling,
- logging/telemetry,
- tests,
- build checks,
- real-device validation,
- entry criteria,
- exit criteria,
- and rollback/recovery behavior.

If a detail depends on an unknown device capability, mark it as unknown and define the observation needed to resolve it. Do not fabricate precision.

---

# 16. Hours-to-working-build plan

The user values extremely short implementation time.

Create a dedicated plan that answers:

- What is the smallest version that produces real user value?
- What can be completed in the first hour?
- What can be completed in the next few hours?
- What work can happen in parallel?
- What is the minimum high-value test set?
- What can safely be deferred?
- Which design choices save implementation time without destroying long-term architecture?
- What would prevent a same-day usable build?

Prefer reversible, observable, low-risk value first.

Do not reduce quality by simply skipping critical validation.

The goal is **minimum path length to a trustworthy working result**, not minimum number of lines or zero testing.

---

# 17. Testing architecture

Create the smallest test architecture that still protects the project from dangerous or expensive mistakes.

Differentiate:

- tests required on every change,
- tests required before device execution,
- tests required before privileged actions,
- tests required before release,
- and experiments that are optional or deferred.

Avoid large test matrices that provide little decision value.

Every important test should have a reason for existing.

---

# 18. Research and evidence package

Preserve important external evidence that materially influenced architecture.

For consequential claims, identify whether they are:

- directly verified on the device,
- supported by primary platform documentation/source,
- supported only by secondary/community evidence,
- inferred,
- or still unknown.

Do not use volume of citations as a proxy for rigor.

---

# 19. Creative idea archive

Do not delete high-potential ideas merely because they did not enter the first build.

Create an innovation archive that records:

- idea,
- mechanism,
- expected user effect,
- why it is interesting,
- why it was not selected now,
- what evidence is missing,
- shortest falsification test,
- and whether it should be revisited.

This preserves creative value without bloating the initial implementation.

---

# 20. Packaging requirement

At the end, create a transfer artifact named approximately:

`FixHUA-Blind-Genesis-Knowledge-Package.zip`

The ZIP must contain the complete knowledge package, including:

- AI/future-model entry file,
- documentation architecture,
- project state,
- architecture documents,
- decision records,
- schemas/examples,
- visual sources,
- rendered visuals if produced,
- traceability maps,
- implementation blueprint,
- hours-to-working-build plan,
- testing/validation plan,
- innovation archive,
- and final mission report.

If the environment supports actual file creation, create the ZIP physically and return its exact path or artifact reference.

If the environment cannot create files, state that limitation explicitly and provide a complete manifest instead of pretending the ZIP exists.

---

# 21. Completion audit

Before packaging, perform a transfer audit from the perspective of a model that knows nothing about the original conversation.

Ask:

- Can it determine what the product is?
- Can it determine what is actually implemented?
- Can it distinguish facts from ideas?
- Can it understand the architecture visually?
- Can it locate the responsibility of each major component?
- Can it understand data flow and privilege boundaries?
- Can it build the project?
- Can it test it?
- Can it know what to implement next?
- Can it detect when a future change contradicts an accepted decision?
- Can it understand which creative ideas are deferred rather than discarded?

If any answer is no, repair the package before finalizing.

---

# Final rule

The documentation architecture must be strong enough to preserve engineering intent, but it must not force the product architecture into a predetermined shape.

**Constrain ambiguity. Do not constrain invention.**
