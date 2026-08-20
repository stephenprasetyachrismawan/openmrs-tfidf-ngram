# Tugas 10 — Halaman 1: search box diagnosis (mockup A)

## Tujuan
Perubahan terkecil yang memberi hampir seluruh perbaikan. **Kirim ini duluan.**

## Rujukan
`docs/arsitektur-halaman.md`. Mockup hidupnya ada di `docs/proposal.html`
bagian 4.1 — buka di browser untuk melihat target tampilannya.

## Langkah
1. Salin CSS mockup ke `resources/pencarian.css`.
2. Tulis `resources/searchbox.js`:
   - debounce 120 ms + `AbortController`
   - fetch ke endpoint dengan `entitas=konsep`
   - render dropdown, penyorotan, navigasi keyboard (salin dari mockup)
3. Pasang pada widget pencarian konsep di form encounter.

## Selesai kalau
- Mengetik `diabete melitus` memunculkan Diabetes mellitus type 1 & 2.
- Mengetik `pnemonia` memunculkan Pneumonia.
- Panah atas/bawah, Enter, Esc bekerja.
- Huruf yang cocok tersorot.
- Mengetik cepat 10 huruf mengirim ≤ 3 permintaan (bukti debounce bekerja).
- Jawaban yang datang terlambat tidak menimpa hasil yang lebih baru.
- Endpoint mati → muncul pesan galat, halaman tidak membeku.
