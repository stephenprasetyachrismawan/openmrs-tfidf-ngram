# PERBAIKAN WAJIB SEBELUM MULAI — K5 menyimpang dari pipeline penelitian

Rincian dan angka pengukurannya di `docs/keputusan.md` bagian
"Dua penyimpangan K5". Ringkasnya:

## 1. Urutan operasi (dampak besar: 17,8% query beda 10-besar)

`FusionSearch` sekarang menggabung per surface form lalu memaksimumkan.
Pipeline penelitian melakukan **kebalikannya**: maksimum per jalur dulu, lalu
digabung.

```python
# riset/eksperimen2.py
def cosine(self, q):   # -> dict pemilik -> skor MAKSIMUM atas surface form-nya
def fusi1(idx, q):
    a, b = idx["W"].cosine(q), idx["G"].cosine(q)   # sudah dimaksimumkan
    s = ALPHA * a.get(k, 0.0) + (1 - ALPHA) * b.get(k, 0.0)
```

Yang harus dilakukan:

- Ubah `FusionSearch` supaya memaksimumkan per jalur lebih dulu.
- **Balik uji `maksimumDiambilSetelahDigabungBukanSebelum`** dan ganti namanya
  jadi `maksimumDiambilPerJalurSebelumDigabung`. Uji lama menegaskan urutan
  yang salah; membiarkannya berarti mengunci kekeliruan.
- Jangan hapus korpus ujinya — korpus itu bagus, hanya arah pernyataannya yang
  dibalik.

Ini **bukan** berarti pendekatan lama buruk secara teori. Alasan lengkap dan
rencana mengujinya sebagai varian di kemudian hari ada di `docs/keputusan.md`.

## 2. Ambang skor 0,07 -> 1e-6 (dampak kecil, tapi tetap diselaraskan)

`FusionSearch.SCORE_THRESHOLD` harus jadi `1e-6`. Nilai 0,07 keliru — itu
ambang mockup demo, bukan parameter penelitian, dan tabel `CLAUDE.md` sudah
dikoreksi. Kesalahan ada pada tabelnya, bukan pada kepatuhan agen.

## Verifikasi wajib setelah perbaikan

Jalankan `python tools/silang_fusi.py` (sudah ada di repo) dan cocokkan skor
Java dengan kolom **A maks->gabung**, ALPHA = 0,45, entitas konsep:

| query | entri | skor acuan |
|---|---|---|
| `panadol` | Acetaminophen | 1,0000 |
| `diabete melitus` | Diabetes mellitus | 0,3055 |
| `diabete melitus` | Diabetes mellitus, type 2 | 0,2500 |
| `pulm edem` | Pulmonary edema | 0,2812 |

Harus cocok sampai 4 desimal. Kalau tidak cocok, **berhenti dan laporkan** —
jangan menyetel ALPHA di atas fondasi yang belum sinkron.

---
# Tugas 06 â€” Menetapkan ALPHA

## Kenapa tugas ini ada
Eksperimen yang dilaporkan memakai ALPHA = 0,45, tetapi sapuan parameter
menunjukkan optimum di sekitar 0,25. Selisihnya kecil (0,811 vs 0,818) tetapi
ketidakcocokan ini sudah tercatat sebagai utang di proposal dan **akan ditanya
penguji**.

## Langkah
1. Jalankan ulang sapuan ALPHA pada **100 query dev** (bukan 180 query uji).
2. Pilih nilai berdasarkan hasil dev itu saja.
3. Tetapkan sebagai konstanta dan sebagai global property OpenMRS
   `unifiedsearch.alpha` supaya bisa disetel tanpa membangun ulang.
4. Catat keputusannya di `docs/keputusan.md`: nilai yang dipilih, hasil sapuannya,
   dan tanggalnya.

## Selesai kalau
- `docs/keputusan.md` berisi tabel sapuan dan satu nilai final.
- Nilai itu dipakai konsisten di kode dan di dokumen.

## Jangan
Jangan memilih ALPHA berdasarkan 180 query uji. Itu membocorkan test set dan
membatalkan klaim statistiknya.

