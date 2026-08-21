# Tugas 08 — Baseline pencocokan awalan, gaya legacy UI (B0)

> **Catatan penamaan (2026-08-22, lihat `docs/keputusan.md` "E1"/"E2").** Judul
> dan beberapa klaim di berkas ini sudah diperbaiki. B0 **bukan** tiruan setia
> OpenMRS — diuji langsung terhadap endpoint pencarian konsep OpenMRS asli
> (`GET /ws/rest/v1/concept?searchType=fuzzy`) dan B0 kalah signifikan (nDCG@10
> 0,664 vs 0,800 pada 42 query konsep dev, p=0,0108). Baseline yang setia
> terhadap OpenMRS asli adalah **B0′**, diukur terpisah di `riset/hasil4/`.
> Spesifikasi teknis B0 di bawah (dua tahap: saring awalan, lalu skor) tetap
> berlaku apa adanya — cuma klaim "lebih kuat dari OpenMRS asli" yang salah.

## Tujuan
Pembanding yang jujur. Tanpa ini, tidak ada angka yang bisa dibandingkan.

## Rujukan
`docs/algoritma.md` bagian 6.

## Langkah
1. Implementasikan penyaringan awalan dua tahap persis seperti spesifikasi.
2. Daftarkan sebagai mode `b0` di endpoint.

## Selesai kalau
- Query `diabete melitus` mengembalikan **0 hasil**. Kalau ada hasil,
  penyaring awalannya salah — perbaiki, jangan dibiarkan.
- Query `pulm edem` **mengembalikan hasil** (karena "pulm" awalan "pulmonary").
  Kalau kosong, penyaringnya terlalu ketat.
- Query `diabetes mellitus` mengembalikan hasil yang masuk akal di peringkat atas.

## Jangan
Jangan memperlemah B0 supaya usulan terlihat lebih unggul. **Koreksi
2026-08-22:** klaim lama di sini ("tiruan kami sudah lebih kuat dari yang
dilaporkan komunitas") **terbukti salah** setelah diuji langsung terhadap
endpoint OpenMRS asli (`riset/hasil4/`, lihat `docs/keputusan.md` "E1") — B0
justru lebih lemah dari OpenMRS sungguhan pada query typo. Klaim "konservatif"
tidak berlaku lagi apa adanya; baseline yang setia adalah B0′.


---

# TITIK RAWAN — baseline yang lemah membuat seluruh penelitian tidak berguna

B0 adalah pembanding. Setiap klaim penelitian ini berbentuk "metode kami lebih
baik **daripada B0**". Kalau B0 dibuat lebih lemah dari OpenMRS sebenarnya,
seluruh selisih yang dilaporkan jadi palsu — dan itu jenis kesalahan yang paling
mudah dituduhkan penguji, karena paling menguntungkan penulis.

Jadi: **kalau ragu, buat B0 lebih kuat, bukan lebih lemah.**

**Koreksi 2026-08-22 (lihat `docs/keputusan.md` "E1"):** kalimat di atas dulu
menyatakan tiruan B0 "ternyata lebih kuat" dari OpenMRS asli. Itu klaim
spekulatif yang belum diuji saat ditulis, dan sekarang terbukti **salah**: B0
diuji langsung terhadap endpoint fuzzy OpenMRS asli pada 42 query konsep dan
kalah signifikan (nDCG@10 0,664 vs 0,800, p=0,0108). B0 tetap dipertahankan
apa adanya (spesifikasi di bawah tidak berubah) sebagai baseline pencocokan
awalan yang independen — bukan lagi diklaim sebagai tiruan yang lebih kuat
dari aslinya.

## Uji penentu

Query `diabete melitus` harus mengembalikan **0 hasil**. Kalau tidak,
penyaringan awalannya salah — dan justru itu inti temuan penelitian ini
(dokumen yang benar dibuang sebelum diberi skor).

Acuan implementasi: `heuristik_openmrs()` di `riset/eksperimen2.py`. Ikuti
persis, termasuk urutan penyaringan lalu penskoran, dan bobot
+10000 / +1000 / +500 / +100.

## Jangan

- Jangan "memperbaiki" B0 supaya lebih masuk akal. Ia harus meniru yang ada,
  bukan yang seharusnya.
- Jangan menambahkan toleransi salah ketik ke B0. Ketiadaan itulah yang diukur.
