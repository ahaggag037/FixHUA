# R2 — Bootstrap & Persistence

Status: PARTIALLY CLOSED / ARCHITECTURE DECISION READY

## Objective

Determine which useful capabilities can be initialized once and remain available to FixHUA after the desktop/ADB session ends, and which capabilities require a live shell/Binder broker.

## Verified AOSP facts

### Development permissions are a real Android permission class

Android 12 declares multiple permissions with `signature|privileged|development`, including:

- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.DUMP`
- `android.permission.CONTROL_UI_TRACING`
- `android.permission.READ_LOGS`
- `android.permission.SET_DEBUG_APP`
- `android.permission.SET_PROCESS_LIMIT`
- `android.permission.SET_ALWAYS_FINISH`
- `android.permission.SIGNAL_PERSISTENT_PROCESSES`
- `android.permission.GET_APP_OPS_STATS`
- `android.permission.UPDATE_APP_OPS_STATS`
- `android.permission.BATTERY_STATS`
- `android.permission.PACKAGE_USAGE_STATS` also includes `development` plus AppOp/user-control semantics
- additional specialized development permissions exist outside the main development-tools block.

These permissions are not ordinary third-party permissions. The `development` flag is the relevant primitive for shell-grantable development-tool access.

### PackageManager handles development permissions as install permissions

AOSP PackageManager code explicitly handles development permissions specially: when granted through the permission-grant path, it uses the install-permission state rather than treating them as ordinary per-user runtime permissions, and persists package settings.

Architectural implication:

**A development permission can be session-independent once legitimately granted to a package.** It is not conceptually tied to the lifetime of an ADB process.

Important limits:

- The target APK must declare the permission in its manifest.
- The caller granting it must have the platform authority required by PackageManager.
- OEM modifications may restrict or alter what stock AOSP permits.
- Persistence across app uninstall/reinstall, package identity changes, or OEM policy changes must not be assumed.
- A permission being declared `development` does not guarantee that every API protected by it is otherwise reachable or semantically useful on Huawei.

## Huawei-specific verified constraint

Huawei support states that Wireless Debugging is not available on Huawei phones/tablets and recommends USB debugging or other wired methods. This applies to EMUI families including EMUI 13.

Therefore:

**Wireless-ADB/Shizuku cannot be a mandatory base architecture for FixHUA on the current Huawei target.**

A Shizuku-style process may still be started through USB ADB and used as a dynamic broker, but it is not the persistent foundation.

## Resulting privilege split

### Plane B1 — Persistent delegated capabilities

Candidate capabilities that can potentially be initialized once and then used by FixHUA without a live desktop connection:

| Permission / access | Candidate value | Status |
|---|---|---|
| `DUMP` | system-service diagnostics, richer failure evidence | BUILD PROTOTYPE |
| `READ_LOGS` | wider log access where SELinux/service policy permits | BUILD PROTOTYPE |
| `WRITE_SECURE_SETTINGS` | bounded secure-setting operations | BUILD PROTOTYPE |
| `GET_APP_OPS_STATS` | deeper AppOps inspection | BUILD PROTOTYPE |
| `UPDATE_APP_OPS_STATS` | potential AppOps control via APIs | BUILD PROTOTYPE |
| `BATTERY_STATS` | richer power diagnostics | BUILD PROTOTYPE |
| `PACKAGE_USAGE_STATS` | usage/process behavior evidence | ALREADY AVAILABLE via user AppOp path; no shell dependency required |

The MVP must not request every possible development permission. The bootstrap manifest uses the minimum set justified by a concrete feature.

### Plane B2 — Dynamic shell/Binder capabilities

Capabilities that should remain behind a live privileged backend instead of trying to persist them inside FixHUA:

- force-stop arbitrary packages;
- package enable/disable operations that ordinary APIs cannot perform;
- `cmd appops` operations not covered by a safe persistent API path;
- device-idle / standby-bucket administration;
- JobScheduler and other shell-only diagnostic/control surfaces;
- package installation flows requiring shell authority;
- selected package-manager administration;
- system-service calls where shell UID itself is the authority rather than a grantable permission.

Provider interface should be generic:

```text
PrivilegedBackend
  - capabilities(): Set<CapabilityId>
  - execute(request): Result
  - authority(): SHELL | HUAWEI_OEM | OTHER
  - lifecycle(): LIVE | BOOTSTRAPPED | TEMPORARY
```

Shizuku is one possible implementation, not the architectural API.

### Plane B3 — User-authorized permanent Android access

These do not require ADB bootstrap and should remain independent:

- Usage Access
- Notification Listener
- VPN service authorization
- battery optimization request path
- role / default-handler selection where relevant
- Accessibility only for narrowly justified features; not a generic privilege escalator

## Bootstrap product concept

The product should expose an optional **Advanced Enable** flow:

```text
Install FixHUA
     |
     v
Normal mode works immediately (P0/P1)
     |
     +--> Advanced Enable (optional)
              |
              v
        one-time USB bootstrap
              |
              +--> grant only supported declared development permissions
              +--> verify each grant after execution
              +--> store evidence + capability state
              +--> disconnect desktop
              |
              v
        Enhanced local mode
```

A later live-shell session can attach dynamically for capabilities that cannot be persisted.

## Critical implementation rule

Never represent the bootstrap as `root`.

Every resolved capability reports:

```text
authorityPlane = PUBLIC | USER_AUTHORIZED | PERSISTENT_DELEGATED | SHELL | HUAWEI_OEM | SYNTHETIC | GUEST
scope = exact capability IDs
persistent = true/false
verifiedAt = timestamp
evidence = permission/service/backend result
```

## What R2 now establishes

### ADOPT

1. **Persistent Bootstrap Plane** as a first-class architecture layer.
2. **USB bootstrap** as the Huawei-compatible initialization route.
3. **Generic dynamic privileged backend interface** rather than hard-depending on Shizuku.
4. Capability-by-capability verification after bootstrap.

### PROTOTYPE

1. Real grantability of the minimum selected development permissions on Huawei EMUI 13.
2. Whether Huawei modifies PackageManager behavior materially for these grants.
3. Actual API usefulness after grant (`DUMP`, `READ_LOGS`, `WRITE_SECURE_SETTINGS`, AppOps permissions).

These questions are deliberately postponed to implementation-time automated bootstrap validation rather than asking the user to run more manual experiments now.

### REJECT

- Mandatory Wireless Debugging architecture.
- Permanent dependency on a desktop ADB process.
- Requesting broad development permissions with no mapped product capability.

## Next gate

Proceed to **R3 — Huawei Trusted Plane**.

R3 must answer whether Huawei exposes useful OEM-authorized capabilities through HDB/HiSuite/HMS/System Manager that can become providers in the Capability Resolver without reverse-engineering a security bypass.
