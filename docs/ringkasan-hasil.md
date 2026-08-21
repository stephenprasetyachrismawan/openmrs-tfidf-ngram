# Ringkasan hasil — Pencarian Terpadu OpenMRS dengan TF-IDF Kata–Karakter

Satu halaman, dibaca sebelum proposal penuh (`docs/proposal.html`). Angka
di sini konsisten dengan proposal — kalau ada beda, proposal yang salah,
laporkan.

## Apa yang dibangun

Modul OpenMRS (`.omod`) yang menambah pencarian tahan-salah-ketik lintas
6 tabel (konsep, obat, pasien, form, lokasi, provider) ke OpenMRS, tanpa
Lucene, tanpa server indeks terpisah, tanpa GPU, tanpa layanan internet:

- **Indeks 13-nya** (6 lokal kata + 6 lokal kepingan karakter + 1 global)
  dibangun di memori saat modul start — **1,2 detik**, 4.748 dokumen,
  29.320 surface form.
- **Endpoint REST** pencarian (`mode=b0|b1|e1|e3`) dan evaluasi. Latensi
  p95 **26 ms** setelah JVM hangat (sekitar 60 ms pada 20 permintaan
  pertama pasca-restart — dilaporkan apa adanya, bukan disembunyikan).
- **Tiga antarmuka**: halaman JSP legacy UI (pencarian + panel evaluasi),
  ESM RefApp 3 "Pencarian Terpadu" (halaman + panel evaluasi setara), dan
  ESM "Perbandingan Pencarian" (tiga kolom berdampingan: pencarian konsep
  OpenMRS asli, baseline kami, sistem usulan kami).

## Angka apa yang didapat, terhadap baseline mana

Tiga sumber angka, **jangan dicampur**:

| Sumber | Cakupan | Baseline | nDCG@10 sistem usulan (E3) |
|---|---|---|---|
| **Test set resmi** (`riset/hasil3/`, 180 query, sekali jalan) | 6 tabel penuh | B0 (tiruan pencocokan awalan kami) | **0,815** (vs B0 0,628, +0,187 p&lt;0,001; vs E1 +0,013 p=0,039) |
| **Dev 100 query** (endpoint Java live, `docs/keputusan.md` "C1") | 6 tabel penuh | B0 (sama) | **0,846** (vs B0 0,660) — cocok Python sampai presisi `double` |
| **Dev 42 query konsep** (`riset/hasil4/`, baseline B0′ = OpenMRS asli) | konsep saja | **B0′ (endpoint fuzzy Lucene OpenMRS sungguhan)** | **0,903** (vs B0′ 0,800, **+0,103, p=0,0076** — jauh lebih kecil dari klaim terhadap B0) |

**Klaim yang bertahan setelah B0′ diukur:** kepingan karakter (E1 vs B0,
+0,174 nDCG, p&lt;0,001, 180 query) tetap satu-satunya komponen yang
terbukti berpengaruh substantif. Weighted RRF (E3 vs E1, +0,013, p=0,039)
tetap efek kecil, signifikan nominal saja — bukan klaim setara K4.

**Klaim yang harus dikoreksi turun:** "+0,174 nDCG di atas heuristik
OpenMRS" mengukur jarak dari tiruan kami (B0), yang terbukti **lebih lemah**
dari OpenMRS sungguhan (B0′ menang atas B0 sendiri, +0,136, p=0,0108).
Terhadap OpenMRS asli, E3 masih menang tapi cuma +0,103 (p=0,0076) — sekitar
4× lebih kecil. Arah klaim tidak berubah; besarnya keunggulan berubah.

## Apa yang tidak berhasil

**Tugas 13 (kotak diagnosis di Visit Note lewat extension slot) — tidak
bisa dikerjakan.** Diperiksa langsung ke RefApp 3 yang berjalan (bukan
diasumsikan): workspace Visit Note pada versi ini
(`@openmrs/esm-patient-notes-app@12.3.4`) tidak punya extension slot sama
sekali — bukan cuma kotak diagnosisnya. Sesuai aturan proyek ("jangan
menambal app resmi, kalau slot tidak ada, laporkan"), rencana ini
dihentikan, bukan dipaksakan. Halaman "Perbandingan Pencarian" dibangun
sebagai gantinya — membuktikan klaim yang sama tanpa menyentuh app resmi.

## Keterbatasan

- **B0 bukan OpenMRS sungguhan** — baseline pencocokan-awalan independen,
  bukan tiruan setia (diuji langsung, kalah dari B0′).
- **B0′ cuma terukur pada 42 query konsep** — endpoint fuzzy OpenMRS
  cuma melayani konsep; lima entitas lain tidak punya padanan pencarian
  tahan-salah-ketik di OpenMRS sama sekali (temuan ini sendiri memperkuat
  klaim kontribusi lintas-6-tabel).
- **Sampel tipis untuk uji per jenis salah ketik** pada 42 query (6-10
  per sel) — gambaran arah, bukan bukti statistik per sel.
- **Skala korpus belum diuji** — kamus CIEL penuh ~50.000 konsep;
  instalasi ini 4.249. Indeks kepingan 3-5× ukuran indeks kata.
- **Indeks tidak diperbarui otomatis** — dibangun sekali saat modul start;
  pasien/konsep baru tidak masuk indeks sampai modul di-restart.

## Yang belum bisa dikerjakan agen — sisa pekerjaan manusia

1. **Rekaman layar demo** (30–60 detik, query gagal di kotak bawaan →
   berhasil di sistem kami). Tidak ada perekam layar di lingkungan agen.
2. **Uji instalasi bersih** dengan volume Docker baru
   (`docker compose down -v`). Melanggar aturan satu-stack tanpa izin
   eksplisit — `docs/reproduksi.md` mencatat bagian mana yang sudah
   diuji (build dari nol, `npm ci` dari nol, skrip verifikasi) dan mana
   yang belum (instalasi database kosong sungguhan).

## Rujukan lengkap

`docs/proposal.html` (proposal penuh), `docs/algoritma.md` (spesifikasi
K1–K6 + B0), `docs/keputusan.md` (seluruh keputusan & temuan, kronologis),
`docs/reproduksi.md` (langkah instalasi), `riset/hasil3/` (test set resmi),
`riset/hasil4/` (eksperimen B0′).
