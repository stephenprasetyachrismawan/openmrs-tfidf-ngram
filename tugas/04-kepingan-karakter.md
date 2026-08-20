# Tugas 04 — Indeks kepingan karakter (K4)

## Tujuan
Komponen inti penelitian. Ini yang membawa +0,176 nDCG.

## Rujukan
`docs/algoritma.md` bagian 1.

## Langkah
1. `Tokenizer.charGrams(String s, int n)`:
   - normalisasi, ganti spasi jadi `_`
   - kalau panjang < n, kembalikan `[teks]` — **bukan** daftar kosong
   - selain itu, jendela geser sepanjang n
2. Bangun indeks kepingan per entitas memakai **kelas `TfIdfIndex` yang sama**
   dari tugas 03. Tidak ada rumus baru — hanya unit tokennya yang berbeda.

## Selesai kalau
- `charGrams("pulm edem", 4)` = `["pulm","ulm_","lm_e","m_ed","_ede","edem"]`
- `charGrams("tb", 4)` = `["tb"]`
- Query `pulm edem` menemukan "Pulmonary edema" dengan skor > 0,4.
- Query `diabete melitus` menemukan "Diabetes mellitus, type 2" dengan skor > 0,3.
- Indeks kepingan seluruh korpus terbangun < 5 detik.

## Catatan
Ukuran indeks kepingan 3–5× indeks kata. Catat penggunaan memorinya — ini risiko
rekayasa nomor satu di proposal, dan angkanya akan ditanya penguji.
