# FixHUA Auto System Diagnostics v3.3.0

Android diagnostic + GBox stability utility for the Huawei JLN-LX1 test device.

## v3.3 focus

This build replaces manual lag marking with automatic incident detection and fixes the diagnostic session reliability problems found in the previous report.

- sampling runs on a dedicated `HandlerThread`, not the Activity/main looper
- a bounded partial wakelock is held only during the explicit diagnostic session
- normal sampling is 1 Hz; suspected/active/recovering incidents temporarily use 2 Hz
- automatic state machine: `NORMAL -> SUSPECTED -> INCIDENT -> RECOVERY -> NORMAL`
- recovery must remain stable for 12 seconds before an incident closes
- scheduler lateness and sample gaps are recorded so a stalled FixHUA service becomes evidence rather than missing data
- memory/I/O PSI unavailable is represented as `NA`/`UNAVAILABLE`, never a fake zero
- CPU frequency drops only gain incident weight when paired with meaningful CPU load or other pressure signals
- Usage Access has a vendor-tolerant self-test and foreground package correlation
- final report is built automatically when the session ends and a notification announces success/failure
- the manual “lag now” button is removed

## Visibility boundary

Without Root/ADB, Android does not allow a normal app to inspect every driver, Binder call, service, process, private log, or another app's rendering pipeline. FixHUA records the broadest read-only device evidence available to its granted APIs and marks missing sources honestly.

When Usage Access is granted, foreground package names may be recorded for attribution. App content, messages, photos, contacts, precise location, Android ID, serial, IP addresses, and passwords are not collected.

## Safety

FixHUA does not root the phone, unlock the bootloader, disable thermal protection, bypass Play Integrity/DRM, blindly kill processes, or make persistent kernel/sysfs writes.

## Branch

`build/v3.3-auto-system-diagnostics`
