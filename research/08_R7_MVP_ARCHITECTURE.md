# R7 — MVP Architecture Freeze

Status: **FROZEN FOR MVP-1 IMPLEMENTATION**
Branch: `research/synthetic-system-v1`
Date: 2026-10-11

This document closes broad architecture research for MVP-1. New broad research is no longer allowed unless an implementation step is blocked by a concrete capability or new evidence invalidates a frozen assumption.

## 1. Security principle discovered during R7

**Never dynamically execute imported target-app code inside the privileged FixHUA Controller UID.**

Android dynamically loaded code executes with the permissions and authority of the hosting application. The Controller may later possess user-authorized or bootstrapped P1/P2/P3 capabilities. Loading arbitrary target code in that UID would let imported code inherit the Controller's authority.

Therefore Controller and Synthetic Runtime are separate Android packages with separate Linux UIDs.

This is a hard architectural boundary.

## 2. Minimal Gradle/module topology

MVP-1 intentionally uses only four modules:

```text
:app            Controller application — com.fixhua.guard
:runtime        Synthetic Runtime application — com.fixhua.runtime
:core           Pure-Java model/resolver/recipe/evidence library
:sample-target  Internal deterministic test APK used only for CI/runtime acceptance
```

Do not split into additional Gradle modules until compilation/test pressure justifies it. Internal Java packages provide separation inside each module.

## 3. Controller application (`:app`)

Package/application ID: `com.fixhua.guard`

Responsibilities:

- UI and user consent;
- `DeviceProfile` / Huawei profile;
- capability detection;
- `CapabilityResolver`;
- validated `RecipeStore`;
- `ProviderRegistry`;
- P0/P1 Android providers;
- future P2 bootstrap-granted providers;
- future P3 shell/Binder provider;
- P4 Huawei/HMS providers;
- local `EvidenceStore`;
- runtime-session creation and revocation;
- immutable resolution plans;
- runtime IPC policy enforcement.

### Controller rule

Controller may execute host-changing operations only through a registered provider selected by the resolver. It never accepts arbitrary shell commands, reflection targets, intents or Binder transaction codes from Runtime.

## 4. Synthetic Runtime application (`:runtime`)

Application ID: `com.fixhua.runtime`

Separate Linux UID from Controller.

MVP permissions: minimum possible. Do **not** grant P2/P3 privileges to Runtime. Any host operation above its normal sandbox is mediated by Controller.

Responsibilities:

- receive an imported APK via read-only file descriptor / immutable artifact reference;
- create private transformed execution image;
- DEX transformation;
- class/resource loading;
- `ProxyActivity` and delegated lifecycle;
- `SyntheticContext`;
- `SyntheticPackageManager` / package-result shaping;
- synthetic service registry;
- Virtual Caller Identity;
- runtime-local virtual app data;
- runtime evidence emission;
- request pre-authorized host capabilities from Controller.

### Runtime truth rule

Runtime may alter app perception, never host truth. Every synthetic response carries `SYNTHETIC_VIEW` scope.

## 5. Imported-code threat model

Imported target code is treated as **potentially untrusted**, even when selected by the user.

A separate Runtime UID prevents it from inheriting Controller permissions, but target code shares Runtime's UID and can potentially inspect Runtime process memory/classes/files available to that UID.

Therefore:

- Runtime itself holds no powerful Controller permission.
- Controller exposes only a narrow capability service.
- Runtime cannot submit arbitrary operations.
- every runtime session is bound to an immutable `ResolutionPlan` created by Controller;
- Controller authorizes requests only when `(runtimeSessionId, targetArtifactHash, capabilityId, operation shape)` matches the active plan;
- no free-form command strings;
- no raw shell/Binder handles;
- no generic "execute intent as Controller" endpoint;
- destructive actions cannot be pre-authorized silently and still require Controller-side per-action consent.

P0 supports a trusted purpose-built sample target. Security hardening for hostile arbitrary production APKs remains a pre-production gate, not a reason to put target code in Controller.

## 6. Controller ↔ Runtime IPC

### Family-app authentication

Both APKs are signed with the same stable FixHUA signing certificate.

Controller defines a custom `signature` permission:

`com.fixhua.guard.permission.RUNTIME_BRIDGE`

Runtime requests it. Exported bridge services require it.

Controller additionally validates Binder caller UID and package signing certificate at runtime. String extras such as `callerPackage` are never trusted as identity.

### Stable signing requirement

The old CI pattern of generating a temporary signing key for every build is **not acceptable** for the final multi-APK architecture because signature-level IPC depends on stable signing identity and updates must preserve signatures.

CI policy:

- test/debug builds may use the same deterministic CI/debug key for both family APKs;
- release artifacts require a stable release key supplied through GitHub Actions secrets or another protected signing path;
- private key material is never committed to the repository.

### IPC API v0

Keep Binder surface deliberately small:

```text
IRuntimeControl
- openSession(SessionDescriptor) -> RuntimeSession
- installPlan(sessionId, planDigest, planEnvelope)
- closeSession(sessionId)
- queryRuntimeHealth(sessionId)

IHostCapabilityBroker
- executeAuthorized(sessionId, capabilityId, requestEnvelope) -> CapabilityResult

IRuntimeEvidenceSink
- appendEvidence(sessionId, EvidenceEnvelope)
```

No arbitrary command API exists.

For MVP, hand-written Binder or AIDL is acceptable. Payloads use fixed primitive/Parcelable schemas, bounded sizes and explicit version fields. Do not deserialize arbitrary target-defined Parcelable classes across the trust boundary.

## 7. Runtime session authorization

Controller owns all session truth.

```text
RuntimeSessionRecord
- sessionId
- runtimePackage
- runtimeUid
- targetArtifactSha256
- recipeId + recipeVersion
- resolutionPlanDigest
- allowedCapabilities
- createdAt
- expiresAt / revocation state
```

For every broker request Controller verifies:

1. Binder caller UID is the expected Runtime UID.
2. Runtime package signing certificate is trusted.
3. session is active.
4. artifact hash matches session.
5. capability is present in frozen ResolutionPlan.
6. request shape matches provider contract.
7. truth scope is allowed.
8. consent/destructive gates are satisfied.

A target app cannot gain a new Controller capability merely by constructing another request.

## 8. Storage model

### Controller private store

- device profile;
- recipe database;
- provider capability cache;
- immutable ResolutionPlans;
- evidence index;
- original imported artifact metadata and SHA-256;
- bootstrap capability state.

### Runtime private store

- execution images;
- virtual app state;
- transformed DEX/resources;
- runtime recipe/plan snapshot containing only data needed by Runtime;
- local synthetic evidence queue.

### Content-addressed execution image

Transformed image identity:

```text
SHA256(
  originalApkHash ||
  transformEngineVersion ||
  recipeDigest ||
  runtimeVersion
)
```

Changing any input creates a new image. Original APK is never overwritten.

Android 14+ dynamic-code requirements are anticipated now: generated/loaded execution artifacts are made read-only before class loading. The Android 12 target does not yet require this enforcement, but using the stricter rule now reduces future migration risk.

## 9. TSR process model

MVP-1:

- Controller process: `com.fixhua.guard`.
- Runtime process/package: `com.fixhua.runtime` separate UID.
- one active managed target per Runtime instance for P0/P1 implementation simplicity.
- `ProxyActivity` is a real Runtime activity declared in Runtime manifest.
- transformed target lifecycle is delegated inside `ProxyActivity`.
- public APIs first for archive parsing, DEX loading and resource loading.
- all hidden/non-SDK access, if proven necessary later, is isolated behind one versioned `PlatformAdapter` package.

### Future isolation option

A no-permission `isolatedProcess` worker may later be used for hostile parsing/transformation work. It is not required for P0 because arbitrary Activity execution cannot live entirely in an isolated Service and the Runtime UID is already intentionally low-privilege.

## 10. `:core` library

Pure Java where possible so deterministic logic is testable without emulator.

Packages:

```text
com.fixhua.core.model
  CapabilityId
  TruthScope
  AuthorityPlane
  ProviderSemantics
  ProviderDescriptor
  CapabilityRequest
  CapabilityResult
  ResolutionPlan
  ExecutionEvidence
  VirtualClientIdentity

com.fixhua.core.recipe
  Recipe
  RecipeValidator
  RecipeSelector

com.fixhua.core.resolve
  CapabilityResolver
  DependencyGraph
  ResolutionFailure

com.fixhua.core.security
  PlanDigest
  SessionPolicy
```

No Android Context in resolver/model classes.

## 11. Provider registry

Controller provider families:

```text
P0 AndroidPublicProvider
P1 UserAccessProvider
P2 BootstrapPermissionProvider       [initially probe-only]
P3 ShellBinderProvider               [interface first; implementation later]
P4 HuaweiHostProvider / HmsProvider
P5 RuntimeSyntheticProvider          [advertises Runtime-side capabilities]
GBoxFallbackProvider
```

Each provider implements the frozen R6 descriptor/preflight/execute contract.

Runtime provider families:

```text
SyntheticPackageProvider
SyntheticIdentityProvider
SyntheticServiceProvider
HostDelegationClient
GoogleCompatProvider                 [simple MVP stub first, microG later]
```

## 12. Bootstrap interface

Bootstrap is optional for MVP base launch and never grants power to Runtime.

Controller exposes capability state such as:

```text
BootstrapCapabilityState
- capabilityId
- requestedPermission/primitive
- status: UNKNOWN | AVAILABLE | GRANTED | DENIED | OEM_BLOCKED
- lastVerifiedAt
- evidence
```

No capability is considered available merely because AOSP documents it. The Controller verifies the actual grant/effect before advertising provider availability.

## 13. Google plane in MVP

Do not integrate full microG first.

MVP uses a tiny `GoogleCompatProvider` test implementation proving:

- synthetic `com.google.android.gms` package visibility;
- configurable synthetic signing view;
- VCI propagation;
- one VCI-aware local Binder/service operation;
- one safe stub;
- one real host redirect.

After this contract works, a managed microG provider can replace the test provider incrementally.

## 14. First vertical slice — V1 acceptance contract

The first build milestone is called **Synthetic Slice V1**.

### Input

A purpose-built `:sample-target` APK containing deterministic assertions and no native code.

### Flow

1. Controller imports sample APK through SAF/file picker.
2. Controller computes SHA-256 and parses archive metadata.
3. Recipe validator selects `sample.synthetic.v1`.
4. Resolver produces immutable ResolutionPlan.
5. Controller opens Runtime session and passes read-only APK descriptor + plan digest.
6. Runtime creates content-addressed execution image.
7. Runtime transforms/loads sample code and resources.
8. `ProxyActivity` displays sample UI.
9. Sample asks for a package fact that differs between host and synthetic view.
10. `SyntheticPackageProvider` returns recipe-controlled synthetic result.
11. Sample performs one service request routed through a safe stub.
12. Sample performs one allowed host capability request through Controller broker (initially read-only and harmless).
13. UI/evidence log displays:
    - host fact;
    - synthetic fact;
    - authority plane;
    - truth scope;
    - actual provider result.
14. Runtime is killed/restarted and the Controller can rebuild/reopen the session without modifying the original APK.

### Required security assertions

- sample target cannot read Controller private files;
- sample target cannot call Controller bridge without Runtime/session identity;
- request for capability not in plan is denied;
- malformed/oversized request is denied;
- destructive capability request is denied in V1;
- runtime has no bootstrapped development permissions;
- original APK hash is unchanged after execution.

## 15. CI / verification matrix

### Pure JVM

- recipe schema/validator fixtures;
- dependency-cycle detection;
- provider ranking;
- truth-scope mismatch rejection;
- destructive-gate rejection;
- VCI/runtime-group coherence;
- deterministic plan digest;
- malformed recipe rejection.

### Android instrumented/emulator

Primary: API 31 (Android 12) because target Huawei is Android 12.

Secondary: API 35 for forward-compat smoke.

Tests:

- both APKs install side-by-side;
- family signature permission works;
- foreign test APK cannot bind Controller bridge;
- Runtime UID differs from Controller UID;
- sample APK resources/classes load;
- ProxyActivity launches;
- synthetic package result differs from host result as expected;
- Controller denies out-of-plan request;
- process restart/recovery;
- DCL execution files are read-only before load;
- package/permission surface audit.

### Huawei-device verification later

Only after CI/emulator Synthetic Slice V1 passes. No additional user testing is required during current R7/R8 scaffold work.

## 16. MVP-1 explicit non-goals

Not required before first useful APK:

- host root;
- bootloader unlock;
- arbitrary APK compatibility;
- native/JNI target support;
- full Play Store;
- Play Integrity / bank / DRM / anti-cheat bypass;
- full VM;
- silent package installation;
- Device Owner provisioning;
- unrestricted shell command execution;
- persistent keep-alive hacks.

## 17. Release gates

### Gate A — Core resolver

Pure-Java resolver + recipe fixtures pass.

### Gate B — Two-APK security boundary

Controller and Runtime install with different UIDs; signature IPC works; unauthorized/out-of-plan calls fail.

### Gate C — TSR loader

Sample code/resources load from immutable artifact and execution image is reproducible.

### Gate D — Synthetic reality

At least two controlled app-facing perception changes work with evidence.

### Gate E — Host delegation

One harmless Controller capability is requested by Runtime and executed strictly through the frozen plan.

### Gate F — Recovery

Runtime crash/restart does not corrupt Controller state or original artifact.

Passing A–F constitutes Synthetic Slice V1.

## 18. R7 result

**PASS — MVP architecture frozen.**

The project now moves to **R8 Implementation**.

Broad research is stopped by project rule. Future research must be tied to a concrete failing implementation capability or new evidence that invalidates this architecture.
