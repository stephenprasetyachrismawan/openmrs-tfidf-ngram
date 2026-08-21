# Tugas 08 — Baseline heuristik OpenMRS (B0)

## Tujuan
Pembanding yang jujur. Tanpa ini, tidak ada angka yang bisa dibandingkan.

## Rujukan
`docs/algoritma.md` bagian 6.

## Langkah
1. Implementasikan penyaringan awalan dua tahap persis seperti spesifikasi.
2. Daftarkan sebagai mode `b0` di endpoint.

## Selesai kalau
- Query `diabete melitus` mengembalikan **0 hasil**. Kalau ada hasil,
  penyaring awalannya salah — perbaiki, jangan dibiarkan.
- Query `pulm edem` **mengembalikan hasil** (karena "pulm" awalan "pulmonary").
  Kalau kosong, penyaringnya terlalu ketat.
- Query `diabetes mellitus` mengembalikan hasil yang masuk akal di peringkat atas.

## Jangan
Jangan memperlemah B0 supaya usulan terlihat lebih unggul. Tiruan kami sudah
lebih kuat dari yang dilaporkan komunitas, dan itu justru membuat klaim
penelitian ini konservatif — sifat yang harus dipertahankan.


---

# TITIK RAWAN — baseline yang lemah membuat seluruh penelitian tidak berguna

B0 adalah pembanding. Setiap klaim penelitian ini berbentuk "metode kami lebih
baik **daripada B0**". Kalau B0 dibuat lebih lemah dari OpenMRS sebenarnya,
seluruh selisih yang dilaporkan jadi palsu — dan itu jenis kesalahan yang paling
mudah dituduhkan penguji, karena paling menguntungkan penulis.

Jadi: **kalau ragu, buat B0 lebih kuat, bukan lebih lemah.**

Proposal sudah menyatakan tiruan B0 kelompok ini ternyata *lebih kuat* dari yang
dilaporkan komunitas OpenMRS, sehingga selisih yang dilaporkan konservatif.
Pertahankan sifat itu.

## Uji penentu

Query `diabete melitus` harus mengembalikan **0 hasil**. Kalau tidak,
penyaringan awalannya salah — dan justru itu inti temuan penelitian ini
(dokumen yang benar dibuang sebelum diberi skor).

Acuan implementasi: `heuristik_openmrs()` di `riset/eksperimen2.py`. Ikuti
persis, termasuk urutan penyaringan lalu penskoran, dan bobot
+10000 / +1000 / +500 / +100.

## Jangan

- Jangan "memperbaiki" B0 supaya lebih masuk akal. Ia harus meniru yang ada,
  bukan yang seharusnya.
- Jangan menambahkan toleransi salah ketik ke B0. Ketiadaan itulah yang diukur.
