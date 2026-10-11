# R7 Addendum — Bugreport-Driven Implementation Gates

Status: BINDING CORRECTION TO R8
Date: 2026-10-11
Source: device Full Bug Report captured after R7 freeze.

This addendum does not reopen broad architecture research. It adds implementation gates based on new direct target-device evidence.

## C1 — Process Budget Gate

The target device currently reports an effective cached-process cap of 4 while GBox uses at least nine host processes. R8 must model process budget explicitly.

Requirements:

- `DeviceProfile` contains effective cached-process limit / whether a developer override is active when observable.
- Runtime descriptor declares expected process footprint.
- Resolver can reject or warn about a runtime whose process topology conflicts with host policy.
- Synthetic Slice V1 uses one primary Runtime process; additional workers require explicit justification.

## C2 — Lifecycle Integrity Gate

The bugreport recorded repeated ChatGPT crashes while hosted by GBox/vLite with `Application` null during configuration handling / Activity launch.

Synthetic Slice V1 therefore MUST test:

- Application creation precedes any callback that requires it;
- Context and Resources remain coherent after configuration change;
- Activity recreation works;
- Runtime process restart works;
- Service/Provider initialization cannot observe an uninitialized synthetic application state.

A launch-only demo is not sufficient.

## C3 — No Opaque Internal Process Killer

The bugreport shows GBox's server running its own `AppProcessKiller` loop on top of Android/EMUI process management.

R8 rule:

- do not introduce a hidden periodic kill engine;
- runtime resource decisions must be explicit, evidence-backed and visible to Controller;
- process termination in MVP occurs only through lifecycle/session ownership, explicit user action, crash recovery or Android host policy.

## C4 — Host Developer-Options Doctor

DeviceProfile / Host Doctor must represent compatibility-impacting developer overrides rather than assuming default Android behavior.

Initial rules:

- non-standard `Background process limit` -> high-impact compatibility conflict for multi-process runtimes;
- `Don't keep activities` -> high-impact lifecycle conflict when enabled;
- `Force 4x MSAA` -> resource-risk flag for general compatibility workloads, not an automatic global change;
- WebView provider state -> report actual provider/health rather than assuming Google WebView.

The Controller may recommend a correction. It must not reset all Developer Options blindly.

## C5 — Google Compatibility Scope Correction

Target evidence proves GBox already combines microG-derived services with Huawei differentiated-signature integration. R8 must not treat that combination as the product's unique result.

Synthetic Slice V1 continues to use the tiny test GoogleCompatProvider defined in R7. Managed microG integration comes only after Slice V1 passes. The differentiator is the controlled low-process runtime + typed shims + lifecycle correctness + resolver/evidence architecture.

## Release gate update

Synthetic Slice V1 now passes only if original R7 Gates A–F pass **and**:

- `ProcessBudgetTest` passes;
- `ApplicationLifecycleIntegrityTest` passes through recreation/configuration change;
- Runtime has no periodic opaque process killer;
- Host Doctor detects a simulated process-limit override and emits a deterministic conflict finding.

These are implementation tests, not additional user-device tests.
