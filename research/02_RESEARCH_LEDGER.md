# FixHUA Research Ledger v0.1

Purpose: prevent idea drift. Every meaningful research result must become a decision with evidence, dependency and next action.

## Decision states

- `ADOPT` — use in architecture now.
- `PROTOTYPE` — build a bounded proof before adopting.
- `DEFER` — keep, but only reactivate on named trigger.
- `REJECT` — do not spend more research time unless new evidence appears.
- `UPDATE` — supersedes a prior entry.

---

## RL-001 — Root is not a binary product requirement

**State:** ADOPT

**Decision:** Treat root as a bundle of concrete capabilities. Resolve each feature through the lowest privilege layer able to provide the user-visible outcome.

**Why:** Android exposes materially different authority tiers (ordinary app, special access, shell, OEM channels, synthetic runtime, guest root, host root). Treating all of them as “root/no-root” loses viable design space.

**Depends on:** `01_CAPABILITY_GRAPH.md`

**Next action:** Keep capabilities individually testable and provider-swappable.

---

## RL-002 — GBox is a backend, not the project center

**State:** ADOPT

**Decision:** GBox is one runtime/fallback provider inside the resolver. FixHUA must not optimize only GBox.

**Why:** Compatibility failures can originate from Huawei/EMUI policies, HMS, Google-service assumptions, WebView, package identity, background execution, account/push/network requirements or the app itself.

**Next action:** All GBox-specific work must map to a capability ID.

---

## RL-003 — microG is a provider, not a universal solution

**State:** ADOPT

**Decision:** microG is one Google-compatibility backend. It may be native, Huawei-specific, or hosted in a managed runtime depending on app requirements.

**Why:** Some apps need only subsets of GMS; some require signature expectations or privileged behavior that microG alone cannot provide on stock EMUI.

**Next action:** R5 compares microG, genuine sandboxed GMS, service substitution and GBox per capability.

---

## RL-004 — Huawei differentiated signature is an architectural primitive

**State:** PROTOTYPE

**Decision:** Reproduce the *concept* of differentiated/app-specific identity inside FixHUA’s managed runtime when host EMUI does not expose the Huawei primitive.

**Why:** Huawei and microG-HW demonstrate that compatibility can depend on the identity/signature view presented to a caller rather than changing the APK itself.

**Boundary:** Do not claim the host package signature changed. This is app-facing synthetic reality only.

**Next action:** R4 defines a virtual PackageManager interface and identity profile.

---

## RL-005 — GrapheneOS GmsCompat pattern is highly relevant

**State:** ADOPT (pattern), PROTOTYPE (implementation transfer)

**Decision:** Use GmsCompat as evidence for a compatibility strategy: selective stubs, result shaping, feature disabling and service shims can allow unprivileged Google components to function without granting every expected system privilege.

**Boundary:** GrapheneOS integrates hooks into its OS/framework. Stock EMUI cannot reuse ROM-level hooks directly.

**Next action:** R5 classify GmsCompat techniques into: app-layer transferable, managed-runtime transferable, ROM-only.

---

## RL-006 — One-time bootstrap is preferable to permanent computer dependency

**State:** PROTOTYPE

**Decision:** Design an optional bootstrap plane where a user-authorized shell session can grant or initialize capabilities that persist legitimately after the desktop disconnects.

**Candidate examples:** Android `development` permissions such as DUMP / READ_LOGS / WRITE_SECURE_SETTINGS where the OEM build permits them.

**Boundary:** No assumption that every AOSP development permission is grantable on EMUI 13. Must be proven capability-by-capability.

**Next action:** R2 builds a grantability/persistence matrix from Android source semantics and Huawei constraints.

---

## RL-007 — Shizuku-style Binder delegation is dynamic privilege, not root

**State:** ADOPT as architecture pattern; PROTOTYPE on target environment

**Decision:** A shell/Binder broker is the preferred provider for capabilities that shell can perform but cannot be persisted safely in FixHUA itself.

**Boundary:** shell authority is below root and is OEM/SELinux dependent.

**Next action:** Define `PrivilegedBackend` interface independent of Shizuku so other brokers can be substituted.

---

## RL-008 — Wireless-debugging-dependent architecture is unsuitable for current EMUI target

**State:** REJECT for base path

**Decision:** Do not make wireless debugging a mandatory runtime dependency for FixHUA on the current Huawei target.

**Why:** EMUI availability differs and the current project should survive without a permanently active wireless ADB session.

**Next action:** Bootstrap once where needed; retain optional dynamic USB/shell backend.

---

## RL-009 — Device Owner is not the base architecture

**State:** DEFER

**Decision:** Do not require Device Owner provisioning for the existing user device.

**Why:** powerful but disruptive provisioning/reset constraints make it a poor default for an already configured personal phone.

**Trigger to revisit:** dedicated secondary device, managed-device edition, or enterprise variant.

---

## RL-010 — Huawei HDB/HiSuite is a distinct OEM trust plane

**State:** PROTOTYPE / research

**Decision:** Treat HDB/HiSuite as a separate provider candidate rather than assuming all elevated Huawei operations flow through ADB.

**Why:** Huawei documents application management, system recovery/updates and backup/restore behavior through HDB/HiSuite, including app data categories unavailable to ordinary apps.

**Boundary:** proprietary desktop mediation may make direct in-app use impossible or unsuitable.

**Next action:** R3 map documented capabilities, trust/consent flow and whether any reusable on-device API exists.

---

## RL-011 — Full Android VM is not the first implementation

**State:** DEFER

**Decision:** Do not begin by embedding a full virtual Android system.

**Why:** high memory/storage/startup cost and broad compatibility surface. Use selective virtualization first.

**Trigger to revisit:** a high-value capability set remains impossible with host + delegated + selective synthetic layers.

---

## RL-012 — Selective virtualization is the core novel bet

**State:** PROTOTYPE

**Decision:** Build toward a Split-Reality Runtime that virtualizes only the system views an app needs (package identity, service responses, filesystem/property views, intent routing) while delegating hardware and ordinary Android functions to the real host.

**Why:** reduces overhead compared with full VM and avoids requiring host framework modification for app-specific compatibility views.

**Next action:** R4 chooses the smallest viable interception/runtime mechanism.

---

## RL-013 — Compatibility recipes must be data, not hard-coded branches

**State:** ADOPT

**Decision:** Per-app behavior should be represented as versioned recipes describing required capabilities, preferred providers, shims, fallbacks and known incompatibilities.

**Why:** prevents an unmaintainable accumulation of app-specific `if` statements.

**Next action:** R6 defines schema, validation and precedence.

---

## RL-014 — Safe stubs are valid only with known semantics

**State:** ADOPT

**Decision:** Never return generic “success” merely to bypass a check. A stub is allowed only when the caller’s expected semantics are understood and the neutral/failure-safe value preserves correctness.

**Why:** fake success can silently corrupt application state and create harder failures.

**Next action:** recipe schema must distinguish `stub-safe`, `redirect`, `deny`, and `unsupported`.

---

## RL-015 — Keep host truth separate from synthetic app perception

**State:** ADOPT

**Decision:** The product UI/logs must explicitly distinguish:

- host fact,
- delegated host operation,
- synthetic runtime view,
- guest state.

**Why:** prevents false claims such as “device is rooted” when only guest/synthetic root exists.

**Next action:** every backend result returns `authorityPlane` + `evidence` + `scope`.

---

## RL-016 — Genuine host-root boundaries remain boundaries

**State:** ADOPT

**Decision:** Host SELinux modification, real UID 0, real protected partition writes, arbitrary platform-signature grants, kernel modification and global system_server replacement are not to be mislabeled as achievable by Synthetic Root.

**Next action:** resolver treats these as P7 and seeks alternative formulations before declaring a feature blocked.

---

## RL-017 — Project research stopping rule

**State:** ADOPT

**Decision:** Broad research stops when R1–R7 gates are satisfied. From that point, new research is allowed only to unblock a failing implementation capability or respond to new evidence.

**Why:** avoids endless “interesting idea” accumulation.

**Next action:** track each session against one gate only.

---

## Open questions queue

Only these questions are authorized as broad research topics until resolved or explicitly superseded:

1. Which AOSP development permissions are practically grantable and persistent on stock Huawei/EMUI 13?
2. Which useful shell capabilities survive Huawei SELinux/OEM restrictions?
3. What exactly does the Huawei HDB/HiSuite trust plane expose, and can any portion be reused on-device?
4. Which selective-virtualization framework/primitives remain maintainable on Android 12+ and can be reduced to the needs of FixHUA?
5. Which GmsCompat techniques are transferable without ROM modification?
6. What is the smallest viable virtual PackageManager / service-shim proof?
7. How should compatibility recipes express required capabilities, safe shims and fallback ordering?
8. What first vertical slice demonstrates measurable value on a Huawei phone without requiring host root?

Any new research question must first explain which capability or gate it unblocks.
