# R9 — Ordinary Activity Facade Acceptance

Status: **PASS / CLOSED**
Date: 2026-10-11
Branch: `build/synthetic-slice-v1`
Accepted head: `00074dc21ade02b9d97df03d2ebb3f29d9125b37`
Synthetic System CI: run `38124251975` / run number 126 — **PASS**
Android CI: run `38124251980` / run number 215 — **PASS**

## Question

Can FixHUA host an ordinary imported Activity class from the APK instead of requiring a purpose-built delegate, while keeping the real Android Activity owned by the low-privilege Runtime and preventing imported code from inheriting Controller-family authority?

## Implemented mechanism

1. `ActivityFacadeTransformer` rewrites direct `android.app.Activity` inheritance only in the immutable derived execution image.
2. The imported source APK remains unchanged.
3. The rewritten class inherits `SyntheticActivityFacade`.
4. `RuntimeActivity` remains the real framework-owned Activity/Window/lifecycle owner.
5. `ForeignActivitySession` creates the imported Activity object and explicitly dispatches its bounded lifecycle.
6. `SyntheticRestrictedContext` is attached as its base Context.
7. UI calls such as `setContentView()` are delegated to the real Runtime Activity.
8. Target `bindService()`/service-start calls do not inherit Runtime-family bridge authority; unmodeled service access is denied/fail-closed.

## Accepted behaviors

The Android 12 end-to-end gate proved:

- one ordinary `SampleActivity` was transformed to the facade;
- imported Activity `onCreate` and `onResume` were delivered through the proxy lifecycle;
- imported UI reached the real proxy Activity;
- synthetic package identity, PackageManager view, permission SAFE_STUB and host connectivity redirect remained active inside the ordinary Activity;
- direct binding to `com.fixhua.guard/.HostCapabilityBrokerService` stayed denied;
- Activity recreation rebuilt the ordinary imported Activity without leaking Controller authority;
- Runtime process death recovered the Controller-owned session and rebuilt the imported Activity;
- Controller, Runtime and separately installed foreign sample remained distinct Linux UIDs;
- out-of-plan host capabilities stayed denied.

## Security result

**ADOPT.** The imported Activity is not attached to `ActivityThread` as a real host Activity and does not receive Controller permissions. Framework ownership remains with `RuntimeActivity`; target-facing behavior is supplied by a bounded facade and restricted Context.

## Known boundary after R9

R9 does not yet provide a target-owned `Application` object. Many real applications initialize global state in `Application.onCreate()` and expect Activity/application contexts to agree. That is the next concrete compatibility primitive.

Next gate: **R10 — Synthetic Application Environment**.
