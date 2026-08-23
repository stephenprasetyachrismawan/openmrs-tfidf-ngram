# Pencarian Terpadu OpenMRS — repo kerja

Modul OpenMRS yang menambahkan pencarian tahan salah ketik, memakai TF-IDF pada
dua unit sekaligus: kata dan kepingan karakter 4 huruf.

## Setup dari nol (developer baru)

Repo ini **tidak** memuat stack Docker OpenMRS — folder
`openmrs-distro-referenceapplication/` sengaja di-gitignore karena itu klon
hulu dengan `.git` sendiri (lihat `CLAUDE.md` aturan 9). Ikuti langkah di
bawah untuk siap kontribusi.

### Prasyarat

- Docker Desktop (dengan WSL2 di Windows) — harus bisa jalankan `docker compose`
- Git
- JDK 17 (Oracle atau OpenJDK) — build modul, **jangan** naikkan target bytecode
- PowerShell (skrip di `scripts/` ditulis untuk PowerShell)
- Maven 3.9.x — opsional pasang sendiri; kalau tidak ada, unduh Apache Maven
  3.9.16 portabel dan ekstrak ke `tools/apache-maven-3.9.16/` (folder ini
  di-gitignore, tiap developer punya salinan sendiri)

### 1. Klon repo ini

```bash
git clone https://github.com/stephenprasetyachrismawan/openmrs-tfidf-ngram.git
cd openmrs-tfidf-ngram
```

### 2. Klon stack Docker OpenMRS (terpisah, bukan bagian repo ini)

Dikunci ke commit yang sudah diverifikasi jalan (lihat `docs/lingkungan.md`):

```bash
git clone https://github.com/openmrs/openmrs-distro-referenceapplication.git
cd openmrs-distro-referenceapplication
git checkout a09ff7a0136dee61f3d2281e8a7bd26b175858c9
cd ..
```

Folder hasil klon **harus** bernama persis `openmrs-distro-referenceapplication`
sejajar dengan folder repo ini — nama project Docker Compose diambil dari nama
folder (lihat `CLAUDE.md` aturan 8).

### 3. Nyalakan stack

```powershell
cd openmrs-distro-referenceapplication
docker compose up -d
cd ..
```

Jangan pakai `-p <nama-lain>` — itu bikin stack duplikat kedua dan menghabiskan
port/RAM. Tunggu sampai 4 container `Up (healthy)`:

```powershell
docker ps --format "{{.Names}} | {{.Status}}"
```

Lalu verifikasi:

```powershell
.\scripts\verify-openmrs.ps1
```

Harus 200 di seluruh cek. **Selalu pakai `http://127.0.0.1`, jangan
`localhost`** — ada Apache2 di WSL yang menguasai port 80 pada `::1` dan
mengembalikan 404 untuk seluruh path `/openmrs/...`. Detail lengkap di
`docs/lingkungan.md`.

Login demo default: `admin` / `Admin123` di `http://127.0.0.1/openmrs/`.

### 4. Build dan pasang modul backend

```powershell
.\scripts\pasang-modul.ps1
```

Skrip ini build `.omod`, salin ke container backend, restart, tunggu sehat
lagi. Tidak ada OpenMRS SDK server di proyek ini — siklus pasangnya memang
package → copy → restart (lihat `docs/keputusan.md`).

### 5. Pasang halaman ESM frontend

```powershell
.\scripts\pasang-esm.ps1
```

Menyisipkan satu kunci ke `importmap.json` yang sudah ada di container
frontend, bukan menimpanya.

### 6. Lanjutkan pengembangan

Setelah stack hidup dan modul terpasang, lanjut ke bagian
"Cara memakai repo ini dengan Claude Code" di bawah.

## Cara memakai repo ini dengan Claude Code

1. Buka folder ini di terminal, jalankan `claude`.
2. Agen otomatis membaca `CLAUDE.md` — itu aturan proyeknya.
3. Kerjakan tugas berurutan:

```
kerjakan tugas/00-persiapan.md
```

Setelah lulus kriteria "Selesai kalau", lanjut ke berikutnya.

Jangan melompati urutan. Tugas 04 bergantung pada kelas yang dibuat di tugas 03,
dan seterusnya.

## Peta berkas

```
CLAUDE.md                  aturan proyek — dibaca agen otomatis
docs/
  proposal.html            proposal lengkap + mockup hidup + contoh perhitungan
  kontrak-data.md          bentuk dokumen virtual, wajib diikuti persis
  algoritma.md             spesifikasi K1–K6 dan baseline B0
  arsitektur-halaman.md    cara memindahkan mockup jadi halaman modul
tugas/
  00 … 12                  tugas berurutan, masing-masing dengan kriteria uji
```

## Urutan pengerjaan

| # | Tugas | Hasil |
|---|---|---|
| 00 | Persiapan lingkungan | OpenMRS + Maven jalan |
| 01 | Kerangka OMOD | `.omod` terpasang |
| 02 | Dokumen virtual (K1, K2) | 4.748 dokumen, 29.320 surface form |
| 03 | Indeks TF-IDF kata (K3) | pencarian cosine |
| 04 | Kepingan karakter (K4) | **komponen inti** |
| 05 | Fusi (K5) | satu skor per dokumen |
| 06 | Menetapkan ALPHA | utang penelitian dilunasi |
| 07 | Weighted RRF (K6) | penyatuan 6 tabel |
| 08 | Baseline B0 | pembanding |
| 09 | Endpoint REST | dua endpoint + indeks startup |
| 10 | Halaman search box | **kirim duluan** |
| 11 | Halaman menu terpadu | pemilih metode |
| 12 | Panel evaluasi | reproduksibilitas |

## Tiga hal yang paling sering salah

1. **Determinisme.** Kunci urut harus `(-skor, id)`. Pernah menghabiskan berjam-jam.
2. **Surface form.** Alias tidak boleh dilebur jadi satu string.
3. **Klaim berlebihan.** Hanya kepingan karakter yang terbukti signifikan secara
   substantif (+0,174, p&lt;0,001). Weighted RRF (E3 vs E1) +0,013, p=0,039 —
   nominal signifikan, efek kecil; jangan dilebih-lebihkan. Perluasan query
   memperburuk dan sudah dibuang.

## Keterbatasan yang sudah diketahui

- **Indeks dibangun sekali saat modul start** (listener aktivasi), disimpan di memori,
  dan **tidak diperbarui** saat data OpenMRS berubah. Tambah/obat/konsep baru tidak
  masuk indeks sampai server di-restart. Itu keterbatasan yang sudah diakui di
  proposal — bukan bug tersembunyi.
- Belum diuji pada kamus CIEL penuh (~50.000 konsep).
- Query uji dibangkitkan program, belum dikonfirmasi klinisi.
