# FixHUA Turbo

FixHUA Turbo is a lightweight host-side stability guard for Huawei devices using GBox.

## What v1.0 does
- Starts an explicit foreground stability session before opening GBox.
- Holds a partial CPU wake lock so Android does not suspend the host CPU during an active session.
- Requests Android high-performance Wi-Fi mode while Turbo is active.
- Re-checks and re-acquires those locks every 20 seconds if the OEM releases them.
- Shows whether GBox and FixHUA are excluded from battery optimization.
- Provides a one-time FixHUA battery-optimization exemption flow and Huawei app-launch/background settings shortcut.
- Stops immediately from the app or persistent notification and releases all locks.

## Scope
FixHUA does not patch or reverse-engineer GBox, bypass Play Integrity, or claim to repair closed-source bugs inside GBox. The v1.0 goal is to reduce host-side suspension, Wi-Fi power saving, and Huawei background-management interference during an active GBox session.

## Battery
Turbo intentionally trades battery efficiency for stability while active. Stop Turbo after finishing the GBox session.
