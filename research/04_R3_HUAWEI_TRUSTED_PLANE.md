# R3 — Huawei Trusted / OEM Plane

Status: ARCHITECTURE DECISION READY

## Objective

Determine which Huawei-specific channels provide capabilities beyond an ordinary Android app, and whether they belong in FixHUA's base APK, an optional companion, or only as compatibility knowledge.

## 1. HDB / HiSuite is a distinct Huawei trust plane

Huawei officially exposes **HDB** as a separate user-authorized connection mode for HiSuite. The user enables “Allow HiSuite to use HDB”, approves the connection, and can revoke HDB authorizations later.

Huawei also documents that HiSuite can:

- manage applications and user data;
- back up and restore data;
- update / roll back Huawei system software;
- perform system recovery;
- manage files and other device content;
- install/update its phone-side companion when needed.

Huawei support further documents backup of **some third-party app data**, with an explicit note that supported app data is located under `data/data/<package name>`.

### Architectural implication

HDB/HiSuite demonstrates an important primitive:

> Huawei has an OEM-authorized path that can reach operations/data unavailable to an ordinary app without turning the phone into a generally rooted device.

This validates the concept of a separate **P4 Huawei Trusted Plane**.

### But HDB is not the base FixHUA backend

The documented flow is:

```text
User enables HDB
    -> authorized computer / HiSuite
    -> Huawei phone-side companion / backup components
    -> OEM-authorized operations
```

There is no current evidence that a normal third-party APK can simply request equivalent HDB authority on-device.

Therefore:

- Do not claim HDB access from FixHUA APK.
- Do not make proprietary HiSuite protocol reverse engineering a prerequisite for MVP-1.
- Preserve HDB as a future **Desktop/OEM Bridge provider** if a legitimate, maintainable integration surface is established.

Decision: `DEFER` direct HDB provider; `ADOPT` HDB as evidence and optional future architecture plane.

## 2. Huawei App Launch / background policy is a first-class compatibility layer

Huawei support documents three distinct application-launch controls:

- Auto-launch
- Secondary launch
- Run in background

Huawei explicitly states that third-party apps can stop downloading, playing media or performing other background work when these policies do not allow background execution.

### Architectural implication

FixHUA must model Huawei launch policy separately from generic Android battery optimization / App Standby.

Create a `HuaweiPolicyProfile` with at least:

```text
autoLaunch
secondaryLaunch
runInBackground
batteryOptimizationState
dataSaverExemption
recentTaskLockHint
```

Not every field will be programmatically writable by a normal app. The profile distinguishes:

- detectable automatically;
- openable via settings surface;
- user-action required;
- dynamically controllable through a privileged backend;
- unavailable.

Decision: `ADOPT` Huawei Policy Adapter.

## 3. Do not depend on private System Manager / PowerGenie APIs

Huawei's Optimizer / System Manager clearly participates in process and power behavior, but official consumer documentation exposes user settings rather than a stable third-party control API for arbitrary application policy.

Decision:

- Model Huawei-specific policy and symptoms.
- Deep-link to supported settings where possible.
- Use shell/Binder authority only for Android-level controls proven to map safely.
- Do not base MVP-1 on undocumented private Huawei Binder services or signature-only interfaces.

State: `REJECT` private-OEM-service dependency for base architecture.

## 4. HMS Core is an open service plane, not automatic GMS substitution

Huawei exposes HMS Core kits such as Push Kit through supported SDKs. The documented Push Kit model requires the application developer to register/configure the app, integrate the HMS SDK, obtain a token and use Huawei's cloud service.

Therefore an arbitrary closed-source third-party app that expects FCM does not automatically become HMS-compatible merely because HMS exists on the device.

Architectural use of HMS:

- FixHUA's own services can use HMS where useful.
- A managed/synthetic runtime may route a compatible abstraction to HMS only when semantic translation is explicitly implemented and valid.
- Native third-party HMS builds should be preferred when available.
- Never advertise HMS as a transparent universal FCM replacement.

Decision: `ADOPT` HMS as a service provider, not as a magic GMS replacement.

## 5. Huawei backup capability is strategically interesting

HiSuite backup shows that Huawei can selectively expose otherwise protected app data through a user-authorized OEM workflow.

Potential future provider:

```text
DesktopHuaweiBridge
  capability: BACKUP_SUPPORTED_APP_DATA
  authority: HUAWEI_OEM
  lifecycle: USER_AUTHORIZED_SESSION
  transport: HDB / official HiSuite-compatible surface
```

This provider is not required for MVP-1 and must not be emulated through security bypasses.

Decision: `DEFER` until there is a documented or robust legitimate integration route.

## 6. R3 capability decisions

### ADOPT

- Huawei-specific policy profile as a separate compatibility input.
- HMS Core as a legitimate provider where application semantics support it.
- HDB/HiSuite as proof that OEM-authorized capabilities form a distinct privilege plane.
- Resolver metadata must distinguish `ANDROID_SHELL` from `HUAWEI_OEM` authority.

### PROTOTYPE

- Detection of Huawei App Launch / power policy state where Android/Huawei surfaces expose it.
- Safe navigation to Huawei policy settings from FixHUA.
- Correlation engine between Huawei policy state and per-app failures.

### DEFER

- Direct HDB protocol provider.
- HiSuite desktop companion integration.
- Backup/restore of arbitrary target app data.

### REJECT for base product

- Undocumented private Huawei system-service calls as critical dependencies.
- Treating HMS as transparent replacement for arbitrary GMS-dependent third-party apps.
- Requiring a PC/HiSuite session for ordinary day-to-day FixHUA operation.

## 7. Resulting base architecture adjustment

```text
HuaweiPolicyAdapter
  -> detects / explains OEM policy
  -> opens supported user-control surfaces
  -> emits CapabilityState

HmsServiceProvider
  -> exposes only semantically valid HMS-backed capabilities

HuaweiOemBridge (future optional)
  -> HDB / HiSuite mediated
  -> never required for MVP-1
```

## Next gate

Proceed to **R4 — Synthetic Reality**.

R4 must choose a maintainable strategy for selective virtualization on Android 12+ and determine the smallest proof capable of presenting a managed app with a controlled PackageManager/service view while continuing to use real Huawei hardware/services where desired.
