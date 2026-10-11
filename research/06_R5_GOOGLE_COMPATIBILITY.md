# R5 — Google Compatibility Plane

Status: GATE DECISION READY
Branch: `research/synthetic-system-v1`
Date: 2026-10-11

## Research question

For FixHUA's Synthetic Runtime, determine which Google compatibility techniques can be implemented at app/runtime level on stock EMUI, which require ROM/framework modification, and which Google backend should be used first.

## Core finding

Google compatibility is not one capability. It is a graph of:

- package identity / signature expectations;
- caller UID/package identity;
- GMS service-broker bindings;
- account/auth flows;
- push registration and delivery;
- location APIs;
- package/install-source queries;
- privileged Android APIs that GMS assumes are available;
- feature flags that select privileged vs unprivileged code paths;
- process/background-lifetime assumptions;
- Play Store / installer integration;
- device integrity / attestation boundaries.

FixHUA must therefore expose a `GoogleCompatProvider` contract rather than hard-code "use microG" or "use GBox".

## Evidence: GrapheneOS Sandboxed Google Play

GrapheneOS demonstrates that official Google Play components can run as ordinary sandboxed apps when the OS supplies a deep compatibility layer. Their current GmsCompat code/config uses several distinct techniques:

1. **Package/application result shaping**
   - adjust `ApplicationInfo` flags;
   - synthesize/modify package records;
   - filter queries requiring privileged cross-user flags;
   - hide or pseudo-disable selected packages.

2. **Safe stubs**
   - return neutral values for privileged methods when the feature can degrade safely.

3. **Feature-path steering**
   - override GMS/Phenotype flags to disable modules or force code paths that do not require unavailable privileges.

4. **Service substitution / redirection**
   - reroute location and other functions to compatibility implementations.

5. **Process-model repair**
   - compensate for assumptions about process visibility and persistent-process lifetime.

6. **Framework-integrated hooks**
   - GrapheneOS can hook/replace framework classes such as PackageManager behavior because it owns the ROM.

The first five are useful architectural patterns. The sixth cannot be copied directly onto stock EMUI.

## Transferability classification

### A — Directly transferable to TSR

These can be implemented inside FixHUA's managed runtime without modifying the host ROM:

- synthetic `PackageInfo` / `ApplicationInfo` results;
- virtual package visibility and pseudo-package records;
- virtual install-source metadata inside the runtime;
- safe method stubs with known semantics;
- service redirection to FixHUA/HMS/microG backends;
- feature-flag / configuration-result shaping when the query passes through TSR;
- virtual process registry and process-state answers;
- virtual permission/capability answers **only for synthetic-runtime semantics**;
- WebView per-process data-directory handling;
- runtime-specific package/signature identity views;
- compatibility crash collection inside managed processes.

### B — Transferable only with TSR transformation/interception

These are not globally available on stock EMUI but can be redirected when target code is transformed or all calls pass through FixHUA:

- static method calls such as selected `Build`/identity APIs;
- ContentResolver queries used for GMS configuration;
- selected system-service method calls;
- calls that require a synthetic package manager instead of the host PM;
- feature checks that select privileged modules;
- component/intents targeting virtual Google packages.

### C — Host delegated / shell provider required

These require real host authority and cannot be truthfully synthesized when the operation must affect the host:

- selected AppOps / settings changes;
- package install/uninstall operations;
- diagnostics or dumps outside FixHUA processes;
- selected background/power policy changes;
- package management beyond normal public APIs.

Use P2/P3 providers where allowed; synthetic answers must not claim the host action occurred when it did not.

### D — ROM/framework-only or inherently privileged

These remain outside the base stock-EMUI runtime:

- globally replacing framework PackageManager behavior for normal installed apps;
- arbitrary platform-signature permissions;
- system_server global hooks;
- protected hardware/system services whose semantics require OEM/system privilege;
- global UID/SELinux changes.

GrapheneOS can implement these because it owns the OS. FixHUA cannot on a stock locked host.

### E — Explicit integrity boundary

Do not make Play Integrity, banking integrity, DRM or anti-cheat bypass a product objective. If a target requires trusted host/package attestation, report the integrity boundary and select a legitimate fallback path.

## Evidence: microG

microG is Apache-2.0 open source and is the best first Google-service implementation candidate, but stock-host integration has two hard requirements that matter for FixHUA:

### 1. Signature view

microG documentation states that normal drop-in use requires ROM signature-spoofing support. Huawei-specific microG code includes a `com.huawei.signature.diff.SignatureService` whose Binder API returns a per-package real/fake signature decision. This validates FixHUA's app-specific identity concept.

On stock EMUI where Huawei's differentiated-signature primitive is absent/unusable, FixHUA can reproduce the **runtime-visible** signature view inside TSR instead of changing the host framework.

### 2. Caller identity

Current microG code frequently relies on Binder caller identity. Examples include:

- deriving the caller package from `Binder.getCallingUid()` / PID;
- checking that a requested package belongs to the calling UID;
- building client identity from calling UID/PID;
- permission/AppOps checks against that identity.

Therefore a target app running virtually under the FixHUA host UID cannot transparently call an external host microG instance and expect complete behavior. microG would see FixHUA's real UID rather than the target's virtual UID.

This becomes a first-class R5 requirement:

## Virtual Caller Identity (VCI)

TSR must track an immutable virtual identity record per managed app:

```text
VirtualClientIdentity
- virtualPackageName
- originalSigningInfo
- syntheticSigningView
- virtualUid
- virtualPid/processId
- requestedPermissions
- grantedSyntheticCapabilities
- hostUid
- runtimeInstanceId
```

Every synthetic service request must carry/derive VCI. The provider must distinguish:

- host caller identity (truth);
- virtual caller identity (runtime contract);
- delegated host operation authority.

No provider may silently use host UID as if it were the target app.

## R5 provider decision

### Primary open provider: Managed microG

**State: PROTOTYPE, not yet implementation-frozen.**

Use microG as the first open Google-service backend, but design it as a **managed runtime provider**, not as a magical host-global replacement.

Preferred long-term forms, in order:

1. **TSR-managed microG service plane** — microG components run within/alongside the managed runtime and receive VCI-aware calls.
2. **FixHUA microG bridge** — a narrowly modified/integrated microG provider that accepts authenticated virtual-client identity from FixHUA while preserving host truth.
3. Host-installed microG only where caller-identity-sensitive APIs are proven not to require virtualization.

Do not assume option 3 is sufficient globally.

### Genuine Google Play provider

**State: DEFER.**

GrapheneOS proves this is possible with an OS compatibility layer, but on stock EMUI it is substantially harder because its official implementation relies on framework integration. Genuine GMS is also proprietary, so FixHUA must not redistribute it.

Revisit only after TSR can host complex multi-component service packages and recipe-driven shims.

### GBox provider

**State: ADOPT as fallback backend.**

GBox remains useful for applications that need a broader Google-like environment than the current synthetic provider can supply. It is not the canonical Google plane.

## Compatibility-shim taxonomy for R6 recipes

R5 defines the following recipe actions:

- `PASS_THROUGH` — use host Android result.
- `SYNTHETIC_VALUE` — provide runtime-only value with explicit scope.
- `SAFE_STUB` — known-safe neutral behavior.
- `REDIRECT_SERVICE` — route to HMS/microG/FixHUA/other provider.
- `FILTER_FLAGS` — remove unsupported privileged query flags.
- `SHAPE_RESULT` — transform a host result into runtime-compatible form.
- `DISABLE_SUBFEATURE` — steer target away from an unavailable privileged feature.
- `VIRTUAL_IDENTITY` — answer using VCI/package/signature view.
- `HOST_DELEGATE` — execute through P2/P3 provider and return real result.
- `UNSUPPORTED_INTEGRITY_BOUNDARY` — stop and explain; do not fake success.

## Google vertical slice for MVP

The first Google-related proof should **not** attempt full Play Store or full GMS.

Target a small, deterministic slice:

1. Synthetic PM reports a configured `com.google.android.gms` package identity to a test client.
2. Target client sees a recipe-selected signing view inside TSR.
3. A simple Google-like Binder/service request is routed through a VCI-aware FixHUA service.
4. One capability uses a safe stub and one uses a real host redirect.
5. Evidence log shows real host identity vs virtual target identity separately.

Once this works, microG integration can replace the simple provider incrementally.

## R5 gate result

**PASS for research/design.**

Adopt:

- provider-based Google architecture;
- GrapheneOS compatibility patterns, not its ROM hooks;
- runtime-level differentiated signature view;
- Virtual Caller Identity as a core primitive;
- microG as the first open managed Google backend;
- GBox as fallback.

Defer:

- official Google Play inside TSR;
- full Play Store lifecycle;
- integrity-sensitive functionality.

## Dependencies carried forward

R6 recipe/resolver must encode:

- provider choice per capability;
- VCI requirements;
- shim type;
- host-vs-synthetic scope;
- safety semantics;
- fallback order;
- integrity-boundary behavior.
