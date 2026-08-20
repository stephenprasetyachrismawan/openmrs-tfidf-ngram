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
