# FixHUA Capability Graph v0.1

Purpose: decompose “root-like power” into concrete capabilities and map each one to the least-privileged provider that could satisfy it.

Legend:
- **P0** Public Android SDK
- **P1** user-authorized special access
- **P2** persistent delegated/development permission
- **P3** live shell/Binder broker
- **P4** Huawei trusted/OEM plane
- **P5** synthetic runtime / compatibility shim
- **P6** guest root
- **P7** real host root / privileged-system integration

Status:
- `ADOPT` architecture-worthy now
- `PROTOTYPE` needs bounded implementation proof
- `DEFER` useful but blocked
- `REJECT` poor fit for base product
- `BOUNDARY` requires real host privilege unless outcome can be reformulated

## A. Diagnosis / observability

| # | Capability | Candidate providers | Current status | Boundary / note |
|---|---|---|---|---|
| 1 | Read own app state | P0 | ADOPT | trivial host backend |
| 2 | Read installed-package metadata | P0 / P3 | ADOPT | package visibility can limit P0 |
| 3 | Read UsageStats for target apps | P1 | ADOPT | requires user Usage Access |
| 4 | Read system-service dumps | P2 `DUMP` / P3 | PROTOTYPE | OEM grant semantics must be verified |
| 5 | Read logcat beyond own UID | P2 `READ_LOGS` / P3 | PROTOTYPE | protected; varies by build |
| 6 | Read ApplicationExitInfo for other apps | P2/P3 | PROTOTYPE | privileged DUMP/shell path |
| 7 | Read ANR/tombstone diagnostics | P3 | PROTOTYPE | shell/SELinux dependent |
| 8 | Read global/secure settings | P0 read subset / P2/P3 | ADOPT | write is separate capability |
| 9 | Read package AppOps | P3 | PROTOTYPE | shell command or Binder service |
|10 | Read power/idle state | P0/P3 | ADOPT | deeper per-package state may need P3 |

## B. Package / lifecycle control

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|11 | Launch application/activity | P0 | ADOPT | intent routing core |
|12 | Resolve deep links / handlers | P0/P5 | ADOPT | can synthesize routing |
|13 | Force-stop arbitrary package | P3 | PROTOTYPE | shell has authority; ordinary app does not |
|14 | Clear package cache | P3 / user settings surface | PROTOTYPE | data clearing is higher risk and app-specific |
|15 | Clear arbitrary app data | P3/P4 | DEFER | destructive; explicit consent gate required |
|16 | Install APK | P0 user-confirmed / P3 | ADOPT | base product uses PackageInstaller user flow |
|17 | Silent install/update | P3/P4 | DEFER | not required for MVP |
|18 | Uninstall package | P0 user-confirmed / P3 | ADOPT | explicit user action preferred |
|19 | Disable/enable package/component | P3 / P5 for synthetic runtime | PROTOTYPE | host vs virtual distinction |
|20 | Query package signatures | P0 | ADOPT | host reality only |
|21 | Present altered package/signature view to managed app | P5 | PROTOTYPE | core selective-virtualization feature |
|22 | Present virtual installed-package set | P5 | PROTOTYPE | virtual PackageManager primitive |

## C. Permissions / policy / AppOps

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|23 | Request runtime permissions for FixHUA | P0 | ADOPT | normal flow |
|24 | Grant/revoke runtime permissions for another app | P3 / Device Owner | PROTOTYPE | Device Owner not base-path on already-provisioned device |
|25 | Read AppOps | P3 | PROTOTYPE | shell/Binder |
|26 | Change AppOps modes | P3 | PROTOTYPE | high-value root-like primitive |
|27 | Write secure settings for FixHUA workflows | P2 `WRITE_SECURE_SETTINGS` / P3 | PROTOTYPE | persistent bootstrap candidate |
|28 | Change arbitrary protected global settings | P2/P3 | PROTOTYPE | allowlist only, no blanket editor |
|29 | Battery optimization exemption request | P1 | ADOPT | user-authorized path |
|30 | Device-idle whitelist control | P3 | PROTOTYPE | shell path, exact OEM behavior varies |
|31 | App standby bucket control | P3 | PROTOTYPE | useful for container stability |
|32 | Background restriction inspection | P0/P3 | ADOPT | action may be OEM-specific |
|33 | Huawei app-launch policy guidance/control | P4/P0 settings intents | PROTOTYPE | OEM surface, no unsupported hidden writes assumed |

## D. Process / jobs / alarms

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|34 | Observe own process/memory | P0 | ADOPT | |
|35 | Observe target-process existence/state | P3 / P1 UsageStats | PROTOTYPE | P1 gives weaker evidence |
|36 | Inspect JobScheduler jobs | P3 | PROTOTYPE | valuable for GBox/GMS persistence |
|37 | Run FixHUA foreground coordinator | P0 | ADOPT | persistent notification required |
|38 | Schedule maintenance/recovery | P0 WorkManager/AlarmManager | ADOPT | do not fight OS indefinitely |
|39 | Keep third-party process alive directly | none safe / P3 limited | REJECT | Android owns process lifecycle; use policy/routing instead |
|40 | Restart crashed target app | P0/P1 signal detection | PROTOTYPE | user-experience policy required |

## E. Notifications / push

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|41 | Observe notifications with user consent | P1 NotificationListener | ADOPT | privacy-sensitive, opt-in only |
|42 | Re-present bridged notification | P0 | ADOPT | can support container-to-host bridge |
|43 | Detect missing push path | P0/P5 diagnostics | PROTOTYPE | recipe-based |
|44 | Provide FCM-like compatibility | microG / native GMS / P5 shim | PROTOTYPE | app-specific, backend dependent |
|45 | Substitute HMS Push when app supports it | P4 HMS | ADOPT | only if app/service contract supports HMS |

## F. Network

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|46 | Per-device local VPN tunnel | P1 VpnService | ADOPT | user-authorized |
|47 | Per-app routing via VPN | P1 | PROTOTYPE | Android allowed/disallowed app lists |
|48 | DNS policy / fallback | P1/P0 | PROTOTYPE | avoid TLS interception by default |
|49 | Observe coarse connectivity failures | P0 | ADOPT | |
|50 | Modify arbitrary app traffic transparently | P1 limited / P7 full | BOUNDARY | encrypted payload interception is not a base goal |

## G. Accounts / Google / HMS compatibility

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|51 | Detect GMS/microG/HMS presence and health | P0 | ADOPT | core profile input |
|52 | Route Google-service-dependent app to suitable runtime | resolver + P5 | ADOPT | central product behavior |
|53 | Provide microG APIs | microG | PROTOTYPE | Huawei flavor / signing expectations matter |
|54 | Run genuine GMS unprivileged with compatibility shims | P5 pattern inspired by GmsCompat | PROTOTYPE | cannot copy ROM-only hooks blindly |
|55 | Stub unsupported privileged GMS subfeature safely | P5 | PROTOTYPE | only when semantics understood |
|56 | Redirect service to HMS equivalent | P4/P5 | PROTOTYPE | API semantic mismatch must be explicit |
|57 | Google account integration inside managed runtime | P5 + chosen GMS backend | DEFER | after runtime selection |
|58 | Play Store intent fallback/router | P0/P5 | ADOPT | AppGallery/browser/GBox/native route |

## H. Synthetic reality / selective virtualization

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|59 | Virtual PackageManager | P5 | PROTOTYPE | cornerstone of Split-Reality Runtime |
|60 | Virtual ApplicationInfo/PackageInfo responses | P5 | PROTOTYPE | identity/view substitution |
|61 | Virtual filesystem paths | P5 | PROTOTYPE | app-private / managed runtime only |
|62 | Virtual system properties visible to managed app | P5 | PROTOTYPE | app-facing only, not host properties |
|63 | Safe API return-value shim | P5 hooks/runtime | PROTOTYPE | recipe controlled |
|64 | System-service proxy/stub | P5 | PROTOTYPE | use exact semantics, not generic fake success |
|65 | Intent interception and reroute inside managed runtime | P5 | PROTOTYPE | high value |
|66 | ContentProvider query/result shim | P5 | PROTOTYPE | GmsCompat proves value of shaped results |
|67 | Dynamic feature flag override inside managed service stack | P5 | DEFER | requires chosen backend and compatibility contract |
|68 | Per-app synthetic identity profile | P5 | PROTOTYPE | package/signature/property view bundle |
|69 | Full Android VM | P6 | DEFER | too heavy for base path |
|70 | Thin guest-root bubble | P5/P6 | DEFER | requires runtime proof; promising but not MVP blocker |

## I. Huawei trusted/OEM plane

| # | Capability | Candidate providers | Status | Boundary / note |
|---|---|---|---|---|
|71 | Detect HDB/HiSuite availability/state | P0/P4 | PROTOTYPE | OEM-specific |
|72 | OEM-authorized backup/restore mediation | P4 | DEFER | desktop/proprietary service dependency |
|73 | HMS Core health and service routing | P4/P0 | ADOPT | |
|74 | Huawei Optimizer/System Manager integration | P0 settings intents / P4 documented APIs | PROTOTYPE | avoid private unsupported interfaces in MVP |
|75 | PowerGenie behavior modeling | observation + recipe | PROTOTYPE | control may be limited |
|76 | Huawei differentiated-signature primitive | P4 if supported / P5 synthetic equivalent | PROTOTYPE | host feature may be absent on EMUI 13 |

## J. Boundaries that remain real

| # | Capability | Provider | Status | Boundary |
|---|---|---|---|---|
|77 | Read arbitrary `/data/data/<other-app>` directly on host | P7 / OEM-authorized backup channel | BOUNDARY | normal app/shell SELinux limits |
|78 | Change host SELinux policy | P7 | BOUNDARY | genuine privileged-system/root territory |
|79 | Write real boot/vendor/system partitions | P7 | BOUNDARY | not Synthetic Root |
|80 | Become real host UID 0 | P7 | BOUNDARY | guest uid 0 is separate |
|81 | Grant arbitrary platform-signature permissions | P7 / platform signing | BOUNDARY | cannot synthesize host permission grant |
|82 | Modify kernel / load kernel modules | P7 | BOUNDARY | host root / unlocked chain required |
|83 | Replace host system_server behavior globally | P7 / custom ROM | BOUNDARY | selective app-facing shims are alternative |
|84 | Override hardware-backed attestation truth | secure hardware/system | REJECT | not a compatibility objective |

## Graph rules

1. Every feature request is resolved into one or more capability IDs.
2. Each capability can have multiple provider edges ordered by cost/risk.
3. P5 can replace *perception* but must never be recorded as changing host truth.
4. P2/P3/P4 providers must record the authority source used for each action.
5. Destructive package/data operations require explicit user approval even when technically available.
6. A `BOUNDARY` capability does not block the product unless a required user-visible outcome has no alternative formulation.
7. New research is accepted only when it resolves an unresolved edge or invalidates an existing one.
