# Tugas 10 — Kerangka microfrontend O3 (GERBANG KEPUTUSAN)

> Prasyarat: **tugas 09 (endpoint REST) sudah selesai dan lulus uji `curl`.**
> Jangan mulai tugas ini sebelum itu. Alasannya di `docs/keputusan.md`.

## Tujuan
Satu ESM kosong yang benar-benar tampil di RefApp 3 yang sedang berjalan.
Belum ada pencarian. Hanya membuktikan rantai build → daftar → render bekerja.

## Kenapa tugas ini berdiri sendiri
Seluruh risiko jalur O3 menumpuk di sini. Kalau bagian ini bisa dilewati,
sisanya tinggal menulis React biasa. Kalau tidak bisa, kelompok turun ke JSP —
lihat "Gerbang" di bawah.

## Langkah

1. Cari tahu versi `@openmrs/esm-framework` yang dipakai frontend yang
   **sedang berjalan**. Jangan pakai versi terbaru dari npm — harus cocok.
   Petunjuk: baca `importmap.json` di container frontend, lalu periksa
   `package.json` atau berkas bundel salah satu app resmi di dalamnya.

2. Buat proyek di `frontend/esm-unified-search/`. Gunakan perkakas resmi O3
   (`openmrs` CLI / template `openmrs-esm-template-app`). Kalau template
   menarik versi framework yang berbeda dari langkah 1, samakan.

3. Isi awal: satu halaman yang hanya menampilkan teks
   `Pencarian Terpadu — modul termuat`, terdaftar sebagai satu entri menu.

4. Build produksi menghasilkan berkas `.js` + `importmap` fragmen.

5. Tulis `scripts/pasang-esm.ps1`:
   ```
   npm ci → npm run build → docker cp hasil build ke container frontend
   → sisipkan entri ke importmap.json → reload
   ```
   **Sisipkan, jangan timpa.** `importmap.json` berisi puluhan app resmi;
   menimpanya akan mematikan seluruh RefApp. Baca berkasnya, tambahkan satu
   kunci, tulis kembali. Simpan salinan asli sebelum perubahan pertama.

## Selesai kalau

- `npm run build` sukses.
- `scripts/pasang-esm.ps1` berjalan dari awal sampai akhir tanpa campur tangan.
- Buka RefApp di browser: entri menu baru muncul dan halamannya menampilkan
  teks penanda.
- Console browser bersih dari galat module federation.
- **Seluruh app RefApp lain masih berfungsi** — buka daftar pasien dan satu
  chart pasien untuk memastikan `importmap.json` tidak rusak.
- `docker ps` tetap 4 container.

## Gerbang keputusan

Kalau setelah usaha wajar tugas ini belum lulus — khususnya kalau tersendat di
ketidakcocokan versi framework atau module federation — **berhenti**. Jangan
lanjut menebak-nebak.

Laporkan ke manusia dengan: pesan galat apa adanya, versi framework RefApp yang
ditemukan, versi yang ditarik template, dan apa saja yang sudah dicoba.
Kelompok akan memutuskan turun ke JSP legacyui, dan keputusan itu dicatat di
`docs/keputusan.md`.

Ini bukan kegagalan. Tidak ada klaim penelitian yang bergantung pada jalur ini.

## Jangan

- Jangan membangun ulang seluruh frontend RefApp dari sumber.
- Jangan menimpa `importmap.json`.
- Jangan menaikkan versi app resmi mana pun supaya "cocok" dengan ESM kita.
  Arah penyesuaiannya satu arah: ESM kita menyesuaikan RefApp.
- Jangan membuat container atau stack baru.
