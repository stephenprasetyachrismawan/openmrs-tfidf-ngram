# Tugas 03 — Indeks TF-IDF kata (K3)

## Tujuan
Indeks terbalik dengan pembobotan `ltc` dan pencarian cosine.

## Rujukan
`docs/algoritma.md` bagian 0, 1, 2.

## Langkah
1. `TextNormalizer.norm(String)` sesuai spesifikasi.
2. `Tokenizer.words(String)`.
3. Kelas `TfIdfIndex`:
   - `build(List<String> surfaceForms)` — hitung df, idf, vektor ternormalisasi.
   - `search(String query)` — kembalikan skor cosine per surface form.
4. Bangun satu indeks kata per entitas (6 indeks).

## Selesai kalau
- Query yang identik dengan sebuah surface form memberi skor ≈ 1,0 (toleransi 1e-6).
- Query berisi kata yang tidak ada di kosakata memberi skor 0, tidak melempar galat.
- Uji unit pada korpus kecil buatan (3–5 dokumen) dengan angka yang dihitung tangan.
- Dua kali pemanggilan `search` dengan query sama memberi urutan **identik**.

## Perhatian
Simpan idf dalam struktur berurutan (`TreeMap`) supaya iterasi deterministik.
