# Tugas 06 — Menetapkan ALPHA

## Kenapa tugas ini ada
Eksperimen yang dilaporkan memakai ALPHA = 0,45, tetapi sapuan parameter
menunjukkan optimum di sekitar 0,25. Selisihnya kecil (0,811 vs 0,818) tetapi
ketidakcocokan ini sudah tercatat sebagai utang di proposal dan **akan ditanya
penguji**.

## Langkah
1. Jalankan ulang sapuan ALPHA pada **100 query dev** (bukan 180 query uji).
2. Pilih nilai berdasarkan hasil dev itu saja.
3. Tetapkan sebagai konstanta dan sebagai global property OpenMRS
   `unifiedsearch.alpha` supaya bisa disetel tanpa membangun ulang.
4. Catat keputusannya di `docs/keputusan.md`: nilai yang dipilih, hasil sapuannya,
   dan tanggalnya.

## Selesai kalau
- `docs/keputusan.md` berisi tabel sapuan dan satu nilai final.
- Nilai itu dipakai konsisten di kode dan di dokumen.

## Jangan
Jangan memilih ALPHA berdasarkan 180 query uji. Itu membocorkan test set dan
membatalkan klaim statistiknya.
