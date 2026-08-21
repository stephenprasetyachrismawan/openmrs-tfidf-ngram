# Pencarian Terpadu OpenMRS — repo kerja

Modul OpenMRS yang menambahkan pencarian tahan salah ketik, memakai TF-IDF pada
dua unit sekaligus: kata dan kepingan karakter 4 huruf.

## Cara memakai repo ini dengan Claude Code

1. Buka folder ini di terminal, jalankan `claude`.
2. Agen otomatis membaca `CLAUDE.md` — itu aturan proyeknya.
3. Kerjakan tugas berurutan:

```
kerjakan tugas/00-persiapan.md
```

Setelah lulus kriteria "Selesai kalau", lanjut ke berikutnya.

Jangan melompati urutan. Tugas 04 bergantung pada kelas yang dibuat di tugas 03,
dan seterusnya.

## Peta berkas

```
CLAUDE.md                  aturan proyek — dibaca agen otomatis
docs/
  proposal.html            proposal lengkap + mockup hidup + contoh perhitungan
  kontrak-data.md          bentuk dokumen virtual, wajib diikuti persis
  algoritma.md             spesifikasi K1–K6 dan baseline B0
  arsitektur-halaman.md    cara memindahkan mockup jadi halaman modul
tugas/
  00 … 12                  tugas berurutan, masing-masing dengan kriteria uji
```

## Urutan pengerjaan

| # | Tugas | Hasil |
|---|---|---|
| 00 | Persiapan lingkungan | OpenMRS + Maven jalan |
| 01 | Kerangka OMOD | `.omod` terpasang |
| 02 | Dokumen virtual (K1, K2) | 4.748 dokumen, 29.320 surface form |
| 03 | Indeks TF-IDF kata (K3) | pencarian cosine |
| 04 | Kepingan karakter (K4) | **komponen inti** |
| 05 | Fusi (K5) | satu skor per dokumen |
| 06 | Menetapkan ALPHA | utang penelitian dilunasi |
| 07 | Weighted RRF (K6) | penyatuan 6 tabel |
| 08 | Baseline B0 | pembanding |
| 09 | Endpoint REST | dua endpoint + indeks startup |
| 10 | Halaman search box | **kirim duluan** |
| 11 | Halaman menu terpadu | pemilih metode |
| 12 | Panel evaluasi | reproduksibilitas |

## Tiga hal yang paling sering salah

1. **Determinisme.** Kunci urut harus `(-skor, id)`. Pernah menghabiskan berjam-jam.
2. **Surface form.** Alias tidak boleh dilebur jadi satu string.
3. **Klaim berlebihan.** Hanya kepingan karakter yang terbukti signifikan secara
   substantif (+0,174, p&lt;0,001). Weighted RRF (E3 vs E1) +0,013, p=0,039 —
   nominal signifikan, efek kecil; jangan dilebih-lebihkan. Perluasan query
   memperburuk dan sudah dibuang.

## Keterbatasan yang sudah diketahui

- Indeks dibangun saat startup, tidak diperbarui saat data berubah.
- Belum diuji pada kamus CIEL penuh (~50.000 konsep).
- Query uji dibangkitkan program, belum dikonfirmasi klinisi.
