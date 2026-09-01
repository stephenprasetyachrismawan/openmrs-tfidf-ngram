# Desain — Eksperimen K2 (saran ketik "Maksud Anda")

Tanggal: 2026-09-01
Status: disetujui (brainstorming, 9 bagian + keseluruhan)

## Konteks

Framing artikel: dua kontribusi.

- **K1 — metode peringkat** (menentukan hasil): TF-IDF kata + kepingan karakter
  n-gram + Weighted RRF = sistem E3. Bukti lengkap: `eksperimen2.py` → `hasil3/`
  (7 sistem, 180 query test), `eksperimen3_baseline_asli.py` → `hasil4/` (B0′).
  Sudah siap artikel.
- **K2 — metode pembantu** (mempermudah pengguna): saran ketik Jaccard bigram
  ("Maksud Anda"). Kode ada di main (`BigramJaccardSuggester.java`, commit
  `8ef5799`; tie-breaker `8566ba1`), endpoint `GET /unifiedsearch/saran`.
  **Belum ada eksperimen, belum ada bagian hasil, belum ada di proposal.**

Dokumen ini merancang eksperimen K2 dari nol terhadap implementasi yang ada di
main. (Branch `s1-penyaran-query` / commit `967a99a` berisi implementasi paralel
yang lebih rumit — paket `suggest/` + `tugas/14` 302 baris — yang tim tidak
pakai; sengaja tidak dijadikan rujukan.)

Kondisi K2 di main:
- `BigramJaccardSuggester.search(surfaceForms, query)` — skor tiap surface form
  = Jaccard irisan bigram karakter `|A∩B| / |A∪B|`, `NGRAM=2`.
- Gerbang: surface form diskor hanya bila berbagi **≥2 bigram** dengan query,
  kecuali kedua himpunan bigram identik setelah normalisasi (`MIN_IRISAN=2`).
- Kunci urut majemuk: `-skor`, lalu cocok-lewat-judul sebelum cocok-lewat-alias,
  lalu kunci dokumen (`KUNCI_COMPARATOR`, aturan 1).
- `UnifiedSearchService.saran(q, limit)` — jalankan per entitas, gabung, urut
  ulang `KUNCI_COMPARATOR`, saring `pasien`/`hasillab`/`kondisi` lewat privilege
  "View Patients" (aturan 5), potong ke `limit` (default 6 dari controller).
- Deterministik (TreeMap/TreeSet, kunci majemuk).

---

## Bagian 1 — Ruang lingkup & klaim

### Yang dibuktikan

1. **Akurasi saran** — diberi query terdegradasi (salah ketik / terpotong),
   suggester menaruh dokumen yang dimaksud di daftar saran top-k. Dilaporkan:
   hit@1, hit@3, hit@6, MRR@6, dirinci per jenis degradasi. Tanpa pembanding.
2. **Penyelamatan query buntu** — pada query yang jalur peringkat E3 beri
   0 dokumen relevan di top-10, satu klik saran teratas membawa pengguna ke
   hasil relevan. Dilaporkan: tingkat buntu (sebelum) → tingkat penyelamatan →
   tingkat buntu efektif (sesudah 1 klik).

### Yang TIDAK diklaim (eksplisit di tulisan)

- K2 tidak menaikkan nDCG / P@1 / metrik peringkat apa pun. Tak menyentuh jalur
  peringkat. Ini yang menjaga K2 = S1 interaktif, bukan K7 (perluasan query
  otomatis) yang dilarang.
- Angka K2 tidak mengubah klaim K1 mana pun. Korpus beda (8 entitas vs 6),
  query set beda, `hasil3/` tak tersentuh.
- Suggester mengembalikan **dokumen**, bukan koreksi ejaan; "Maksud Anda: Fever"
  = judul dokumen, bukan string terkoreksi.

### Invarian S1 (bukan K7)

- Query yang dieksekusi selalu = isi kotak pencarian.
- Saran hanya menulis kotak setelah diklik pengguna.
- Sistem tak pernah mengubah query diam-diam.

### Aturan 5

Query set K2 menyertakan `hasillab` + `kondisi` (data pasien). Eksperimen
menjalankan suggester tanpa filter (indeks penuh) tetapi melaporkan bahwa
endpoint live menyaring `pasien`/`hasillab`/`kondisi` lewat privilege
"View Patients" — dan cross-check membuktikannya.

---

## Bagian 2 — Korpus 8 entitas

**Sudah ada** (`riset/data/`, dipakai K1, tak disentuh): `konsep.jsonl` (4249),
`obat.jsonl` (322), `pasien.jsonl` (100), `lain.jsonl` (77 = form+lokasi+provider).

**Baru — diekspor sekali, hasilnya di-commit:**

| Berkas | Skrip | Cermin dari | Isi baris |
|---|---|---|---|
| `riset/data/hasillab.jsonl` | `riset/ekspor_hasillab.sql` | `HasilLabSource.java` | `obs` kelas konsep `Test`/`LabSet`, `voided=0`. `judul` = nama konsep tes (FULLY_SPECIFIED, locale `en`). `alias` = `[nama lengkap pasien preferred]`. `kode` = null. `konteks` = nilai + tanggal (tak diindeks). `tautan_pasien` = `obs.person_id`. `id` = `hasillab:<obs_id>`. |
| `riset/data/kondisi.jsonl` | `riset/ekspor_kondisi.sql` | `ConditionSource.java` | `conditions` `voided=0`. `judul` = nama konsep `condition_coded` (fallback `condition_non_coded` **hanya** bila `condition_coded` NULL). `alias` = `[nama lengkap pasien]`. `kode` = null. `konteks` = `clinical_status` + `onset_date`. `tautan_pasien` = `conditions.patient_id`. `id` = `kondisi:<condition_id>`. |

Format baris = `JSON_OBJECT(...)` per baris, sama pola `ekspor_lain.sql`;
dibaca `encoding="utf-8-sig"`.

**Prosedur ekspor** (sekali):
1. Stack Docker hidup: `cd openmrs-distro-referenceapplication && docker compose up -d`
   (tanpa `-p`, aturan 8).
2. `docker exec <db-container> mysql -uroot -p... openmrs < ekspor_hasillab.sql`
   → `hasillab.jsonl`; sama untuk kondisi.
3. Catat jumlah baris + SHA-256 tiap berkas ke header `eksperimen_k2.py` dan
   entri `keputusan.md`.
4. Commit kedua `.jsonl` → reproduksi berikutnya tak perlu Docker.

**Loader** — `eksperimen_k2.py`:

```python
def muat8():
    rec = eksperimen2.muat()              # 6 entitas, tak diubah
    for fn in ("hasillab.jsonl", "kondisi.jsonl"):
        # baca utf-8-sig; map tautan_pasien -> field 'tautan';
        # entitas, judul, alias, kode, konteks; refs=set(), kelas=""
    return rec
```

`ENT8 = ENT + ["hasillab", "kondisi"]`. Fungsi `bangun` / `bobot_koleksi` di
`eksperimen2.py` mengiterasi `ENT` global → butuh versi 8-entitas: salin
`bangun8` / `bobot_koleksi8` / `cari8` ke `eksperimen_k2.py` yang memakai `ENT8`.

**Catatan tulisan:** `kontrak-data.md` menyatakan hasillab+kondisi "tidak ada
padanan di `eksperimen2.py`". Eksperimen K2 = pengukuran riset pertama yang
menyentuh keduanya. Ditulis eksplisit: ini korpus K2, terpisah dari `hasil3/`
6-entitas; angka K1 tetap hanya berlaku untuk 6 entitas.

---

## Bagian 3 — Query set K2

**Sumber:** record `seed` disampel dari 8 entitas, ditimbang ukuran korpus.
Rencana (~215 query, tanpa dev/test split — tak ada yang disetel, lihat Bagian 7):

| entitas | jml | entitas | jml |
|---|---|---|---|
| konsep | 70 | kondisi | 25 |
| obat | 40 | hasillab | 25 |
| pasien | 30 | lokasi | 15 |
| | | form | 5 |
| | | provider | 5 |

**Degradasi** — mesin `degradasi()` `eksperimen2.py` + 2 jenis baru:

| jenis | dari eksperimen2 | definisi | peran |
|---|---|---|---|
| `persis` | ya | kata judul digabung apa adanya | kontrol langit-langit/sanity (bukan abstain — query persis = cocok bigram sempurna, skor 1,0) |
| `typo` | ya | 1 kata ≥5 huruf, hapus/tukar 1 huruf tengah | kasus inti |
| `trunkasi` | ya | tiap kata >5 huruf → 4–5 huruf | kasus inti |
| `trunkasi_pendek` | **baru** | kata pertama judul → potong ke 3–5 huruf (`"Fever"→"fev"`, `"Diabetes mellitus"→"diabe"`). Buang bila seluruh judul ≤5 huruf. | query sangat pendek — E3 (NGRAM=4) mati |
| `typo_pendek` | **baru** | `trunkasi_pendek` lalu sisip/tukar/gandakan 1 huruf (`"fev"→"fevv"`) | pendek **dan** salah eja — kasus `"fevvr"` (commit `967a99a`) |

Proporsi siklus: `typo` 25%, `trunkasi` 20%, `trunkasi_pendek` 20%,
`typo_pendek` 15%, `persis` 20%.

**Degradasi diterapkan ke `judul` saja** (konsisten `eksperimen2`). Degradasi
alias nama-pasien di hasillab/kondisi = di luar lingkup v1 (dicatat).

**Gold (`rel`):**
- 6 entitas asli: pakai ulang `eksperimen2.gold(r)` — `seed` grade 2, tautan
  obat↔konsep / same-refterm grade 1.
- `hasillab`/`kondisi`: tak ada logika tautan → `{seed_id: 2}` saja
  (konservatif, dicatat).

**Determinisme:** `SEED_K2 = 20260901`, dibangkitkan urut-rencana, tanpa shuffle.
Query set ditulis ke `riset/hasil5/query_k2.json` (`q`, `jenis`, `entitas`,
`seed`, `rel`) + SHA-256 — dibaca ulang, bukan dibangkitkan ulang, saat
cross-check Java.

**Buang:** query <3 huruf, atau yang degradasinya = judul asli (tak berubah).

---

## Bagian 4 — Reimplementasi Python suggester

`_grams_impl(s, 2)` di `eksperimen2.py` sudah cermin persis
`Tokenizer.charGrams(s, 2)` Java (Javadoc menyatakannya; `tools/silang_skor.py`
sudah buktikan jalur karakter cocok 4 desimal).

```python
def saran_k2(lokal8, q, limit=6):
    gq = sorted(set(eksperimen2._grams_impl(q, 2)))     # bigram; norm identik
    if not gq:
        return []
    terbaik = {}                                         # kunci -> (skor, via_judul)
    for e in ENT8:
        idx = lokal8[e]
        for teks, pem, utama in zip(idx["teks"], idx["pem"], idx["utama"]):
            gf = set(eksperimen2._grams_impl(teks, 2))
            if not gf:
                continue
            iris = set(gq) & gf
            if not iris:
                continue
            persis = set(gq) == gf
            if len(iris) < 2 and not persis:             # MIN_IRISAN=2
                continue
            skor = len(iris) / len(set(gq) | gf)          # Jaccard
            prev = terbaik.get(pem)
            if prev is None or skor > prev[0] or (skor == prev[0] and utama and not prev[1]):
                terbaik[pem] = (skor, utama)
    return sorted(terbaik.items(), key=lambda kv: (-kv[1][0], not kv[1][1], kv[0]))[:limit]
```

**Kesetiaan ke Java — 3 titik yang dijaga:**
1. `_grams_impl` = `charGrams` (norm, `' '→'_'`, jendela-2, `len<2`→utuh,
   kosong→`[]`).
2. Gerbang: `|iris| ≥ 2` kecuali `set(gq) == gf` (cocok-persis lolos walau 1 gram).
3. Urut: `(-skor, via_judul dulu, kunci naik)` = `KUNCI_COMPARATOR`. `pem`
   (= `"entitas:id"`) = `kunci`.

**Privilege (aturan 5):** `saran_k2` jalan tanpa filter. Filter
`pasien`/`hasillab`/`kondisi` dilaporkan sebagai perilaku endpoint, dibuktikan
di cross-check (Bagian 7).

**`limit`:** default 6. Metrik hit@k pakai daftar penuh (tak dipotong);
hit@6 = perilaku dropdown nyata.

---

## Bagian 5 — Metrik 1: akurasi saran

Untuk tiap query K2: jalankan `saran_k2()` (daftar penuh), pakai `rel` dari
Bagian 3.

- **hit@k** (k∈{1,3,6}) = 1 kalau ada dokumen `rel>0` di k-teratas daftar
  saran, else 0. Sama konvensi `R@k` di K1.
- **MRR@6** = 1/peringkat kemunculan pertama dokumen `rel>0` dalam 6 teratas,
  else 0.
- **saran-kosong** = proporsi query yang `saran_k2()` mengembalikan daftar
  kosong (gerbang bigram tak lolos apa pun) — diagnostik, bukan kegagalan
  per se.

**Tabel utama** (per jenis degradasi + keseluruhan):

| jenis | n | hit@1 | hit@3 | hit@6 | MRR@6 | saran-kosong |
|---|---|---|---|---|---|---|
| persis | | | | | | |
| typo | | | | | | |
| trunkasi | | | | | | |
| trunkasi_pendek | | | | | | |
| typo_pendek | | | | | | |
| **keseluruhan** | | | | | | |

Kalau `persis` sendiri gagal tinggi → ada bug di reimpl/gerbang, bukan temuan.

**Sekunder** (di `hasil5/hasil.json`, bukan tabel headline): rincian per entitas
(8 baris), pola `per_entitas` K1.

---

## Bagian 6 — Metrik 2: penyelamatan query buntu

Untuk tiap query K2 `q`:

1. Jalankan **E3** (8-entitas, `cari8`) → top-10.
2. **Buntu** = tak ada dokumen `rel>0` di E3 top-10 (setara `nDCG@10 = 0`).
   Mencakup 0-hasil **dan** "ada hasil tapi tak ada yang relevan".
3. Kalau buntu: ambil **saran top-1** dari `saran_k2(q)`. Judul dokumennya →
   query baru `q'`. (Kalau `saran_k2(q)` kosong → penyelamatan gagal.)
4. Jalankan E3 pada `q'` → top-10, dinilai terhadap **`rel` yang sama** (target
   tak berubah, cuma teks query).
5. **Terselamatkan** = E3(`q'`) top-10 sekarang punya dokumen `rel>0`.

**Dilaporkan:**

| jenis | n | buntu sebelum | terselamatkan (dari buntu) | buntu efektif sesudah 1 klik |
|---|---|---|---|---|
| typo | | | | |
| trunkasi | | | | |
| trunkasi_pendek | | | | |
| typo_pendek | | | | |
| persis | | ~0 | — | — |
| **keseluruhan** | | | | |

Plus tabel sempit (kerangka commit `967a99a`):

| | sebelum | sesudah 1 klik saran |
|---|---|---|
| E3 mengembalikan 0 dokumen | x% | y% |

**Keputusan desain (dinyatakan di tulisan):**
- "1 klik" = saran top-1 (aksi tunggal paling realistis). Sekunder: ulangi
  dengan "saran terbaik dari top-3 terlihat" sebagai batas atas — di
  `hasil.json`, bukan headline.
- `q'` = **judul** dokumen saran — cermin perilaku frontend ("klik menimpa isi
  kotak, cari ulang").
- **E3 di sini = E3 korpus-K2 (8 entitas).** nDCG standalone-nya tidak
  dilaporkan dan tidak sebanding `hasil3/`. Dipakai semata untuk mendefinisikan
  "buntu" dan mengukur penyelamatan.
- Mekanisme: saran top-1 benar → `q'` = judul persis → E3(`q'`) peringkat #1
  lewat cocok-judul → terselamatkan. Saran top-1 salah → tetap buntu (menghukum
  saran buruk).

**Opsional (dicatat, tak default):** ulangi metrik penyelamatan dengan jalur
**B0** sebagai basis ("gaya OpenMRS sekarang").

---

## Bagian 7 — Determinisme + cross-check Java

**Determinisme (aturan 1):**
- `saran_k2`: kunci urut `(-skor, bukan via_judul, kunci)` — urutan total.
  Skor = rasio bilangan bulat kecil → seri persis mungkin, dipecah via_judul
  lalu string kunci.
- Query gen: `SEED_K2` tetap, urut-rencana, tanpa shuffle.
- Jalur E3: `eksperimen2.cari` sudah deterministik.
- Penyelamatan: `q'` = top-1 deterministik.
- **Uji:** jalankan `eksperimen_k2.py` 2× di 2 proses terpisah, banding byte
  `hasil5/{hasil.json, query_k2.json, per_query_k2.json}` minus field waktu.
  Plus loop 20× dalam-proses. Identik sampai digit terakhir.

**Cross-check Java (pola `keputusan.md` "C1"):**
1. Docker hidup, pasang `.omod` (salin, jangan ubah distro — aturan 9).
2. Subset tetap ~30 query dari `query_k2.json` (8 entitas × 5 jenis),
   **dibaca**, bukan dibangkitkan ulang.
3. Tiap query: `GET /unifiedsearch/saran?q=<q>&limit=50` sebagai admin (punya
   View Patients → tanpa filter). Ambil daftar `kunci` + urutan.
4. Banding ke `saran_k2(q, limit=50)` Python — daftar **dan** urutan.
5. Harapan: identik. Selisih → selidiki, dokumentasikan seperti C1.
6. Simpan mentah: `hasil5/cross_check_java.json`.

**Sub-cek privilege (aturan 5):**
- Bukti utama: uji unit modul — mock `Context.hasPrivilege("View Patients")`
  = false → hasil `saran()` nol baris `pasien`/`hasillab`/`kondisi`; = true →
  muncul. (Cek apakah `BigramJaccardSuggesterTest` sudah punya; kalau belum,
  tambah.)
- Bukti sekunder bila ada user terbatas: panggil `/saran` 2× (admin vs tanpa
  View Patients) pada ~5 query yang kena dokumen pasien — respons kedua wajib
  kosong dari ketiga entitas itu.

**Snapshot korpus:** `.jsonl` di-commit → sisi Python reproduksi penuh tanpa
Docker. Sisi Java baca DB live — catat jumlah dokumen per entitas dari
`IndexBuilder` vs jumlah baris `.jsonl`, wajib sama. Bila demo DB bergeser,
query cross-check bisa beda — caveat sama seperti `reproduksi.md` untuk `hasil4`.

---

## Bagian 8 — Keluaran & tulisan

**Berkas dibuat:**

| Berkas | Isi |
|---|---|
| `riset/ekspor_hasillab.sql`, `riset/ekspor_kondisi.sql` | ekspor SQL (Bagian 2) |
| `riset/data/hasillab.jsonl`, `riset/data/kondisi.jsonl` | snapshot korpus, di-commit |
| `riset/eksperimen_k2.py` | mengimpor `eksperimen2` sebagai modul — **tidak memanggil `main()`** (aturan 10; pengaman sama seperti `ekspor_gold_dev.py`) |
| `riset/hasil5/` | `hasil.json`, `query_k2.json`, `per_query_k2.json`, `ringkasan.csv`, `cross_check_java.json`, `laporan.md` |
| `tugas/14-eksperimen-k2.md` | protokol eksperimen (implementasi K2 sudah di main via `8ef5799`) |

**Doc diperbarui:**

| Doc | Perubahan |
|---|---|
| `docs/algoritma.md` | bagian baru "K2 — saran ketik Jaccard bigram": norm→bigram, gerbang `|iris|≥2` kecuali cocok-persis, Jaccard `|∩|/|∪|`, kunci urut majemuk. Setara detail bagian B0. |
| `docs/keputusan.md` | entri bertanggal: snapshot korpus (jumlah + SHA-256), 2 tabel hasil, hasil cross-check, bukti determinisme |
| `docs/proposal.html` | subbagian baru di §6: "Kontribusi 2 — saran ketik". 2 tabel + kotak "yang tidak diklaim" |
| `docs/ringkasan-hasil.md` | baris K2 di "Angka apa yang didapat" + perbarui "Apa yang dibangun" |
| `docs/kontrak-data.md` | catatan: `hasillab`/`kondisi` kini diukur di `eksperimen_k2.py` (K2, bukan K1) |
| `CLAUDE.md` | catatan ringkas: K2 punya eksperimen sendiri (`hasil5/`), angka rujukan K2 terpisah; aturan 2/3 tetap tak berlaku untuk jalur K2 |

**Angka K1 (`hasil3/`, `hasil4/`, `proposal.html` §6 tabel utama) — tidak
disentuh sama sekali.**

Penamaan: `hasil5/` (hasil, hasil2, hasil3, hasil4 sudah dipakai).

---

## Bagian 9 — Pagar pengaman

**K2 ≠ K7** (perluasan query otomatis, dilarang, gagal 2×). K2/S1 = Interactive
Query Expansion (Ruthven, SIGIR 2003): sistem menawarkan, pengguna memutus.
- Metrik penyelamatan mensimulasikan klik pengguna, bukan penulisan ulang
  otomatis. Tulisan selalu "setelah pengguna mengklik saran".
- `q'` hanya ada setelah klik tersimulasi. Tak pernah diumpankan balik otomatis
  ke pipeline peringkat. Angka E3 (`hasil3/`) tetap dihitung atas query mentah.
- Tak ada angka diklaim untuk "E3 + ekspansi otomatis" — itu E4, sudah mati.

**Aturan 2/3.** K2 kontribusi terpisah yang lebih kecil. Tulisan tak boleh
menaruhnya setara K4. Klaim K2: "mengubah jalan buntu jadi satu klik" — manfaat
interaksi, eksplisit bukan peningkatan mutu peringkat.

**Aturan 10.** `eksperimen_k2.py` mengimpor, tak pernah jalankan
`eksperimen2.main()`. Query set K2 milik sendiri, bukan split dari `qs`. Tak ada
skrip K2 membaca `qs[100:]`.

**Aturan 6.** Suggester = Jaccard lokal murni, nol panggilan jaringan.

**Komponen baru?** Tidak. K2/S1 sudah dipasang atas persetujuan eksplisit
pemilik repo (`8ef5799`). Eksperimen mengukur komponen yang sudah ada.
