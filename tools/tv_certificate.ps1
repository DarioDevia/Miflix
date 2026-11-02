# Public certificate metadata only. Accept one unambiguous identity across apksigner output versions.
function Get-MiFlixCertificateDigest {
    param([string[]]$Lines)
    $digests=@($Lines | Where-Object { $_ -match 'certificate SHA-256 digest:' } | ForEach-Object {
        ($_ -replace '^.*certificate SHA-256 digest:\s*','' -replace '[:\s]','').ToLowerInvariant()
    } | Sort-Object -Unique)
    if ($digests.Count -ne 1 -or $digests[0] -notmatch '^[a-f0-9]{64}$') {
        throw 'Expected one unambiguous public SHA-256 certificate digest.'
    }
    return $digests[0]
}
