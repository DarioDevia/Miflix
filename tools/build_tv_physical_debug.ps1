# Run in Windows PowerShell, not Android Studio's Run deployment.
[CmdletBinding()]
param([string]$AndroidSdk = $env:ANDROID_HOME, [switch]$DiagnosticWithoutTelegram)
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
$previousSdkEnv=$env:ANDROID_HOME
$env:ANDROID_HOME=$AndroidSdk
$familyFlag=if ($DiagnosticWithoutTelegram) { 'false' } else { 'true' }
$telegramMode=if ($DiagnosticWithoutTelegram) { 'ci-empty' } else { 'private-preconfigured' }
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $configPresent=Test-Path 'telegram.local.properties' -PathType Leaf
    if (-not $DiagnosticWithoutTelegram -and -not $configPresent) {
        throw 'Falta telegram.local.properties en la raiz. Restaurar el archivo local original antes de compilar la APK familiar.'
    }
    if ($DiagnosticWithoutTelegram -and $configPresent) {
        throw 'DiagnosticWithoutTelegram requires a separate checkout without local properties. No configured values will be silently replaced.'
    }
    if (-not (Test-Path (Join-Path $env:USERPROFILE '.android\debug.keystore') -PathType Leaf)) {
        throw 'Original local debug key missing. Restore it; do not generate another identity to update an existing installation.'
    }
    # Explicit target set prevents carrying a single emulator ABI into this build.
    # testOnly=false is for this normal APK task only; no global/manifest override.
    & .\gradlew.bat :app:testTvDebugUnitTest :app:testTvReleaseUnitTest :app:testMobileDebugUnitTest :app:lintTvDebug :app:lintTvRelease :app:lintMobileDebug :app:assembleTvDebug :app:assembleTvRelease :app:assembleMobileDebug `
        '-Pandroid.injected.build.abi=armeabi-v7a,arm64-v8a,x86,x86_64' `
        '-Pandroid.injected.testOnly=false' `
        "-PmiflixTvFamilyBuild=$familyFlag"
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed. Do not distribute the APK.' }
    $tvApkPath = & py -3 tools/locate_tv_apk.py
    if ($LASTEXITCODE -ne 0) { throw 'Cannot locate a unique TV Debug APK from AGP metadata.' }
    & py -3 tools/verify_tv_physical_apk.py $tvApkPath --apksigner $apksigner
    if ($LASTEXITCODE -ne 0) { throw 'APK inspection failed. Do not distribute the APK.' }
    $releasePath = & py -3 tools/locate_tv_apk.py --variant tvRelease
    $mobilePath = & py -3 tools/locate_tv_apk.py --variant mobileDebug
    & py -3 tools/inspect_tv015_apks.py --debug $tvApkPath --release $releasePath --mobile $mobilePath --apksigner $apksigner --telegram-mode $telegramMode
    if ($LASTEXITCODE -ne 0) { throw 'Home/security/variant inspection failed.' }
    if ($DiagnosticWithoutTelegram) {
        Write-Host 'DIAGNOSTIC APK WITHOUT PRECONFIGURATION. Not the configured family delivery.'
    } else {
        Write-Host 'PRIVATE configured TV APK built locally. Do not upload this APK or its generated sources to GitHub or public artifacts.'
    }
    Write-Host 'Packaging checked; compare the installed certificate before updating. Real Telegram login remains a device test.'
} finally { Pop-Location; $env:ANDROID_HOME=$previousSdkEnv }
