# R4 — Synthetic Reality / Selective Virtualization

Status: GATE DECISION READY
Branch: `research/synthetic-system-v1`
Date: 2026-10-11

## Research question

Choose one maintainable prototype strategy for running a target app inside a FixHUA-controlled synthetic view of Android without host root, and reject unsuitable alternatives.

## Evidence summary

### Public Android primitives are stronger than expected

On Android 12 / API 31 the following can be done with public APIs:

- Parse an APK archive with `PackageManager.getPackageArchiveInfo()`.
- Load DEX code from APK/JAR using `DexClassLoader`.
- Load resources directly from an APK using API 30+ `ResourcesProvider.loadFromApk()` + `ResourcesLoader`.
- Use a real host `Activity` / `Service` as a container for delegated UI/lifecycle.

This means code/resources do not require a full VM or a legacy hidden-API parser.

### What public APIs do NOT solve

Public loading APIs do not make an uninstalled APK a real Android package. `system_server` still owns:

- Activity / Service / Provider registration and launch;
- task/process bookkeeping;
- package identity and UID;
- permissions/AppOps;
- component resolution;
- calls made directly to Binder-backed system services.

A general arbitrary-APK runtime therefore needs either:

1. system-service/component interception, or
2. transformation of target code so it talks to a FixHUA compatibility runtime instead of assuming normal installation.

### VirtualApp-style container

Architecturally proven: virtual PackageManager, virtual ActivityManager, Binder proxies, stub components, classloader/resource redirection.

Rejected as the prototype base because:

- current commercial VirtualApp has explicit licensing constraints;
- community forks carry provenance/maintenance risk;
- modern variants rely heavily on hidden APIs, reflection bypasses and native hooks;
- Android non-SDK restrictions intentionally change across releases;
- inheriting the full engine creates a much larger compatibility surface than FixHUA currently needs.

Use only as an architectural reference, not a code dependency.

### DroidPlugin-style arbitrary APK container

Demonstrates arbitrary APK execution without install/repackaging, but its design is old and documented limitations include native-code handling, notifications, externally visible components and modern Android adaptation. It is evidence that the concept is possible, not a suitable base.

### Shadow-style transformed plugin runtime

Important evidence: Tencent Shadow demonstrates that component/lifecycle virtualization can be implemented with build-time bytecode transformation, proxy components and delegation while avoiding reflection/hidden Android APIs in its main design. Its limitation for FixHUA is that plugins are normally transformed during their own build and some third-party APIs expecting a real `Activity` type can break.

Key lesson to adopt: move complexity from unstable system hooks into deterministic code transformation + explicit delegates.

### LSPatch / APK patching

Rejected as the primary runtime. Repackaging/re-signing target APKs breaks apps that depend on their original signing identity or integrity. The upstream project is archived. Keep only as a precision fallback for apps explicitly known to tolerate patching.

### Full Android VM / AVF

Rejected for base R4:

- Android Virtualization Framework was introduced in Android 13 and depends on system-image/hypervisor support not present as an app-level primitive on the current Android 12 target;
- a full Android guest is too heavy for the first product slice.

### PRoot / fake Linux root

Rejected for Android-app virtualization. PRoot can fake uid 0 and filesystem paths for Linux processes but does not grant Android Binder/system-server privileges and does not provide Android component lifecycle. Useful later only as an optional Linux userspace backend.

## R4 decision

### PROTOTYPE: FixHUA Transformed Synthetic Runtime (TSR)

Build a clean-room, selective runtime that preserves the original imported APK as immutable evidence and creates a private transformed execution image inside FixHUA.

The prototype deliberately does **not** try to emulate all of Android.

### Core pipeline

```text
Original APK (immutable)
        |
        +--> Archive inspector
        |      - package / manifest metadata
        |      - original signing metadata
        |      - capabilities / native libs
        |
        +--> Internal transform pipeline
                - DEX rewrite rules
                - component/lifecycle adaptation
                - selected API redirection
                - compatibility bootstrap injection
                        |
                        v
              Private execution image
                        |
              DexClassLoader + ResourcesLoader
                        |
                 FixHUA ProxyActivity
                        |
              Synthetic Context / services
```

### Why transform instead of broad system hooking

A deterministic DEX transform:

- can be unit-tested instruction-by-instruction;
- is versioned with the compatibility recipe;
- can be rolled back by discarding the execution image;
- avoids modifying the original APK;
- reduces dependence on Android private internals;
- lets each app receive only the minimum shims it needs.

The Google-maintained smali/dexlib2 fork provides current DEX parsing/rewriting primitives under a permissive BSD-style license lineage and is a candidate implementation dependency. Dependency choice remains subject to a build-size/runtime test before adoption.

## Prototype scope: TSR-P0

R4 is considered implemented only when a bounded prototype can do all of the following on API 31:

1. Import an APK from local storage and hash/store the original immutably.
2. Parse package metadata without installing it.
3. Load at least one class from its DEX using app-private storage.
4. Load its resources from the APK via public resource-loader APIs.
5. Execute the target inside a real FixHUA host `ProxyActivity`.
6. Inject a `SyntheticContext` / service gateway.
7. Demonstrate at least two controlled perception changes:
   - virtual package query result;
   - redirected or stubbed system-facing service/API result.
8. Log host truth separately from synthetic truth.
9. Discard/rebuild the transformed image without modifying the original APK.

The first target should be a purpose-built test APK with known assertions. Arbitrary production APK compatibility is **not** the P0 acceptance criterion.

## Planned internal modules

- `runtime-archive` — immutable APK intake, hashing, metadata.
- `runtime-transform` — DEX rewriting rules.
- `runtime-loader` — class/resource loading.
- `runtime-container` — ProxyActivity / lifecycle delegate.
- `synthetic-pm` — virtual package/identity view.
- `synthetic-services` — service shim/redirect registry.
- `runtime-evidence` — per-call authority plane + real/synthetic result logging.

## Hidden/non-SDK policy

R4 prototype rule:

- Prefer public APIs and transformation.
- Any non-SDK dependency must live behind a single versioned `PlatformAdapter` interface.
- No scattered reflection across the runtime.
- P0 target is Android 12 / API 31 first; do not prematurely generalize to all Android releases.

If a required P0 capability cannot be achieved without a hidden API, record exactly that capability and add the smallest adapter needed. Do not import a broad hidden-API bypass framework by default.

## Native code boundary

Native libraries are explicitly outside TSR-P0. JNI-heavy apps are a later gate because native code can bypass Java-level transformation and inspect real process/filesystem/package state.

Reactivation trigger: Java/Kotlin P0 succeeds and the first high-value target requires native support.

## Security / integrity boundary

TSR is a compatibility runtime, not a mechanism to bypass banking, DRM, Play Integrity, anti-cheat or security controls. Apps that require trusted host/package attestation may remain incompatible. The resolver must report this as an integrity boundary rather than spoofing success.

## Rejected for R4 base

- Fork current commercial VirtualApp: REJECT.
- Depend on arbitrary community VirtualApp fork: REJECT for base.
- Full VM / AVF: DEFER.
- PRoot as Android runtime: REJECT.
- LSPatch/re-sign every target: REJECT as base; DEFER precision fallback.
- Broad hidden-API hooking before a public/transform prototype: REJECT.

## R4 gate result

**Chosen strategy:** clean-room Transform + Delegate selective runtime.

**Gate state:** PASS for research/design; next required action is implementation proof TSR-P0 after R5/R6 define the Google-service and recipe contracts that the runtime must expose.

## Dependencies carried forward

- R5 must classify Google compatibility shims into `runtime-transferable` vs `ROM-only`.
- R6 must define transform/shim recipes declaratively so TSR does not accumulate hard-coded app rules.
- R7 must freeze module/IPC/storage interfaces before production implementation.
