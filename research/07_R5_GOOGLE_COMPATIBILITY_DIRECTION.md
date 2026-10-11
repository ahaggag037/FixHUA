# R5 — Google Compatibility Direction

Status: DECISION READY
Date: 2026-10-11
Depends on: R4 Transformed Synthetic Runtime + `06_DEVICE_BUGREPORT_FINDINGS_2026-10-11.md`

## R5 question

Given that the target Huawei device does not provide normal privileged GMS integration, which Google compatibility functions should FixHUA supply through microG, which can be shimmed or redirected, which may be delegated to the host/HMS, and which require a later genuine-Google sandbox backend?

## New device evidence that changes R5

The Full Bug Report proves the current GBox runtime already contains:

- microG-derived GMS services (`org.microg.gms.*`), including GCM/MCS;
- Huawei differentiated-signature components (`com.huawei.signature.diff.*`);
- virtual component proxying (`ComponentProxyStubs`);
- a native vLite virtualization core;
- many guest execution processes.

Therefore the project must not define innovation as “microG + signature spoofing inside a container.” That architecture already exists in the target environment and still exhibits lifecycle/proxy/runtime failures.

## External architecture evidence

GrapheneOS demonstrates a different compatibility principle: official Google Play components can run as ordinary sandboxed applications when a system compatibility layer teaches them to operate without privileged integration. Its current GmsCompat configuration uses several distinct techniques instead of a single privilege bypass:

1. feature/Phenotype flag overrides to disable privileged subfeatures;
2. safe API stubs with explicit return semantics;
3. component disabling for services that cannot function safely;
4. PackageManager/process/permission view adjustments;
5. redirects/reimplementations for selected services such as location;
6. crash/reporting and process-state compatibility logic.

The important lesson is not to copy GrapheneOS ROM hooks. It is to reproduce the smallest useful subset inside the FixHUA managed runtime.

---

# Transferability matrix

## T1 — Runtime-transferable now

These are suitable for the R4 transformed-runtime model because the target app/GMS process is under FixHUA control.

### A. Safe method stubs

Examples of the pattern found in GmsCompat:

- unavailable privileged Bluetooth operations -> known neutral/failure values;
- UsageStats queries -> defined safe result;
- unavailable telephony identifiers -> null/unknown;
- unavailable DevicePolicy state -> defined non-owner state;
- unsupported privileged location operations -> false/null where semantics allow.

FixHUA rule: a stub is permitted only when its semantics are explicitly modeled in a recipe. Never return generic success.

### B. Feature flag shaping

When a Google component contains a feature whose only viable path requires privileged OS integration, disable that subfeature rather than failing the entire process.

This is especially suitable for a data-driven Recipe engine.

### C. Component suppression

Disable a Service/Receiver/Provider within the synthetic package view when:

- it is not required for the requested app capability;
- it deterministically crashes because the host privilege/service is absent;
- a safer substitute exists.

### D. Package/identity view

Synthetic PackageManager can control the package/feature/signature view presented inside the managed runtime without claiming the host PackageManager changed.

### E. Content/query result shaping

For known configuration providers such as flag/config stores, FixHUA can intercept a bounded query and return a semantically valid transformed result.

### F. WebView/process hygiene

Public per-process WebView data-directory separation and explicit process/lifecycle initialization should be adopted because GmsCompat itself handles multi-process WebView assumptions and the target bugreport shows WebView is involved in real guest execution.

---

## T2 — Transferable with a FixHUA broker

These need a host-side provider rather than pure synthetic return values.

### A. Location

Preferred provider order for MVP:

1. host Android/Huawei location where ordinary APIs suffice;
2. microG location compatibility service;
3. explicit unsupported state.

Do not fake coordinates or claim a provider succeeded when it did not.

### B. Package install/update

Use ordinary Android package-install flows with user authorization. Do not emulate privileged silent installation in the base product.

### C. Notifications / push wakeup

Use real host notification APIs and an explicit bridge. Avoid depending on a large permanently privileged virtual GMS process when a narrower bridge is enough.

### D. Network / connectivity

Delegate ordinary connectivity to host Android; use a local VPN backend only for capabilities that actually require routing/inspection.

---

## T3 — Later experimental genuine-Google backend

GrapheneOS proves that genuine Play Services can operate with no special app privileges when the OS provides deep compatibility support. On stock EMUI, those framework hooks do not exist.

FixHUA should therefore NOT make genuine GMS sandboxing the first milestone.

After R4 runtime stability is proven, a later backend may investigate user-supplied/legitimately installed Google components inside the managed runtime with:

- transformed call sites;
- synthetic package/service views;
- safe stubs;
- component/flag compatibility recipes;
- host service redirection.

This backend remains experimental until compatibility and licensing/distribution constraints are resolved.

---

## T4 — ROM/framework-only or host-privileged functions

These are not promised by MVP-1:

- global framework hooks affecting arbitrary host applications;
- arbitrary platform-signature permissions;
- replacing real system_server services globally;
- host SELinux policy changes;
- kernel/UID-0 operations;
- privileged device-management functions with no safe delegated substitute;
- global permission-check spoofing outside FixHUA-managed processes.

The resolver should seek reformulations or contained synthetic equivalents instead.

---

# MVP R5 decision

## D-R5-01 — microG-first provider, not microG-container clone

**State: ADOPT**

MVP-1 Google compatibility uses microG-derived/open Google-service implementations only for capabilities that actually require them. It does not recreate GBox's broad multi-process virtual GMS environment.

## D-R5-02 — FixHUA shims precede additional privilege

**State: ADOPT**

Before requesting a stronger privilege plane, the resolver checks whether the failing requirement can be safely handled with:

- a feature flag override,
- safe stub,
- component disable,
- result shaping,
- host/HMS redirect,
- synthetic package identity.

## D-R5-03 — Genuine sandboxed GMS is Phase 2

**State: DEFER / PROTOTYPE LATER**

It is valuable for compatibility but too coupled to deep framework assumptions to be the first implementation target on stock EMUI.

Reactivation trigger: Transformed Synthetic Runtime passes lifecycle/component/PackageManager vertical-slice tests.

## D-R5-04 — GBox remains fallback and comparison oracle

**State: ADOPT**

GBox is retained as:

- fallback runtime for applications FixHUA cannot yet host;
- real-world reference for which compatibility mechanisms are needed;
- comparison target for process count, memory, launch reliability and failure transparency.

It is not the architecture to clone.

---

# First vertical slice implied by R4 + R5

The first buildable proof should not start with a large third-party application. Build a controlled Compatibility Probe APK plus FixHUA runtime that proves:

1. import and parse an APK without installing it as a normal host app;
2. load code/resources through the controlled runtime;
3. establish Application before Activity configuration callbacks;
4. route one Activity through a host ProxyActivity;
5. expose a synthetic PackageManager identity entry;
6. intercept one deliberately unsupported system call and apply a typed safe stub;
7. redirect one service request to a real host provider;
8. log every decision as `host`, `shim`, `synthetic`, `delegated`, or `unsupported`;
9. run with one primary runtime process and no opaque process killer;
10. survive Activity recreation/configuration change without the null-Application failure observed in the GBox-hosted ChatGPT crash.

Passing this vertical slice is now the release gate before broad Google-app compatibility work.

# R5 gate result

R5 is sufficiently resolved for MVP architecture. Remaining broad Google compatibility research is frozen until the first R4/R5 vertical slice exists and exposes a concrete missing primitive.

Next gate: R6 — recipe schema + deterministic capability resolver.
