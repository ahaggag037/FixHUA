#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p dist

on_error() {
  rc=$?
  echo "::error::Synthetic emulator smoke failed at line ${BASH_LINENO[0]} (exit ${rc})"
  for f in dist/controller_uid.txt dist/runtime_uid.txt dist/sample_uid.txt dist/uid-boundary.txt; do
    [[ -f "$f" ]] && { echo "### $f"; cat "$f"; }
  done
  [[ -f dist/host-probe-logcat.txt ]] && tail -n 320 dist/host-probe-logcat.txt || true
  [[ -f dist/runtime-logcat.txt ]] && tail -n 640 dist/runtime-logcat.txt || true
  [[ -f dist/runtime-lifecycle-logcat.txt ]] && tail -n 640 dist/runtime-lifecycle-logcat.txt || true
  [[ -f dist/runtime-recovery-logcat.txt ]] && tail -n 700 dist/runtime-recovery-logcat.txt || true
  [[ -f dist/sample-logcat.txt ]] && tail -n 240 dist/sample-logcat.txt || true
  exit "$rc"
}
trap on_error ERR

uid_of() {
  adb shell dumpsys package "$1" | tr -d '\r' \
    | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' | sed -n '1p'
}
step() { echo "::group::$1"; }
endstep() { echo "::endgroup::"; }

step "Install three APKs"
# -g intentionally grants requested runtime/development permissions to the low-privilege Controller
# for this bounded emulator acceptance. Runtime remains installed without -g and is audited below.
adb install -r -g dist/FixHUA-Controller.apk
adb install -r dist/FixHUA-Runtime.apk
adb install -r dist/FixHUA-SampleTarget.apk
endstep

step "Verify three distinct Linux UIDs"
controller_uid="$(uid_of com.fixhua.guard)"
runtime_uid="$(uid_of com.fixhua.runtime)"
sample_uid="$(uid_of com.fixhua.sampletarget)"
printf '%s\n' "$controller_uid" > dist/controller_uid.txt
printf '%s\n' "$runtime_uid" > dist/runtime_uid.txt
printf '%s\n' "$sample_uid" > dist/sample_uid.txt
printf 'controller=%s runtime=%s sample=%s\n' "$controller_uid" "$runtime_uid" "$sample_uid" | tee dist/uid-boundary.txt
[[ -n "$controller_uid" && -n "$runtime_uid" && -n "$sample_uid" ]]
[[ "$controller_uid" != "$runtime_uid" && "$controller_uid" != "$sample_uid" && "$runtime_uid" != "$sample_uid" ]]
echo "UID_BOUNDARY_PASS"
endstep

step "Verify sanitized shell process-budget import survives reinstall"
adb shell dumpsys package com.fixhua.guard | tr -d '\r' | tee dist/controller-package-after-grant.txt
grep -q 'android.permission.DUMP: granted=true' dist/controller-package-after-grant.txt
adb logcat -c
adb shell am force-stop com.fixhua.guard || true
adb shell am start -W -n com.fixhua.guard/.MainActivity
sleep 5
adb logcat -d -v brief > dist/host-probe-logcat.txt
cat dist/host-probe-logcat.txt
grep -q 'FixHUAHostProbe.*dumpPermission=true probeSuccess=true source=ADB_SHELL_SANITIZED current=[0-9][0-9]*' dist/host-probe-logcat.txt
echo "SANITIZED_PROCESS_BUDGET_RELOAD_PASS"
endstep

step "Import foreign sample and launch Controller-approved Runtime session"
adb logcat -c
adb shell am force-stop com.fixhua.guard || true
adb shell am force-stop com.fixhua.runtime || true
adb shell am start -W -n com.fixhua.guard/.AcceptanceBootstrapActivity
sleep 8
adb logcat -d -v brief > dist/runtime-logcat.txt
cat dist/runtime-logcat.txt
grep -q 'FixHUAAcceptance.*sessionReady=true package=com.fixhua.sampletarget' dist/runtime-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationReady=true' dist/runtime-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationInvariant=true phase=activityOnCreate' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*bridgeAuthenticated=true protocol=5' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*packageIdentityShim=true package=com.fixhua.sampletarget.*truth=SYNTHETIC_VIEW' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*packageManagerView=true package=com.fixhua.sampletarget selfFound=true absentFound=false truth=SYNTHETIC_VIEW' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*hostCapability=true capability=1002 gboxInstalled=false.*truth=HOST_REAL' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*outOfPlanDenied=true capability=9999' dist/runtime-logcat.txt
if grep -q 'legacyArtifactFallback=true' dist/runtime-logcat.txt; then
  echo "Legacy artifact fallback must not exist" >&2
  false
fi
echo "PLAN_AUTHORIZED_HOST_CAPABILITY_PASS"
endstep

step "Verify DEX callsites synthesize identity, safe-stub permission and delegate connectivity"
grep -q 'FixHUARuntime.*sessionArtifact=true code=sample-code-v1 resource=sample-resource-v1' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*transformCallsites=4.*pmCallsites=1.*contextCallsites=1.*safeStubCallsites=1.*hostRedirectCallsites=1.*activityFacadeClasses=1' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*appView=package=com.fixhua.sampletarget;gms=absent;bridgePerm=denied;connectivity=host;' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*sourceReadOnly=true' dist/runtime-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-logcat.txt; then
  echo "Transformed session artifact verification failed" >&2
  false
fi
source_sha="$(grep -m1 'FixHUARuntime.*sessionArtifact=true' dist/runtime-logcat.txt \
  | sed -n 's/.*sourceSha256=\([0-9a-f][0-9a-f]*\).*/\1/p')"
[[ ${#source_sha} -eq 64 ]]
printf '%s\n' "$source_sha" > dist/session-source-sha256.txt
echo "SYNTHETIC_AND_HOST_REDIRECT_VIEW_PASS"
endstep

step "Verify imported Application exists before Activity and uses restricted synthetic context"
grep -q 'FixHUASampleApp.*applicationProbe=true created=true;package=com.fixhua.sampletarget;bridgePerm=denied;connectivity=host;bridgeBind=denied;appContext=com.fixhua.sampletarget.SampleApplication' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.application-facade-v1.*subject=application-facade:attach.*outcome=READY' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.application-facade-v1.*subject=application-facade:onCreate.*outcome=DELIVERED' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.application-session-v1.*subject=application-session:create.*outcome=com.fixhua.sampletarget.SampleApplication' dist/runtime-logcat.txt
app_line="$(grep -n -m1 'FixHUASampleApp.*applicationProbe=true' dist/runtime-logcat.txt | cut -d: -f1)"
activity_line="$(grep -n -m1 'FixHUASample.*hostBaseline=true' dist/runtime-logcat.txt | cut -d: -f1)"
[[ -n "$app_line" && -n "$activity_line" ]]
(( app_line < activity_line ))
echo "SYNTHETIC_APPLICATION_ORDER_PASS applicationLine=$app_line activityLine=$activity_line"
endstep

step "Verify ordinary imported Activity facade, UI and shared synthetic Application context"
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onCreate facade=true' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onResume facade=true' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.activity-facade-v2.*subject=activity-facade:attach.*outcome=READY' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.activity-facade-v2.*subject=activity-facade:setContentView.*outcome=DELIVERED' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.ordinary-activity-session-v2.*subject=ordinary-activity:onCreate.*outcome=DELIVERED' dist/runtime-logcat.txt
grep -q 'FixHUASample.*hostBaseline=true package=com.fixhua.sampletarget;gms=absent;bridgePerm=denied;connectivity=host;context=com.fixhua.sampletarget.SampleActivity;appContextPackage=com.fixhua.sampletarget;appContextClass=com.fixhua.sampletarget.SampleApplication' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.restricted-context-v2.*decision=UNSUPPORTED.*subject=bind-service.*outcome=DENIED:com.fixhua.guard/.HostCapabilityBrokerService' dist/runtime-logcat.txt
grep -q 'FixHUASample.*bridgeDenied=true reason=bindService_false' dist/runtime-logcat.txt
if grep -q 'SECURITY_FAILURE' dist/runtime-logcat.txt; then
  echo "Imported code inherited forbidden Runtime-family bridge authority" >&2
  false
fi
echo "ORDINARY_ACTIVITY_AND_APPLICATION_SECURITY_PASS"
endstep

step "Verify imported screen ProxyActivity lifecycle and typed evidence stream"
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onCreate marker=create=1;resume=0;config=0;destroy=0' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onResume marker=create=1;resume=1;config=0;destroy=0' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=SYNTHETIC.*truth=SYNTHETIC_VIEW' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=SAFE_STUB.*truth=SYNTHETIC_VIEW.*outcome=DENIED' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=DELEGATED.*truth=HOST_DELEGATED.*subject=system-service:connectivity.*outcome=PRESENT' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=HOST.*truth=HOST_REAL.*subject=package:com.gbox.android.*outcome=ABSENT' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=UNSUPPORTED.*outcome=DENIED_OUT_OF_PLAN' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=SHIM.*subject=proxy-lifecycle:onCreate' dist/runtime-logcat.txt
grep -q 'FixHUAEvidence.*decision=SHIM.*subject=proxy-lifecycle:onResume' dist/runtime-logcat.txt
echo "STRUCTURED_EXECUTION_EVIDENCE_PASS"
endstep

step "Verify lifecycle recreation rebuilds Application before ordinary Activity"
adb logcat -c
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell settings put system user_rotation 0
sleep 4
adb logcat -d -v brief > dist/runtime-lifecycle-logcat.txt
cat dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationConfigChanged=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationInvariant=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*packageManagerView=true package=com.fixhua.sampletarget selfFound=true absentFound=false truth=SYNTHETIC_VIEW' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*hostCapability=true capability=1002 gboxInstalled=false.*truth=HOST_REAL' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*transformCallsites=4.*pmCallsites=1.*contextCallsites=1.*safeStubCallsites=1.*hostRedirectCallsites=1.*activityFacadeClasses=1.*appView=package=com.fixhua.sampletarget;gms=absent;bridgePerm=denied;connectivity=host;' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUASampleApp.*applicationProbe=true created=true;package=com.fixhua.sampletarget;bridgePerm=denied;connectivity=host;bridgeBind=denied;appContext=com.fixhua.sampletarget.SampleApplication' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onDestroy facade=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onCreate facade=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onResume facade=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.activity-facade-v2.*subject=activity-facade:setContentView.*outcome=DELIVERED' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.application-facade-v1.*subject=application-facade:onCreate.*outcome=DELIVERED' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.restricted-context-v2.*subject=bind-service.*outcome=DENIED:com.fixhua.guard/.HostCapabilityBrokerService' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUASample.*appContextPackage=com.fixhua.sampletarget;appContextClass=com.fixhua.sampletarget.SampleApplication' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUASample.*bridgeDenied=true reason=bindService_false' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onDestroy' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onCreate marker=create=1;resume=0;config=0;destroy=0' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onResume marker=create=1;resume=1;config=0;destroy=0' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUAEvidence.*decision=SHIM.*subject=proxy-lifecycle:onDestroy' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUAEvidence.*decision=DELEGATED.*subject=system-service:connectivity.*outcome=PRESENT' dist/runtime-lifecycle-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-lifecycle-logcat.txt; then
  echo "Transformed session did not survive recreation" >&2
  false
fi
if grep -q 'SECURITY_FAILURE' dist/runtime-lifecycle-logcat.txt; then
  echo "Recreated imported code inherited forbidden bridge authority" >&2
  false
fi
max_create_count="$(grep -o 'FixHUARuntime.*activityReady=true createCount=[0-9]*' dist/runtime-lifecycle-logcat.txt | sed 's/.*createCount=//' | sort -n | tail -n 1)"
[[ -n "$max_create_count" ]]
(( max_create_count >= 2 ))
adb shell pidof com.fixhua.runtime | tee dist/runtime-pid-after-rotation.txt
grep -q 'com.fixhua.runtime/.RuntimeActivity' <(adb shell dumpsys activity activities | tr -d '\r')
echo "LIFECYCLE_SYNTHETIC_REALITY_PASS createCount=$max_create_count"
adb shell settings put system accelerometer_rotation 1 || true
endstep

step "Verify Runtime process-death recovery rebuilds Application then Activity"
adb logcat -c
runtime_pid_before="$(adb shell pidof com.fixhua.runtime | tr -d '\r')"
[[ -n "$runtime_pid_before" ]]
printf '%s\n' "$runtime_pid_before" > dist/runtime-pid-before-recovery.txt
adb shell am force-stop com.fixhua.runtime
sleep 2
runtime_pid_stopped="$(adb shell pidof com.fixhua.runtime | tr -d '\r' || true)"
[[ -z "$runtime_pid_stopped" ]]
adb shell am start -W -n com.fixhua.runtime/.RuntimeActivity
sleep 8
runtime_pid_after="$(adb shell pidof com.fixhua.runtime | tr -d '\r')"
[[ -n "$runtime_pid_after" ]]
[[ "$runtime_pid_after" != "$runtime_pid_before" ]]
printf '%s\n' "$runtime_pid_after" > dist/runtime-pid-after-recovery.txt
adb logcat -d -v brief > dist/runtime-recovery-logcat.txt
cat dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationReady=true' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*bridgeAuthenticated=true protocol=5' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*activeSession=true package=com.fixhua.sampletarget recipe=sample.synthetic.v1:7' dist/runtime-recovery-logcat.txt
grep -q "FixHUARuntime.*activeSession=true.*sourceSha256=$source_sha" dist/runtime-recovery-logcat.txt
grep -q 'FixHUASampleApp.*applicationProbe=true created=true;package=com.fixhua.sampletarget;bridgePerm=denied;connectivity=host;bridgeBind=denied;appContext=com.fixhua.sampletarget.SampleApplication' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationInvariant=true phase=activityOnCreate' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onCreate facade=true' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*ordinaryActivityLifecycle=true phase=onResume facade=true' dist/runtime-recovery-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.activity-facade-v2.*subject=activity-facade:setContentView.*outcome=DELIVERED' dist/runtime-recovery-logcat.txt
grep -q 'FixHUASample.*hostBaseline=true package=com.fixhua.sampletarget;gms=absent;bridgePerm=denied;connectivity=host;context=com.fixhua.sampletarget.SampleActivity;appContextPackage=com.fixhua.sampletarget;appContextClass=com.fixhua.sampletarget.SampleApplication' dist/runtime-recovery-logcat.txt
grep -q 'FixHUAEvidence.*provider=runtime.restricted-context-v2.*subject=bind-service.*outcome=DENIED:com.fixhua.guard/.HostCapabilityBrokerService' dist/runtime-recovery-logcat.txt
grep -q 'FixHUASample.*bridgeDenied=true reason=bindService_false' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onCreate marker=create=1;resume=0;config=0;destroy=0' dist/runtime-recovery-logcat.txt
grep -q 'FixHUARuntime.*foreignScreenLifecycle=true phase=onResume marker=create=1;resume=1;config=0;destroy=0' dist/runtime-recovery-logcat.txt
grep -q 'FixHUAEvidence.*decision=SHIM.*subject=proxy-lifecycle:onCreate' dist/runtime-recovery-logcat.txt
grep -q 'FixHUAEvidence.*decision=DELEGATED.*subject=system-service:connectivity.*outcome=PRESENT' dist/runtime-recovery-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-recovery-logcat.txt; then
  echo "Recovered Runtime used a non-verified artifact" >&2
  false
fi
if grep -q 'SECURITY_FAILURE' dist/runtime-recovery-logcat.txt; then
  echo "Recovered imported code inherited forbidden bridge authority" >&2
  false
fi
echo "PROCESS_DEATH_APPLICATION_ACTIVITY_RECOVERY_PASS before=$runtime_pid_before after=$runtime_pid_after"
endstep

step "Verify foreign caller cannot bind protected broker"
adb logcat -c
adb shell am force-stop com.fixhua.sampletarget || true
adb shell am start -W -n com.fixhua.sampletarget/.SampleActivity
sleep 3
adb logcat -d -v brief > dist/sample-logcat.txt
cat dist/sample-logcat.txt
grep -q 'FixHUASample.*bridgeDenied=true reason=bindService_false' dist/sample-logcat.txt
if grep -q 'FixHUASample.*bridgeConnectedUnexpectedly=true' dist/sample-logcat.txt; then
  echo "Foreign APK unexpectedly connected to protected broker" >&2
  false
fi
echo "FOREIGN_CALLER_DENIED_PASS"
endstep

echo "SYNTHETIC_ANDROID12_SMOKE_PASS"
