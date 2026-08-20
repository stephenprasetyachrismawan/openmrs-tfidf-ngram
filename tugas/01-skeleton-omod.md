# Tugas 01 — Kerangka modul OMOD

## Tujuan
Modul kosong yang bisa dipasang dan muncul di daftar modul OpenMRS.

## Langkah
1. Buat proyek modul: `mvn openmrs-sdk:create-project`.
   - id modul: `unifiedsearch`
   - nama: Pencarian Terpadu
2. Struktur yang diharapkan:
   ```
   api/          kode inti, tanpa ketergantungan web
   omod/         controller, halaman, konfigurasi
   pom.xml
   ```
3. Tambahkan entri menu kosong dulu, mengarah ke halaman placeholder.

## Selesai kalau
- `mvn clean install` menghasilkan `.omod`.
- Modul terpasang, statusnya *Started* di halaman Manage Modules.
- Entri menu "Pencarian Terpadu" muncul dan halamannya terbuka (boleh kosong).

## Jangan
Jangan menambahkan dependensi Lucene/Elasticsearch. Lihat CLAUDE.md.
