# Run in Windows PowerShell, not Android Studio's Run deployment.
[CmdletBinding()]
param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Get-ChildItem (Join-Path $AndroidSdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'No installed Android SDK Build Tools found. Supply -AndroidSdk.' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
if (-not (Test-Path $apksigner)) { throw 'Official apksigner.bat not found.' }
$null = Get-Command py -ErrorAction Stop
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    # Explicit target set prevents carrying a single emulator ABI into this build.
    # testOnly=false is for this normal APK task only; no global/manifest override.
    & .\gradlew.bat :app:testTvDebugUnitTest :app:testMobileDebugUnitTest :app:lintTvDebug :app:lintMobileDebug :app:assembleTvDebug `
        '-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64' `
        '-Pandroid.injected.testOnly=false'
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed. Do not distribute the APK.' }
    $tvApkPath = & py -3 tools/locate_tv_apk.py
    if ($LASTEXITCODE -ne 0) { throw 'Cannot locate a unique TV Debug APK from AGP metadata.' }
    & py -3 tools/verify_tv_physical_apk.py $tvApkPath --apksigner $apksigner
    if ($LASTEXITCODE -ne 0) { throw 'APK inspection failed. Do not distribute the APK.' }
    Write-Host 'Packaging and signature checked; installation/playback on a physical TV remain pending.'
} finally { Pop-Location }
