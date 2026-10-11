# FixHUA — Device Bugreport Findings (2026-10-11)

Status: VERIFIED DEVICE EVIDENCE → ARCHITECTURE INPUT
Target: Huawei nova 9 SE family / JLN-L21 on EMUI 13 / Android 12
Source: user-generated Android Full Bug Report from Developer Options, captured 2026-10-11.

Privacy note: this document intentionally excludes serial numbers, account identifiers, SSIDs, phone numbers, user content, tokens and unrelated package/user data. Only project-relevant system/runtime facts are retained.

## Executive result

The bugreport materially changes the FixHUA plan. The dominant compatibility problem is not simply “Huawei kills GBox” and not simply “missing Google services”. The device snapshot shows a three-layer conflict:

1. Android/EMUI host process policy is manually overridden to an extremely small cached-process pool.
2. GBox itself runs a large multi-process virtual runtime and contains its own process-killing policy.
3. Real guest failures are visible in GBox/vLite lifecycle, proxy-service and native-runtime paths.

At the same time, the report proves that GBox already contains several mechanisms FixHUA was considering independently: microG-derived services, Huawei differentiated-signature support, virtual process/component proxies, internal package storage and a native virtualization core. Therefore “put microG inside a container and spoof signature” is not a sufficient differentiator. FixHUA must improve the architecture: selective virtualization, lifecycle correctness, fewer synthetic processes, transparent host delegation and deterministic per-app shims.

---

## F-01 — Host cached-process pool is explicitly overridden to 4

Verified `ActivityManager` settings:

```text
mOverrideMaxCachedProcesses=4
mCustomizedMaxCachedProcesses=32
CUR_MAX_CACHED_PROCESSES=4
CUR_MAX_EMPTY_PROCESSES=2
```

The Developer Options screenshots independently show `Background process limit → At most 4 processes`.

### Interpretation

This is not a low-RAM device condition. It is an explicit developer override reducing the cached-process pool from the device/OEM customized value of 32 to 4. A container architecture that intentionally multiplexes many guest processes is unusually sensitive to this setting.

### Decision impact

- Add `background_process_limit` / effective cached-process capacity to the Host Profile.
- Treat non-standard process-limit overrides as a high-impact compatibility conflict for multi-process containers.
- FixHUA should detect and explain this state rather than blindly changing unrelated battery settings.
- For the current device, restoring Android's Standard limit is the highest-confidence immediate configuration correction.

---

## F-02 — GBox is running at least nine host processes simultaneously

Current ActivityManager state includes:

```text
com.gbox.android
com.gbox.android:server
com.gbox.android:vlapp01
com.gbox.android:vlapp02
com.gbox.android:vlapp03
com.gbox.android:vlapp04
com.gbox.android:vlapp05
com.gbox.android:vlapp06
com.gbox.android:vlapp08
```

The processes occupy multiple OOM-adjustment states (service, started-service, previous/recent-provider, process-management state).

### Interpretation

The user-selected host limit of four cached processes is structurally hostile to the observed runtime topology. This does not prove every crash is caused by the limit, but it creates strong process churn pressure.

### Design lesson

FixHUA's runtime should avoid allocating one long-lived host process per synthetic guest slot unless a workload genuinely requires it.

---

## F-03 — Raw RAM shortage is not the primary failure at capture time

Bugreport memory summary:

```text
Total RAM: ~7.8 GB
Free RAM: ~3.28 GB
Used RAM: ~4.60 GB
ZRAM physical used: ~250 MB
Swap represented: ~885 MB
```

GBox's active process set collectively consumes roughly around 0.9–1.0 GB PSS at the snapshot, but the system still reports substantial available/cached/free memory.

### Interpretation

A generic “the phone does not have enough RAM” diagnosis is unsupported. The process-count policy and layered runtime management are more relevant than total physical RAM pressure in this snapshot.

---

## F-04 — Repeated GBox guest/runtime crashes and ANRs are real

The DropBox service contains many entries associated with GBox virtual processes during Oct 8–10. A bounded classification of GBox/vLite-related entries produced:

- `data_app_wtf`: 71
- `data_app_crash`: 13
- `data_app_anr`: 8

These counts are diagnostic indicators, not all necessarily defects in the GBox engine: a guest application crash is attributed to the virtual host process that executes it.

### High-value concrete examples

#### ChatGPT launch/configuration crash

Three fatal exceptions on 2026-10-10 show:

```text
Process: com.openai.chatgpt
java.lang.NullPointerException:
Attempt to invoke virtual method
'android.content.res.Resources android.app.Application.getResources()'
on a null object reference

at android.app.ConfigurationController.handleConfigurationChanged(...)
at android.app.ActivityThread.handleLaunchActivity(...)
```

The failures repeat at different times/PIDs, which makes a one-off transient explanation weak.

#### ChatGPT ANR

An ANR records:

```text
Input dispatching timed out
com.openai.chatgpt/com.openai.chatgpt.MainActivity
```

Its classloader/library state includes the GBox base APK and `libvlitecore64.so`, showing that ChatGPT is executing through the GBox/vLite runtime context.

#### GBox virtual proxy service ANR

Another ANR records:

```text
executing service
com.gbox.android/com.vlite.sdk.proxy.ComponentProxyStubs$Service07
Cmd line: com.moonfrog.ludo.club
```

This is direct evidence that a guest service is mediated through GBox proxy components and that the proxy/service lifecycle can itself become an ANR boundary.

### Design lesson

R4 must prioritize lifecycle correctness before broad API emulation. The first transformed-runtime proof should validate `Application` creation, configuration changes, Activity launch, Service mapping and Context/Resources consistency.

---

## F-05 — Historical native crashes exist inside the vLite/GBox virtualization path

Historical tombstone examples retained by the device include:

### GBox virtual process

```text
Cmdline: com.gbox.android:vlapp05
signal 11 (SIGSEGV), fault addr 0x80
...
com.vlite.sdk.client.virtualservice...
com.vlite.sdk.client.j.initClient
com.vlite.sdk.proxy.StubContentProvider.call
```

### Virtual Google Play Services

```text
Cmdline: com.google.android.gms
signal 11 (SIGSEGV), fault addr 0x0
frame in GBox libvlitecore64.so
```

These tombstones are historical and may correspond to older GBox builds; they must not be used to claim the current build contains the same bug. They are architectural evidence that a deep native virtualization core adds a large, hard-to-debug failure surface.

### Design lesson

Keep FixHUA's first runtime predominantly Java/Kotlin/public-runtime based. Native interception should be an optional narrow backend, not the foundation.

---

## F-06 — GBox already embeds microG-derived Google services

Current report contains active components such as:

```text
org.microg.gms.gcm.McsService
org.microg.gms.checkin...
org.microg.gms.location...
org.microg.gms.gcm...
```

### Consequence for R5

This invalidates any product differentiation based solely on “combine GBox with microG”. GBox is already using microG-derived functionality in its virtual Google-services layer.

FixHUA must differentiate through:

- lower virtualization overhead,
- better lifecycle behavior,
- deterministic compatibility recipes,
- stronger Huawei-host delegation,
- per-app minimal shims instead of broad virtualization,
- better observability and recovery.

---

## F-07 — GBox already uses Huawei differentiated-signature integration internally

The report includes virtualized actions/services for:

```text
_vlite_com.gbox.android_com.google.android.gms_com.huawei.signature.diff.InitReceiver
com.google.android.gms/com.huawei.signature.diff.SignatureService
```

These are mapped through GBox `ComponentProxyStubs` services.

### Consequence for R5

Huawei differentiated signature remains a valuable primitive, but reproducing signature-view substitution alone is not novel enough. FixHUA should treat identity virtualization as one small component of a larger compatibility model.

---

## F-08 — GBox contains its own process-killing/resource policy

The `com.gbox.android:server` process (UID 10174) logs repeated entries such as:

```text
AppProcessKiller: Type=4
AppProcessKiller: Loop Time Frame 4085 MiB
AppProcessKiller: doKillProcess 4
Apps: RunningAppsInfo(...)
```

Equivalent cycles repeat with other type values.

### Interpretation

This is GBox's internal process-management logic, not Android LMKD or Huawei System Manager, because the logger UID/process belongs to GBox itself.

The bugreport did not expose a corresponding pattern of standard LMKD/ActivityManager low-memory kills targeting GBox at the capture time.

### Design lesson

There are multiple competing process authorities:

1. Android/EMUI process management,
2. user-imposed Android process cap,
3. GBox internal AppProcessKiller.

FixHUA should avoid adding yet another opaque killer. Its resource policy should be explainable, state-aware and subordinate to host memory/importance signals.

---

## F-09 — Huawei WebView is installed, valid and healthy at service level

`dumpsys webviewupdate` reports:

```text
Multiprocess enabled: true
Current WebView package: com.huawei.webview 114.0.5.302
Preferred WebView package: com.huawei.webview 114.0.5.302
WebView package dirty: false
Any WebView package installed: true
Valid package ... installed/enabled for all users
```

Google Chrome / Google WebView / AOSP WebView are not installed as host WebView providers.

### Decision impact

Reject “missing/broken WebView provider” as the primary current diagnosis. Keep WebView compatibility as a per-app capability because guest apps may still make assumptions that differ from Huawei WebView behavior.

---

## F-10 — Current GBox package identity and compatibility posture

Verified package state:

```text
package: com.gbox.android
uid: 10174
shared user: com.gbox.shared
versionName: 1.8.4.21
versionCode: 1080421
minSdk: 21
targetSdk: 28
installer: com.huawei.appmarket
lastUpdateTime: 2026-09-24
```

GBox intentionally targets an old SDK level relative to Android 12. This is a compatibility choice that preserves legacy platform behavior but also increases the number of behavioral branches the runtime must manage.

---

## F-11 — Host boot state remains locked and verified

Verified properties:

```text
ro.boot.flash.locked=1
ro.boot.vbmeta.device_state=locked
ro.boot.verifiedbootstate=green
ro.boot.veritymode=enforcing
ro.boot.avb_version=1.2
ro.debuggable=0
ro.adb.secure=1
```

No new bugreport evidence changes the earlier conclusion: there is no demonstrated host root. FixHUA should continue to treat P7 host-root authority as unavailable for the base product.

---

## F-12 — Developer settings materially affect compatibility

Verified current settings include:

```text
always_finish_activities=0
anr_show_background=0
debug.egl.force_msaa=true
window_animation_scale=0.5
mOverrideMaxCachedProcesses=4
```

`Don't keep activities` is correctly disabled. `Force 4x MSAA` is enabled, which adds unnecessary GPU/rendering overhead for a general compatibility phone and should not be part of the recommended stable profile.

### Immediate product implication

FixHUA Host Doctor should detect risky Developer Options and classify them by effect. It should never reset all Developer Options blindly.

---

# Architecture updates caused by this report

## A-01 — UPDATE: microG + signature virtualization is necessary but not differentiating

GBox already demonstrates that combination. R5 must focus on *how* Google-service assumptions are satisfied with fewer synthetic layers and better lifecycle behavior.

## A-02 — ADOPT: Lifecycle Integrity Layer

The runtime needs explicit invariants for:

- Application exists before any configuration callback requiring it,
- consistent Context / Resources identity,
- Activity launch order,
- Service create/bind/start/stop mapping,
- provider initialization ordering,
- configuration-change replay,
- component identity translation.

These invariants become deterministic tests before loading real third-party apps.

## A-03 — ADOPT: Process Budget capability

A recipe/runtime must declare its process requirement and compare it against effective host process policy. The resolver must refuse or warn when a runtime topology is incompatible with the host cap.

## A-04 — ADOPT: Minimize process virtualization

The first FixHUA synthetic runtime should prefer:

```text
one host runtime process
+ optional isolated worker when technically required
```

rather than a large fixed pool of virtual app slots.

## A-05 — ADOPT: Resource policy without an opaque internal killer

Do not reproduce GBox `AppProcessKiller` as a hidden independent subsystem. Use a transparent state machine with evidence and Android host signals.

## A-06 — ADOPT: Full Bug Report as optional high-value diagnostic input

Android/EMUI's user-generated Full Bug Report is a useful privileged diagnostic snapshot without host root. FixHUA may support importing such a report for local analysis. Direct programmatic generation remains a separate privileged-capability question and is not assumed available to an ordinary APK.

---

# Highest-confidence current configuration corrections

These are not speculative experiments; they directly reverse risky developer overrides observed in the bugreport:

1. `Background process limit` → `Standard limit`.
2. `Force 4x MSAA` → OFF unless explicitly needed for a specific old OpenGL ES workload.
3. Keep `Don't keep activities` → OFF.

FixHUA should eventually detect these conditions automatically and explain why they matter.

---

# Next gate

Continue R5 with the following updated question:

> Given that GBox already provides microG-derived GMS, signature-differentiation integration and a deep vLite virtual runtime, what is the minimum Google-compatibility surface FixHUA must implement to achieve equal-or-better app compatibility with lower lifecycle/process complexity?

The R5 answer must classify each Google compatibility requirement into:

- real host service,
- microG provider,
- genuine Google component in sandbox,
- FixHUA shim/redirect,
- synthetic identity/PackageManager view,
- unsupported/ROM-only.
