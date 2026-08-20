# Eksperimen 2 — Pencarian Terpadu Lintas-Entitas OpenMRS

*Dijalankan 20 Agustus 2026 terhadap MariaDB OpenMRS (stack `openmrs-distro-referenceapplication`, demo data resmi).*
*Skrip: `eksperimen2.py`, `eksperimen2b.py` · hasil mentah: `hasil2/`*

---

## 1. Korpus

| Entitas | Dokumen | Surface form |
|---|---:|---:|
| konsep | 4.249 | |
| obat | 322 | |
| pasien | 100 | |
| lokasi | 61 | |
| form | 10 | |
| provider | 6 | |
| **total** | **4.748** | **29.320** |

Waktu indeks penuh (13 indeks: 6 kata + 6 gram + 1 global): **1,32 detik**.

**Query:** 280 diturunkan otomatis dari nama entitas nyata, didegradasi menjadi lima tipe
(persis, typo, trunkasi, hilang_kata, urut_balik). Split 100 dev / **180 test**.
**Relevansi bertingkat** diturunkan dari foreign key nyata: rel 2 = entitas target;
rel 1 = obat yang menunjuk konsep target (`drug.concept_id`), konsep yang berbagi
reference term non-CIEL, dan obat lain pada konsep yang sama.

**Reproduksibilitas:** tie-break dibuat deterministik menurut id. Dua proses terpisah
(hash seed berbeda) menghasilkan angka identik sampai digit terakhir. Sebelum perbaikan ini
hasilnya bergoyang ±0,02 nDCG antar-run — catat ini sebagai temuan metodologis.

---

## 2. Hasil utama (split test, n = 180)

| Sistem | P@1 | P@5 | R@10 | MRR | MAP | **nDCG@10** | 0-hasil | latensi |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| B0 heuristik OpenMRS | 0,689 | 0,187 | 0,521 | 0,743 | 0,466 | 0,628 | **18,3%** | 0,15 ms |
| B1 TF-IDF kata | 0,572 | 0,267 | 0,666 | 0,691 | 0,523 | 0,646 | 11,1% | 0,14 ms |
| B2 BM25 | 0,533 | 0,250 | 0,658 | 0,651 | 0,485 | 0,615 | 11,1% | 0,21 ms |
| E1 hibrida kata+4gram, gabung skor | 0,783 | **0,326** | **0,785** | 0,877 | **0,672** | 0,804 | 0,6% | 0,74 ms |
| E2 E1 + **RRF baku** | 0,306 | 0,254 | 0,763 | 0,576 | 0,412 | **0,580** | 0,6% | 0,82 ms |
| **E3 E1 + Weighted RRF (usulan)** | **0,806** | 0,311 | 0,781 | **0,888** | 0,663 | **0,811** | 0,6% | 1,28 ms |
| E4 E3 + query expansion (PRF) | 0,456 | 0,291 | 0,761 | 0,596 | 0,492 | 0,589 | 0,6% | 6,88 ms |

### Uji signifikansi (paired bootstrap 5.000×, selisih nDCG@10)

| Perbandingan | selisih | CI95 | p |
|---|---:|---|---:|
| E3 vs B0 | **+0,183** | [+0,136, +0,232] | **0,000** |
| E1 vs B0 | +0,176 | [+0,131, +0,223] | 0,000 |
| E3 vs E2 | **+0,231** | [+0,207, +0,256] | **0,000** |
| E2 vs B0 | −0,048 | [−0,099, +0,005] | 0,069 |
| B1 vs B0 | +0,018 | [−0,037, +0,071] | 0,510 |
| B2 vs B0 | −0,013 | [−0,072, +0,045] | 0,656 |
| **E3 vs E1** | **+0,007** | **[−0,004, +0,019]** | **0,207** |
| E4 vs B0 | −0,039 | [−0,102, +0,026] | 0,228 |

---

## 3. Tiga temuan yang harus mengubah proposal

### 3.1 TERBUKTI KUAT — RRF baku memang degeneratif pada koleksi lepas

Ini hipotesis inti proposal, dan datanya mendukung dengan margin lebar.
Fusi hibrida yang menghasilkan 0,804 **jatuh ke 0,580** begitu digabung dengan RRF baku —
bahkan lebih buruk dari heuristik OpenMRS. Weighted RRF mengembalikannya ke 0,811.

Mekanismenya terlihat dari distribusi entitas pada top-5:

| Sistem | konsep | obat | pasien | lokasi | **form** |
|---|---:|---:|---:|---:|---:|
| E1 gabung skor | 0,482 | 0,282 | 0,185 | 0,032 | 0,015 |
| **E2 RRF baku** | 0,471 | 0,269 | 0,105 | **0,069** | **0,076** |
| E3 Weighted RRF | 0,475 | 0,270 | 0,201 | 0,044 | 0,006 |

Koleksi `form` hanya berisi **10 dari 4.748 dokumen (0,2% korpus)**, tetapi RRF baku
memberinya **7,6% kursi di top-5** — naik lima kali lipat. Persis prediksi teoretisnya:
setiap koleksi mendapat satu kursi berskor `1/(k+1)` di puncak, terlepas dari apakah ia
punya jawaban. P@1 runtuh dari 0,783 ke 0,306 karena itu.

### 3.2 TIDAK TERBUKTI — penggabungan skor mentah ternyata tidak bermasalah

**E3 vs E1 hanya +0,007 dengan p = 0,207.** Tidak signifikan. Bahkan pada P@5 dan MAP,
E1 sedikit unggul.

Artinya premis RM3 di proposal — "skor lintas koleksi tidak sebanding sehingga
penggabungan skor mentah didominasi satu entitas" — **tidak didukung data ini**. Pada
korpus nyata, cosine ternormalisasi ternyata cukup sebanding antar koleksi.

Klaim yang benar karena itu bukan *"Weighted RRF mengalahkan penggabungan skor"*, melainkan:

> **Weighted RRF adalah yang membuat fusi berbasis peringkat menjadi layak sama sekali.**
> Tanpa pembobotan, RRF kehilangan 0,231 nDCG; dengan pembobotan, ia setara penggabungan
> skor sambil mempertahankan sifat bebas-skala yang dibutuhkan saat koleksi baru
> ditambahkan atau saat skor tidak tersedia.

Ini klaim yang lebih sempit, dan datanya benar-benar mendukungnya.

### 3.3 GAGAL LAGI — query expansion, kali ini dengan sebab berbeda

E4 = 0,589, jauh di bawah E3 (0,811). Penyebabnya terlacak: alias obat pada demo data
berisi ratusan nama dagang (konsep *Acetaminophen* punya **129 alias**: Tylenol, Panadol,
Calpol, …). Memanen alias dari top-5 mencemari query alih-alih memperkayanya. Buktinya
ada di rincian per entitas: nDCG E4 untuk `lokasi` **0,228** dan `form` **0,156** —
hancur, karena query yang membengkak menarik obat ke mana-mana.

Ini kegagalan kedua untuk query expansion, dengan sebab berbeda dari eksperimen 1
(dulu pemicunya tidak pernah menyala; kini ekspansinya terlalu bising). Perbaikan yang
masuk akal: batasi ekspansi pada **kode terminologi saja**, bukan alias teks bebas.

---

## 4. Rincian per jenis degradasi (nDCG@10)

| Sistem | persis | typo | trunkasi | hilang_kata | urut_balik |
|---|---:|---:|---:|---:|---:|
| B0 heuristik OpenMRS | 0,770 | **0,081** | 0,822 | 0,709 | 0,788 |
| B1 TF-IDF kata | 0,827 | 0,464 | **0,353** | 0,734 | 0,848 |
| B2 BM25 | 0,816 | 0,449 | **0,312** | 0,718 | 0,774 |
| E2 RRF baku | 0,604 | 0,542 | 0,594 | 0,554 | 0,613 |
| **E3 usulan** | 0,849 | **0,789** | **0,789** | 0,754 | **0,888** |

Pola dari eksperimen 1 **terkonfirmasi ulang pada korpus lintas-entitas**: heuristik
OpenMRS runtuh pada typo (0,081), TF-IDF dan BM25 runtuh pada trunkasi (0,35 / 0,31),
dan hanya representasi hibrida yang menutup keduanya.

## 5. Rincian per entitas target (nDCG@10)

| Sistem | konsep | obat | pasien | lokasi | form | provider |
|---|---:|---:|---:|---:|---:|---:|
| B0 | 0,638 | 0,550 | 0,694 | 0,636 | 0,750 | 0,500 |
| B1 | 0,616 | 0,637 | 0,656 | 0,699 | 0,908 | 0,815 |
| E1 | 0,786 | 0,811 | 0,795 | 0,842 | 1,000 | 0,815 |
| E2 | 0,665 | 0,543 | 0,462 | 0,562 | 1,000 | 0,465 |
| **E3** | **0,796** | **0,816** | 0,787 | **0,876** | **1,000** | **1,000** |

E3 unggul atau setara pada kelima entitas. Perhatikan `pasien`: E1 sedikit lebih baik
(0,795 vs 0,787) — pembobotan koleksi kadang meredam entitas kecil yang benar.

## 6. Sapuan parameter (E3, nDCG@10)

| n-gram | 2 | 3 | 4 | 5 | 6 |
|---|---:|---:|---:|---:|---:|
| nDCG | 0,811 | 0,811 | **0,811** | 0,784 | 0,735 |

| k RRF | 5 | 10 | 20 | 60 |
|---|---:|---:|---:|---:|
| nDCG | 0,813 | 0,813 | 0,811 | 0,808 |

| ε lantai | 0,00 | 0,05 | 0,15 | 0,30 |
|---|---:|---:|---:|---:|
| nDCG | 0,811 | 0,811 | 0,812 | 0,812 |

| α (bobot kata) | 0,00 | 0,25 | 0,45 | 0,65 | 1,00 |
|---|---:|---:|---:|---:|---:|
| nDCG | 0,793 | **0,818** | 0,811 | 0,803 | 0,657 |

**Bacaan:** n = 2/3/4 setara, turun tajam mulai n = 5 → pilihan 4 aman dan dapat
dipertahankan. Sistem hampir tidak peka terhadap k dan ε (rentang 0,808–0,813), yang
bagus: tidak ada parameter rapuh yang perlu disetel hati-hati. α = 1,00 (kata saja)
= 0,657 sementara α = 0,00 (karakter saja) = 0,793 — **jalur karakter menyumbang jauh
lebih besar daripada jalur kata**, dan optimum ada di α ≈ 0,25, bukan 0,45 yang dipakai.

## 7. Keterbatasan yang harus ditulis

1. **B0 adalah reimplementasi**, bukan REST API OpenMRS produksi. Validasi lewat
   `/ws/rest/v1/concept?q=` masih tertunda.
2. **Query dan relevansi diturunkan otomatis.** Relevansi rel 1 memakai foreign key nyata
   (`drug.concept_id`, reference term bersama), tetapi tidak ada anotasi klinisi.
   Relevansi lintas-entitas yang bersifat *klinis* (mis. obat yang mengobati diagnosis
   tanpa berbagi nama) tidak tertangkap — dan memang tidak dapat ditemukan sistem leksikal.
3. **Ketimpangan ukuran koleksi ekstrem** (4.249 vs 6). Perilaku pada instalasi produksi
   dengan puluhan ribu pasien akan berbeda, dan kemungkinan besar membalik sebagian
   temuan tentang bias koleksi kecil.
4. **P@5 rendah secara artifisial** karena mayoritas query hanya punya 1–3 dokumen relevan.
5. α belum disetel ulang pada split dev setelah pindah ke korpus lintas-entitas;
   nilai 0,45 diwarisi dari eksperimen 1 dan ternyata bukan optimum.

## 8. Berkas

| Berkas | Isi |
|---|---|
| `eksperimen2.py` | Pipeline lengkap: pemuatan, 13 indeks, 7 sistem, metrik, bootstrap |
| `eksperimen2b.py` | Sapuan parameter n, k, ε, α + uji E3 vs E1 |
| `ekspor_pasien.sql`, `ekspor_obat.sql`, `ekspor_lain.sql` | Ekspor korpus dari MariaDB |
| `data/*.jsonl` | Korpus terekspor, 4.748 dokumen |
| `hasil2/hasil.json` | Seluruh metrik, uji, rincian |
| `hasil2/sapuan.json` | Hasil sapuan parameter |
| `hasil2/per_query.json` | Top-5 tiap sistem untuk 180 query test (audit) |
| `hasil2/ringkasan.csv` | Tabel metrik |
