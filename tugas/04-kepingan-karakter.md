# TITIK RAWAN — baca sebelum menulis kode

Fungsi `kepingan()` harus mencerminkan Python **persis**, termasuk dua kasus
tepi yang mudah terlewat. Kalau meleset, selisihnya kecil dan sangat melelahkan
dilacak karena angkanya tetap "kelihatan masuk akal".

Acuan Python (`riset/eksperimen2.py`):

```python
def keping(s, k=4):
    t = norm(s).replace(" ", "_")
    if not t: return []
    if len(t) < k: return [t]
    return [t[i:i+k] for i in range(len(t)-k+1)]
```

Yang wajib sama:

1. **Spasi diganti `_` SETELAH normalisasi**, bukan sebelum. Urutannya
   menentukan hasil.
2. **Teks lebih pendek dari k dikembalikan utuh sebagai satu kepingan.**
   `"pulm"` dengan k=4 menghasilkan `["pulm"]`; `"tbc"` menghasilkan
   `["tbc"]` — bukan daftar kosong. Query pendek sangat bergantung ini.
3. Teks kosong menghasilkan daftar kosong, bukan `[""]`.

Sertakan uji unit untuk ketiganya, dengan nilai harapan ditulis eksplisit.

Verifikasi silang yang paling meyakinkan: ambil 20 judul acak dari
`riset/data/konsep.jsonl`, hasilkan kepingannya di Java dan di Python,
lalu bandingkan daftarnya harus identik.

---
# Tugas 04 â€” Indeks kepingan karakter (K4)

## Tujuan
Komponen inti penelitian. Ini yang membawa +0,176 nDCG.

## Rujukan
`docs/algoritma.md` bagian 1.

## Langkah
1. `Tokenizer.charGrams(String s, int n)`:
   - normalisasi, ganti spasi jadi `_`
   - kalau panjang < n, kembalikan `[teks]` â€” **bukan** daftar kosong
   - selain itu, jendela geser sepanjang n
2. Bangun indeks kepingan per entitas memakai **kelas `TfIdfIndex` yang sama**
   dari tugas 03. Tidak ada rumus baru â€” hanya unit tokennya yang berbeda.

## Selesai kalau
- `charGrams("pulm edem", 4)` = `["pulm","ulm_","lm_e","m_ed","_ede","edem"]`
- `charGrams("tb", 4)` = `["tb"]`
- Query `pulm edem` menemukan "Pulmonary edema" dengan skor > 0,4.
- Query `diabete melitus` menemukan "Diabetes mellitus, type 2" dengan skor > 0,3.
- Indeks kepingan seluruh korpus terbangun < 5 detik.

## Catatan
Ukuran indeks kepingan 3â€“5Ã— indeks kata. Catat penggunaan memorinya â€” ini risiko
rekayasa nomor satu di proposal, dan angkanya akan ditanya penguji.

