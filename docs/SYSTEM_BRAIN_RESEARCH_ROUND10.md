# FixHUA 3 — System Brain Research Round 10

## Theme
Root-agent privilege architecture, boot lifecycle, SELinux boundaries, safe mode, and reversible control.

## 1. Root access is a capability, not an architecture

Granting the APK unrestricted `su` would make FixHUA powerful but fragile. The root component should be treated as a tiny privileged service with a narrow API.

Proposed split:
- ordinary Android APK: UI, policy, visualization, user consent
- Brain Engine: decision logic and experiment planning
- Root Agent: predefined privileged operations only

The APK should never accept arbitrary shell text and forward it to root.

## 2. Root Agent command model

Example read-only commands:
- READ_PSI
- READ_VMSTAT
- READ_ZRAM_CAPS
- READ_CPUFREQ
- READ_DEVFREQ
- READ_THERMAL
- READ_CGROUPS
- READ_TASK_PROFILES
- READ_LMKD_CONFIG
- READ_BLOCK_TOPOLOGY
- READ_PERFETTO_CAPS

Example future write commands:
- APPLY_TASK_PROFILE
- SET_BOUNDED_VM_TUNABLE
- SET_BOUNDED_DEVFREQ_POLICY
- RESTORE_ACTION

Every write command must include:
- capability proof
- allowed range
- previous value
- TTL / timeout
- verification readback
- rollback action
- policy reason + evidence ID

## 3. Least privilege should be enforced below the app

KernelSU App Profiles show that a root-granted process can be constrained by UID/GID/groups, Linux capabilities and SELinux policy. This supports an architecture where FixHUA's helper gets only the privileges needed for its narrow job.

Principle:
- if read-only access is enough, do not grant write capability
- if one sysfs subtree is enough, do not allow whole-filesystem writes
- no network permission is required for the privileged daemon itself
- no arbitrary executable launching
- NO_NEW_PRIVS or equivalent restrictions where feasible

## 4. Boot lifecycle

Most FixHUA work does not need to run in early `post-fs-data`.

Safer lifecycle:
1. boot begins normally
2. root framework initializes
3. late-start service begins a read-only capability inventory
4. after boot completed, Brain Engine may enable previously approved policies
5. experiments never modify boot-critical partitions

Early boot scripts should be avoided unless a future feature proves they are necessary.

## 5. Safe Mode must be independent of the APK UI

If a bad policy causes instability before Android UI is usable, recovery cannot depend on opening FixHUA.

Required safety path:
- persistent `disable-experiments` flag readable by the root module
- boot-time timeout watchdog
- automatic rollback if boot-complete is not reached within expected window
- module-disable mechanism compatible with the selected root framework
- no policy should survive reboot unless explicitly promoted from temporary to persistent

## 6. Temporary-first policy model

All new tuning should start as session-scoped:
- apply after boot
- expires on daemon restart / reboot
- reverts automatically on anomaly

Only after repeated successful experiments may a policy be promoted to persistent.

This greatly reduces boot-loop risk.

## 7. SELinux is part of the design, not an obstacle to disable

Do not switch the device to global SELinux permissive mode as an optimization strategy.

The preferred model is a narrow custom domain/rule set if the chosen root framework requires it. Android vendor-init itself demonstrates the principle of reducing privileges for vendor-specific privileged work.

## 8. New component: Action Ledger

Every privileged action should append a local immutable-style record:
- timestamp
- action ID
- caller policy
- evidence that justified action
- old state
- requested state
- readback state
- result
- expiry
- rollback status

This ledger is essential for postmortem debugging and self-correction.

## 9. New component: Experiment Governor

Before any root write:
- check thermal safety state
- check device is not booting/recovering
- check battery floor
- check no unresolved previous rollback
- enforce one experimental domain at a time initially
- enforce cooldown between changes

If any guard fails, the action is rejected.

## 10. Root framework choice remains device-dependent

KernelSU officially focuses on unlocked-bootloader GKI 5.10+ devices; older kernels may require nonstandard integration. If the target device is truly 4.19, Magisk may be operationally simpler, but this must be decided only after the capability probe confirms boot image/kernel details.

FixHUA should support the root transport through an abstraction so the Brain Engine does not care whether privileges come from Magisk or KernelSU.

## Provisional conclusion

The Root Agent should be less like a superuser shell and more like a hardware-control microservice: small command surface, least privilege, readback verification, TTLs, rollback, and an independent safe-mode path.

## Sources
- KernelSU App Profile: https://kernelsu.org/guide/app-profile.html
- KernelSU module guide: https://kernelsu.org/guide/module.html
- KernelSU FAQ: https://kernelsu.org/guide/faq.html
- Android vendor init / SELinux confinement: https://source.android.com/docs/security/features/selinux/vendor-init
- Android cgroup/task profiles: https://source.android.com/docs/core/perf/cgroups
