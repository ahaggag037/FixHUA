# FixHUA Active Guard v2.2.0

Temporary active-performance build for Huawei + GBox while the long-horizon FixHUA research project is still in progress.

Target verified by user report:
- Huawei JLN-LX1 / JLN-L21
- Android 12 / SDK 31
- GBox 1.8.4.21
- GBox battery exemption already enabled
- healthy RAM/storage/network/thermal snapshot at test time

## Goal

Improve the live GBox session instead of replacing performance work with diagnostics.

The guard uses an explicit foreground service with bounded CPU and Wi-Fi performance locks. With Usage Access enabled it focuses protection around actual GBox foreground/recent activity. It automatically backs off Wi-Fi boosting on thermal pressure and disables all locks at severe thermal status.

Modes:
- Balanced: CPU-awake protection around the GBox window + high-performance Wi-Fi when conditions are good.
- Stability: stronger session protection + low-latency Wi-Fi while cool, degrading to high-performance mode as temperature rises.
- Eco: minimal foreground-only CPU-awake protection, no Wi-Fi boost.

## What it deliberately does not do

- no RAM cleaner
- no blind process killing
- no thermal-safety disabling
- no device-property spoofing
- no Play Integrity / banking / DRM bypass
- no unrestricted root shell
- no persistent kernel/sysfs writes

## Branch

`build/v2.2-active-guard`

This build is intentionally separate from the clean v2 diagnostic rewrite and from the long-horizon research repositories.
