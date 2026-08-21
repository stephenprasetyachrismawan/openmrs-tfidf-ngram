<#
.DESCRIPTION
  Selalu 127.0.0.1, jangan localhost — Apache2 di WSL menguasai port 80 pada
  ::1 dan mengembalikan 404 Apache2 Ubuntu Default Page untuk seluruh path
  /openmrs/... (lihat docs/lingkungan.md, CLAUDE.md aturan 7).
#>
[CmdletBinding()]
param(
    [int]$Port = 80
)

$ErrorActionPreference = 'Stop'
$baseUrl = "http://127.0.0.1:$Port"
$checks = @(
    @{ Name = 'Gateway'; Url = "$baseUrl/" },
    @{ Name = 'O3 SPA'; Url = "$baseUrl/openmrs/spa/home" },
    @{ Name = 'REST session'; Url = "$baseUrl/openmrs/ws/rest/v1/session" }
)

foreach ($check in $checks) {
    try {
        $response = Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 30
        Write-Output ("{0}: HTTP {1}" -f $check.Name, $response.StatusCode)
    }
    catch {
        $response = $_.Exception.Response
        if ($null -ne $response) {
            $status = [int]$response.StatusCode
            if ($status -in 401, 302) {
                Write-Output ("{0}: HTTP {1} (expected unauthenticated/redirect response)" -f $check.Name, $status)
            }
            else {
                throw ("{0}: unexpected HTTP {1}" -f $check.Name, $status)
            }
        }
        else {
            throw ("{0}: request failed: {1}" -f $check.Name, $_.Exception.Message)
        }
    }
}

# --- Determinisme C2: dua panggilan yang sama harus byte-identik ---------
# waktu_ms dipindah ke header X-Unifiedsearch-Waktu-Ms (bukan badan JSON),
# supaya badan tetap sama persis antar panggilan. Lihat docs/keputusan.md.
$searchUrl = "$baseUrl/openmrs/ws/rest/v1/unifiedsearch?q=diabete%20melitus&mode=e3"
try {
    $session = Invoke-RestMethod -Uri "$baseUrl/openmrs/ws/rest/v1/session" -Method Get `
        -Headers @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:Admin123')) } `
        -SessionVariable webSession -TimeoutSec 30 | Out-Null

    $r1 = Invoke-WebRequest -Uri $searchUrl -UseBasicParsing -WebSession $webSession -TimeoutSec 30
    $r2 = Invoke-WebRequest -Uri $searchUrl -UseBasicParsing -WebSession $webSession -TimeoutSec 30

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $bytes1 = [Text.Encoding]::UTF8.GetBytes($r1.Content)
    $bytes2 = [Text.Encoding]::UTF8.GetBytes($r2.Content)
    $sha1 = -join ($sha256.ComputeHash($bytes1) | ForEach-Object { $_.ToString('x2') })
    $sha2 = -join ($sha256.ComputeHash($bytes2) | ForEach-Object { $_.ToString('x2') })

    if ($sha1 -ne $sha2) {
        throw "Determinisme C2 GAGAL: badan /unifiedsearch berbeda antar dua panggilan ($sha1 vs $sha2)"
    }
    Write-Output ("Determinisme C2: OK (badan identik, SHA-256 {0})" -f $sha1)

    $waktu1 = $r1.Headers['X-Unifiedsearch-Waktu-Ms']
    $waktu2 = $r2.Headers['X-Unifiedsearch-Waktu-Ms']
    Write-Output ("Latensi via header: {0} ms lalu {1} ms" -f $waktu1, $waktu2)
}
catch {
    throw ("Determinisme C2: gagal menjalankan uji: {0}" -f $_.Exception.Message)
}
