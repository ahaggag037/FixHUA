#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p dist

on_error() {
  rc=$?
  echo "::error::Synthetic emulator smoke failed at line ${BASH_LINENO[0]} (exit ${rc})"
  for f in dist/controller_uid.txt dist/runtime_uid.txt dist/sample_uid.txt dist/uid-boundary.txt; do
    [[ -f "$f" ]] && { echo "### $f"; cat "$f"; }
  done
  [[ -f dist/runtime-logcat.txt ]] && tail -n 360 dist/runtime-logcat.txt || true
  [[ -f dist/runtime-lifecycle-logcat.txt ]] && tail -n 380 dist/runtime-lifecycle-logcat.txt || true
  [[ -f dist/sample-logcat.txt ]] && tail -n 200 dist/sample-logcat.txt || true
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
adb install -r dist/FixHUA-Controller.apk
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
grep -q 'FixHUARuntime.*bridgeAuthenticated=true protocol=4' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*packageIdentityShim=true package=com.fixhua.sampletarget.*truth=SYNTHETIC_VIEW' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*packageManagerView=true package=com.fixhua.sampletarget selfFound=true absentFound=false truth=SYNTHETIC_VIEW' dist/runtime-logcat.txt
if grep -q 'legacyArtifactFallback=true' dist/runtime-logcat.txt; then
  echo "Legacy artifact fallback must not exist" >&2
  false
fi
echo "SYNTHETIC_PACKAGE_MANAGER_VIEW_PASS"
endstep

step "Verify DEX callsites drive foreign package and PackageManager perception"
grep -q 'FixHUARuntime.*sessionArtifact=true code=sample-code-v1 resource=sample-resource-v1' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*transformCallsites=2.*pmCallsites=1.*contextCallsites=1' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*appView=package=com.fixhua.sampletarget;gms=absent;' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*sourceReadOnly=true' dist/runtime-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-logcat.txt; then
  echo "Transformed session artifact verification failed" >&2
  false
fi
echo "SYNTHETIC_CONTEXT_IDENTITY_PASS"
endstep

step "Verify lifecycle recreation preserves synthetic reality and transform"
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
grep -q 'FixHUARuntime.*sessionArtifact=true.*transformCallsites=2.*pmCallsites=1.*contextCallsites=1.*appView=package=com.fixhua.sampletarget;gms=absent;' dist/runtime-lifecycle-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-lifecycle-logcat.txt; then
  echo "Transformed session did not survive recreation" >&2
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

step "Verify foreign-signed sample cannot bind Controller bridge"
adb logcat -c
adb shell am force-stop com.fixhua.sampletarget || true
adb shell am start -W -n com.fixhua.sampletarget/.SampleActivity
sleep 3
adb logcat -d -v brief > dist/sample-logcat.txt
cat dist/sample-logcat.txt
grep -q 'FixHUASample.*bridgeDenied=true' dist/sample-logcat.txt
if grep -q 'SECURITY_FAILURE' dist/sample-logcat.txt; then
  echo "Foreign sample unexpectedly reached Controller bridge" >&2
  false
fi
echo "FOREIGN_BRIDGE_DENIAL_PASS"
endstep

echo "SYNTHETIC_EMULATOR_SMOKE_PASS"
