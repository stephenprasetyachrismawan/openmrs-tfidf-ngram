# Pencarian Terpadu Tahan Salah Ketik untuk OpenMRS

Modul OpenMRS (`.omod`) yang menambahkan pencarian tahan salah ketik ke seluruh
data OpenMRS sekaligus — konsep, obat, pasien, formulir, lokasi, penyedia
layanan, hasil laboratorium, dan kondisi — dengan TF-IDF pada **dua unit
sekaligus**: token kata dan kepingan karakter 4 huruf.

Tanpa Lucene. Tanpa server indeks terpisah. Tanpa GPU. Tanpa sambungan internet.

```
Ketik:  "diabete melitus"     →  Diabetes mellitus            (peringkat 1)
Ketik:  "pulm edem"           →  Pulmonary edema              (peringkat 1)
Ketik:  "amoxicilin"          →  Amoxicillin 500mg tablet     (peringkat 1)
```

| | |
|---|---|
| **Hasil utama** | kueri tanpa hasil **15,4% → 0,0%**; nDCG@10 0,670 vs 0,631 (260 kueri uji, p=0,079 — tidak signifikan) |
| **Ketahanan salah ketik** | nDCG pada kueri typo **0,086 → 0,713** (hampir 8×) |
| **Pembangunan indeks** | 17 indeks, 8.045 dokumen, 35.914 *surface form*, **2,08 detik** |
| **Latensi kueri** | p95 **50 ms** (setelah JVM hangat) |
| **Laporan lengkap** | [`article/main.pdf`](article/main.pdf) |

> **Kejujuran hasil.** Seluruh angka berasal dari korpus **8 entitas**, sama
> persis dengan cakupan modul yang dipasang. Pada korpus itu, keunggulan
> nDCG@10 sistem usulan atas baseline **tidak mencapai signifikansi**
> (+0,039, p=0,079), dan terhadap pencarian konsep OpenMRS asli juga tidak
> (+0,055, p=0,269). Yang bertahan adalah hilangnya layar kosong dan
> ketahanan salah ketik. Manfaatnya juga **tidak merata**: besar pada entitas
> berciri kamus, negatif pada entitas instans per-pasien. Rinciannya di
> [`docs/ringkasan-hasil.md`](docs/ringkasan-hasil.md).

---

## Daftar isi

1. [Untuk siapa dokumen ini](#1-untuk-siapa-dokumen-ini)
2. [Prasyarat](#2-prasyarat)
3. [Instalasi dari nol — langkah demi langkah](#3-instalasi-dari-nol--langkah-demi-langkah)
4. [Memakai modul](#4-memakai-modul)
5. [Penanganan galat](#5-penanganan-galat)
6. [Perintah harian](#6-perintah-harian)
7. [Peta kode — di mana letak tiap komponen](#7-peta-kode--di-mana-letak-tiap-komponen)
8. [Panduan pengembangan lanjutan](#8-panduan-pengembangan-lanjutan)
9. [Menjalankan ulang eksperimen](#9-menjalankan-ulang-eksperimen)
10. [Aturan yang mengikat kontributor](#10-aturan-yang-mengikat-kontributor)
11. [Dokumentasi lain](#11-dokumentasi-lain)

---

## 1. Untuk siapa dokumen ini

| Anda ingin… | Mulai dari |
|---|---|
| **memakai** modulnya | [`docs/panduan-pengguna.md`](docs/panduan-pengguna.md) |
| **memasang** dari nol | [Bagian 3](#3-instalasi-dari-nol--langkah-demi-langkah) di bawah |
| **mengembangkan** kodenya | [Bagian 7](#7-peta-kode--di-mana-letak-tiap-komponen) dan [8](#8-panduan-pengembangan-lanjutan) |
| **mengulang hasil penelitian** | [Bagian 9](#9-menjalankan-ulang-eksperimen) dan [`docs/reproduksi.md`](docs/reproduksi.md) |
| **membaca laporannya** | [`article/main.pdf`](article/main.pdf) |

---

## 2. Prasyarat

### Perangkat lunak

| Alat | Versi minimum | Versi yang diuji | Untuk apa |
|---|---|---|---|
| **Docker Desktop** | dengan `docker compose` v2 | — | menjalankan stack OpenMRS |
| **Git** | 2.45+ | 2.45 | klon repositori |
| **JDK** | 17 | Oracle JDK 17.0.17 | membangun modul |
| **Node.js** | 20+ | v26.7.0 | membangun antarmuka ESM |
| **npm** | 10+ | 11.11.0 | idem (bukan yarn) |
| **Python** | 3.9+ | 3.14.3 | menjalankan eksperimen |
| **PowerShell** | 5.1 (bawaan Windows) | 5.1 | skrip `scripts/*.ps1` |
| **Maven** | 3.9.x | 3.9.16 (portabel) | opsional, lihat catatan |

**Maven tidak perlu dipasang ke sistem.** Salinan portabel diletakkan di
`tools/apache-maven-3.9.16/` (folder ini di-*gitignore*, tiap pengembang
menyalin sendiri). Unduh Apache Maven 3.9.16 binary zip, ekstrak ke sana.
Kalau sudah punya `mvn` di PATH, pakai itu saja.

### Sumber daya mesin

| Kebutuhan | Nilai |
|---|---|
| RAM bebas | ≥ 6 GB (stack OpenMRS + build) |
| Ruang disk | ≥ 10 GB |
| Port yang harus bebas | **80** (gateway OpenMRS) |

### Catatan sistem operasi

Skrip pemasangan (`scripts/*.ps1`) ditulis untuk **PowerShell 5.1 di Windows**
dan belum diverifikasi pada PowerShell Core di Linux/macOS. Langkah Docker dan
Maven-nya sendiri lintas-platform; hanya lapisan otomasinya yang belum diuji.

---

## 3. Instalasi dari nol — langkah demi langkah

Ikuti berurutan. Setiap langkah punya **cara memastikan berhasil** — jangan
lanjut sebelum langkah sebelumnya lulus.

---

### Langkah 0 — Periksa port 80 sudah bebas

Ini penyebab kegagalan nomor satu, dan paling membingungkan kalau tidak
diperiksa lebih dulu.

```powershell
netstat -ano | Select-String ":80\s"
```

**Yang diharapkan:** tidak ada baris `LISTENING` pada port 80, atau hanya milik
Docker kalau stack sudah pernah jalan.

Kalau ada proses lain (sering: Apache di dalam WSL, IIS, Skype), hentikan dulu.
Untuk Apache di WSL:

```bash
sudo service apache2 stop
```

> **Kenapa ini penting.** Windows meresolusi `localhost` ke IPv6 `::1` lebih
> dulu. Kalau ada server lain mendengarkan `::1:80`, seluruh permintaan ke
> `http://localhost/openmrs/...` mendarat di sana dan mengembalikan **404**,
> sementara modulnya sendiri baik-baik saja.
>
> **Aturan proyek: selalu `http://127.0.0.1`, jangan pernah `localhost`.**
> Terbukti:
> ```
> http://localhost/openmrs/ws/rest/v1/session   -> 404  (server lain)
> http://127.0.0.1/openmrs/ws/rest/v1/session   -> 200  (OpenMRS)
> ```

---

### Langkah 1 — Klon repositori penelitian

```bash
git clone https://github.com/stephenprasetyachrismawan/openmrs-tfidf-ngram.git
cd openmrs-tfidf-ngram
```

**Cara tahu berhasil:** `ls` menunjukkan `backend/`, `frontend/`, `riset/`,
`docs/`, `scripts/`, `article/`, `tugas/`, dan `CLAUDE.md`.

---

### Langkah 2 — Klon stack Docker OpenMRS

Stack OpenMRS **bukan bagian dari repositori ini**. Ia adalah klon hulu dengan
`.git` sendiri, sengaja di-*gitignore*.

```bash
git clone https://github.com/openmrs/openmrs-distro-referenceapplication.git
cd openmrs-distro-referenceapplication
git checkout a09ff7a0136dee61f3d2281e8a7bd26b175858c9
cd ..
```

Commit di atas adalah versi yang sudah diverifikasi jalan untuk proyek ini.

**Dua syarat yang tidak boleh dilanggar:**

1. Nama folder **harus persis** `openmrs-distro-referenceapplication`. Docker
   Compose mengambil nama project dari nama folder.
2. Folder itu harus **sejajar** dengan folder repositori ini, bukan di dalamnya:

```
C:\src\
  +-- openmrs-tfidf-ngram\                    <- repositori ini
  +-- openmrs-distro-referenceapplication\    <- stack Docker
```

> Susunan alternatif (stack di dalam repositori) juga bekerja selama nama
> foldernya sama; skrip mencari di kedua tempat.

**Cara tahu berhasil:** folder itu berisi `docker-compose.yml` dan
`docker-compose.override.yml`.

---

### Langkah 3 — Nyalakan stack

```powershell
cd openmrs-distro-referenceapplication
docker compose up -d
cd ..
```

> **JANGAN memakai `-p <nama-lain>`.** Itu membuat stack duplikat kedua yang
> memakan port dan RAM tanpa kelihatan. Proyek ini hanya boleh punya **satu**
> stack.

**Cara tahu berhasil:**

```powershell
docker ps --format "{{.Names}} | {{.Status}}"
```

Harus muncul **empat** kontainer:

```
openmrs-distro-referenceapplication-gateway-1   | Up
openmrs-distro-referenceapplication-frontend-1  | Up (healthy)
openmrs-distro-referenceapplication-backend-1   | Up (healthy)
openmrs-distro-referenceapplication-db-1        | Up (healthy)
```

**Butuh berapa lama?** Penyalaan pertama kali menjalankan migrasi Liquibase dari
database kosong: **5–10 menit**. Penyalaan berikutnya: 1–2 menit. Selama proses,
`backend-1` akan berstatus `health: starting` — itu normal, tunggu saja.

Pantau prosesnya:

```powershell
docker compose logs -f backend
```

**Cara tahu OpenMRS benar-benar siap:**

```powershell
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1/openmrs/ws/rest/v1/session
```

Harus `200`. Kalau `502` atau `503`, backend masih menyala — tunggu.

**Buka di peramban:** `http://127.0.0.1/openmrs/`
Kredensial demo bawaan: `admin` / `Admin123`

---

### Langkah 4 — Bangun modul backend

```powershell
cd backend\openmrs-module-tfidf-search
..\..\tools\apache-maven-3.9.16\bin\mvn.cmd clean package
cd ..\..
```

Kalau `mvn` sudah ada di PATH, cukup `mvn clean package`.

**Cara tahu berhasil:**

- Diakhiri `BUILD SUCCESS`
- `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`
- Berkas `omod/target/unifiedsearch-omod-1.0.0-SNAPSHOT.omod` ada (± 84 KB)

**Butuh berapa lama?** ± 13–15 detik setelah dependensi Maven terunduh. Unduhan
dependensi pertama kali bisa beberapa menit.

> **Jangan menaikkan target bytecode.** Modul dikompilasi dengan JDK 17 tetapi
> menargetkan bytecode Java 8 lewat `--release 8`, dan dijalankan di JRE 21 di
> dalam kontainer. Ketiganya kompatibel; menaikkan target akan merusaknya.

---

### Langkah 5 — Pasang modul backend

```powershell
.\scripts\pasang-modul.ps1
```

Skrip ini: membangun `.omod` → menyalinnya ke `/openmrs/data/modules` di
kontainer backend → me-*restart* backend → menunggu sampai sehat lagi.

Kalau sudah membangun di langkah 4 dan ingin melewati build ulang:

```powershell
.\scripts\pasang-modul.ps1 -SkipBuild -TimeoutSeconds 300
```

**Cara tahu berhasil:**

1. Skrip mencetak `==> Selesai`.
2. Buka `http://127.0.0.1/openmrs/admin/modules/module.list` — cari
   **"Pencarian Terpadu"** dengan status *started*.
3. Baris pembangunan indeks muncul di log:

```powershell
docker exec openmrs-distro-referenceapplication-backend-1 `
  sh -c "grep -i 'unified search index' /openmrs/data/openmrs.log"
```

Harus menunjukkan:

```
WARN - IndexBuilder.build(151) |...| Unified search index build finished in
2076 ms (8045 documents, 35914 surface forms, 17 indices)
```

> Korpus evaluasi kini **sama persis** dengan korpus yang dipasang: 8 entitas,
> 8.045 dokumen, 35.914 surface form, 8×2+1 = 17 indeks. Angka 6-entitas yang
> lebih tinggi dan pernah beredar sudah dihapus; alasan pergantian tercatat di
> `docs/keputusan.md` entri "F2". Yang penting: baris itu **muncul**, dan
> angkanya bukan nol.

> Baris ini sengaja dicetak pada level `WARN`, bukan `INFO`, karena konfigurasi
> log distro mengunci `org.openmrs` ke `WARN` — pada level `INFO` baris ini
> tidak akan pernah terlihat.

---

### Langkah 6 — Bangun dan pasang antarmuka ESM

```powershell
.\scripts\pasang-esm.ps1
```

Skrip ini: `npm ci` → `npm run build` → menyalin hasil build ke kontainer
frontend → **menyisipkan satu kunci** ke `importmap.json` yang sudah ada
(bukan menimpanya).

Kalau ingin menjalankan tahapannya sendiri:

```bash
cd frontend/esm-unified-search
npm ci
npm run build
cd ../..
```
```powershell
.\scripts\pasang-esm.ps1 -SkipInstall -SkipBuild
```

**Butuh berapa lama?**

| Tahap | Waktu |
|---|---|
| `npm ci` dari kosong (1.505 paket) | ± 2 menit |
| `npm run build` pertama kali | ± 48 detik |
| `npm run build` berikutnya (cache rspack) | ± 10 detik |

**Cara tahu berhasil:**

- `dist/openmrs-esm-unified-search-app.js` dan `dist/routes.json` ada.
- Skrip mencetak `==> Selesai` dan baris entri importmap dengan nama direktori
  **bertanggal-jam baru**.

> Peringatan `EBADENGINE`, `deprecated`, dan peringatan ukuran aset >244 KiB
> **jinak** — sudah diverifikasi berkali-kali tidak menghalangi build.

> **Nama direktori harus baru setiap kali memasang.** Kalau direktori dipakai
> ulang, peramban menyajikan versi lama dari cache selama satu tahun. Skrip
> sudah menangani ini dengan menambahkan tanggal-jam.

---

### Langkah 7 — Verifikasi menyeluruh

```powershell
.\scripts\verify-openmrs.ps1
```

**Cara tahu berhasil:** seluruh baris `HTTP 200`, ditutup dengan
`Determinisme C2: OK (badan identik, SHA-256 ...)` dan baris latensi.

---

### Langkah 8 — Buka di peramban

Semua alamat **`http://127.0.0.1`**, bukan `localhost`.

| Halaman | Alamat |
|---|---|
| Pencarian Terpadu (ESM) | `http://127.0.0.1/openmrs/spa/unified-search` |
| Perbandingan Pencarian | `http://127.0.0.1/openmrs/spa/perbandingan-pencarian` |
| Pengujian Ablasi | `http://127.0.0.1/openmrs/spa/pengujian-ablasi` |
| Halaman antarmuka lama | `http://127.0.0.1/openmrs/module/unifiedsearch/pencarianTerpadu.form` |

Ketiga halaman ESM juga muncul di **App Menu** pada bilah atas.

**Uji terima akhir:** ketik `diabete melitus` pada halaman Pencarian Terpadu
dengan mode `e3`. **Diabetes mellitus** harus muncul di peringkat 1.

---

## 4. Memakai modul

### Antarmuka

Panduan lengkap untuk pengguna akhir: [`docs/panduan-pengguna.md`](docs/panduan-pengguna.md).

### API REST

Basis alamat: `http://127.0.0.1/openmrs/ws/rest/v1/unifiedsearch`

#### `GET /unifiedsearch` — pencarian

| Parameter | Wajib | Bawaan | Keterangan |
|---|---|---|---|
| `q` | ya | — | teks yang dicari |
| `mode` | tidak | `e3` | `b0`, `b1`, `e1`, atau `e3` |
| `limit` | tidak | `10` | jumlah hasil maksimum |
| `entitas` | tidak | semua | batasi ke satu jenis data |

```bash
curl -u admin:Admin123 \
  "http://127.0.0.1/openmrs/ws/rest/v1/unifiedsearch?q=diabete%20melitus&mode=e3"
```

#### `GET /unifiedsearch/saran` — saran ketik

| Parameter | Wajib | Bawaan |
|---|---|---|
| `q` | ya | — |
| `limit` | tidak | `6` |

Hasil berjenis `pasien`, `hasillab`, dan `kondisi` disaring menurut privilese
`View Patients` milik pemanggil.

#### `GET /unifiedsearch/eval` — evaluasi

| Parameter | Wajib | Bawaan |
|---|---|---|
| `mode` | tidak | `e3` |

Menjalankan evaluasi atas 100 kueri pengembangan dan mengembalikan P@1, P@5,
R@10, MRR, MAP, nDCG@10, serta proporsi kueri tanpa hasil. Jawaban menyertakan
`gold_sha256` supaya standar emas yang dipakai bisa dicocokkan.

#### Catatan tentang tajuk waktu

Setiap jawaban menyertakan tajuk `X-Unifiedsearch-Waktu-Ms`. Waktu sengaja
ditempatkan di **tajuk**, bukan di badan JSON, supaya dua panggilan yang sama
menghasilkan badan jawaban **identik pada tingkat bita** — syarat pengujian
determinisme.

---

## 5. Penanganan galat

### Halaman "Apache2 Ubuntu Default Page" muncul

**Sebab:** Anda membuka `localhost`, bukan `127.0.0.1`. Ada server lain di
`::1:80`.
**Perbaikan:** ganti alamat ke `http://127.0.0.1/...`. Atau hentikan server itu
(`sudo service apache2 stop` di WSL) — periksa dulu, mungkin dipakai proyek lain.

---

### Seluruh endpoint REST mengembalikan `500` (`ClassNotFoundException`)

**Gejala:** `http://127.0.0.1/openmrs/ws/rest/v1/session` memberi HTTP 500
dengan halaman galat Tomcat. Seluruh REST OpenMRS mati, bukan hanya endpoint
modul ini.

**Diagnosis — 3 langkah, jangan langsung menebak:**

```powershell
# 1. Kelas apa yang tidak ditemukan?
docker logs --tail 200 openmrs-distro-referenceapplication-backend-1 |
  Select-String -Pattern "ClassNotFoundException|Cannot find class"

# 2. Apakah OpenMRS gagal menghapus cache-nya?
docker logs openmrs-distro-referenceapplication-backend-1 |
  Select-String -Pattern "could not remove directory" | Select-Object -First 3

# 3. Apakah cache bisa ditulis oleh Tomcat?
docker exec openmrs-distro-referenceapplication-backend-1 sh -c `
  "id; ls -ld /openmrs/data/.openmrs-lib-cache; touch /openmrs/data/.openmrs-lib-cache/.uji 2>&1"
```

**Akar masalah** kalau langkah 3 menjawab `Permission denied`:

OpenMRS menghapus lalu membangun ulang `/openmrs/data/.openmrs-lib-cache`
setiap kali start. Tomcat berjalan sebagai `uid=1001`, tetapi direktori cache
itu bisa berakhir milik `root` dengan mode `0750` — uid 1001 hanya dapat
`r-x`, **tanpa izin tulis**. Akibat berantai:

1. Penghapusan cache gagal → ratusan baris `could not remove directory`.
2. Cache tetap versi lama, tidak cocok lagi dengan berkas `.omod` yang sudah
   diperbarui.
3. Kelas yang berubah tidak ditemukan → `ClassNotFoundException` pada
   `webservices.rest`, `patientflags`, `initializer`, dan modul lain.
4. Konteks Spring batal → seluruh REST balas 500.

Penyebab cache jadi milik root: `docker cp` dan `docker exec` berjalan sebagai
**root** secara bawaan, sehingga berkas yang disalin ke dalam kontainer ikut
jadi milik root.

**Perbaikan** — perbaiki kepemilikannya, jangan hapus cache-nya:

```powershell
# ambil uid:gid yang benar dari direktori modules, lalu samakan
docker exec -u root openmrs-distro-referenceapplication-backend-1 sh -c `
  "own=$(stat -c '%u:%g' /openmrs/data/modules); chown -R \$own /openmrs/data/.openmrs-lib-cache /openmrs/data/modules"
docker restart openmrs-distro-referenceapplication-backend-1
```

Kalau cache-nya sudah telanjur rusak (bukan sekadar salah pemilik), sisihkan
supaya OpenMRS membangunnya ulang. Cache ini **dibangkitkan ulang dari berkas
`.omod`**, jadi menyisihkannya tidak menghilangkan data apa pun:

```powershell
docker exec -u root openmrs-distro-referenceapplication-backend-1 sh -c `
  "mv /openmrs/data/.openmrs-lib-cache /openmrs/data/.openmrs-lib-cache.lama"
docker restart openmrs-distro-referenceapplication-backend-1
# setelah terbukti pulih, hapus salinan lamanya (± 320 MB):
docker exec -u root openmrs-distro-referenceapplication-backend-1 sh -c `
  "rm -rf /openmrs/data/.openmrs-lib-cache.lama"
```

**Pencegahan:** sejak perbaikan ini, `scripts/pasang-modul.ps1` menyamakan
kepemilikan `.omod` dengan direktori `modules`, dan memeriksa apakah lib-cache
masih bisa ditulis — kalau tidak, kepemilikannya diperbaiki otomatis sebelum
restart. Selama pemasangan selalu lewat skrip itu, masalah ini tidak berulang.

Halaman antarmuka lama (`/openmrs/module/unifiedsearch/pencarianTerpadu.form`)
**tidak** melewati lapisan REST, jadi biasanya masih bisa dipakai saat REST mati.

---

### Kontainer `backend-1` tidak pernah jadi `healthy`

**Sebab paling sering:** migrasi Liquibase pertama kali belum selesai.
**Perbaikan:** tunggu sampai 10 menit sambil memantau:

```powershell
docker compose logs -f backend
```

Kalau lebih dari itu, periksa RAM Docker Desktop (butuh ≥ 6 GB) dan cari
`ERROR` pada log.

---

### `docker compose up` gagal karena port 80 dipakai

**Perbaikan:** lihat [Langkah 0](#langkah-0--periksa-port-80-sudah-bebas).
Jangan mengubah port di `docker-compose.yml` — seluruh skrip, dokumen, dan uji
dalam proyek ini mengasumsikan port 80.

---

### Muncul stack Docker kedua

**Gejala:** `docker ps` menunjukkan lebih dari empat kontainer OpenMRS, dengan
awalan nama berbeda.
**Sebab:** `docker compose up -p <nama-lain>` pernah dijalankan.
**Perbaikan:** matikan stack yang salah, sebutkan nama project-nya secara
eksplisit:

```powershell
docker compose -p <nama-yang-salah> down
```

Jangan pakai `-v` kecuali Anda memang ingin menghapus datanya.

---

### Menu ESM tidak muncul di App Menu

**Sebab:** entri `importmap.json` tidak tersisip, atau peramban menyajikan
versi lama dari cache.
**Perbaikan:**

```powershell
# periksa entri importmap
docker exec openmrs-distro-referenceapplication-frontend-1 `
  sh -c "grep unified-search /usr/share/nginx/html/importmap.json"

# pasang ulang, lalu muat ulang keras di peramban (Ctrl+Shift+R)
.\scripts\pasang-esm.ps1
```

---

### `npm ci` gagal

**Perbaikan:** hapus `node_modules/` dan coba lagi. Peringatan `EBADENGINE`
boleh diabaikan; yang menghentikan build hanya baris `npm error`.

```powershell
Remove-Item -Recurse -Force frontend\esm-unified-search\node_modules
```

---

### Pencarian tidak menemukan data yang baru ditambahkan

**Bukan galat.** Indeks dibangun sekali saat modul dijalankan dan tidak
diperbarui otomatis. Jalankan ulang modul:

```powershell
cd openmrs-distro-referenceapplication
docker compose restart backend
```

---

### Angka evaluasi berbeda dari yang tertulis di dokumen

Periksa dulu **angka mana** yang Anda bandingkan:

| Angka | Cakupan |
|---|---|
| nDCG@10 `e3` = **0,737** | 100 kueri dev, korpus 8 entitas (yang dijalankan panel `/eval`) |
| nDCG@10 `e3` = **0,670** | 260 kueri uji, korpus 8 entitas (dilaporkan di artikel) |
| nDCG@10 `e3` = **0,788** | 34 kueri konsep, eksperimen baseline B0′ |

Ketiganya benar dan tidak boleh dicampur. Angka 6-entitas yang lebih tinggi
(0,846 / 0,815) berasal dari cakupan korpus lama yang sudah tidak dipakai;
lihat `docs/keputusan.md` entri "F2".

Kalau angka Anda tetap berbeda jauh setelah memperhitungkan ini, kemungkinan
besar korpus instalasi Anda berbeda dari demo data resmi OpenMRS.

Kalau angka Anda tetap berbeda jauh setelah memperhitungkan ini, kemungkinan
besar korpus instalasi Anda berbeda dari demo data resmi OpenMRS.

---

## 6. Perintah harian

```powershell
# --- Stack ---------------------------------------------------------------
cd openmrs-distro-referenceapplication
docker compose up -d                  # nyalakan
docker compose ps                     # status
docker compose logs -f backend        # pantau log backend
docker compose restart backend        # jalankan ulang backend (memuat ulang indeks)
docker compose down                   # matikan  (data TETAP ada)
# docker compose down -v              # HAPUS SELURUH DATA - jangan, kecuali sengaja
cd ..

# --- Modul backend -------------------------------------------------------
cd backend\openmrs-module-tfidf-search
..\..\tools\apache-maven-3.9.16\bin\mvn.cmd clean package   # build + 47 uji
..\..\tools\apache-maven-3.9.16\bin\mvn.cmd test            # uji saja
cd ..\..
.\scripts\pasang-modul.ps1                                  # build + pasang + restart

# --- Antarmuka ESM -------------------------------------------------------
cd frontend\esm-unified-search
npm run build                         # build produksi
npm run lint                          # eslint
npm run typescript                    # pemeriksaan tipe
npm test                              # vitest
cd ..\..
.\scripts\pasang-esm.ps1              # build + pasang

# --- Verifikasi ----------------------------------------------------------
.\scripts\verify-openmrs.ps1

# --- Basis data ----------------------------------------------------------
docker exec openmrs-distro-referenceapplication-db-1 `
  mysql -uroot -popenmrs -N -B -e "SELECT COUNT(*) FROM openmrs.concept;"
```

### Akses basis data

```
host       127.0.0.1  (dari dalam jaringan Docker: db)
database   openmrs
pengguna   openmrs / openmrs
root       root / openmrs
```

Port 3306 **tidak** dipetakan ke host — akses harus lewat `docker exec`.

---

## 7. Peta kode — di mana letak tiap komponen

```
backend/openmrs-module-tfidf-search/
  api/src/main/java/org/openmrs/module/unifiedsearch/
    TextNormalizer.java          normalisasi teks (huruf kecil, non-alfanumerik -> spasi)
    Tokenizer.java               token kata + kepingan karakter n=4
    SurfaceFormExtractor.java    dokumen -> daftar surface form
    TfIdfIndex.java              indeks terbalik, pembobotan ltc, cosine
    GlobalIndex.java             indeks global (hanya untuk bobot entitas)
    FusionSearch.java            fusi tingkat 1: alpha*kata + (1-alpha)*kepingan
    WeightedRrf.java             fusi tingkat 2: Weighted RRF antar-entitas
    OpenMrsHeuristic.java        baseline B0 (pencocokan awalan)
    BigramJaccardSuggester.java  jalur saran ketik (terpisah dari peringkat)
    RankingEngine.java           pemilih mode b0 / b1 / e1 / e3
    IndexBuilder.java            membangun 13 indeks saat modul mulai
    UnifiedSearchService.java    layanan + penyaringan hak akses pasien
    EvalService.java             evaluasi + metrik
    EvalMetrics.java             P@1, P@5, R@10, MRR, MAP, nDCG@10
    AlphaConfig.java             baca global property unifiedsearch.alpha
    source/                      proyeksi 8 tabel OpenMRS -> dokumen virtual
      ConceptSource, DrugSource, PatientSource, FormSource,
      LocationSource, ProviderSource, HasilLabSource, ConditionSource
  api/src/main/resources/
    gold-dev-100.json            standar emas 100 kueri dev (+ sha256 sumber)
  api/src/test/java/...          47 uji unit
  omod/src/main/java/.../web/
    UnifiedSearchRestController.java   3 endpoint REST
    UnifiedSearchPageController.java   halaman JSP antarmuka lama

frontend/esm-unified-search/src/
  root.component.tsx             halaman Pencarian Terpadu
  comparison.component.tsx       halaman Perbandingan Pencarian (3 kolom)
  ablation.component.tsx         halaman Pengujian Ablasi
  eval-panel.component.tsx       panel evaluasi
  nav-search-form.component.tsx  kotak saran di bilah atas
  search-results.component.tsx   daftar hasil + lencana entitas
  highlight.tsx                  penebalan bagian yang cocok
  use-unified-search.ts          hook pemanggilan endpoint pencarian
  use-saran.ts                   hook pemanggilan endpoint saran
  routes.json                    pendaftaran halaman + extension slot
```

### Alur data, dari tabel ke hasil

```
8 tabel OpenMRS
      |  source/*.java  (proyeksi)
      v
Dokumen virtual  { entitas, id, judul, alias[], kode[], konteks }
      |  SurfaceFormExtractor
      v
Surface form  (judul + tiap alias + tiap kode = unit terpisah)
      |  Tokenizer: dua jalur
      +--> token kata      --> TfIdfIndex (per entitas)  --.
      +--> kepingan n=4    --> TfIdfIndex (per entitas)  --+--> FusionSearch
      |                                                        (alpha = 0,20)
      |  seluruh surface form digabung --> GlobalIndex             |
      |                                        |                   v
      |                                        +--> bobot entitas  |
      |                                                 |          |
      |                                                 v          v
      |                                            WeightedRrf (k = 20)
      v                                                      |
UnifiedSearchService  <-- penyaringan privilese View Patients |
      v                                                      v
Hasil terurut  (kunci: -skor, lalu kunci unik entitas:id)
```

---

## 8. Panduan pengembangan lanjutan

### 8.1 Menyiapkan lingkungan pengembangan

Ikuti [Bagian 3](#3-instalasi-dari-nol--langkah-demi-langkah) sampai selesai.
Sesudah itu, siklus kerja normalnya:

```powershell
# ubah kode Java -> uji -> pasang -> periksa di peramban
cd backend\openmrs-module-tfidf-search
..\..\tools\apache-maven-3.9.16\bin\mvn.cmd test     # cepat, ± 10 detik
cd ..\..
.\scripts\pasang-modul.ps1                           # ± 1-2 menit termasuk restart
```

Untuk perubahan frontend, siklusnya jauh lebih cepat:

```powershell
cd frontend\esm-unified-search
npm run build ; cd ..\.. ; .\scripts\pasang-esm.ps1 -SkipInstall -SkipBuild
```

### 8.2 Menambahkan jenis data (entitas) baru

Contoh nyata: entitas `hasillab` dan `kondisi` ditambahkan setelah enam entitas
awal. Ikuti pola yang sama.

1. **Baca kontraknya dulu:** [`docs/kontrak-data.md`](docs/kontrak-data.md).
   Struktur dokumen virtual tidak boleh diubah.
2. **Buat kelas sumber** di `api/src/main/java/.../source/`, turunan dari
   `SqlDocumentSource`. Petakan tabel Anda ke medan `entitas`, `id`, `judul`,
   `alias`, `kode`, `konteks`.
   - `judul` = nama utama yang ditampilkan.
   - `alias` = sinonim, nama lain, nama dagang.
   - `kode` = identifier, kode terminologi, nomor rekam medis.
   - `konteks` **tidak diindeks** — hanya ditampilkan. Jangan memasukkan teks
     panjang ke `judul` atau `alias` untuk "menambah sinyal"; itu mencemari IDF.
3. **Daftarkan** sumber baru ke `DocumentRepository`.
4. **Kalau entitas itu berisi data pasien**, tambahkan namanya ke pemeriksaan
   `isDataPasien` di `UnifiedSearchService` supaya ikut disaring privilese
   `View Patients`. **Ini wajib, bukan opsional.**
5. **Tulis uji unit** untuk proyeksinya.
6. **Jangan mencampur angkanya dengan hasil penelitian yang sudah ada** —
   korpus enam entitas adalah dasar seluruh angka B0/B1/E1/E3.

### 8.3 Mengubah parameter algoritma

Empat parameter dikunci berdasarkan sapuan pada 100 kueri **pengembangan**:

| Parameter | Nilai | Letak |
|---|---|---|
| `ALPHA` (bobot jalur kata) | 0,20 | global property `unifiedsearch.alpha` — `AlphaConfig.GLOBAL_PROPERTY` |
| `NGRAM` (panjang kepingan) | 4 | `IndexBuilder.NGRAM` |
| `K_RRF` | 20 | `IndexBuilder.K_RRF` |
| `EPS` (lantai bobot entitas) | 0,05 | `IndexBuilder.EPS` |
| ambang skor minimum | 1e-6 | `FusionSearch.SCORE_THRESHOLD` |
| `NGRAM` jalur saran ketik | 2 | `BigramJaccardSuggester.NGRAM` (terpisah, jangan disamakan) |

`ALPHA` bisa diubah tanpa membangun ulang, lewat halaman Administrasi →
Settings → global property `unifiedsearch.alpha`.

> **Kalau Anda menyetel ulang parameter, setel pada 100 kueri pengembangan
> saja.** Himpunan uji (260 kueri) sudah dijalankan satu kali dan tidak boleh
> dilihat lagi sambil menyetel — begitu dilihat berulang, ia berhenti menjadi
> ukuran independen. Lihat [aturan 10](#10-aturan-yang-mengikat-kontributor).

### 8.4 Menambahkan mode pencarian baru

1. Tambahkan cabang di `RankingEngine.search()`.
2. Tambahkan namanya ke daftar mode yang diizinkan di
   `EvalService.validateMode()`.
3. Tambahkan pilihannya di pemilih mode pada antarmuka ESM.
4. Tulis uji unit yang membandingkan hasilnya dengan mode yang sudah ada.

Setiap komponen **wajib punya sakelar**. Ini bukan fitur pengguna — ini syarat
supaya kontribusi tiap komponen tetap bisa diukur ulang.

### 8.5 Menjaga determinisme — aturan yang paling sering dilanggar

Pengurutan akhir **harus** memakai kunci majemuk `(-skor, kunci unik)`, bukan
skor saja. RRF menghasilkan banyak nilai yang persis sama; tanpa pemecah seri
yang stabil, hasilnya berubah antar-proses.

Pola yang dipakai di `WeightedRrf.java` — salin ini, jangan mengarang varian
sendiri:

```java
// BENAR - skor turun, seri dipecah oleh kunci unik "entitas:id"
Collections.sort(hasil, new Comparator<RankedDocument>() {
    @Override
    public int compare(RankedDocument a, RankedDocument b) {
        int byScore = Double.compare(b.getSkor(), a.getSkor());
        return byScore != 0
            ? byScore
            : a.getDokumen().getKunci().compareTo(b.getDokumen().getKunci());
    }
});

// SALAH - saat skor seri, urutannya berubah antar-proses
Collections.sort(hasil, new Comparator<RankedDocument>() {
    @Override
    public int compare(RankedDocument a, RankedDocument b) {
        return Double.compare(b.getSkor(), a.getSkor());
    }
});
```

Aturan turunannya:

- Jangan pernah mengiterasi `HashSet` / `HashMap` tanpa mengurutkannya dulu.
  Pakai `TreeMap` / `TreeSet`, atau `sorted()`.
- Jangan menaruh nilai yang berubah-ubah (waktu, angka acak) di **badan**
  jawaban REST. Waktu pemrosesan sudah ditempatkan di tajuk
  `X-Unifiedsearch-Waktu-Ms` justru karena alasan ini.

**Cara mengujinya:** jalankan evaluasi dua kali pada dua proses berbeda.
Angkanya harus identik sampai digit terakhir. Ada uji khusus untuk ini:
`WeightedRrfSeparateProcessDeterminismTest`.

### 8.6 Menjalankan uji

```powershell
cd backend\openmrs-module-tfidf-search
..\..\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

47 uji, ± 10 detik. Uji yang paling penting dipahami sebelum mengubah algoritma:

| Uji | Menjaga apa |
|---|---|
| `TfIdfIndexTest` | pembobotan `ltc` dan cosine |
| `TokenizerCharGramsTest` | pembentukan kepingan karakter, termasuk teks pendek |
| `CharGramsSilangPythonTest` | kesetaraan numerik dengan pipeline Python |
| `FusionSearchTest` | urutan operasi fusi (maksimum dulu, baru gabung) |
| `WeightedRrfTest` | rumus bobot entitas dan lantai `EPS` |
| `WeightedRrfSeparateProcessDeterminismTest` | determinisme lintas-proses |
| `OpenMrsHeuristicTest` | baseline B0 tidak berubah diam-diam |
| `BigramJaccardSuggesterTest` | jalur saran ketik |

### 8.7 Memverifikasi implementasi terhadap pipeline penelitian

Implementasi Java harus mereproduksi pipeline Python secara numerik. Kalau Anda
mengubah algoritma, verifikasi ulang:

```powershell
# 1. Angka acuan dari Python
python riset\eksperimen2.py

# 2. Angka dari Java yang berjalan
curl.exe -s -u admin:Admin123 `
  "http://127.0.0.1/openmrs/ws/rest/v1/unifiedsearch/eval?mode=e3"
```

Nilai `gold_sha256` pada jawaban endpoint harus sama dengan `sha256_sumber`
di `api/src/main/resources/gold-dev-100.json`. Kalau berbeda, kedua sisi
memakai standar emas yang berbeda dan angkanya tidak sebanding.

> **Jebakan yang pernah memakan waktu berjam-jam:** jangan membangkitkan ulang
> kueri di sisi Java dengan `java.util.Random(42)` untuk meniru
> `random.Random(42)` Python. `java.util.Random` adalah LCG 48-bit, Python
> memakai Mersenne Twister — benih sama, urutan berbeda total. Standar emas
> **diekspor** dari Python ke berkas JSON, tidak dibangkitkan ulang.

### 8.8 Membangun ulang korpus dari instalasi OpenMRS lain

```bash
cd riset
# jalankan tiap ekspor_*.sql terhadap basis data OpenMRS Anda,
# simpan keluarannya sebagai data/*.jsonl
python eksperimen2.py
```

Berkas SQL-nya: `ekspor_konsep.sql`, `ekspor_obat.sql`, `ekspor_pasien.sql`,
`ekspor_lain.sql`, `ekspor_hasillab.sql`, `ekspor_kondisi.sql`.

### 8.9 Yang sebaiknya ditanyakan dulu sebelum dikerjakan

- Mengganti mesin peringkat dengan pustaka lain (Lucene, Elasticsearch).
  Seluruh premis penelitian ini adalah implementasi eksplisit; menggantinya
  membatalkan dasar ilmiahnya.
- Menambahkan panggilan jaringan keluar. Tidak ada embedding, tidak ada LLM,
  tidak ada API pihak ketiga — premisnya "cukup ringan untuk puskesmas tanpa
  GPU dan tanpa internet".
- Menghidupkan kembali perluasan kueri otomatis. Sudah diuji dua kali, gagal
  dua kali (nDCG turun 0,030, latensi naik enam kali).

### 8.10 Pekerjaan lanjutan yang sudah teridentifikasi

| Pekerjaan | Kenapa penting |
|---|---|
| **Pembaruan indeks inkremental** | Saat ini indeks dibangun sekali saat modul mulai. Untuk data pasien, ini prasyarat pemakaian nyata. |
| **Uji pada kamus CIEL penuh** (~50.000 konsep) | Indeks kepingan 3–5× ukuran indeks kata; perilaku memori pada skala penuh belum terukur. |
| **Studi pengguna dengan klinisi** | nDCG adalah bukti tak langsung; yang perlu diukur adalah apakah klinisi menemukan lebih cepat. |
| **Sapuan ulang panjang kepingan** | `n=3` menunjukkan keunggulan +0,0091 nDCG, tapi bootstrap *borderline* (p=0,0686) pada n=100. Perlu himpunan pengembangan lebih besar. |
| **Perbaikan stack REST** | `webservices.rest` gagal dimuat setelah kontainer di-*recreate*; menghalangi cross-check jalur saran ketik. |
| **Uji instalasi bersih sungguhan** | `docker compose down -v` lalu `up` dari database kosong belum pernah diuji. |
| **Uji di luar Windows** | Skrip `scripts/*.ps1` belum diverifikasi pada PowerShell Core di Linux/macOS. |

---

## 9. Menjalankan ulang eksperimen

Seluruh angka penelitian dapat dibangkitkan ulang. Panduan langkah-demi-langkah
dengan kriteria "cara tahu berhasil": [`docs/reproduksi.md`](docs/reproduksi.md).

```bash
cd riset

python eksperimen2.py                # sistem B0-E4, 260 kueri uji  -> hasil6/
python eval_dev8.py                  # seluruh sistem, 100 kueri dev -> hasil6/
python eksperimen3_baseline_asli.py  # baseline B0' (butuh stack)   -> hasil4/
python eksperimen_k2.py              # jalur saran ketik            -> hasil5/
python sapuan_dev.py                 # sapuan NGRAM/K_RRF/EPS       -> hasil6/
python sapuan_alpha_dev.py           # sapuan ALPHA                 -> hasil6/

cd ../article
python gambar/buat_gambar.py         # bangun ulang gambar artikel
```

Skrip gambar membaca berkas hasil secara langsung — tidak ada angka yang
diketik ulang. Kalau eksperimen dijalankan ulang dan angkanya berubah,
gambarnya ikut berubah.

**Peringatan** untuk `eksperimen3_baseline_asli.py`: jangan menambahkan
parameter `&locale=en` pada URL endpoint konsep OpenMRS. Parameter itu
**merusak pencarian** — endpoint mengembalikan daftar tetap yang tidak
berkaitan untuk setiap kueri. Locale dipatok lewat sesi.

---

## 10. Aturan yang mengikat kontributor

Aturan lengkap ada di [`CLAUDE.md`](CLAUDE.md). Tiga yang paling sering
dilanggar:

### 1. Determinisme wajib

Kunci urut `(-skor, id)`, bukan `-skor` saja. Jangan mengiterasi himpunan tanpa
mengurutkannya. Lihat [8.5](#85-menjaga-determinisme--aturan-yang-paling-sering-dilanggar).

### 2. Jangan mengubah angka hasil penelitian

Berkas di `docs/` dan `riset/hasil*/` memuat angka dari eksperimen yang sudah
dijalankan. Kalau implementasi memberi angka berbeda, **itu temuan yang harus
dilaporkan** — bukan angka dokumen yang disesuaikan.

### 3. Himpunan uji hanya dijalankan sekali

Kueri dibagi `dev, test = qs[:100], qs[100:]` (100 dev / 260 uji). Bagian
`test` sudah dijalankan
satu kali setelah parameter dikunci. Setiap skrip lain yang membaca `qs[100:]`
adalah bug — termasuk "cuma mengecek" dan "cuma sapuan kecil".

Aturan ini pernah dilanggar dalam proyek ini sendiri: sapuan parameter versi
awal dijalankan pada himpunan uji, sehingga seluruh tabel sapuan pada proposal
tercemar. Skrip itu sudah dihapus dan seluruh sapuan dijalankan ulang pada
himpunan pengembangan.

### Jangan melebih-lebihkan komponen mana pun

Penelitian ini menemukan bahwa **hanya satu komponen** yang berpengaruh
substantif: kepingan karakter (+0,114 nDCG di atas TF-IDF kata, p<0,001).
Weighted RRF **tidak** menyumbang nDCG (−0,011, p=0,406) meski menaikkan P@1
dan MRR. RRF tanpa bobot (−0,246) dan perluasan kueri (−0,164) merugikan.
Sistem penuh vs baseline: +0,039, p=0,079 — **tidak signifikan**.

Komentar kode, nama variabel, pesan commit, dan teks antarmuka harus
mencerminkan itu.

---

## 11. Dokumentasi lain

| Berkas | Isi |
|---|---|
| [`docs/panduan-pengguna.md`](docs/panduan-pengguna.md) | panduan untuk pengguna akhir |
| [`docs/reproduksi.md`](docs/reproduksi.md) | 9 langkah mengulang hasil penelitian |
| [`docs/algoritma.md`](docs/algoritma.md) | spesifikasi K1–K6, baseline B0, jalur saran ketik |
| [`docs/kontrak-data.md`](docs/kontrak-data.md) | bentuk dokumen virtual — wajib diikuti persis |
| [`docs/lingkungan.md`](docs/lingkungan.md) | versi terverifikasi, alamat, kredensial, jebakan |
| [`docs/keputusan.md`](docs/keputusan.md) | seluruh keputusan dan temuan, kronologis |
| [`docs/ringkasan-hasil.md`](docs/ringkasan-hasil.md) | ringkasan satu halaman, termasuk keterbatasan |
| [`docs/proposal.html`](docs/proposal.html) | proposal lengkap + *mockup* interaktif |
| [`article/`](article/) | naskah artikel penelitian (LaTeX + PDF) |
| [`CLAUDE.md`](CLAUDE.md) | aturan proyek yang mengikat |
| [`tugas/`](tugas/) | 14 berkas tugas berurutan, masing-masing dengan kriteria uji |

---

## Ringkasan hasil

| Sistem | nDCG@10 | P@1 | % kueri tanpa hasil |
|---|---|---|---|
| B0 — pencocokan awalan | 0,631 | 0,662 | **15,4%** |
| B1 — TF-IDF kata | 0,568 | 0,492 | 8,8% |
| E1 — + kepingan karakter | **0,681** | 0,623 | **0,0%** |
| **E3 — + Weighted RRF (usulan)** | 0,670 | **0,681** | **0,0%** |

260 kueri uji, korpus 8 entitas, sekali jalan, parameter terkunci pada
himpunan pengembangan.

| Ablasi | ΔnDCG | p |
|---|---|---|
| Kepingan karakter (E1 vs B1) | **+0,114** | **<0,001** |
| Weighted RRF (E3 vs E1) | −0,011 | 0,406 |
| TF-IDF kata saja (B1 vs B0) | +0,018 | 0,510 |
| Perluasan kueri (E4 vs B0) | −0,030 | 0,336 |

Terhadap endpoint pencarian konsep OpenMRS yang sesungguhnya (42 kueri konsep):
E3 **+0,103** nDCG (p=0,0076).

---

## Penulis

| Nama | NIM |
|---|---|
| Stephen Prasetya Chrismawan | 25/563032/PPA/07093 |
| M. Syarif Hidayatullah | 25/567018/PPA/07143 |
| Sampurno Aji | 25/568826/PPA/07155 |
| Reza Purwantara Firdaus | 25/565168/PPA/07125 |
