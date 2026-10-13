# INE System Probe

Independent diagnostic APK for the second Huawei phone (INE-LX1r / Android 9 family).

This branch is intentionally isolated from FixHUA logic. It does not contain GBox readiness rules, microG assumptions, or optimizer verdicts. The first-stage APK only collects raw, privacy-safe system measurements so optimization decisions can be made from evidence from this device.

## Privacy

The probe does not collect accounts, contacts, messages, photos, location, Android ID, serial number, or IP address.

## Build target

- applicationId: `com.haggag.inesystemprobe`
- minSdk: 28 (Android 9)
- targetSdk: 28
- compileSdk: 35

CI builds, lints, and runs an install/launch smoke test on an Android 9 / API 28 emulator before publishing the APK artifact.
