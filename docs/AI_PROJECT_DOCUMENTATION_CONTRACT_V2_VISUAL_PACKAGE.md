# FixHUA — AI Project Documentation Contract V2
## Visual Knowledge Architecture + Implementation Memory + Transfer Package

> This V2 extends `docs/AI_PROJECT_DOCUMENTATION_CONTRACT.md`.
> Its purpose is to make the project understandable not only as text, but as a **visual, navigable engineering system** that a future model can reconstruct quickly and accurately.

---

# 1. Prime rule

The final documentation package is not a pile of Markdown files.

It must behave like a **project knowledge architecture** with:

- a single entry point,
- explicit source-of-truth ownership,
- machine-readable state,
- cross-linked documents,
- diagrams that mirror the written architecture,
- implementation contracts,
- evidence and uncertainty labels,
- visual execution flows,
- rollback and failure maps,
- and a packaged ZIP artifact that can be transferred to another model.

The package should allow a new model to answer, without guessing:

1. What exists now?
2. What is proposed only?
3. What is the final architecture?
4. Why was each major decision made?
5. What files/classes/modules must be created or changed?
6. How do components communicate?
7. What data moves between them?
8. What is safe to automate?
9. What must never be changed casually?
10. What is the shortest path to a working build?

---

# 2. Knowledge Graph Architecture

Design the documentation itself as a directed knowledge graph.

Every major document must declare:

- what facts it owns,
- what other documents depend on it,
- which code/modules it maps to,
- which ADRs justify it,
- which tests validate it,
- which diagrams visualize it,
- and whether its content is `IMPLEMENTED`, `VERIFIED`, `PROPOSED`, `EXPERIMENTAL`, `REJECTED`, or `UNKNOWN`.

Do not duplicate authoritative facts across many files. Link instead.

Create a machine-readable relationship index such as:

`docs/project/KNOWLEDGE_GRAPH.json`

Each node should include:

- `id`
- `type`
- `title`
- `path`
- `status`
- `source_of_truth_for`
- `depends_on`
- `validated_by`
- `visualized_by`
- `implemented_by`
- `supersedes`

This file exists so future models can navigate the project programmatically.

---

# 3. Visual Twin Requirement

The project must have a **Visual Twin** that mirrors the architecture.

The model must produce diagrams for at least:

1. **System Context** — FixHUA, Android framework, kernel, Huawei services, apps, root layer.
2. **Component Architecture** — APK, Brain Engine, telemetry, capability probe, policy engine, root agent, storage, UI.
3. **Runtime Data Flow** — signals → classification → decision → action → verification → rollback.
4. **Privilege Boundaries** — normal app vs Shizuku/ADB vs root/systemless.
5. **Memory/App Survival Flow** — app state, LMKD, zRAM, OEM policy, exit attribution.
6. **Performance Incident Flow** — lag/jank event → black-box snapshot → attribution → intervention decision.
7. **Intervention Lifecycle** — PROPOSED → SHADOW → CANARY → VERIFIED → ACCEPTED/REVERTED.
8. **Failure & Recovery** — daemon failure, stale rule, thermal regression, boot-safe recovery.
9. **Build & Release Pipeline** — source → tests → build → signing → artifact → installation.
10. **Implementation Dependency Graph** — what must be built before what.

---

# 4. Figma / Editable Visual Board

If a Figma-capable connector/tool is available, create an editable board named approximately:

`FixHUA System Brain — Architecture & Build Map`

Recommended pages/frames:

- `00 Overview`
- `01 System Context`
- `02 Runtime Architecture`
- `03 Telemetry & Signals`
- `04 Decision Engine`
- `05 Root/Privilege Boundaries`
- `06 App Preservation`
- `07 Incident & Rollback Flows`
- `08 Implementation Roadmap`
- `09 UI/Product Flow`
- `10 Open Questions`

Every visual component should reference the corresponding document/component ID.

If Figma is unavailable, produce editable alternatives inside the ZIP:

- Mermaid source (`.md` / `.mmd`)
- SVG exports
- optional PlantUML or Graphviz source
- a lightweight HTML architecture viewer if useful

Do not return screenshots only. The visual source must remain editable.

---

# 5. Visual-to-Code Traceability

Every important box or node in the architecture diagrams must map to:

`VISUAL_NODE_ID → COMPONENT_CONTRACT → SOURCE_PATH → TESTS → STATUS`

Store this mapping in:

`docs/project/VISUAL_TRACEABILITY.json`

This prevents diagrams from becoming decorative and stale.

---

# 6. Implementation Memory

The documentation must explain **how to build the system**, not only what it is.

For every major component document:

- responsibility
- public interface
- inputs/outputs
- data structures
- thread/process model
- lifecycle
- failure behavior
- privilege requirements
- dependencies
- exact source paths to create/update
- expected class/module names
- pseudocode or algorithm sketch
- logging/telemetry
- configuration
- unit tests
- integration tests
- device validation
- rollback behavior
- completion criteria

The package should allow a future coding model to implement a component without reconstructing design intent from old conversations.

---

# 7. Hours-to-Working-System Principle

The architecture must be optimized for **time-to-value**, not theoretical completeness.

Target a first useful, installable, testable system in **hours of focused work**, not weeks.

This does not mean skipping essential safety validation.

Use this priority rule:

`value / implementation_time / risk`

Prefer mechanisms that:

- reuse Android/AOSP capabilities already present,
- require few dependencies,
- can be implemented in one module,
- produce immediate diagnostic or performance value,
- are measurable,
- are reversible,
- do not require custom kernels or large native stacks for v1.

Defer features that require days of infrastructure unless they are absolutely essential.

Create a document:

`docs/project/27_HOURS_TO_WORKING_BUILD.md`

It must define:

- the smallest useful build,
- the shortest dependency chain,
- exact implementation sequence,
- time estimate per step,
- what can be parallelized,
- minimum high-signal tests,
- what is explicitly deferred,
- and the criterion for saying `READY_FOR_REAL_DEVICE_TEST`.

Do not claim exact completion time if evidence is insufficient; give a bounded engineering estimate and identify blockers.

---

# 8. Minimum-Test, Maximum-Signal Rule

The objective is not the fewest tests possible; it is the **fewest tests that catch the highest-risk failures**.

Create a risk-ranked test matrix.

For v1, prioritize:

- build succeeds,
- app launches,
- capability probe is read-only,
- unsupported interfaces fail closed,
- no unsafe root write path exists,
- rollback works for every implemented write action,
- thermal safety stops intervention,
- stale policy is not silently reused after version/environment change,
- package/update/signing path is reproducible,
- one real-device before/after measurement can be collected.

Avoid large low-value test suites that delay the first working build without changing confidence materially.

---

# 9. Innovation Without Fear

The model is explicitly encouraged to propose mechanisms that are:

- unconventional,
- not widely used by optimizer apps,
- new combinations of known primitives,
- or genuinely novel hypotheses.

Do not reject an idea merely because it is not already common practice.

However, distinguish:

- `NOVEL_HYPOTHESIS`
- `PLAUSIBLE_MECHANISM`
- `PLATFORM_SUPPORTED`
- `DEVICE_CONFIRMED`

A new idea may enter the architecture if it has:

- a plausible causal mechanism,
- a measurable signal,
- a realistic implementation path,
- bounded risk,
- and an experiment capable of falsifying it.

If a radical idea is too risky for automation, preserve it as a research experiment or shadow-mode feature rather than deleting it.

---

# 10. Package Output Contract

At mission completion, generate a transfer package:

`FixHUA-System-Brain-Knowledge-Package.zip`

The ZIP should contain at least:

- all `docs/project/` documents,
- all ADRs,
- schemas,
- examples,
- diagram sources and exports,
- `AGENTS.md`,
- `PROJECT_STATE.json`,
- `KNOWLEDGE_GRAPH.json`,
- `VISUAL_TRACEABILITY.json`,
- independent design snapshot,
- existing-research audit,
- final architecture,
- implementation blueprint,
- hours-to-working-build plan,
- final mission report,
- a `PACKAGE_MANIFEST.md`,
- and a checksum manifest when practical.

If the environment permits file generation, the model must **actually create the ZIP** and provide its exact path or downloadable artifact reference.

If the environment cannot create files, it must state that limitation explicitly and output the exact directory/file structure and content needed to reconstruct the ZIP. Never pretend the ZIP exists.

`PACKAGE_MANIFEST.md` must state:

- generated timestamp
- source repository/branch/commit
- document versions
- completeness status
- known missing artifacts
- diagram format/Figma reference if any
- checksum information if generated

---

# 11. Transfer-to-Next-Model Readiness Test

Before packaging, simulate a new model entering the repository with no conversation history.

Verify that the package answers:

- What is FixHUA?
- What is actually implemented?
- What is only proposed?
- What should I read first?
- What should I build next?
- What must I not change casually?
- How do I know whether a device capability exists?
- How is every intervention validated and rolled back?
- What is the shortest path to a working release?
- Where are the diagrams that explain the whole system?

If any answer requires hidden context, the package is incomplete.

---

# 12. Final package quality bar

The final package must be:

- precise enough for implementation,
- visual enough for architectural review,
- compact enough to navigate,
- explicit about unknowns,
- traceable from goal to code/test,
- safe against future model hallucination,
- and optimized for rapid continuation.

The goal is not maximum page count.

The goal is **minimum ambiguity per unit of documentation**.
