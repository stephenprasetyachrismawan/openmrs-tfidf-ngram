# Tugas 07 — Indeks global dan Weighted RRF (K6)

## Tujuan
Menyatukan enam daftar peringkat jadi satu.

## Rujukan
`docs/algoritma.md` bagian 3 dan 5. Contoh perhitungan lengkap ada di
`docs/proposal.html` bagian "Contoh 3".

## Langkah
1. Bangun indeks global (seluruh surface form keenam entitas). Indeks ke-13.
2. `collectionWeights(query)` → bobot per entitas, dengan lantai EPS = 0,05.
3. `fuse(perEntityResults, weights)` → `bobot[e] * 1/(20 + peringkat)`.
4. Urutkan `(-nilai, entitas+":"+id)`.

## Selesai kalau
- Query `diabete` menghasilkan bobot konsep ≈ 0,38 dan pasien ≈ 0,12
  (toleransi 0,02, pada korpus penuh angkanya akan berbeda dari mockup —
  yang harus cocok adalah **urutan relatifnya**).
- Entitas dengan skor global 0 tetap mendapat bobot 0,05, bukan 0.
- Mode `e1` (tanpa RRF) dan `e3` (dengan RRF) dua-duanya berjalan dan
  menghasilkan urutan yang **berbeda** untuk query `diabete`.
- Uji determinisme: 50 pemanggilan berturut-turut, urutan identik semua.

## Ingat
Komponen ini **tidak signifikan** (+0,007, p=0,207). Ia dipertahankan karena
alasan arsitektural. Jangan menulis komentar kode yang mengklaim sebaliknya.
