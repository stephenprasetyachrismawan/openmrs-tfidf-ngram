<#
.SYNOPSIS
  Build the unifiedsearch module, copy the .omod into the running OpenMRS
  container, restart the backend, and wait until it is healthy again.

.DESCRIPTION
  There is no OpenMRS SDK server in this project (see docs/keputusan.md), so the
  install cycle is: package -> copy -> restart -> wait.

  Paths and names below were verified against the running stack:
    container      openmrs-distro-referenceapplication-backend-1
    module dir     /openmrs/data/modules   (volume openmrs-data)
  The module directory is NOT /usr/local/tomcat/.OpenMRS/modules on this image.

  All HTTP checks use 127.0.0.1. Never localhost: an Apache2 inside WSL owns
  port 80 on ::1 and answers 404 for every /openmrs/... path.
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

$RepoRoot      = Split-Path -Parent $PSScriptRoot
$ModuleDir     = Join-Path $RepoRoot 'backend\openmrs-module-tfidf-search'
$MavenBin      = Join-Path $RepoRoot 'tools\apache-maven-3.9.16\bin'
$Container     = 'openmrs-distro-referenceapplication-backend-1'
$ContainerDir  = '/openmrs/data/modules'
$HealthUrl     = 'http://127.0.0.1/openmrs/ws/rest/v1/session'

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }

# --- 1. Build --------------------------------------------------------------
if (-not $SkipBuild) {
    Write-Step 'Membangun modul (mvn clean package)'
    $env:Path = "$MavenBin;$env:Path"
    Push-Location $ModuleDir
    try {
        & mvn -B clean package
        if ($LASTEXITCODE -ne 0) { throw "mvn clean package gagal (exit $LASTEXITCODE)" }
    }
    finally { Pop-Location }
}

$omod = Get-ChildItem -Path (Join-Path $ModuleDir 'omod\target') -Filter '*.omod' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $omod) { throw "Tidak menemukan berkas .omod di omod\target" }
Write-Host "    berkas: $($omod.Name) ($($omod.Length) bait)"

# --- 2. Periksa target ------------------------------------------------------
Write-Step "Memeriksa direktori modul di container"
$dirCheck = & docker exec $Container sh -c "test -d $ContainerDir && echo ADA || echo TIDAK"
if ($dirCheck.Trim() -ne 'ADA') {
    throw "Direktori $ContainerDir tidak ada di $Container. Periksa ulang dengan: docker exec $Container find / -name '*.omod'"
}

# --- 3. Salin ---------------------------------------------------------------
Write-Step "Menyalin .omod ke ${Container}:$ContainerDir"
& docker cp $omod.FullName "${Container}:$ContainerDir/$($omod.Name)"
if ($LASTEXITCODE -ne 0) { throw "docker cp gagal (exit $LASTEXITCODE)" }

# --- 3b. Samakan kepemilikan berkas ------------------------------------------
# `docker cp` selalu menyalin sebagai root, sedangkan Tomcat berjalan sebagai
# uid lain (uid 1001 pada image ini). Berkas .omod milik root tidak bisa
# diganti/dihapus OpenMRS saat modul diperbarui. Kepemilikan diambil dari
# direktori modules itu sendiri supaya tidak ada uid yang ditulis keras.
Write-Step "Menyamakan kepemilikan .omod dengan direktori modul"
$ownerCmd = "stat -c '%u:%g' $ContainerDir"
$owner = (& docker exec $Container sh -c $ownerCmd).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($owner)) {
    throw "Gagal membaca kepemilikan $ContainerDir"
}
& docker exec -u root $Container sh -c "chown $owner '$ContainerDir/$($omod.Name)' && chmod 644 '$ContainerDir/$($omod.Name)'"
if ($LASTEXITCODE -ne 0) { throw "chown .omod gagal (exit $LASTEXITCODE)" }
Write-Host "    kepemilikan .omod -> $owner"

# --- 3c. Pastikan .openmrs-lib-cache bisa ditulis -----------------------------
# OpenMRS menghapus lalu membangun ulang cache ini setiap start. Kalau
# direktorinya milik root (bekas start sebelumnya, atau tersentuh perintah
# root), penghapusan gagal diam-diam: log penuh "could not remove directory",
# cache tetap versi lama, dan kelas modul yang berubah tidak ditemukan ->
# ClassNotFoundException -> SELURUH REST OpenMRS balas 500.
# Kepemilikannya diperbaiki, bukan cache-nya dihapus.
# Dipotong sebagai string biasa, bukan Split-Path: ContainerDir memakai
# pemisah POSIX sedangkan Split-Path mengembalikan pemisah Windows.
$dataDir  = $ContainerDir.Substring(0, $ContainerDir.LastIndexOf('/'))
$cacheDir = "$dataDir/.openmrs-lib-cache"
$cacheState = (& docker exec $Container sh -c "test -d '$cacheDir' && (touch '$cacheDir/.uji-tulis' 2>/dev/null && rm -f '$cacheDir/.uji-tulis' && echo BISA || echo TERKUNCI) || echo TIDAKADA").Trim()
if ($cacheState -eq 'TERKUNCI') {
    Write-Step "Memperbaiki kepemilikan $cacheDir (tidak bisa ditulis Tomcat)"
    & docker exec -u root $Container sh -c "chown -R $owner '$cacheDir'"
    if ($LASTEXITCODE -ne 0) { throw "chown lib-cache gagal (exit $LASTEXITCODE)" }
    Write-Host "    kepemilikan cache -> $owner"
} else {
    Write-Host "    lib-cache: $cacheState (tidak perlu diperbaiki)"
}

# --- 4. Restart -------------------------------------------------------------
Write-Step "Merestart container backend"
& docker restart $Container | Out-Null
if ($LASTEXITCODE -ne 0) { throw "docker restart gagal (exit $LASTEXITCODE)" }

# --- 5. Tunggu sehat --------------------------------------------------------
Write-Step "Menunggu OpenMRS sehat (maksimum $TimeoutSeconds detik)"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $health = (& docker inspect -f '{{.State.Health.Status}}' $Container).Trim()
    $code = ''
    try {
        $code = (& curl.exe -s -o NUL -w '%{http_code}' $HealthUrl).Trim()
    } catch { $code = '000' }
    Write-Host "    health=$health http=$code"
    if ($health -eq 'healthy' -and $code -eq '200') { $ready = $true; break }
}
if (-not $ready) { throw "OpenMRS tidak sehat dalam $TimeoutSeconds detik. Lihat: docker logs --tail 200 $Container" }

# --- 6. Ringkasan -----------------------------------------------------------
Write-Step 'Selesai'
& docker exec $Container sh -c "ls -l $ContainerDir | grep unifiedsearch"
Write-Host ''
Write-Host 'Periksa status modul di:' -ForegroundColor Green
Write-Host '  http://127.0.0.1/openmrs/admin/modules/module.list'
