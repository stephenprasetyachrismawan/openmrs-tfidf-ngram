<#
.SYNOPSIS
  Build frontend/esm-unified-search dan pasang ke container frontend RefApp
  yang sedang berjalan, dengan menyisipkan satu kunci ke importmap.json
  yang sudah ada — bukan menimpanya (tugas 10).

.DESCRIPTION
  importmap.json berisi puluhan app resmi RefApp. Skrip ini menyalin
  salinan asli ke importmap.json.sebelum-unifiedsearch (sekali saja, tidak
  ditimpa pada jalan berikutnya), membaca JSON-nya, menambah satu kunci
  "@openmrs/esm-unified-search-app", menulis kembali, lalu me-reload nginx
  di container frontend supaya konfigurasi statis yang di-cache browser
  tidak perlu — berkas .js dan importmap.json sendiri disajikan langsung
  oleh nginx tanpa build ulang container.

  Semua HTTP lewat 127.0.0.1, bukan localhost (CLAUDE.md aturan 7).
#>
[CmdletBinding()]
param(
    [switch]$SkipInstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$RepoRoot     = Split-Path -Parent $PSScriptRoot
$AppDir       = Join-Path $RepoRoot 'frontend\esm-unified-search'
$DistDir      = Join-Path $AppDir 'dist'
$Container    = 'openmrs-distro-referenceapplication-frontend-1'
$ContainerRoot = '/usr/share/nginx/html'

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }

if (-not (Test-Path (Join-Path $AppDir 'package.json'))) {
    throw "Tidak ada package.json di $AppDir. Jalankan tugas 10 dulu (scaffold ESM)."
}

$pkg = Get-Content (Join-Path $AppDir 'package.json') -Raw | ConvertFrom-Json
$pkgName = $pkg.name                      # '@openmrs/esm-unified-search-app'
$shortName = ($pkgName -replace '^@openmrs/', '')   # 'esm-unified-search-app'
$dirName = "openmrs-$shortName-$($pkg.version)"     # 'openmrs-esm-unified-search-app-0.1.0'
$entryFile = "openmrs-$shortName.js"                # 'openmrs-esm-unified-search-app.js'

Push-Location $AppDir
try {
    if (-not $SkipInstall) {
        Write-Step 'npm ci'
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw "npm ci gagal (exit $LASTEXITCODE)" }
    }
    if (-not $SkipBuild) {
        Write-Step 'npm run build'
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "npm run build gagal (exit $LASTEXITCODE)" }
    }
}
finally { Pop-Location }

if (-not (Test-Path (Join-Path $DistDir $entryFile))) {
    throw "Berkas $entryFile tidak ditemukan di $DistDir setelah build. Periksa nama entry di dist/*.buildmanifest.json."
}

# --- Salin dist/ ke container -------------------------------------------
Write-Step "Menyalin dist/ ke ${Container}:$ContainerRoot/$dirName"
& docker exec $Container sh -c "rm -rf $ContainerRoot/$dirName && mkdir -p $ContainerRoot/$dirName"
if ($LASTEXITCODE -ne 0) { throw "docker exec (mkdir) gagal" }
& docker cp "$DistDir/." "${Container}:$ContainerRoot/$dirName/"
if ($LASTEXITCODE -ne 0) { throw "docker cp gagal (exit $LASTEXITCODE)" }

# --- Sisipkan satu kunci ke importmap.json, jangan menimpa ---------------
Write-Step 'Menyisipkan entri ke importmap.json (menyimpan salinan asli sekali)'
$tmpImportmap = Join-Path $env:TEMP 'unifiedsearch-importmap.json'
& docker cp "${Container}:$ContainerRoot/importmap.json" $tmpImportmap
if ($LASTEXITCODE -ne 0) { throw "docker cp importmap.json gagal" }

$backupPath = Join-Path $RepoRoot 'docs\arsip\importmap.json.sebelum-unifiedsearch'
if (-not (Test-Path $backupPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $backupPath) | Out-Null
    Copy-Item $tmpImportmap $backupPath
    Write-Host "    salinan asli disimpan: $backupPath"
}

$importmap = Get-Content $tmpImportmap -Raw | ConvertFrom-Json
$relPath = "./$dirName/$entryFile"
$importmap.imports | Add-Member -NotePropertyName $pkgName -NotePropertyValue $relPath -Force
$json = $importmap | ConvertTo-Json -Depth 10 -Compress
# Set-Content -Encoding utf8 menulis BOM, yang membuat JSON.parse() browser gagal.
# UTF8Encoding($false) menulis UTF-8 tanpa BOM.
[System.IO.File]::WriteAllText($tmpImportmap, $json, (New-Object System.Text.UTF8Encoding($false)))

& docker cp $tmpImportmap "${Container}:$ContainerRoot/importmap.json"
if ($LASTEXITCODE -ne 0) { throw "docker cp importmap.json (tulis kembali) gagal" }
Remove-Item $tmpImportmap -Force

# --- Sisipkan entri ke routes.registry.json, jangan menimpa --------------
# importmap.json saja TIDAK CUKUP: shell RefApp memuat halaman/menu dari
# routes.registry.json (dibuktikan lewat network request nyata di browser,
# bukan tebakan) -- itu daftar gabungan routes.json semua app. Tanpa entri
# di sini, JS kita tidak pernah diminta/di-import walau ada di importmap.
Write-Step 'Menyisipkan entri ke routes.registry.json (menyimpan salinan asli sekali)'
$tmpRegistry = Join-Path $env:TEMP 'unifiedsearch-routes-registry.json'
& docker cp "${Container}:$ContainerRoot/routes.registry.json" $tmpRegistry
if ($LASTEXITCODE -ne 0) { throw "docker cp routes.registry.json gagal" }

$registryBackupPath = Join-Path $RepoRoot 'docs\arsip\routes.registry.json.sebelum-unifiedsearch'
if (-not (Test-Path $registryBackupPath)) {
    Copy-Item $tmpRegistry $registryBackupPath
    Write-Host "    salinan asli disimpan: $registryBackupPath"
}

$ourRoutes = Get-Content (Join-Path $AppDir 'src\routes.json') -Raw | ConvertFrom-Json
$ourRoutes | Add-Member -NotePropertyName 'version' -NotePropertyValue $pkg.version -Force

$registry = Get-Content $tmpRegistry -Raw | ConvertFrom-Json
$registry | Add-Member -NotePropertyName $pkgName -NotePropertyValue $ourRoutes -Force
$registryJson = $registry | ConvertTo-Json -Depth 20 -Compress
[System.IO.File]::WriteAllText($tmpRegistry, $registryJson, (New-Object System.Text.UTF8Encoding($false)))

& docker cp $tmpRegistry "${Container}:$ContainerRoot/routes.registry.json"
if ($LASTEXITCODE -ne 0) { throw "docker cp routes.registry.json (tulis kembali) gagal" }
Remove-Item $tmpRegistry -Force

# --- Reload nginx (statis, tidak perlu restart container) ---------------
Write-Step 'Reload nginx di container frontend'
& docker exec $Container nginx -s reload
if ($LASTEXITCODE -ne 0) { throw "nginx -s reload gagal (exit $LASTEXITCODE)" }

Write-Step 'Selesai'
Write-Host "Entri importmap: `"$pkgName`": `"$relPath`""
Write-Host 'Periksa di browser: http://127.0.0.1/openmrs/spa/unified-search'
