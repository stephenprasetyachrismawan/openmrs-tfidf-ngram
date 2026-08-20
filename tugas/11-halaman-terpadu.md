# Tugas 11 — Halaman 2: menu Pencarian Terpadu (mockup B)

## Tujuan
Halaman baru: satu kotak mencari keenam entitas, dengan pemilih metode.

## Rujukan
Mockup hidup di `docs/proposal.html` bagian 4.2.

## Langkah
1. Halaman baru + entri menu.
2. Pemilih metode B0 / B1 / E1 / E3, dikirim sebagai parameter `mode`.
   Default `e3`.
3. Hasil dikelompokkan per entitas, tiap baris menampilkan:
   nama, konteks, skor, dan untuk mode `e3` juga peringkat di tabelnya + skor asli.
4. Tiap baris menautkan ke rekaman aslinya di OpenMRS.
5. Beri label jelas bahwa halaman ini **eksperimental**.

## Selesai kalau
- Berpindah metode mengubah hasil tanpa memuat ulang halaman.
- `diabete melitus` + B0 → kosong; + E3 → berisi. Bedanya terlihat jelas.
- `pulm edem` + B0 → berisi; + B1 → kosong. (Ini contoh yang memperlihatkan
  TF-IDF kata murni justru lebih buruk dari OpenMRS.)
- Hasil pasien tidak muncul untuk pengguna tanpa privilege.
- Tiap baris bisa diklik dan membuka rekaman yang benar.
