# PERBAIKAN WAJIB SEBELUM MULAI

Sebelum menyentuh isi tugas 03, perbaiki bug locale yang dijelaskan di
`docs/keputusan.md` bagian "toLowerCase() tanpa Locale":

1. Ganti setiap `toLowerCase()` / `toUpperCase()` di seluruh basis kode
   dengan varian ber-`Locale.ROOT`.
2. Tambahkan uji unit: normalisasi `"Insulin glargine"` di bawah
   `Locale.forLanguageTag("tr")` harus tetap menghasilkan `insulin glargine`.
3. Jalankan ulang verifikasi tugas 02 (4.748 dokumen / 29.320 surface form)
   untuk memastikan angkanya tidak berubah.

Laporkan hasil ketiganya sebelum lanjut.

---
# Tugas 03 â€” Indeks TF-IDF kata (K3)

## Tujuan
Indeks terbalik dengan pembobotan `ltc` dan pencarian cosine.

## Rujukan
`docs/algoritma.md` bagian 0, 1, 2.

## Langkah
1. `TextNormalizer.norm(String)` sesuai spesifikasi.
2. `Tokenizer.words(String)`.
3. Kelas `TfIdfIndex`:
   - `build(List<String> surfaceForms)` â€” hitung df, idf, vektor ternormalisasi.
   - `search(String query)` â€” kembalikan skor cosine per surface form.
4. Bangun satu indeks kata per entitas (6 indeks).

## Selesai kalau
- Query yang identik dengan sebuah surface form memberi skor â‰ˆ 1,0 (toleransi 1e-6).
- Query berisi kata yang tidak ada di kosakata memberi skor 0, tidak melempar galat.
- Uji unit pada korpus kecil buatan (3â€“5 dokumen) dengan angka yang dihitung tangan.
- Dua kali pemanggilan `search` dengan query sama memberi urutan **identik**.

## Perhatian
Simpan idf dalam struktur berurutan (`TreeMap`) supaya iterasi deterministik.

