# Ringkasan hasil — Pencarian Terpadu OpenMRS dengan TF-IDF Kata–Karakter

Satu halaman, dibaca sebelum naskah lengkap (`article/main.pdf`). Angka di sini
konsisten dengan naskah — kalau ada beda, laporkan.

**Seluruh angka pada berkas ini berasal dari korpus 8 entitas**, sama persis
dengan cakupan modul yang dipasang (`DocumentRepository.ENTITAS`). Angka
6-entitas yang lebih tinggi dan pernah beredar **sudah tidak berlaku** dan
berkas hasilnya dihapus — riwayat lengkapnya di `docs/keputusan.md` entri "F2".

## Apa yang dibangun

Modul OpenMRS (`.omod`) yang menambah pencarian tahan-salah-ketik lintas
8 tabel (konsep, obat, pasien, form, lokasi, provider, hasil laboratorium,
kondisi), tanpa Lucene, tanpa server indeks terpisah, tanpa GPU, tanpa internet:

- **17 indeks** (8 lokal kata + 8 lokal kepingan karakter + 1 global) dibangun
  di memori saat modul start — **2,08 detik**, 8.045 dokumen, 35.914 surface form.
- **Tiga endpoint REST**: pencarian (`mode=b0|b1|e1|e3`), saran ketik, evaluasi.
  Latensi p95 **50 ms** setelah JVM hangat.
- **Tiga antarmuka**: halaman JSP legacy UI, ESM "Pencarian Terpadu", dan ESM
  "Perbandingan Pencarian" (tiga kolom berdampingan).

## Angka utama — himpunan uji 260 kueri, sekali jalan

| Sistem | nDCG@10 | P@1 | R@10 | MRR | % 0-hasil |
|---|---|---|---|---|---|
| B0 pencocokan awalan | 0,631 | 0,662 | 0,594 | 0,725 | **15,4%** |
| B1 TF-IDF kata | 0,568 | 0,492 | 0,600 | 0,612 | 8,8% |
| E1 + kepingan karakter | **0,681** | 0,623 | 0,712 | 0,737 | **0,0%** |
| E3 + Weighted RRF (usulan) | 0,670 | **0,681** | **0,743** | **0,780** | **0,0%** |

## Ablasi — apa yang sebenarnya bekerja

| Perbandingan | ΔnDCG | p | Bacaan |
|---|---|---|---|
| E1 vs B1 (+ kepingan) | **+0,114** | **<0,001** | satu-satunya perolehan besar |
| E1 vs B0 | +0,050 | 0,045 | signifikan nominal |
| **E3 vs B0 (sistem penuh)** | **+0,039** | **0,079** | **TIDAK signifikan** |
| E3 vs E1 (+ Weighted RRF) | −0,011 | 0,406 | tidak menyumbang nDCG |
| B1 vs B0 (TF-IDF kata saja) | −0,064 | 0,021 | lebih buruk dari B0 |
| E2 vs B0 (RRF tanpa bobot) | −0,246 | <0,001 | merugikan |
| E4 vs B0 (perluasan kueri) | −0,164 | <0,001 | merugikan, dibuang |

**Klaim peningkatan mutu peringkat tidak dapat dipertahankan** pada korpus penuh.
Terhadap endpoint pencarian konsep OpenMRS asli (B0′, 34 kueri konsep dev):
E3 **+0,055, p=0,269** — juga tidak signifikan.

## Yang tetap bertahan

1. **Layar kosong hilang.** B0 meninggalkan 15,4% kueri tanpa hasil; seluruh
   varian berkepingan karakter menurunkannya ke **0,0%**.
2. **Ketahanan salah ketik** naik hampir 8×: nDCG pada kueri typo 0,086 (B0) →
   0,713 (E1) / 0,668 (E3).
3. **Pembobotan pada fusi antar-entitas wajib.** RRF tanpa bobot meruntuhkan
   P@1 ke 0,108; pembobotan mengembalikannya ke 0,681.

## Temuan utama — manfaatnya bergantung sifat entitas

Selisih E3 − B0 per entitas (test 260):

```
form +0,333 | obat +0,232 | konsep +0,171 | lokasi +0,058 | provider 0,000
                              kondisi −0,053 | hasil lab −0,180 | pasien −0,242
```

Kepingan karakter **besar pada entitas berciri kamus, negatif pada entitas
instans per-pasien**. Sebabnya struktural: 2.018 hasil laboratorium hanya memuat
203 judul unik ("Haemoglobin" ×37), sehingga kueri tidak punya satu jawaban benar
melainkan puluhan yang sama sahihnya. Pada `hasillab` fusi berbobot memperburuk
secara khusus (E1 0,650 → E3 0,399): RRF mengubah skor identik jadi peringkat
berurutan, dan `1/(k+r)` mendorong kandidat setara keluar dari sepuluh besar.

## Jalur saran ketik (K2) — metrik interaksi, bukan nDCG

214 kueri, korpus sama. Akurasi hit@1/hit@3/hit@6/MRR@6 =
**0,682 / 0,813 / 0,869 / 0,754**. Kuat pada salah ketik kata utuh
(`typo` hit@6 0,985), lemah pada kueri sangat pendek (`typo_pendek` hit@1 0,125).
Penyelamatan kueri buntu 15,8%; kueri 0-hasil 3,7% → **0,0%** setelah satu klik.

## Verifikasi implementasi

| Mode | Java live | Python | Selisih |
|---|---|---|---|
| b0 | 0,5613 | 0,5613 | 0,0000 |
| b1 | 0,5546 | 0,5534 | 0,0012 |
| e1 | 0,7383 | 0,7371 | 0,0012 |
| e3 | 0,7368 | 0,7368 | 0,0000 |

Korpus cocok persis (8.045 / 35.914 / 17). `b0` dan `e3` mereproduksi Python
tanpa selisih; `b1`/`e1` beda 0,0012 (≈1 kueri) akibat pembalikan pasangan seri
pada skor kosinus yang bernilai persis sama — `e3` memeringkat menurut peringkat
bilangan bulat sehingga kebal.

## Parameter — dikunci di 100 kueri dev

| Parameter | Nilai | Catatan sapuan 8 entitas |
|---|---|---|
| ALPHA | 0,20 | **argmax tepat di 0,20** |
| NGRAM | 4 | n=3 unggul +0,0071 tapi p=0,2806 (derau) |
| K_RRF | 20 | seluruh titik beda ≤0,0056 |
| EPS | 0,05 | seluruh titik beda ≤0,0021 |
| ambang skor | 1e-6 | nilai riset |

## Keterbatasan

- **Kueri sintetis**, bukan log klinisi. Sistem unggul hanya pada 1 dari 5 jenis
  degradasi, sehingga hasil agregat peka terhadap asumsi distribusi kesalahan.
- **Dua model relevansi dalam satu metrik** (tautan struktural + kesamaan judul)
  melemahkan ketertafsiran nDCG gabungan.
- **Kueri pasien & hasil lab tidak realistis** — pada praktik nyata selalu
  disertai penyaring terstruktur, bukan teks bebas murni.
- **B0′ hanya 34 kueri konsep** — daya uji rendah; ketiadaan signifikansi bukan
  bukti ketiadaan perbedaan.
- **Skala korpus** belum diuji pada CIEL penuh (~50.000 konsep).
- **Indeks tidak diperbarui otomatis** — dibangun sekali saat modul start.
- **Belum ada validasi klinis.**

## Sisa pekerjaan manusia

1. **Rekaman layar demo** — tidak ada perekam layar di lingkungan agen.
2. **Uji instalasi bersih** (`docker compose down -v`) — melanggar aturan
   satu-stack tanpa izin eksplisit.

## Rujukan lengkap

`article/main.pdf` (naskah), `docs/algoritma.md` (spesifikasi), `docs/keputusan.md`
(keputusan & temuan, kronologis), `docs/reproduksi.md` (langkah instalasi),
`riset/hasil6/` (K1 8 entitas), `riset/hasil4/` (B0′), `riset/hasil5/` (saran
ketik).
