# FixHUA 2.1 Temporary Stability Build

Temporary enhancement of FixHUA 2 while the next-generation architecture is being developed.

Key changes over 2.0.0:
- safer readiness logic with fewer RAM/storage false positives;
- distinguishes missing, disabled, suspended, and non-launchable GBox states;
- action routing only when a safe/relevant settings action exists;
- manual post-incident snapshot/report without background monitoring;
- richer privacy-safe report and clearer interpretation limits;
- localized UI strings, launcher icon, and explicit no-backup rules;
- expanded regression tests;
- CI can use stable signing secrets when configured, otherwise it labels the APK as using an ephemeral CI key.

The app intentionally requests only `ACCESS_NETWORK_STATE`. It does not force-stop apps, change Huawei internals, run a persistent background service, or bypass Android/Huawei security controls.
