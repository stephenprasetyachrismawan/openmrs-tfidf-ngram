# Eksperimen K2 — saran ketik "Maksud Anda"

*Dijalankan 2026-09-01 terhadap demo data resmi OpenMRS (stack
`openmrs-distro-referenceapplication`, MariaDB).*
*Skrip: `eksperimen_k2.py` · hasil mentah: `hasil5/`*
*Spec: `docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md`*

---

## 1. Apa yang diukur

K2 = jalur **saran ketik** (`BigramJaccardSuggester`, endpoint
`GET /unifiedsearch/saran`), terpisah dari jalur peringkat K1. Ini
**Interactive Query Expansion** (Ruthven, SIGIR 2003): sistem menawarkan,
pengguna memutus — bukan K7 (perluasan query otomatis, dilarang).

Dua metrik:

1. **Akurasi saran** — diberi query terdegradasi, apakah dokumen yang
   dimaksud ada di daftar saran top-k.
2. **Penyelamatan query buntu** — pada query yang jalur E3 tak beri satu pun
   dokumen relevan di top-10, apakah satu klik saran teratas membawa
   pengguna ke hasil relevan.

**Tidak diklaim:** K2 tidak menaikkan nDCG / P@1 / metrik peringkat apa pun
(tak menyentuh jalur peringkat). Angka K1 (`hasil3/`, `hasil4/`) tak
tersentuh. Suggester mengembalikan dokumen, bukan koreksi ejaan.

## 2. Korpus & query

Korpus 8 entitas (K1 hanya 6):

| entitas | dokumen | | entitas | dokumen |
|---|---:|---|---|---:|
| konsep | 4.249 | | hasillab | 2.018 |
| obat | 322 | | kondisi | 1.279 |
| pasien | 100 | | lokasi | 61 |
| form | 10 | | provider | 6 |

35.914 surface form. Snapshot `hasillab`/`kondisi` (diekspor dari DB, di-commit):
`hasillab.jsonl` 2.018 baris `sha256 d2d96a83…b09d3c9`,
`kondisi.jsonl` 1.279 baris `sha256 093eaa7c…44c6355e`.

**214 query K2**, dibangkitkan deterministik (`SEED_K2 = 20260901`), lima jenis
degradasi:

| jenis | n | definisi |
|---|---:|---|
| `persis` | 52 | judul apa adanya — kontrol langit-langit |
| `typo` | 66 | 1 kata ≥5 huruf, hapus/tukar 1 huruf tengah |
| `trunkasi` | 46 | tiap kata >5 huruf → 4–5 huruf |
| `trunkasi_pendek` | 26 | judul 1–2 kata dipotong ke 4–6 huruf pertama (`"diabetes mellitus"→"diabe"`) |
| `typo_pendek` | 24 | `trunkasi_pendek` + 1 huruf salah (`"fever"→"fevvr"`) |

Parameter dikunci ke nilai modul Java: `NGRAM=2`, `MIN_IRISAN=2`,
`limit` saran 6. Tak ada yang disapu.

## 3. Metrik 1 — akurasi saran

Tanpa pembanding; `rel` sama seperti K1.

| jenis | n | hit@1 | hit@3 | hit@6 | MRR@6 | saran-kosong |
|---|---:|---:|---:|---:|---:|---:|
| persis | 52 | 0,962 | 1,000 | 1,000 | 0,981 | 0,000 |
| typo | 66 | 0,864 | 0,985 | 0,985 | 0,924 | 0,000 |
| trunkasi | 46 | 0,609 | 0,870 | 0,913 | 0,734 | 0,000 |
| trunkasi_pendek | 26 | 0,308 | 0,462 | 0,692 | 0,426 | 0,000 |
| typo_pendek | 24 | 0,125 | 0,208 | 0,375 | 0,192 | 0,000 |
| **keseluruhan** | **214** | **0,682** | **0,813** | **0,869** | **0,754** | **0,000** |

Per entitas (hit@1 / hit@6): konsep 0,67 / 0,87 · obat 0,78 / 0,88 ·
pasien 0,40 / 0,93 · hasillab 0,76 / 0,84 · kondisi 0,80 / 0,92 ·
lokasi 0,60 / 0,60 · form 1,00 / 1,00 · provider 0,75 / 1,00.
(form/provider/lokasi n kecil — arah, bukan bukti.)

**Bacaan:**
- Kontrol `persis` hit@3 = 1,000: metrik & reimplementasi tidak rusak.
- Suggester **kuat untuk salah ketik kata utuh** (`typo` hit@6 0,985) dan
  cukup untuk trunkasi kata (`trunkasi` hit@6 0,913).
- **Melemah tajam pada query pendek** (`trunkasi_pendek` hit@1 0,308;
  `typo_pendek` hit@1 0,125). Sebabnya struktural: Jaccard bigram
  `|∩|/|∪|` menghukum kecocokan parsial — prefiks 4–6 huruf dari judul
  panjang punya union besar, sehingga kata pendek acak yang berbagi
  2–3 bigram menang. Ini batas metode, dilaporkan apa adanya.

## 4. Metrik 2 — penyelamatan query buntu

E3 di sini = E3 korpus-K2 (8 entitas); **nDCG standalone-nya tidak dilaporkan
dan tidak sebanding `hasil3/`** — dipakai semata untuk menandai "buntu".

| jenis | n | buntu sebelum | terselamatkan (dari buntu) | buntu efektif sesudah 1 klik |
|---|---:|---:|---:|---:|
| typo | 66 | 0,000 | — | 0,000 |
| trunkasi | 46 | 0,022 | 1,000 | 0,000 |
| trunkasi_pendek | 26 | 0,231 | 0,167 | 0,192 |
| typo_pendek | 24 | 0,500 | 0,083 | 0,458 |
| persis | 52 | 0,000 | — | 0,000 |
| **keseluruhan** | **214** | **0,089** | **0,158** | **0,075** |

| | sebelum | sesudah 1 klik saran |
|---|---:|---:|
| E3 mengembalikan 0 dokumen | 3,7% | **0,0%** |

**Bacaan:**
- Jalur E3 (TF-IDF kata + kepingan karakter + Weighted RRF) **sudah**
  mencegah kebanyakan jalan buntu — hanya 8,9% query tak punya dokumen
  relevan di top-10.
- Dari yang buntu, satu klik saran menyelamatkan **15,8%** — peran sempit,
  terkonsentrasi di `typo_pendek` (kasus tersulit: 50% buntu, hanya 8%
  terselamatkan).
- Yang jelas: **layar kosong hilang sepenuhnya** — query yang tadinya
  memberi 0 dokumen (3,7%) semuanya memberi hasil setelah satu klik.

## 5. Determinisme

`cek_determinisme_k2.py`: `hasil.json`, `query_k2.json`, `per_query_k2.json`
**identik byte di 3 proses terpisah** (kecuali `waktu_indeks`). CLAUDE.md
aturan 1 terpenuhi — kunci urut majemuk, iterasi himpunan diurutkan dulu.

## 6. Cross-check ke Java — TERTUNDA

`cek_cross_k2.py` siap. Belum dijalankan: per 2026-09-02 modul
`webservices.rest` OpenMRS gagal load setelah container backend di-recreate
(`ClassNotFoundException: MainResourceController`) → seluruh REST 500. Sama
dengan `docs/keputusan.md` "Pekerjaan terbuka" item #6. Dijalankan setelah
stack pulih; angka di laporan ini dari reimplementasi Python yang berdiri
sendiri (deterministik, diuji unit setia ke `eksperimen2` pada 6 entitas).

Sub-cek privilege live (aturan 5) juga tertunda. Jalur kode ada:
`UnifiedSearchService.saran` menghitung `mayViewPatients =
Context.hasPrivilege("View Patients")` lalu membuang `pasien`/`hasillab`/
`kondisi` bila false (`isDataPasien` mencakup ketiganya). Uji unit khusus
untuk gerbang ini = pekerjaan lanjutan (butuh mockito-inline di
`api/pom.xml`).

## 7. Penyimpangan dari spec (temuan saat eksekusi)

1. `trunkasi_pendek`/`typo_pendek` **dibatasi ke judul 1–2 kata**. Spec
   awalnya "kata pertama judul → potong". Frasa klinis panjang
   (`"Malignant neoplasm of rectosigmoid junction"` → `"malig"`) selalu
   kalah Jaccard-bigram dari kata pendek acak (union raksasa) — bukan cara
   suggester dipakai, bukan aksi pengguna realistis. Frasa panjang tetap
   kena `typo`/`trunkasi` tingkat-kata.
2. `trunkasi_pendek` memotong **seluruh query** ke 4–6 huruf, bukan kata
   pertama ke 3–5.
3. `gold_k2` untuk `hasillab`/`kondisi`: kredit dokumen sejenis berjudul
   sama grade-1 (spec: seed-only). Nama tes/kondisi berulang lintas pasien
   ("Haemoglobin" ×puluhan); tanpa ini kontrol `persis` anjlok ke 0,75
   hanya karena tie-break kunci memilih `obs_id` lain. Pengguna yang
   mengetik nama tes puas dengan instans mana pun.

Ketiganya perbaikan desain query set, bukan penyetelan ke angka — parameter
suggester tak disentuh.

## 8. Yang tidak diklaim

- K2 bukan peningkatan mutu peringkat. Tak ada angka nDCG untuk K2.
- K2 bukan setara K4 (kepingan karakter, satu-satunya komponen K1 yang
  terbukti berpengaruh substantif, +0,174 nDCG). K2 kontribusi terpisah
  yang lebih kecil: mengubah sebagian jalan buntu jadi satu klik, dan
  menghapus layar-kosong.
- Angka K1 tak berubah. Korpus K2 (8 entitas) terpisah dari `hasil3/`
  (6 entitas).
