# Offline/local signing only. No key creation, no passwords in source or command history.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [Parameter(Mandatory=$true)][string]$KeyAlias,
    [Parameter(Mandatory=$true)][string]$ExpectedCertificateSha256,
    [string]$AndroidSdk=$env:ANDROID_HOME
)
$ErrorActionPreference='Stop'
if (-not (Test-Path $KeystorePath -PathType Leaf)) { throw 'Existing signing key required; no new key will be created.' }
if (-not $env:MIFLIX_KS_PASS -or -not $env:MIFLIX_KEY_PASS) { throw 'Set MIFLIX_KS_PASS and MIFLIX_KEY_PASS locally without putting passwords in files.' }
if (Test-Path $OutputApk) { throw 'Output already exists; choose another path.' }
if (-not $AndroidSdk) { $AndroidSdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$tools=Get-ChildItem (Join-Path $AndroidSdk 'build-tools') -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$signer=Join-Path $tools.FullName 'apksigner.bat'
$expected=($ExpectedCertificateSha256 -replace '[:\s]','').ToLowerInvariant()
if ($expected -notmatch '^[a-f0-9]{64}$') { throw 'Supply the public SHA-256 certificate fingerprint from the installed APK.' }
& py -3 tools/inspect_tv015_apks.py --signing-input $UnsignedApk
if ($LASTEXITCODE -ne 0) { throw 'Candidate identity/packaging inspection failed before signing.' }
& $signer sign --ks $KeystorePath --ks-key-alias $KeyAlias --ks-pass env:MIFLIX_KS_PASS --key-pass env:MIFLIX_KEY_PASS --out $OutputApk $UnsignedApk
if ($LASTEXITCODE -ne 0) { throw 'Signing failed. Do not install.' }
$cert=& $signer verify --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
$line=$cert | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' } | Select-Object -First 1
$actual=($line -replace '^.*digest:\s*','' -replace '[:\s]','').ToLowerInvariant()
if ($actual -ne $expected) {
    Remove-Item $OutputApk
    throw 'Certificate does not match the installed application. Candidate discarded; do not uninstall the previous app.'
}
Write-Host 'Signature matches the supplied installed-APK fingerprint. Check package/version/ARM and validate on-device before distribution.'
