# Lingkungan pengembangan — fakta terverifikasi

Diverifikasi 20 Agustus 2026 langsung pada mesin pengembang. Angka dan nama di
sini hasil pemeriksaan, bukan asumsi.

## Stack Docker

Hanya ada **satu** stack. Stack duplikat `openmrs-fresh` sudah dihapus pada
tanggal di atas karena demo data-nya gagal termuat (0 obat, 0 pasien, 0 form).

| Container | Peran | Port |
|---|---|---|
| `openmrs-distro-referenceapplication-gateway-1` | nginx | `0.0.0.0:80 -> 80` |
| `openmrs-distro-referenceapplication-backend-1` | OpenMRS core | 8080 (internal) |
| `openmrs-distro-referenceapplication-frontend-1` | SPA O3 | 80 (internal) |
| `openmrs-distro-referenceapplication-db-1` | MariaDB 10.11.7 | 3306 (internal) |

Volume: `openmrs-distro-referenceapplication_db-data`,
`openmrs-distro-referenceapplication_openmrs-data`.

Compose ada di `openmrs-distro-referenceapplication/`, dijalankan dengan
`docker-compose.yml` + `docker-compose.override.yml`.

## JEBAKAN — pakai 127.0.0.1, JANGAN localhost

Ada **Apache2 di dalam WSL** yang mendengarkan port 80 pada `::1`. Karena
Windows meresolusi `localhost` ke IPv6 `::1` lebih dulu, seluruh permintaan ke
`http://localhost/openmrs/...` mendarat di Apache itu, bukan di gateway OpenMRS.

Terbukti:

```
http://localhost/openmrs/ws/rest/v1/session   -> 404  (Apache2 WSL)
http://127.0.0.1/openmrs/ws/rest/v1/session   -> 200  (OpenMRS)
```

`http://localhost/` mengembalikan halaman "Apache2 Ubuntu Default Page".
Kalau melihat halaman itu, Anda sedang menatap server yang salah.

**Aturan: seluruh URL di skrip, uji, dan dokumen memakai `http://127.0.0.1`.**

Kalau ingin `localhost` berfungsi, hentikan Apache di WSL
(`sudo service apache2 stop`) — tetapi periksa dulu, Apache itu mungkin dipakai
proyek lain.

## Alamat penting

| Keperluan | URL |
|---|---|
| Antarmuka O3 | `http://127.0.0.1/openmrs/spa/home` |
| Antarmuka lama | `http://127.0.0.1/openmrs/` |
| REST session | `http://127.0.0.1/openmrs/ws/rest/v1/session` |
| Endpoint modul (nanti) | `http://127.0.0.1/openmrs/ws/rest/v1/unifiedsearch` |

## Akses database

```
host      127.0.0.1  (dari dalam jaringan docker: db)
database  openmrs
user      openmrs / openmrs
root      root / openmrs
```

Port 3306 **tidak** dipetakan ke host. Untuk query langsung, lewat container:

```powershell
docker exec openmrs-distro-referenceapplication-db-1 `
  mysql -uroot -popenmrs -N -B -e "SELECT COUNT(*) FROM openmrs.concept;"
```

## Jumlah baris terverifikasi

| Tabel | Jumlah |
|---|---|
| concept | 4.252 |
| drug | 322 |
| patient | 100 |
| location | 61 |
| form | 10 |
| provider | 6 |

`concept` = 4.252 di database, sementara eksperimen memakai **4.249** dokumen —
selisih 3 karena konsep yang di-retire disaring saat ekspor. Ini wajar, bukan
kesalahan. `referencedemodata.started` bernilai `true`.

## Perkakas

Maven 3.9.16 ada di `tools/apache-maven-3.9.16/` (portabel, tidak dipasang ke
sistem). Kalau `mvn` tidak dikenali, pakai:

```powershell
$env:Path = "C:\src\tfidf-openmrs\tools\apache-maven-3.9.16\bin;$env:Path"
```

## Cara memeriksa keadaan sehat

```powershell
docker ps --format "{{.Names}} | {{.Status}}"
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1/openmrs/ws/rest/v1/session
```

Harus: 4 container `Up (healthy)`, dan kode HTTP `200`.
