#!/usr/bin/env bash
set -Eeuo pipefail

baseline="${1:-dist/am-settings-shell-baseline.txt}"
[[ -s "$baseline" ]]

extract_value() {
  local key="$1"
  sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\(-\{0,1\}[0-9][0-9]*\).*/\1/p" "$baseline" | head -n 1
}

current="$(extract_value CUR_MAX_CACHED_PROCESSES)"
customized="$(extract_value mCustomizedMaxCachedProcesses)"
platform="$(extract_value max_cached_processes)"
override="$(extract_value mOverrideMaxCachedProcesses)"

[[ "$current" =~ ^[0-9]+$ ]]
(( current >= 1 && current <= 1024 ))

normalize_optional() {
  local value="$1"
  if [[ -z "$value" ]]; then
    printf '%s' -1
    return
  fi
  [[ "$value" =~ ^-?[0-9]+$ ]]
  if (( value == -1 || (value >= 1 && value <= 1024) )); then
    printf '%s' "$value"
    return
  fi
  echo "Invalid optional process-budget value: $value" >&2
  return 1
}

customized="$(normalize_optional "$customized")"
platform="$(normalize_optional "$platform")"
override="$(normalize_optional "$override")"

adb install -r dist/FixHUA-Controller.apk >/dev/null
adb logcat -c
adb shell am force-stop com.fixhua.guard || true
adb shell am start -W -n com.fixhua.guard/.ProcessBudgetImportActivity \
  --ei current "$current" \
  --ei customized "$customized" \
  --ei platform "$platform" \
  --ei override "$override" \
  | tee dist/process-budget-import-start.txt
sleep 2
adb logcat -d -v brief > dist/process-budget-import-logcat.txt
grep -q "FixHUAProcessBudgetImport.*accepted=true.*source=ADB_SHELL_SANITIZED.*current=${current}" \
  dist/process-budget-import-logcat.txt
grep -q "FixHUAHostProbe.*probeSuccess=true.*source=ADB_SHELL_SANITIZED.*current=${current}" \
  dist/process-budget-import-logcat.txt
printf 'current=%s customized=%s platform=%s override=%s\n' \
  "$current" "$customized" "$platform" "$override" \
  | tee dist/process-budget-import-sanitized.txt

echo "SANITIZED_PROCESS_BUDGET_IMPORT_PASS"
