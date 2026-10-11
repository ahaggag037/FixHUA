# FixHUA Synthetic System — Control Plane v0.1

Status: ACTIVE RESEARCH → BUILD PROGRAM
Branch: `research/synthetic-system-v1`
Canonical project source: GitHub repository state on this branch.

## Mission

Build a real Android product for Huawei devices that maximizes app compatibility and useful privileged capabilities without requiring host root or an unlocked bootloader.

The product is not merely a GBox fixer, a microG fork, or a root tutorial. It is a compatibility and capability orchestration layer that combines Android, Huawei and controlled synthetic-runtime primitives to obtain the requested result with the smallest necessary privilege.

Working name: **FixHUA Synthetic System**.

## Research-to-build rule

Research is only valid if it ends in one or more of:

- `ADOPT` — supported enough to enter architecture.
- `PROTOTYPE` — uncertain but valuable enough for a bounded implementation experiment.
- `DEFER` — blocked by a named dependency.
- `REJECT` — poor fit, unacceptable cost, or architectural dead end.
- `UPDATE` — prior decision changed due to stronger evidence.

No new topic may be opened unless it maps to an unresolved capability, dependency, or product risk in the Capability Graph.

## Product objective

For a target app and requested operation, FixHUA should determine:

1. What exact capability is required?
2. Can stock Huawei/Android already provide it?
3. Can it be delegated through user-authorized Android mechanisms?
4. Can it be granted persistently through legitimate development or system-supported permission paths?
5. Can it be proxied through ADB/shell/Binder authority?
6. Can Huawei/HMS/HDB provide an OEM-authorized route?
7. Can the requirement be replaced with a compatibility shim, safe stub, redirect or service substitution?
8. Can the app be placed in a selective virtual environment where FixHUA controls the required view of the system?
9. Is guest root inside a contained runtime necessary?
10. Is genuine host root / privileged-system integration truly unavoidable?

The resolver must prefer the lowest-cost, lowest-risk route that achieves the user-visible behavior.

## Privilege ladder

- **P0 — Public Android:** ordinary SDK APIs.
- **P1 — User-authorized special access:** VPN, notification listener, usage access, accessibility where justified, roles/settings surfaces.
- **P2 — Persistent delegated capabilities:** legitimate permissions that can remain with FixHUA after bootstrap.
- **P3 — Dynamic shell/Binder capabilities:** operations available to `shell` but not a normal app.
- **P4 — Huawei trusted plane:** documented OEM channels such as HDB/HiSuite/HMS/system-management integrations.
- **P5 — Synthetic reality:** FixHUA-controlled package/service/filesystem/property/identity views inside a managed runtime.
- **P6 — Guest root:** uid 0 / writable virtual system inside a guest while the host remains unrooted.
- **P7 — Host root / privileged-system control:** kernel/SELinux/platform-signature/real-partition authority. Not required for the base product unless no lower layer can produce the needed outcome.

Rule: do not require P7 when the same user-visible capability can be synthesized from P0–P6.

## Architecture hypothesis

```text
Target App / User Request
          |
          v
+----------------------------+
| FixHUA Capability Resolver |
+----------------------------+
          |
    +-----+----------------+----------------+----------------+
    |                      |                |                |
    v                      v                v                v
Host Plane          Bootstrap Plane   Synthetic Plane   Service Plane
Android APIs        dev permissions   virtual PM        HMS
Special access      shell/Binder      virtual FS        microG
Huawei settings     OEM/HDB routes    shims/hooks       GBox
                   (when available)   selective runtime native/web fallback
    |                      |                |                |
    +----------------------+----------------+----------------+
                           |
                      Result + evidence
```

## Fixed research gates

### R1 — Capability decomposition
Deliverable: root-feature matrix split into concrete Android capabilities.
Gate: >=40 capabilities with candidate providers and hard boundaries.

### R2 — Bootstrap and persistence
Study: AOSP development permissions, PackageManager grant semantics, shell authority, Shizuku-like delegation, Huawei constraints.
Gate: separate capabilities that survive one-time bootstrap from those requiring a live broker.

### R3 — Huawei trusted plane
Study: HDB/HiSuite/HMS/Optimizer/System Manager/PowerGenie/documented OEM surfaces.
Gate: identify usable OEM capabilities and mark proprietary/dependent ones.

### R4 — Synthetic reality
Study: selective virtualization, virtual PackageManager, filesystem indirection, process/service routing, app-facing compatibility interception.
Gate: choose one maintainable prototype strategy and reject unsuitable frameworks.

### R5 — Google compatibility
Study: microG-Huawei differentiated-signature path, GrapheneOS GmsCompat patterns, native GMS vs microG vs substitution.
Gate: identify which shims can live at app/runtime layer and which require ROM changes.

### R6 — Recipe + resolver
Deliverable: compatibility recipe schema and deterministic capability-resolution algorithm.
Gate: resolver can select host/delegated/synthetic/fallback providers without ad-hoc chains.

### R7 — MVP architecture freeze
Deliverable: modules, interfaces, IPC, persistence, safety, CI/test plan.
Gate: no unresolved primitive blocks implementation.

### R8+ — Implementation
Research continues only when a concrete failing capability needs new evidence.

## MVP-1 success criteria

A real installable Android app containing:

- capability detection engine;
- capability resolver;
- Huawei/Android host profile;
- compatibility recipe format;
- at least one ordinary Android backend;
- at least one elevated-but-non-root backend;
- at least one compatibility shim/substitution path;
- per-app routing with explainable reasons;
- safe fallback behavior;
- local evidence/reporting for every applied action.

MVP-1 does not need to solve every application and does not require host root.

## Anti-drift rules

- No research branch without an explicit unresolved capability or risk.
- Every research finding must change a decision, experiment, capability edge, or rejection entry.
- Repeated research on the same question requires new evidence, not new wording.
- Build readiness is judged by unresolved primitives, not by number of ideas collected.
- Once MVP-1 gates are satisfied, implementation takes priority over further broad research.
- Experimental mechanisms stay isolated behind interfaces until verified.

## Plugin / tool policy

- GitHub is the canonical executable state because this is a software project.
- MASTER-OS may be used as a runtime control/recovery overlay when its bridge is connected, but it must not become a conflicting source of truth.
- Additional plugins (Notion, Google Drive, etc.) may be used only when they materially reduce state loss or improve human review. They are mirrors/supporting views, never competing canonical state.
- External research sources are evidence, not project state.
