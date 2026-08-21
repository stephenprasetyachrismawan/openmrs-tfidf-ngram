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


---

# TITIK RAWAN — determinisme, justru di sini

Bug non-determinisme yang dulu menghabiskan waktu kelompok ini **terjadi persis
di komponen ini**. Alasannya struktural, bukan kebetulan: RRF menghasilkan
banyak skor yang **persis sama** (tiap tabel otomatis menyumbang satu kursi
teratas berskor identik), sehingga urutan akhir sepenuhnya ditentukan oleh
pemecah seri. Kalau pemecah serinya urutan iterasi himpunan, hasilnya berubah
antar proses.

Wajib:

- Kunci sortir akhir `(-skor, id)`, bukan `-skor` saja.
- Jangan mengiterasi `HashSet`/`HashMap` tanpa mengurutkan. Pakai `TreeMap`/
  `TreeSet`, atau urutkan dulu.
- Acuan Python (`eksperimen2.py`) memakai `for k in sorted(set(a) | set(b))`
  — perhatikan `sorted()`-nya.

Uji yang wajib ada: jalankan pencarian yang sama 20 kali dalam satu proses
**dan** bandingkan dengan hasil proses terpisah. Dua-duanya harus identik.

# Jangan lupakan E2 (RRF polos)

Evaluasi membandingkan tujuh sistem, termasuk **E2 = RRF tanpa pembobotan**.
E2 harus tetap diimplementasikan walaupun hasilnya buruk (0,580, lebih rendah
dari baseline). Itu bukan kegagalan yang disembunyikan — itu temuan penelitian
tentang perilaku degeneratif RRF pada koleksi yang saling lepas, dan salah satu
bagian paling menarik dari laporan ini.

Jangan "memperbaiki" E2 supaya kelihatan lebih baik.

# Nada penulisan

Aturan 3 `CLAUDE.md` berlaku ketat di sini. Weighted RRF **tidak signifikan**
(+0,007, p = 0,207 pada eksperimen lama). Komentar kode, nama variabel, dan
teks apa pun tidak boleh menyiratkan komponen ini meningkatkan kualitas.
Nilainya arsitektural: ia menyatukan enam daftar jadi satu.
