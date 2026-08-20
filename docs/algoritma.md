# Spesifikasi algoritma

Cukup rinci untuk diimplementasikan tanpa menebak. Rujukan implementasi yang
sudah terbukti: `eksperimen2.py` di repo penelitian.

## 0. Normalisasi teks

```
norm(s):
  s = huruf kecil semua
  ganti tiap deretan karakter non-[a-z0-9] menjadi satu spasi
  rapatkan spasi ganda, buang spasi di tepi
```

`"Diabetes mellitus, type 2"` → `"diabetes mellitus type 2"`

## 1. Dua cara memotong

**Token kata** — pisah di spasi.
`"pulm edem"` → `["pulm", "edem"]`

**Kepingan karakter (n=4)** — ganti spasi jadi `_`, lalu geser jendela 4 huruf
satu per satu.
`"pulm edem"` → `"pulm_edem"` → `["pulm","ulm_","lm_e","m_ed","_ede","edem"]`

Kalau teks lebih pendek dari 4 huruf, kembalikan teks itu sendiri sebagai satu
kepingan. Jangan kembalikan senarai kosong — itu membuat entri pendek tidak
pernah bisa ditemukan.

Garis bawah pengganti spasi itu disengaja: ia membuat batas kata ikut jadi
sinyal, sehingga `_ede` (awal kata "edema") berbeda dari `nede` (di tengah kata).

## 2. Pembobotan TF-IDF, skema `ltc`

Untuk tiap surface form:

```
tf_w   = 1 + log(jumlah kemunculan term)      // l — logaritmik
idf_w  = log(N / df) + 1                      // t — N = jumlah surface form
bobot  = tf_w * idf_w
```

Lalu **normalisasi cosine** (c): bagi tiap bobot dengan akar dari jumlah kuadrat
seluruh bobot pada surface form itu.

Vektor query dibangun dengan cara yang sama, memakai `idf` dari indeks yang
sedang dipakai.

Kemiripan = dot product dua vektor yang sudah dinormalisasi (= cosine similarity).

**Basis logaritma tidak berpengaruh** pada peringkat asalkan konsisten. Pakai
natural log seperti implementasi Python supaya angkanya bisa dibandingkan langsung.

## 3. Indeks yang dibangun — ada 13

Per entitas (6 entitas):
- 1 indeks kata
- 1 indeks kepingan

Plus 1 indeks **global** berisi seluruh surface form dari keenam entitas
digabung. Indeks global **hanya** dipakai untuk menghitung bobot tabel di K6.
Jangan memakainya untuk memeringkat hasil.

Total: 6 × 2 + 1 = 13.

## 4. K5 — gabungan skor kata dan kepingan

```
skor(dokumen) = maks atas seluruh surface form dari:
                  ALPHA * cosine_kata + (1 - ALPHA) * cosine_kepingan
```

Ambil maksimum **setelah** menggabung, bukan sebelum. Yaitu: hitung skor
gabungan tiap surface form dulu, baru ambil yang tertinggi.

Buang dokumen dengan skor ≤ 0,07.

## 5. K6 — Weighted RRF

Langkah 1 — hitung skor mentah tiap entitas memakai indeks global:

```
g[e] = skor tertinggi yang dicapai surface form milik entitas e
       pada indeks global, untuk query ini
```

Langkah 2 — ubah jadi bobot:

```
total  = jumlah g[e] atas seluruh entitas
bobot[e] = EPS + (1 - EPS) * g[e] / total        // EPS = 0,05
kalau total == 0: bobot[e] = 1 untuk semua e
```

`EPS` adalah lantai. Tanpa itu, entitas yang skor globalnya 0 akan mendapat
bobot 0 dan hasilnya hilang sama sekali — padahal ia mungkin masih relevan.

Langkah 3 — gabungkan:

```
untuk tiap dokumen d pada entitas e dengan peringkat r (mulai dari 1):
    nilai(d) = bobot[e] * 1 / (K_RRF + r)         // K_RRF = 20
urutkan menurun dengan kunci (-nilai, kunci_unik)
```

**Peringkat `r` dihitung di dalam entitasnya sendiri**, bukan peringkat global.

## 6. B0 — tiruan heuristik OpenMRS (untuk pembanding)

Dua tahap. Tahap 1 menyaring, tahap 2 memberi skor.

```
Tahap 1 — saring:
  untuk tiap surface form f:
    lolos = SETIAP kata pada query adalah awalan (prefix) dari
            sekurang-kurangnya satu kata pada f
    kalau tidak lolos, buang f sepenuhnya

Tahap 2 — skor (hanya untuk yang lolos):
  skor = 100 * (jumlah kata query yang cocok sebagai awalan)
  kalau f adalah judul (bukan alias/kode):  skor += 500
  kalau norm(f) == norm(query):             skor += 1000
```

Ini penting untuk kejujuran penelitian: B0 **tidak jelek**. Ia menang pada query
terpotong (nDCG 0,822 vs 0,353 milik TF-IDF kata murni). Yang runtuh adalah
kasus salah eja di tengah kata (0,081). Implementasikan apa adanya — jangan
diperlemah supaya usulan terlihat lebih baik.

## 7. Mode yang harus didukung endpoint

| mode | jalur kata | jalur kepingan | penggabungan antar tabel |
|---|---|---|---|
| `b0` | — (heuristik awalan) | tidak | skor mentah |
| `b1` | ya | tidak | skor mentah |
| `e1` | ya | ya | skor mentah |
| `e3` | ya | ya | Weighted RRF |

`e3` adalah default.

## 8. Determinisme — sekali lagi

Pengurutan akhir memakai `(-skor, kunci_unik)`. Iterasi himpunan selalu
diurutkan dulu. Di Java: `TreeMap`/`TreeSet`, atau `Comparator` majemuk yang
eksplisit. Jangan mengandalkan urutan `HashMap`.
