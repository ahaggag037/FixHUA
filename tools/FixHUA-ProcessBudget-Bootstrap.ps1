$ErrorActionPreference = 'Stop'

function Get-BudgetValue {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Key,
        [int]$Default = -1,
        [switch]$Required
    )

    $escaped = [regex]::Escape($Key)
    $match = [regex]::Match($Text, "(?m)^\s*$escaped\s*=\s*(-?\d+)\s*$")
    if (-not $match.Success) {
        if ($Required) { throw "FixHUA: required key '$Key' was not found in dumpsys activity settings." }
        return $Default
    }

    $value = [int]$match.Groups[1].Value
    if ($Required) {
        if ($value -lt 1 -or $value -gt 1024) { throw "FixHUA: '$Key' is outside the accepted range." }
    } elseif ($value -ne -1 -and ($value -lt 1 -or $value -gt 1024)) {
        throw "FixHUA: '$Key' is outside the accepted range."
    }
    return $value
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'FixHUA: adb was not found in PATH. Install Android Platform Tools and enable USB debugging first.'
}

$devices = (& adb devices) -join "`n"
if ($devices -notmatch '(?m)^\S+\s+device\s*$') {
    throw 'FixHUA: no authorized Android device is connected through adb.'
}

# Raw dumpsys text exists only in this PowerShell process memory. It is never sent to or stored by FixHUA.
$dump = (& adb shell dumpsys activity settings 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'FixHUA: adb shell dumpsys activity settings failed.' }

$current = Get-BudgetValue -Text $dump -Key 'CUR_MAX_CACHED_PROCESSES' -Required
$customized = Get-BudgetValue -Text $dump -Key 'mCustomizedMaxCachedProcesses'
$platform = Get-BudgetValue -Text $dump -Key 'max_cached_processes'
$override = Get-BudgetValue -Text $dump -Key 'mOverrideMaxCachedProcesses'

Write-Host "FixHUA sanitized process budget: current=$current customized=$customized platform=$platform override=$override"

& adb shell am start -W -n com.fixhua.guard/.ProcessBudgetImportActivity `
    --ei current $current `
    --ei customized $customized `
    --ei platform $platform `
    --ei override $override
if ($LASTEXITCODE -ne 0) {
    throw 'FixHUA: Android rejected the protected import Activity. Confirm FixHUA is installed and USB debugging is authorized.'
}

Write-Host 'FixHUA process-budget bootstrap completed. Re-open FixHUA and run Check Now.'
