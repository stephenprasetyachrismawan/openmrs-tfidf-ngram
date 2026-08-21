# Tugas 08b — Evaluasi test set, DIJALANKAN SEKALI

> **Ini satu-satunya tugas yang boleh menyentuh `qs[100:]`.** Aturan 10 `CLAUDE.md`.
> Prasyarat mutlak: tugas 06b, 07, dan 08 selesai, seluruh parameter terkunci.

## Kenapa tugas ini ada

Parameter berubah sejak angka proposal dibuat (`ALPHA` 0,45 → 0,20, dan
kemungkinan lainnya dari tugas 06b). Modul sekarang **tidak lagi menggambarkan
sistem yang dilaporkan**. Panel evaluasi di tugas 12 menjanjikan angka yang bisa
direproduksi — janji itu hanya benar kalau angka laporan berasal dari sistem
yang sama dengan yang terpasang.

## Sebelum menjalankan — periksa dulu

Jalankan hanya kalau **semua** ini benar. Kalau ada satu saja yang belum,
berhenti dan laporkan.

- [ ] `ALPHA`, `NGRAM`, `K_RRF`, `EPS` final, semuanya bersumber dari sapuan dev
- [ ] Weighted RRF (tugas 07) selesai dan teruji
- [ ] Baseline B0 (tugas 08) selesai dan teruji
- [ ] Tujuh sistem (B0, B1, B2, E1, E2, E3, E4) bisa dijalankan
- [ ] Dua kali menjalankan pipeline memberi angka identik

Setelah tugas ini dijalankan, **tidak ada lagi penyetelan parameter.** Kalau
parameter berubah setelah ini, test set sudah tercemar dan angkanya tidak bisa
dipertahankan lagi.

## Langkah

1. Jalankan `riset/eksperimen2.py` dengan parameter final. **Satu kali.**
2. Simpan seluruh keluaran mentah ke `hasil3/` — jangan menimpa `hasil2/`,
   itu arsip angka lama yang dipakai proposal versi sekarang.
3. Hasilkan ulang seluruh tabel:
   - tabel utama tujuh sistem (P@1, R@10, MRR, MAP, nDCG@10, %nol-hasil, ms)
   - tabel uji signifikansi (selisih, CI 95%, p) — bootstrap 5.000 iterasi
   - tabel per jenis kesalahan ketik
   - ringkasan angka di halaman depan proposal
4. Perbarui `docs/proposal.html` dengan angka baru. Termasuk kalimat-kalimat
   yang menyebut angka di dalam prosa, bukan hanya tabel — cari `0,804`,
   `0,811`, `0,176`, `0,183`, `18,3`, `0,6`, `0,628`, `0,689`.
5. Perbarui `docs/keputusan.md`: tabel angka lama vs baru, berdampingan.

## Selesai kalau

- `hasil3/` berisi keluaran lengkap, `hasil2/` tidak tersentuh.
- Setiap angka di `docs/proposal.html` berasal dari `hasil3/`. Tidak ada angka
  campuran dari dua kali jalan berbeda.
- Menjalankan ulang pipeline memberi angka identik sampai digit terakhir.
- Tabel perbandingan lama vs baru ada di `keputusan.md`.

## Yang harus dilaporkan apa adanya

Angka berapa pun yang keluar adalah angka yang dilaporkan. Berdasarkan sapuan
dev, arahnya kemungkinan naik — tapi itu dugaan, bukan janji.

**Kalau angkanya turun**, tetap laporkan. Kalau E1-vs-B0 kehilangan
signifikansi, itu temuan besar yang wajib disampaikan ke manusia segera, bukan
disembunyikan atau diakali dengan mengubah parameter. Aturan 2 dan 3 `CLAUDE.md`.

**Kalau tergoda menjalankan ulang** karena hasilnya mengecewakan — jangan. Itu
persis cara test set kehilangan maknanya. Satu kali, apa adanya.

## Jangan

- Jangan menyetel parameter apa pun setelah melihat hasil test.
- Jangan menjalankan pipeline test dua kali dengan parameter berbeda.
- Jangan menghapus atau menimpa `hasil2/`.
