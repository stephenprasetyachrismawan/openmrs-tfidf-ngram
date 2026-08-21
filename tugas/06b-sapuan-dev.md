# Tugas 06b — Sapuan parameter bersih di 100 query dev

> Lahir dari temuan: `riset/eksperimen2b.py` menjalankan **keempat** sapuannya
> di test set. Lihat `docs/keputusan.md` dan aturan 10 `CLAUDE.md`.

## Tujuan
Mengunci `NGRAM`, `K_RRF`, `EPS` memakai 100 query dev, seperti yang sudah
dilakukan untuk `ALPHA`. Setelah tugas ini, tidak ada lagi parameter yang
nilainya berasal dari test set.

## Langkah

1. Buat `riset/sapuan_dev.py`, meniru pola `riset/sapuan_alpha_dev.py`.
   **Wajib memakai `qs[:100]`.** Jangan menyalin `eksperimen2b.py` mentah-mentah
   — berkas itu justru sumber masalahnya.

2. Sapu tiga parameter, satu per satu, parameter lain dipegang di nilai saat ini
   (`ALPHA = 0,20`):

   | parameter | nilai yang disapu |
   |---|---|
   | `NGRAM` | 2, 3, 4, 5, 6 |
   | `K_RRF` | 5, 10, 20, 60 |
   | `EPS` | 0; 0,05; 0,15; 0,30 |

3. Metrik: nDCG@10 pada sistem E3.

4. Setelah semua nilai final diketahui, **ulang sapuan `ALPHA`** pada
   kombinasi parameter final. Kalau `NGRAM` berubah dari 4, `ALPHA` optimum
   bisa bergeser — parameter tidak saling bebas.

5. Catat seluruh tabel di `docs/keputusan.md`, dan perbarui tabel parameter di
   `CLAUDE.md`.

## Selesai kalau

- `riset/sapuan_dev.py` ada dan **tidak memuat `qs[100:]`** di mana pun.
  Buktikan dengan pencarian teks di berkasnya.
- Empat tabel sapuan (n-gram, k, eps, alpha-ulang) tercatat di `keputusan.md`.
- Tabel parameter `CLAUDE.md` berisi nilai final, semuanya bersumber dev.
- Dua kali menjalankan skrip memberi angka identik (aturan 1).

## Kalau hasilnya berbeda dari proposal

Sangat mungkin. Proposal menyebut n-gram 2–4 setara dan memilih 4. Kalau sapuan
dev memberi urutan berbeda, **laporkan apa adanya** — jangan memilih nilai yang
kebetulan cocok dengan dokumen. Aturan 2 `CLAUDE.md`.

Kalau selisih antar nilai lebih kecil dari derau 100 query, katakan begitu, dan
pilih dengan alasan lain yang disebutkan terang-terangan (misalnya: nilai yang
sama dengan eksperimen pertama, demi kesinambungan). Alasan yang jujur lebih
baik daripada presisi palsu.

## Jangan

- Jangan menjalankan `riset/eksperimen2b.py`. Berkas arsip.
- Jangan menyentuh `qs[100:]`.
- Jangan memperbarui angka di `docs/proposal.html` di tugas ini — itu pekerjaan
  tugas 08b, setelah evaluasi test dijalankan.
