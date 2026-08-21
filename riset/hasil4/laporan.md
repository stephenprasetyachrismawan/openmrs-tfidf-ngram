# Eksperimen 3 — Baseline B0′ (pencarian konsep OpenMRS asli)

Menjawab temuan D1 (`docs/keputusan.md`): `OpenMrsHeuristic` (B0) adalah
tiruan pencocokan-awalan yang gagal total pada query typo, sementara
endpoint fuzzy OpenMRS asli sebagian besar berhasil. B0 karena itu bukan
baseline yang setia terhadap OpenMRS sungguhan. Eksperimen ini mengukur
B0′ langsung dari endpoint asli.

## Metodologi

**Cakupan.** Gold dev 100 query terdiri dari konsep 42, obat 29, pasien 16,
form 6, lokasi 5, provider 2. Endpoint fuzzy OpenMRS hanya melayani
**konsep**, jadi B0′ hanya diukur pada **42 query konsep** — bukan cacat,
melainkan temuan tersendiri: OpenMRS tidak menyediakan pencarian tahan
salah ketik untuk lima entitas lainnya sama sekali.

Rincian 42 query menurut jenis salah ketik: **typo 10, urut_balik 9,
trunkasi 9, persis 8, hilang_kata 6**. Cukup untuk gambaran arah, **tipis
untuk uji signifikansi per jenis** — sel-sel tabel per-jenis di bawah berisi
6–10 query dan tidak boleh dibaca sebagai bukti statistik.

**Endpoint & parameter, dipatok eksplisit:**

```
GET /openmrs/ws/rest/v1/concept?name=<query>&searchType=fuzzy&limit=50&v=custom:(uuid,display)
```

Tanpa filter `class` (beda dari skrip D1 yang meniru kotak diagnosis —
untuk eksperimen ini filter kelas akan membuang jawaban benar sebelum
dinilai, karena 42 query konsep mencakup kelas Test/Procedure/Finding/dst,
bukan cuma Diagnosis).

**Temuan metodologis yang tidak terduga — parameter `locale` merusak
pencarian.** Rencana awal memakai `&locale=en` eksplisit di URL supaya
locale terpatok dan direproduksi. Diverifikasi langsung lewat curl
**sebelum** dipakai di eksperimen: menambahkan parameter itu membuat
endpoint mengembalikan **daftar tetap yang sama sekali tidak berkaitan**
untuk *setiap* query — "kidney" pun memberi `ATT DEFAULT ATTACHMENT`,
`Heparins`, dst. Tanpa parameter locale, hasilnya benar. Locale karena itu
dipatok lewat **sesi** (`GET /ws/rest/v1/session` → `"locale":"en"`,
dikonfirmasi tiap jalan skrip), bukan lewat parameter query. `&limit=50`
sendiri diverifikasi aman (tidak mengubah relevansi, cuma membatasi jumlah).

**Pemetaan uuid → `konsep:<id>`.** Endpoint mengembalikan uuid konsep, bukan
`concept_id` yang dipakai `rel`/gold. Dipetakan lewat SQL langsung
(`SELECT concept_id, uuid FROM concept`, 4.252 baris) — tersimpan di
`uuid_ke_concept_id.json` supaya bisa diperiksa ulang tanpa server hidup.

**Dedupe.** Uuid hasil di-dedupe (pertahankan kemunculan pertama) **sebelum**
dipotong ke top-10 — satu konsep bisa muncul lebih dari sekali kalau
beberapa alias/kodenya cocok.

**Dua angka, bukan satu.** B0′-apaadanya (hasil mentah, termasuk konsep di
luar korpus 4.249-baris `ekspor_konsep.sql`) dan B0′-korpus (hasil di luar
korpus dibuang sebelum dinilai — adil terhadap sistem kita, yang memang
cuma bisa mengembalikan isi korpus).

**Determinisme.** Tiap query dipanggil dua kali, urutan uuid dibandingkan.

**Metrik & gold.** `EvalMetrics`/`metrik()` yang sama dipakai di seluruh
penelitian (P@1, P@5, R@10, MRR, MAP, nDCG@10, %nol-hasil), `rel` dari
`gold-dev-100.json` yang sama. B0, B1, E1, E3 dinilai ulang pada 42 query
yang sama (sistem itu sendiri tidak diubah — cuma dijalankan pada subset
konsep, bukan 100 query dev penuh, supaya adil dibandingkan dengan B0′).

**Signifikansi.** Bootstrap berpasangan (seed=7, top-10) — fungsi yang sama
dipakai di seluruh penelitian.

## Hasil — 0/104 hasil di luar korpus, 0/42 query tidak deterministik

| | Hasil |
|---|---|
| Hasil di luar korpus 4.249-baris | **0 / 104 (0,0%)** — B0′-apaadanya = B0′-korpus persis sama |
| Query tidak deterministik (dua panggilan beda urutan) | **0 / 42** |

Tidak ada bias dari kedua sumber ini pada instalasi ini — dilaporkan
sebagai temuan positif, bukan diasumsikan.

### Tabel utama — 42 query konsep, semua sistem berdampingan

| Sistem | P@1 | P@5 | R@10 | MRR | MAP | nDCG@10 | % nol-hasil |
|---|---|---|---|---|---|---|---|
| B0 (pencocokan awalan, tiruan kami) | 0,762 | 0,152 | 0,558 | 0,762 | 0,550 | 0,664 | 23,8% |
| **B0′ (OpenMRS asli, fuzzy Lucene)** | **0,905** | 0,190 | 0,669 | 0,913 | 0,661 | **0,800** | 7,1% |
| B1 (TF-IDF kata) | 0,714 | 0,200 | 0,672 | 0,762 | 0,557 | 0,693 | 7,1% |
| E1 (TF-IDF + kepingan) | 0,976 | 0,252 | 0,794 | 0,988 | 0,765 | 0,893 | 0,0% |
| **E3 (usulan)** | 0,976 | 0,257 | 0,802 | 0,988 | 0,775 | **0,903** | 0,0% |

**B0′ jauh mengungguli B0** (+0,136 nDCG@10) — mengonfirmasi temuan D1
secara kuantitatif: tiruan heuristik kami meremehkan kemampuan OpenMRS
sungguhan. **E3 tetap unggul di atas B0′**, tapi dengan margin yang jauh
lebih kecil dari yang terlihat melawan B0.

### Uji signifikansi (bootstrap, seed=7, top-10, n=42)

| Pasangan | Selisih nDCG | CI 95% | p | Bacaan |
|---|---|---|---|---|
| **E3 vs B0′** | **+0,103** | [+0,034, +0,183] | **0,0076** | Signifikan — E3 tetap lebih baik dari OpenMRS asli, tapi margin ~4× lebih kecil dari E3 vs B0 |
| E3 vs B0 | +0,239 | [+0,127, +0,364] | 0,0002 | Signifikan — tapi B0 bukan baseline yang setia (lihat B0′ vs B0 di bawah) |
| B0′ vs B0 | +0,136 | [+0,040, +0,248] | 0,0108 | Signifikan — OpenMRS asli mengalahkan tiruan kami sendiri |

**Bacaan jujur:** klaim "+0,174 nDCG di atas heuristik OpenMRS" (dari tabel
uji resmi 180-query, terhadap B0) **tidak salah**, tapi mengukur jarak dari
proksi yang lemah. Terhadap baseline yang sesungguhnya (B0′, walau cuma
diukur pada 42 query konsep dev, bukan 180 query uji), E3 masih menang
(+0,103, p=0,0076), tapi keunggulannya jauh lebih kecil. **Kesimpulan
penelitian tidak berubah** — E3 tetap lebih baik dari pencarian konsep
OpenMRS asli — tapi besarnya keunggulan itu, bukan cuma arahnya, harus
dikoreksi turun secara jujur di laporan akhir.

### Per jenis kesalahan ketik (42 query, n=6-10/sel — arah, bukan bukti statistik)

| Jenis | n | B0 | B0′ | E3 |
|---|---|---|---|---|
| persis | 8 | 0,819 | 0,819 | 0,869 |
| typo | 10 | **0,000** | 0,604 | **0,913** |
| trunkasi | 9 | **0,942** | 0,904 | 0,929 |
| hilang_kata | 6 | 0,971 | 0,971 | 0,971 |
| urut_balik | 9 | 0,781 | 0,781 | 0,850 |

**Temuan paling menjelaskan di eksperimen ini:** pada **trunkasi**, B0
(tiruan kami) sendiri sedikit **mengungguli** B0′ (OpenMRS asli) *dan* E3
(0,942 vs 0,904 vs 0,929) — pencocokan awalan murni memang unggul pada
kata yang dipotong, persis klaim lama proposal, dan sekarang terbukti juga
berlaku dibanding OpenMRS asli, bukan cuma dibanding TF-IDF kata. Pada
**typo**, urutannya berbalik total: B0 runtuh ke nol, OpenMRS asli (fuzzy
Lucene) menutup sebagian besar lubang itu (0,604), dan E3 tetap tertinggi
(0,913).

## Berkas

- `mentah_b0prime.json` — jawaban mentah kedua panggilan tiap query (untuk
  dinilai ulang tanpa server hidup).
- `uuid_ke_concept_id.json` — peta uuid → concept_id dari SQL langsung.
- `hasil.json`, `per_query.json`, `ringkasan.csv` — angka teragregasi dan
  per-query.

## Yang TIDAK dilakukan

Parameter penelitian (ALPHA, NGRAM, K_RRF, EPS) **tidak disentuh** —
terkunci sejak tugas 06/06b. `OpenMrsHeuristic.java` **tidak diubah** — B0
tetap seperti semula, B0′ adalah baseline TAMBAHAN, bukan pengganti. Test
set (`qs[100:]`) **tidak disentuh** — seluruh eksperimen ini memakai
`qs[:100]`, subset konsepnya saja.
