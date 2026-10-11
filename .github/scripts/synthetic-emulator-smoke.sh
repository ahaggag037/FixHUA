#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p dist

on_error() {
  rc=$?
  echo "::error::Synthetic emulator smoke failed at line ${BASH_LINENO[0]} (exit ${rc})"
  echo "--- UID files ---"
  for f in dist/controller_uid.txt dist/runtime_uid.txt dist/sample_uid.txt dist/uid-boundary.txt; do
    if [[ -f "$f" ]]; then
      echo "### $f"
      cat "$f"
    fi
  done
  echo "--- runtime log tail ---"
  [[ -f dist/runtime-logcat.txt ]] && tail -n 260 dist/runtime-logcat.txt || true
  echo "--- lifecycle log tail ---"
  [[ -f dist/runtime-lifecycle-logcat.txt ]] && tail -n 300 dist/runtime-lifecycle-logcat.txt || true
  echo "--- sample log tail ---"
  [[ -f dist/sample-logcat.txt ]] && tail -n 200 dist/sample-logcat.txt || true
  exit "$rc"
}
trap on_error ERR

uid_of() {
  local pkg="$1"
  adb shell dumpsys package "$pkg" \
    | tr -d '\r' \
    | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' \
    | sed -n '1p'
}

step() {
  echo "::group::$1"
}
endstep() {
  echo "::endgroup::"
}

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
[[ "$controller_uid" != "$runtime_uid" ]]
[[ "$controller_uid" != "$sample_uid" ]]
[[ "$runtime_uid" != "$sample_uid" ]]
echo "UID_BOUNDARY_PASS"
endstep

step "Import foreign sample into Controller store and launch trusted Runtime session"
adb logcat -c
adb shell am force-stop com.fixhua.guard || true
adb shell am force-stop com.fixhua.runtime || true
# This activity is guarded by android.permission.DUMP and additionally refuses non-emulator
# hardware. It copies the installed foreign-signed sample into Controller-private storage,
# creates the persisted session, then launches Runtime with the session envelope.
adb shell am start -W -n com.fixhua.guard/.AcceptanceBootstrapActivity
sleep 6
adb logcat -d -v brief > dist/runtime-logcat.txt
cat dist/runtime-logcat.txt
grep -q 'FixHUAAcceptance.*sessionReady=true package=com.fixhua.sampletarget' dist/runtime-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationReady=true' dist/runtime-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationInvariant=true phase=activityOnCreate' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*bridgeAuthenticated=true protocol=2' dist/runtime-logcat.txt
if grep -q 'FixHUARuntime.*legacyArtifactFallback=true' dist/runtime-logcat.txt; then
  echo "Runtime unexpectedly used legacy installed-package fallback" >&2
  false
fi
echo "SESSION_BRIDGE_AUTH_PASS"
endstep

step "Verify Runtime receives read-only Controller artifact and loads code/resources"
grep -q 'FixHUARuntime.*sessionArtifact=true code=sample-code-v1 resource=sample-resource-v1' dist/runtime-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true.*imageReadOnly=true' dist/runtime-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-logcat.txt; then
  echo "Session artifact verification failed" >&2
  false
fi
echo "SESSION_ARTIFACT_LOADER_PASS"
endstep

step "Verify Application Resources and session artifact survive Activity recreation"
adb logcat -c
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
adb shell settings put system user_rotation 0
sleep 3
adb logcat -d -v brief > dist/runtime-lifecycle-logcat.txt
cat dist/runtime-lifecycle-logcat.txt

grep -q 'FixHUARuntimeLifecycle.*applicationConfigChanged=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntimeLifecycle.*applicationInvariant=true' dist/runtime-lifecycle-logcat.txt
grep -q 'FixHUARuntime.*sessionArtifact=true code=sample-code-v1 resource=sample-resource-v1' dist/runtime-lifecycle-logcat.txt
if grep -q 'FixHUARuntime.*sessionArtifact=false' dist/runtime-lifecycle-logcat.txt; then
  echo "Session artifact did not survive recreation" >&2
  false
fi

max_create_count="$(grep -o 'FixHUARuntime.*activityReady=true createCount=[0-9]*' dist/runtime-lifecycle-logcat.txt \
  | sed 's/.*createCount=//' \
  | sort -n \
  | tail -n 1)"
[[ -n "$max_create_count" ]]
(( max_create_count >= 2 ))

adb shell pidof com.fixhua.runtime | tee dist/runtime-pid-after-rotation.txt
grep -q 'com.fixhua.runtime/.RuntimeActivity' <(adb shell dumpsys activity activities | tr -d '\r')
echo "LIFECYCLE_SESSION_INTEGRITY_PASS createCount=$max_create_count"

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
