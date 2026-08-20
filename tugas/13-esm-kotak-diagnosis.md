# Tugas 13 — Kotak diagnosis di Visit Note (mockup A)

> Prasyarat: tugas 11 lulus. Ini tugas yang paling bagus untuk demo sidang,
> tapi paling terakhir karena paling dalam menyentuh RefApp.

## Tujuan
Kotak pencarian diagnosis pada form klinis nyata — tempat petugas sungguhan
mengetik — dibuat instan dan tahan salah ketik.

## Acuan
`docs/proposal.html` bagian 4.1, termasuk tabel "Yang membuatnya terasa seperti
Algolia".

## Langkah

1. Cari titik sisip yang benar. RefApp 3 memakai **extension slot**. Temukan
   slot yang dipakai form Visit Note / pencarian diagnosis, dan daftarkan
   komponen kita ke situ. **Cari, jangan menebak** — telusuri app resmi yang
   sudah mengisi slot itu.

2. Komponen memanggil endpoint pencarian dengan `mode=e3`, dibatasi ke
   entitas konsep saja.

3. Perilaku yang harus ada:
   - hasil muncul saat mengetik (debounce ±150 ms)
   - navigasi keyboard: panah atas/bawah, Enter memilih, Esc menutup
   - penyorotan huruf yang cocok
   - nilai terpilih masuk ke form seperti biasa

## Aturan yang mengikat

- **Jangan mengubah kode app resmi RefApp.** Tambah lewat extension slot, atau
  tidak sama sekali. Kalau slot yang cocok tidak ada, laporkan — jangan
  menambal (patch) app orang lain.
- Kalau endpoint kita gagal atau lambat, komponen harus **kembali ke perilaku
  bawaan**, bukan menampilkan layar rusak. Form klinis tidak boleh macet
  gara-gara modul penelitian.
- Perhitungan tetap di server.

## Selesai kalau

- Buka chart pasien → Visit Note → ketik `diabete melitus` di kotak diagnosis →
  Diabetes mellitus muncul. **Inilah demo inti seluruh penelitian.**
- Ketik `pulm edem` → Pulmonary edema muncul.
- Pilih satu hasil → nilainya benar-benar tersimpan di form.
- Matikan modul backend → kotak diagnosis kembali ke perilaku bawaan, form
  tetap bisa dipakai.
- Seluruh app RefApp lain masih berfungsi.
- `docker ps` tetap 4 container.

## Rekam demonya
Setelah lulus, rekam layar 30–60 detik: query yang gagal di kotak bawaan, lalu
query yang sama berhasil di kotak kita. Simpan di `docs/arsip/`. Ini bahan
presentasi paling meyakinkan yang bisa dimiliki kelompok, dan lebih baik direkam
saat masih segar daripada dikejar semalam sebelum sidang.
