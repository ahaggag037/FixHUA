# R8 — Synthetic Vertical Slice Acceptance

Status: **PASS / IMPLEMENTED**
Date: 2026-10-11
Implementation branch: `build/synthetic-slice-v1`
Verified head before next-stage work: `867c8e99987c56f797719e7bee1d4330ce6963e2`
Synthetic System CI run: `38123196382` — PASS
Earlier host-redirect acceptance run: `38122673183` — PASS

## What is now proven on Android 12

The first R4/R5 vertical slice is no longer a research hypothesis. CI executes the complete path on an Android 12 emulator and verifies:

1. A foreign APK is imported into Controller-private content-addressed storage.
2. The source artifact remains immutable and is addressed by SHA-256.
3. Controller and Runtime have different Linux UIDs.
4. Runtime receives only a Controller-approved, session-bound read-only artifact descriptor.
5. Runtime re-hashes the artifact and creates a private read-only derived execution image.
6. Code and resources are loaded from the uninstalled imported APK.
7. DEX callsites are transformed in the derived image while the original APK remains unchanged.
8. Foreign code sees a `SYNTHETIC_VIEW` package identity instead of the Runtime package identity.
9. Foreign `PackageManager#getPackageInfo` calls are routed through the synthetic package view.
10. A permission check is handled by a typed `SAFE_STUB` that returns DENIED rather than inheriting Runtime authority or fabricating success.
11. A connectivity service request is handled by a typed `HOST_DELEGATED` redirect to the real Android host service.
12. A real host fact about GBox is returned with `HOST_REAL` truth.
13. An out-of-plan host capability is rejected by Controller.
14. Runtime survives Activity recreation/configuration changes without the null-Application/resource failure observed in the device GBox bugreport.
15. Runtime process death is followed by reconstruction from the Controller-owned persisted session.
16. No legacy `sourceDir` shortcut is admitted.
17. Runtime contains no opaque process-killer path in the acceptance boundary.
18. An imported screen object is created from the transformed APK and receives lifecycle callbacks through the Runtime-owned ProxyActivity model.
19. Compatibility decisions emit structured evidence with typed decision classes: `HOST`, `SHIM`, `SYNTHETIC`, `DELEGATED`, `SAFE_STUB`, and `UNSUPPORTED`.

## Important truth boundary

This does **not** claim host root, system_server modification, SELinux modification, or global package spoofing. The proven mechanism changes the environment perceived by code executing inside the managed Runtime and delegates selected real host capabilities through explicit providers.

## Product gap exposed by the successful slice

The imported lifecycle target used for the proof is purpose-built as a delegate. Real third-party APKs contain ordinary Android `Activity` subclasses and will not implement a FixHUA-specific delegate contract.

Therefore the next implementation blocker is not another broad Google-services survey. It is:

**R9 — Ordinary Activity → Proxy-compatible Activity transformation.**

Goal: take an ordinary Activity already present in an imported APK, transform only the minimum lifecycle/context/UI call surface required by the managed Runtime, instantiate it without installing the APK as a host package, and render through a real Runtime-owned ProxyActivity.

The implementation must continue to avoid hidden ActivityThread/`Activity.attach` dependencies as the primary design. If a public-API facade cannot support a required Activity operation, that operation becomes an explicit compatibility capability/shim instead of silently falling back to host truth.

## R9 acceptance gate

R9 is PASS only when the unmodified source `SampleActivity` (or another ordinary Activity compiled without FixHUA delegate APIs):

- is selected from the imported APK metadata/recipe;
- is transformed in the private execution image;
- is instantiated under a Runtime-owned host Activity;
- receives create/resume/destroy lifecycle through the facade/proxy;
- can create at least one Android View and request `setContentView` through the proxy;
- sees the same synthetic package/permission/service perception as other transformed foreign code;
- survives recreation and process-death recovery;
- does not require installing the target APK as the host execution source;
- does not execute inside Controller UID.

Only after this gate should FixHUA treat broad third-party Google/microG compatibility as a product-stage runtime problem rather than a controlled probe problem.
