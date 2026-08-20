# Tugas 09 — Endpoint REST

## Tujuan
Dua endpoint, plus pembangunan indeks saat startup.

## Langkah
1. `IndexBuilder` yang jalan sekali saat modul start (listener aktivasi modul).
   Bangun 13 indeks di memori. Catat waktunya ke log.
2. `GET /ws/rest/v1/unifiedsearch`
   - parameter: `q` (wajib), `mode` (default `e3`), `limit` (default 10),
     `entitas` (opsional, batasi ke entitas tertentu)
   - bentuk jawaban: lihat `docs/arsitektur-halaman.md`
3. `GET /ws/rest/v1/unifiedsearch/eval`
   - parameter: `mode`
   - menjalankan gold standard bawaan, mengembalikan P@1, P@5, R@10, MRR, MAP,
     nDCG@10, persentase query nol-hasil, dan waktu.
4. **Penyaringan hak akses**: saring hasil entitas `pasien` menurut privilege
   pengguna pemanggil, sebelum dikembalikan.

## Selesai kalau
- Indeks terbangun saat startup, waktunya tercatat di log (< 10 detik).
- `?q=diabete%20melitus&mode=b0` → daftar kosong.
- `?q=diabete%20melitus&mode=e3` → Diabetes mellitus di peringkat atas.
- Pengguna tanpa privilege pasien tidak pernah menerima baris `entitas=pasien`.
- Panggilan yang sama dua kali memberi jawaban byte-identik.
- Latensi query < 50 ms pada demo data.

## Perhatian
Indeks di memori dibangun saat startup dan **tidak** diperbarui saat data
berubah. Itu keterbatasan yang sudah diakui di proposal. Catat di README, jangan
diam-diam berpura-pura tidak ada.
