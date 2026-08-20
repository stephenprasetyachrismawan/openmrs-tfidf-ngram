# Tugas 11 — ESM "Pencarian Terpadu" (mockup B)

> Prasyarat: tugas 10 lulus. Kalau tugas 10 gagal dan kelompok turun ke JSP,
> berkas ini digantikan — tanya manusia dulu.

## Tujuan
Halaman pencarian lintas 6 tabel di dalam RefApp 3, memanggil endpoint REST
dari tugas 09.

## Acuan rancangan
`docs/proposal.html` bagian 4.2. Perilaku dan tata letaknya sudah ada di sana
sebagai mockup yang berjalan — tiru perilakunya, bukan kodenya (mockup itu
menghitung di browser; di sini perhitungan ada di server).

## Yang harus ada

1. **Satu kotak pencarian**, hasil muncul saat mengetik. Debounce ±150 ms.
2. **Hasil dikelompokkan per jenis data** (konsep, obat, pasien, form, lokasi,
   provider), dengan label jumlah per kelompok.
3. **Pemilih mode**: `b0`, `b1`, `e1`, `e3` — diteruskan apa adanya ke
   parameter `mode` endpoint. Default `e3`.
   Ini bukan fitur pengguna akhir; ini alat peraga penelitian. Beri keterangan
   singkat di layar tentang arti tiap mode.
4. **Penyorotan huruf yang cocok** pada nama hasil.
5. **Skor ditampilkan** di tiap baris.
6. Keadaan kosong, keadaan memuat, dan keadaan galat ditangani.

## Aturan yang mengikat

- **Perhitungan peringkat ada di server.** ESM ini tidak boleh menghitung
  TF-IDF, kepingan, atau RRF sendiri. Kalau tergoda menyalin logika dari mockup
  di `proposal.html`, jangan — dua implementasi akan berbeda diam-diam dan
  angka penelitian jadi tidak bisa dipertahankan.
- **Baris pasien hanya muncul kalau server mengembalikannya.** Penyaringan hak
  akses ada di server (aturan 5 `CLAUDE.md`). Jangan menyaring di klien, dan
  jangan mengakali kalau pasien tidak muncul — itu berarti privilege memang
  tidak ada.
- Teks antarmuka bahasa Indonesia; kode dan nama variabel bahasa Inggris.
- Pakai komponen Carbon yang sudah dipakai RefApp. Jangan menambah pustaka UI
  baru.

## Selesai kalau

- Mengetik `diabete` menampilkan hasil dari lebih dari satu jenis data.
- Mengetik `diabete melitus` menampilkan konsep Diabetes mellitus —
  inilah demo utamanya.
- Mengganti mode ke `b0` pada query yang sama menghasilkan **0 hasil**.
  Kalau `b0` tetap memberi hasil, mode tidak benar-benar diteruskan ke server.
- Tidak ada permintaan jaringan ke luar (periksa tab Network).
- Seluruh app RefApp lain masih berfungsi.
- `docker ps` tetap 4 container.

## Jangan

- Jangan menyimpan hasil pencarian ke penyimpanan browser.
- Jangan menambahkan pemeringkatan berbasis popularitas atau riwayat klik —
  proposal secara eksplisit menolaknya (bagian 4.1, "Yang TIDAK kami tiru dari
  Algolia").
