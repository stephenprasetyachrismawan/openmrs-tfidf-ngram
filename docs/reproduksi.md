# Reproduksi — dari nol sampai halaman perbandingan terbuka

Berkas ini membuktikan orang lain bisa mengulang hasil modul ini. Ditulis dan
diuji **tanpa** menyentuh stack Docker yang sedang berjalan (CLAUDE.md aturan
8: satu stack saja) — bagian yang butuh instalasi bersih sungguhan (volume
baru, stack kedua) ditandai jelas di bagian akhir sebagai **belum diuji**.

## Prasyarat

| Alat | Versi yang dipakai di sini | Catatan |
|---|---|---|
| JDK | 17 (Amazon Corretto / Temurin) | Maven mengompilasi ke bytecode target Java 8 lewat `--release 8` — lihat `docs/keputusan.md` 2026-08-20. Tidak perlu memasang JDK 8. |
| Maven | 3.9.16 (portabel, `tools/apache-maven-3.9.16/`) | Sudah ada di repo, tidak perlu dipasang terpisah. Dikecualikan dari git (`.gitignore`). |
| Node.js | v26.7.0 | `npm` bawaan v11.11.0 |
| npm | 11.11.0 | Dipakai langsung, bukan yarn (lihat `docs/keputusan.md` "Langkah B" — `esm-unified-search` sudah diganti dari yarn ke npm). |
| Docker Desktop | — | Stack `openmrs-distro-referenceapplication` harus sudah berjalan (`docker compose up -d` dari direktori itu, **tanpa** `-p`). |
| PowerShell | 5.1 (Windows bawaan) | Skrip pemasangan (`scripts/*.ps1`) ditulis untuk PowerShell 5.1, bukan Core 7. |
| git | 2.45+ | — |

Port yang dipakai: **80** (gateway RefApp, HTTP saja — `http://127.0.0.1`,
**bukan** `localhost`, lihat CLAUDE.md aturan 7).

## Langkah 1 — clone dan periksa struktur

```bash
git clone <url-repo> tfidf-openmrs
cd tfidf-openmrs
```

**Cara tahu berhasil:** `ls` menunjukkan `backend/`, `frontend/`, `riset/`,
`docs/`, `tugas/`, `scripts/`.

## Langkah 2 — stack Docker (lewati kalau sudah jalan)

```bash
cd openmrs-distro-referenceapplication
docker compose up -d
```

**Cara tahu berhasil:** `docker ps` menunjukkan **4 container**:
`...-backend-1`, `...-frontend-1`, `...-gateway-1`, `...-db-1`, semuanya
`healthy` atau `Up`. Backend perlu waktu 1-2 menit untuk siap sepenuhnya
setelah start pertama kali (lebih lama pada mesin baru — migrasi database
belum pernah jalan).

**Kalau stack lain sudah berjalan dengan nama project berbeda:** JANGAN
menjalankan `docker compose up` dengan `-p` nama lain — itu membuat stack
kedua yang memakan port dan RAM (CLAUDE.md aturan 8). Berhenti dan tanya
manusia.

## Langkah 3 — build modul backend dari nol

```bash
cd backend/openmrs-module-tfidf-search
../../tools/apache-maven-3.9.16/bin/mvn.cmd -o clean package
```

**Diuji sungguhan sesi ini** (aman, tidak menyentuh Docker): total waktu
**~13-15 detik**, 47 unit test lulus.

**Cara tahu berhasil:**
- Output diakhiri `BUILD SUCCESS`.
- `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`.
- Berkas `omod/target/unifiedsearch-omod-1.0.0-SNAPSHOT.omod` ada
  (~84 KB — diverifikasi sesi ini, ukuran bisa berubah sedikit antar-versi).

## Langkah 4 — pasang modul backend ke OpenMRS

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pasang-modul.ps1 -SkipBuild -TimeoutSeconds 300
```

(Hilangkan `-SkipBuild` kalau ingin skrip yang membangun ulang — perilakunya
sama seperti langkah 3.)

**Cara tahu berhasil:** skrip mencetak `==> Selesai` dan URL
`http://127.0.0.1/openmrs/admin/modules/module.list`. Buka URL itu (login
`admin`/`Admin123` pada instalasi demo default) dan cari "Pencarian
Terpadu" berstatus started/dijalankan.

**Bukti log yang harus muncul** (indeks dibangun saat startup, bukan lazy):

```
docker exec openmrs-distro-referenceapplication-backend-1 sh -c \
  "grep -i 'unified search index' /openmrs/data/openmrs.log"
```

harus menunjukkan baris seperti:
`WARN - IndexBuilder.build(137) |...| Unified search index build finished in ~1200 ms (4748 documents, 29320 surface forms, 13 indices)`

## Langkah 5 — build ESM frontend dari nol

```bash
cd frontend/esm-unified-search
npm ci
npm run build
```

**Diuji sungguhan sesi ini** (aman, tidak menyentuh Docker — hanya menghapus
dan memasang ulang `node_modules/` lokal):

| Langkah | Waktu |
|---|---|
| `npm ci` (dari `node_modules/` kosong) | **± 2 menit** (1.505 paket; tergantung kecepatan jaringan npm registry) |
| `npm run build` (pertama kali, tanpa cache) | **± 48 detik** (diukur ulang: 47,9 detik) |
| `npm run build` (build berikutnya, cache rspack sudah ada) | **± 10 detik** |

**Cara tahu berhasil:**
- `npm ci` selesai tanpa `npm error` (peringatan `EBADENGINE`/`deprecated`
  boleh diabaikan — sudah diverifikasi tidak menghalangi build).
- `npm run build` diakhiri `Rspack compiled ... in ~10s` (satu peringatan
  ukuran aset >244 KiB itu jinak, sudah diverifikasi berkali-kali).
- `dist/openmrs-esm-unified-search-app.js` dan `dist/routes.json` ada.

## Langkah 6 — pasang ESM ke RefApp 3

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pasang-esm.ps1 -SkipInstall -SkipBuild
```

(Hilangkan kedua flag kalau ingin skrip menjalankan `npm ci` + build sendiri
— totalnya sama dengan langkah 5 ditambah proses pemasangan di bawah.)

**Cara tahu berhasil:** skrip mencetak `==> Selesai` dan baris
`Entri importmap: "@openmrs/esm-unified-search-app": "./openmrs-esm-unified-search-app-<versi>-<tanggal-jam>/..."`.
Tanggal-jam di nama direktori **harus** baru (lihat `docs/keputusan.md`
"C-2" — bug cache 1-tahun kalau direktori dipakai ulang).

## Langkah 7 — verifikasi otomatis

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-openmrs.ps1
```

**Cara tahu berhasil:** semua baris `HTTP 200`, ditutup dengan
`Determinisme C2: OK (badan identik, SHA-256 ...)` dan baris latensi.

## Langkah 8 — buka di browser (verifikasi manual)

Semua URL **`http://127.0.0.1`**, jangan `localhost` (CLAUDE.md aturan 7).

1. **Halaman JSP (Langkah A):**
   `http://127.0.0.1/openmrs/module/unifiedsearch/pencarianTerpadu.form`
   — login dulu, ketik `diabete melitus`, harus muncul "Diabetes mellitus"
   peringkat 1 pada mode `e3`.
2. **ESM Pencarian Terpadu (tugas 11):**
   `http://127.0.0.1/openmrs/spa/unified-search`
   — via menu "App Menu" di top nav, entri "Pencarian Terpadu".
3. **ESM Perbandingan Pencarian (D1, pengganti tugas 13):**
   `http://127.0.0.1/openmrs/spa/perbandingan-pencarian`
   — via menu yang sama, entri "Perbandingan Pencarian". Klik contoh
   `diabete melitus`, ketiga kolom harus terisi.
4. **Panel evaluasi (tugas 12):** di halaman ESM Pencarian Terpadu, gulir ke
   bawah, klik "Jalankan seluruh data uji" — tabel `nDCG@10` untuk `e3`
   harus di sekitar **0,846** (dev), bukan 0,811 (itu angka test set).

## Langkah 9 — mengulang eksperimen B0′ (baseline OpenMRS asli)

Butuh stack Docker sudah berjalan (langkah 2) dan login sesi aktif di
`http://127.0.0.1` (kredensial `admin`/`Admin123` pada instalasi demo
default — skrip login lewat `admin:Admin123` basic auth ke
`/ws/rest/v1/session`, bukan lewat browser).

```bash
cd riset
python eksperimen3_baseline_asli.py
```

**Cara tahu berhasil:**
- Baris pertama mencetak `Locale sesi dikonfirmasi: 'en'` — kalau sesi
  bukan locale `en`, skrip berhenti dengan `AssertionError` (disengaja,
  lihat komentar di kepala skrip soal bug parameter `locale` pada endpoint).
- Ringkasan akhir menunjukkan nDCG@10: B0≈0,664, **B0′≈0,800**, B1≈0,693,
  E1≈0,893, E3≈0,903 (42 query konsep dev, `qs[:100]` saja).
- `riset/hasil4/` berisi `hasil.json`, `per_query.json`, `ringkasan.csv`,
  `laporan.md`, `mentah_b0prime.json`, `uuid_ke_concept_id.json`.
- **Jangan kaget** kalau angka persis berbeda sedikit dari yang tercatat
  di `docs/keputusan.md` ("E1") — itu wajar kalau korpus demo di instalasi
  lain sedikit berbeda datanya; yang harus konsisten adalah **arah**
  temuannya (B0′ jauh lebih kuat dari B0, E3 tetap menang tapi dengan
  margin lebih kecil dari klaim terhadap B0).

**Peringatan penting yang harus dibaca sebelum menjalankan ulang:**
parameter `&locale=en` pada endpoint `GET /ws/rest/v1/concept?searchType=fuzzy`
**merusak pencarian** (mengembalikan daftar tetap tidak berkaitan untuk
query apa pun) — jangan menambahkannya kalau memodifikasi skrip ini.
Locale dipatok lewat sesi, bukan parameter query. Rincian lengkap:
`docs/keputusan.md` ("E1").

## Yang TIDAK diuji sesi ini — sisa pekerjaan manusia

Sesuai CLAUDE.md aturan 8 (satu stack Docker saja), langkah-langkah berikut
**tidak dijalankan** karena akan berarti menghapus volume atau membuat
stack kedua:

- **Instalasi bersih sungguhan** (`docker compose down -v` lalu
  `up -d` dari nol, database kosong). Perkiraan waktu: 5-10 menit untuk
  migrasi Liquibase pertama kali, berdasarkan log startup yang teramati.
- **Uji di mesin lain** (bukan Windows) — skrip `scripts/*.ps1` ditulis
  untuk PowerShell 5.1 Windows; belum diverifikasi jalan di PowerShell Core
  di Linux/Mac.
- **Uji setelah `docker compose down` lalu `up` ulang** (tanpa `-v`, jadi
  data tetap ada) — kemungkinan aman karena `.omod` dan pemasangan ESM
  ada di volume yang persisten, tapi belum dicoba sesi ini.

Kalau salah satu di atas perlu diuji, **tanya dulu sebelum menjalankan**
apa pun yang menghentikan atau menghapus stack yang sedang dipakai untuk
demo (CLAUDE.md aturan 8).
