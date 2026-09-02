# Tugas 14 — Eksperimen K2 (saran ketik "Maksud Anda")

## Catatan urutan

Implementasi K2 (`BigramJaccardSuggester`, endpoint `/unifiedsearch/saran`)
**sudah** di main sejak commit `8ef5799` — di luar urutan tugas 00–13, atas
permintaan pemilik repo. Tugas ini adalah **bagian pengukurannya**, yang
sebelumnya kosong.

## Tujuan

Mengukur kontribusi K2 dengan dua metrik: akurasi saran, dan penyelamatan
query buntu. Menghasilkan `riset/hasil5/` dan bagian hasil di
`docs/proposal.html`.

## Rujukan

- Spec: `docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md`
- Rencana: `docs/superpowers/plans/2026-09-01-eksperimen-k2.md`
- Algoritma suggester: `docs/algoritma.md` bagian 6b

## Langkah

1. Ekspor `hasillab` + `kondisi` dari DB (`riset/ekspor_hasillab.sql`,
   `riset/ekspor_kondisi.sql` → `riset/data/*.jsonl`, di-commit).
2. `riset/eksperimen_k2.py` — impor `eksperimen2` sebagai modul (JANGAN
   panggil `main()` — aturan 10). Loader 8 entitas, jalur peringkat
   8 entitas (`bangun8`/`cari8`), reimplementasi suggester (`saran_k2`),
   query set K2 (`bangun_query_k2`), dua metrik.
3. Verifikasi: `cek_determinisme_k2.py`, `cek_cross_k2.py`,
   `test_eksperimen_k2.py`.
4. Tulis hasil: `hasil5/laporan.md`, `docs/proposal.html` §6, `keputusan.md`,
   `docs/ringkasan-hasil.md`, `docs/algoritma.md`, `docs/kontrak-data.md`,
   `CLAUDE.md`.

## Selesai kalau

- [x] `python riset/eksperimen_k2.py` menghasilkan
      `hasil5/{hasil.json, query_k2.json, per_query_k2.json, ringkasan.csv}`.
- [x] `python riset/cek_determinisme_k2.py` → OK (3 proses identik).
- [x] `python -m pytest riset/test_eksperimen_k2.py` → semua lulus.
- [ ] `python riset/cek_cross_k2.py` → cocok. **TERTUNDA** — stack REST
      OpenMRS mati (webservices.rest gagal load, seperti `keputusan.md`
      item #6). Angka laporan dari reimplementasi Python; cross-check
      dijalankan setelah stack pulih.
- [ ] Sub-cek privilege live (aturan 5). **TERTUNDA** bersama cross-check.
      Jalur kode ada (`UnifiedSearchService.saran`, gerbang `mayViewPatients`);
      uji unit khusus = pekerjaan lanjutan.
- [x] Angka di `hasil5/laporan.md`, `docs/proposal.html` §6 subbagian K2,
      `docs/ringkasan-hasil.md` semua bersumber `hasil5/hasil.json`.
- [x] `hasil3/`, `hasil4/` tak berubah.

## Perhatian

- K2 = S1 (Interactive Query Expansion), **bukan** K7. Metrik penyelamatan
  mensimulasikan **klik pengguna**, bukan penulisan ulang otomatis. `q'`
  hanya ada setelah klik; tak pernah diumpankan balik ke pipeline peringkat.
- K2 tak punya angka nDCG. Jangan menaruhnya setara K4.
- Korpus K2 (8 entitas) terpisah dari `hasil3/` (6 entitas). Angka K1 tetap
  hanya berlaku untuk enam entitas asli.
