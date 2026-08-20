# Tugas 12 — Panel evaluasi

## Tujuan
Yang membuat penelitian ini bisa diperiksa orang lain dengan satu tombol.

## Langkah
1. Paketkan gold standard sebagai sumber daya JSON di dalam modul, supaya semua
   orang memakai data uji yang sama.
2. Implementasikan metrik: P@1, P@5, R@10, MRR, MAP, nDCG@10, % query nol-hasil.
   Definisi dan contoh perhitungan bertangan ada di `docs/proposal.html` bagian 5.5 —
   pakai contoh itu sebagai kasus uji.
3. Implementasikan paired bootstrap 5.000 iterasi dengan **seed tetap**.
4. Panel di halaman: tombol "Jalankan seluruh data uji", tabel hasil per mode,
   dan kolom perbandingan terhadap B0.

## Selesai kalau
- Contoh dari proposal (`[2,1,0,1,0]`, 3 relevan) memberi persis:
  P@1 = 1,00 · P@5 = 0,60 · R@10 = 1,00 · MRR = 1,000 · MAP = 0,917 · nDCG = 0,978
  Ini uji unit wajib.
- Menjalankan panel dua kali memberi angka identik sampai digit terakhir.
- Bootstrap dengan seed sama memberi p-value identik.
- Hasil mode `e1` dan `e3` berselisih kecil dan **dilaporkan sebagai tidak
  signifikan** kalau memang begitu — jangan dibulatkan jadi cerita sukses.

## Terakhir
Bandingkan angka modul dengan angka `eksperimen2.py`. Kalau berbeda jauh,
**laporkan ke manusia**. Jangan menyesuaikan angka dokumen. Lihat CLAUDE.md aturan 2.
