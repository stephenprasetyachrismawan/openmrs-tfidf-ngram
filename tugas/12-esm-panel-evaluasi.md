# Tugas 12 — Panel evaluasi di dalam ESM

> Prasyarat: tugas 11 lulus.

## Tujuan
Panel di halaman Pencarian Terpadu yang menjalankan data uji bawaan dan
menampilkan metriknya saat itu juga. Ini yang membuat angka penelitian bisa
diperiksa orang lain tanpa membangun ulang apa pun.

## Acuan
`docs/proposal.html` bagian 4.2, sub-bagian "Panel evaluasi di halaman yang sama".

## Yang harus ada

1. Tombol **"Jalankan seluruh data uji"** memanggil endpoint evaluasi
   (tugas 09). Perhitungan ada di server.
2. Tabel metrik agregat: **P@1, P@5, R@10, MRR, MAP, nDCG@10**, plus persentase
   query nol-hasil, waktu indeks, dan latensi rata-rata.
3. Tabel perbandingan **B0 / B1 / E1 / E3** berdampingan, dengan nDCG@10 sebagai
   kolom utama.
4. Rincian **per jenis kesalahan ketik** (persis, tipo, trunkasi, hilang kata,
   urut balik). Ini tabel paling menjelaskan di seluruh penelitian — heuristik
   OpenMRS runtuh pada tipo tapi menang pada trunkasi.
5. Keterangan singkat arti tiap metrik, dalam bahasa Indonesia, ditulis untuk
   orang yang belum pernah mendengarnya. Ambil dari bagian 5.5 proposal.

## Aturan yang mengikat

- **Jangan menampilkan angka yang tidak dihitung saat itu.** Dilarang keras
  menaruh angka dari `docs/proposal.html` sebagai nilai bawaan atau contoh
  tampilan. Seluruh premis panel ini adalah angkanya dihitung ulang di depan
  mata. Sebelum tombol ditekan, tabelnya kosong.
- Kalau angka hasil panel **berbeda** dari yang tertulis di proposal, itu
  temuan yang dilaporkan ke manusia — bukan sesuatu yang disesuaikan. Aturan 2
  `CLAUDE.md`.
- Jangan membulatkan lebih dari 3 desimal.

## Selesai kalau

- Tombol ditekan → tabel terisi, tanpa galat di console.
- nDCG@10 untuk `e3` berada di sekitar **0,811**, dan untuk `b0` di sekitar
  **0,628**. Selisih besar dari itu harus dilaporkan, bukan didiamkan.
- Menjalankan dua kali berturut-turut memberi angka **identik** — determinisme,
  aturan 1 `CLAUDE.md`. Kalau berbeda, ada kebocoran non-determinisme di server;
  berhenti dan laporkan.
- Panel tetap bisa dibaca di layar selebar 1280 px.

## Catatan
Ini tugas terakhir yang direncanakan. Setelahnya: uji reproduksi di instalasi
bersih, dan penulisan laporan.
