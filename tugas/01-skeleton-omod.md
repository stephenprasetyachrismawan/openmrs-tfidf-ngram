# Tugas 01 — Kerangka modul OMOD

## Tujuan
Modul kosong yang bisa dibangun, dipasang, dan muncul sebagai *Started* di
OpenMRS — tanpa memakai server SDK.

## Batasan yang sudah diputuskan (jangan diubah sendiri)

- **Jangan** jalankan `openmrs-sdk:setup` atau `openmrs-sdk:run`. Alasannya di
  `docs/keputusan.md`. Memakai `openmrs-sdk:create-project` **boleh** — plugin
  itu hanya membuat berkas, tidak membuat server.
- Target bytecode **Java 8**: `<maven.compiler.release>8</maven.compiler.release>`.
  Bukti di `docs/keputusan.md`.
- Platform target **2.8.8**, `openmrs-api` versi itu sebagai `provided`.
- Lokasi: `backend/openmrs-module-tfidf-search/` (folder sudah ada, masih kosong).

## Langkah

1. Buat proyek modul di `backend/openmrs-module-tfidf-search/`:
   - id modul: `unifiedsearch`
   - nama: Pencarian Terpadu
   - package: `org.openmrs.module.unifiedsearch`

   Kalau `openmrs-sdk:create-project` memaksa interaktif, berikan parameternya
   lewat `-D`. Kalau tetap tidak bisa, susun struktur Maven-nya manual —
   `api/` + `omod/` + `pom.xml` induk. Yang penting hasilnya, bukan caranya.

2. Struktur yang diharapkan:
   ```
   backend/openmrs-module-tfidf-search/
     pom.xml
     api/     kode inti, tanpa ketergantungan web
     omod/    controller, halaman, konfigurasi modul
   ```

3. Set `maven.compiler.release` ke 8 di pom induk.

4. Tambahkan satu entri menu mengarah ke halaman placeholder (boleh kosong,
   sekadar membuktikan modul termuat).

5. Tulis skrip pemasangan `scripts/pasang-modul.ps1` yang melakukan:
   ```
   build  → salin .omod ke container → restart backend → tunggu healthy
   ```
   Salin ke: `/usr/local/tomcat/.OpenMRS/modules/` di container backend
   (verifikasi dulu path modul yang benar pada instalasi ini — jangan menebak;
   cari direktori yang sudah berisi `.omod` lain).

## Selesai kalau

- `mvn clean package` sukses, menghasilkan berkas `.omod`.
- `scripts/pasang-modul.ps1` berjalan dari awal sampai akhir tanpa campur tangan.
- Modul muncul di `http://127.0.0.1/openmrs/admin/modules/module.list`
  dengan status **Started**.
- Entri menu muncul dan halamannya terbuka (boleh kosong).
- `docker ps` tetap menunjukkan **4** container — tidak bertambah.
- Verifikasi bytecode hasil build benar-benar Java 8:
  ```powershell
  # major version harus 52
  ```
  Kalau bukan 52, `release` belum berlaku — perbaiki sebelum lanjut.

## Jangan

- Jangan menambah dependensi Lucene / Elasticsearch / Solr. Lihat `CLAUDE.md`.
- Jangan mengubah apa pun di dalam `openmrs-distro-referenceapplication/`.
- Jangan membuat container atau stack Docker baru.

## Kalau tersendat

Laporkan pesan galat apa adanya dan berhenti. Jangan mengganti pendekatan
diam-diam — misalnya beralih memakai SDK server karena build gagal. Keputusan
seperti itu diambil manusia dan dicatat di `docs/keputusan.md`.
