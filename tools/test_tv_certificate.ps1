$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'tv_certificate.ps1')
$a='a'*64
$b='b'*64
foreach ($header in @('Signer #1 certificate SHA-256 digest: ','V2 Signer: certificate SHA-256 digest: ')) {
    if ((Get-MiFlixCertificateDigest -Lines @(($header+$a))) -ne $a) { throw 'Valid public signer format rejected.' }
}
if ((Get-MiFlixCertificateDigest -Lines @("Signer #1 certificate SHA-256 digest: $a","V2 Signer: certificate SHA-256 digest: $a")) -ne $a) { throw 'Repeated identical identity rejected.' }
foreach ($case in @('missing','ambiguous','malformed')) {
    $lines=switch ($case) {
        'missing' { @('No public certificate') }
        'ambiguous' { @("Signer #1 certificate SHA-256 digest: $a","Signer #2 certificate SHA-256 digest: $b") }
        'malformed' { @('V2 Signer: certificate SHA-256 digest: short') }
    }
    $rejected=$false
    try { $null=Get-MiFlixCertificateDigest -Lines $lines } catch { $rejected=$true }
    if (-not $rejected) { throw 'Unsafe public certificate input accepted.' }
}
Write-Host '6 public-certificate parser cases passed; no keys, passwords, APK signing or ADB accessed.'
