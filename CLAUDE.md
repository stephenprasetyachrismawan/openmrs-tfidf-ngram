# Modul OpenMRS â€” Pencarian Terpadu TF-IDF Kataâ€“Karakter

Repo ini dikerjakan bersama agen Claude Code. Berkas ini adalah aturan proyek.
**Baca ini sampai habis sebelum menyentuh kode apa pun.**

## Apa yang sedang dibangun

Modul OpenMRS (`.omod`) yang menambahkan pencarian tahan salah ketik ke dalam OpenMRS:

1. **Pembangun indeks** â€” jalan sekali saat OpenMRS start, membaca 6 tabel, membangun 13 indeks di memori.
2. **Dua endpoint REST** â€” satu untuk mencari, satu untuk menjalankan evaluasi.
3. **Dua halaman** â€” search box diagnosis yang diperbaiki, dan menu baru "Pencarian Terpadu".

Dasar ilmiahnya ada di `docs/proposal.html`. Spesifikasi algoritmanya di `docs/algoritma.md`.
Kontrak datanya di `docs/kontrak-data.md`.

## Aturan keras â€” jangan dilanggar tanpa izin manusia

### 1. Determinisme wajib

Pengurutan akhir **harus** memakai kunci majemuk `(-skor, id)` â€” bukan `-skor` saja.

Alasannya bukan gaya: RRF menghasilkan banyak skor yang persis sama, dan tanpa
tie-break yang stabil hasilnya berubah antar-proses. Ini pernah terjadi dan
menghabiskan waktu berjam-jam untuk dilacak.

Berlaku juga untuk iterasi himpunan: jangan pernah mengiterasi `Set`/`HashSet`
tanpa mengurutkannya dulu. Pakai `TreeSet` atau `sorted()`.

Kriteria uji: jalankan evaluasi dua kali di dua proses berbeda. Angkanya harus
identik sampai digit terakhir. Kalau tidak, ada kebocoran non-determinisme.

### 2. Jangan mengubah angka hasil penelitian

Berkas di `docs/` memuat angka dari eksperimen yang sudah dijalankan. Agen
**tidak boleh** mengubah angka-angka itu supaya cocok dengan hasil implementasi.
Kalau implementasi memberi angka berbeda, itu temuan yang harus dilaporkan ke
manusia â€” bukan angka dokumen yang disesuaikan.

### 3. Jangan melebih-lebihkan komponen mana pun

Penelitian ini menemukan bahwa **hanya satu komponen** yang berpengaruh
signifikan (kepingan karakter, +0,174, p&lt;0,001). Weighted RRF (E3 vs E1)
+0,013 nDCG di test, p=0,039 — signifikan secara nominal, efek kecil, tanpa
koreksi multi-perbandingan; jangan dilebih-lebihkan sebagai peningkatan kualitas
setara kepingan karakter. Perluasan query memperburuk dan sudah dibuang.

Komentar kode, nama variabel, pesan commit, dan teks antarmuka harus
mencerminkan itu. Jangan menulis "meningkatkan akurasi" untuk komponen yang
tidak terbukti meningkatkan akurasi.

### 4. Tiap komponen harus bisa dimatikan

K3, K4, K5, K6 masing-masing harus punya sakelar (konfigurasi atau parameter
query). Ini bukan fitur pengguna â€” ini syarat supaya kontribusi tiap komponen
tetap bisa diukur ulang.

Endpoint pencarian menerima parameter `mode` dengan nilai: `b0`, `b1`, `e1`, `e3`.
Default `e3`.

### 5. Hak akses pasien

Endpoint yang mengembalikan data pasien **harus** memeriksa privilege OpenMRS
milik pengguna yang memanggil. Indeks dibangun dari seluruh data, tetapi hasil
disaring per pengguna sebelum dikembalikan. Jangan pernah mengembalikan baris
pasien tanpa pemeriksaan ini, bahkan di lingkungan uji.

### 6. Tanpa layanan luar

Tidak ada panggilan jaringan keluar. Tidak ada embedding, tidak ada LLM, tidak
ada API pihak ketiga. Seluruh premis penelitian ini adalah "cukup ringan untuk
dipasang di puskesmas tanpa GPU dan tanpa internet".

## Parameter â€” nilai resmi

| Parameter | Nilai | Catatan |
|---|---|---|
| `NGRAM` panjang kepingan | 4 | dikunci tugas 06b dari sapuan 100 query dev. NGRAM=3 tampak unggul (+0,0091) tapi bootstrap borderline (CI95 [0,0007; 0,0209], p=0,0686 pada n=100) — dipertahankan 4 demi kesinambungan, bukan kebetulan cocok proposal. Lihat `docs/keputusan.md`. |
| `ALPHA` bobot jalur kata | **0,20** | ditetapkan tugas 06 dari sapuan 100 query dev (bukan 180 query uji); diulang tugas 06b pada kombinasi NGRAM/K_RRF/EPS final — hasil identik. Global property `unifiedsearch.alpha`. |
| `K_RRF` | 20 | dikunci tugas 06b dari sapuan 100 query dev; seluruh titik (5/10/20/60) beda ≤0,0029 nDCG, di dalam derau — dipertahankan demi kesinambungan. |
| `EPS` lantai bobot tabel | 0,05 | dikunci tugas 06b dari sapuan 100 query dev; seluruh titik (0/0,05/0,15/0,30) beda ≤0,0018 nDCG, di dalam derau — dipertahankan demi kesinambungan. |
| ambang skor minimum | **1e-6** | nilai riset (`eksperimen2.py`). 0,07 yang tertulis sebelumnya KELIRU — itu ambang mockup demo, bukan parameter penelitian. Lihat `docs/keputusan.md`. |

Jangan menetapkan parameter apa pun diam-diam — seluruh empat di atas kini
bersumber sapuan 100 query dev (tugas 06 dan 06b), tidak ada lagi yang
berasal dari test set atau disalin mentah dari proposal.

## Alur kerja

Kerjakan satu berkas tugas dalam satu waktu, berurutan. Tiap berkas di `tugas/`
punya bagian **"Selesai kalau"** yang berisi kriteria yang bisa diuji.

Jangan menyatakan tugas selesai sebelum kriteria itu benar-benar dijalankan dan
lulus. Kalau tersendat, berhenti dan laporkan â€” jangan mengarang jalan pintas.

Perintah yang dipakai anggota kelompok:

```
kerjakan tugas/03-indeks-tfidf.md
```

## Yang tidak boleh dikerjakan tanpa bertanya dulu

- Mengganti algoritma peringkat dengan pustaka lain (Lucene, Elasticsearch, dsb).
  Seluruh penelitian ini soal implementasi eksplisit; menggantinya membatalkan skripsinya.
- Menambah komponen baru yang tidak ada di proposal.
- Mengubah struktur `docs/kontrak-data.md`.
- Menghidupkan kembali perluasan query (K7). Sudah diuji dua kali, gagal dua kali.

## Bahasa

Kode, nama variabel, dan komentar: bahasa Inggris (konvensi OpenMRS).
Dokumen, teks antarmuka, dan pesan ke pengguna: bahasa Indonesia.

## Lingkungan â€” baca `docs/lingkungan.md` sebelum menjalankan apa pun

Ringkasan yang paling sering menjebak:

### 7. Selalu `http://127.0.0.1`, jangan pernah `localhost`

Ada Apache2 di WSL yang menguasai port 80 pada `::1`. `localhost` mendarat di
sana dan mengembalikan 404 untuk seluruh path `/openmrs/...`. Ini sudah
diverifikasi:

```
http://localhost/openmrs/ws/rest/v1/session   -> 404  (Apache2 WSL)
http://127.0.0.1/openmrs/ws/rest/v1/session   -> 200  (OpenMRS)
```

Kalau sebuah permintaan mengembalikan 404 dan Anda melihat "Apache2 Ubuntu
Default Page", masalahnya bukan pada modul â€” Anda menatap server yang salah.

### 8. Hanya ada satu stack Docker

`openmrs-distro-referenceapplication` (port 80). Stack kedua bernama
`openmrs-fresh` sudah dihapus. **Jangan menjalankan `docker compose up` dengan
nama project lain**, karena itu akan membuat stack duplikat lagi dan memakan
port serta RAM. Kalau perlu menghidupkan ulang:

```powershell
cd C:\src\tfidf-openmrs\openmrs-distro-referenceapplication
docker compose up -d
```

Tanpa `-p`. Nama project diambil dari nama folder, dan itu memang yang benar.

### 9. Jangan mengubah isi `openmrs-distro-referenceapplication/`

Folder itu klon hulu dengan `.git` sendiri dan sudah masuk `.gitignore`. Modul
kita dibangun di `backend/openmrs-module-tfidf-search/`, terpisah. Modul
dipasang ke OpenMRS dengan menyalin `.omod`-nya, bukan dengan mengubah distro.

## Peta folder

```
CLAUDE.md            berkas ini
README.md            cara memakai repo
docs/
  lingkungan.md      fakta Docker, port, kredensial, jebakan localhost
  proposal.html      proposal + mockup hidup + contoh perhitungan
  kontrak-data.md    bentuk dokumen virtual
  algoritma.md       spesifikasi K1-K6 dan baseline B0
  arsitektur-halaman.md  cara memindahkan mockup jadi halaman modul
tugas/               00..12, dikerjakan berurutan
riset/               eksperimen Python, SQL ekspor, data, hasil
backend/openmrs-module-tfidf-search/   <- modul dibangun di sini
openmrs-distro-referenceapplication/   stack Docker (klon hulu, jangan diubah)
tools/               Maven portabel
```

## Angka rujukan untuk verifikasi silang

Hasil `riset/eksperimen2.py` pada korpus yang sama. Implementasi Java harus
mendekati angka ini. Kalau meleset jauh, laporkan â€” jangan sesuaikan dokumen.

| Sistem | nDCG@10 | P@1 | 0-hasil |
|---|---|---|---|
| B0 baseline pencocokan awalan (gaya legacy UI) | 0,628 | 0,689 | 18,3% |
| B1 TF-IDF kata | 0,646 | 0,572 | 11,1% |
| E1 + kepingan karakter | 0,802 | 0,772 | 0,6% |
| E3 + Weighted RRF | 0,815 | 0,806 | 0,6% |

**Catatan (2026-08-22):** B0 di atas bukan tiruan setia OpenMRS —
dibandingkan langsung terhadap endpoint pencarian konsep OpenMRS asli
(`GET /ws/rest/v1/concept?searchType=fuzzy`), B0 kalah signifikan
(nDCG@10 0,664 vs 0,800 pada 42 query konsep dev, p=0,0108). Baseline
yang setia terhadap OpenMRS asli adalah **B0′**, lihat `docs/keputusan.md`
("E1") dan `riset/hasil4/`. Angka B0 di tabel ini (dari 180 query uji
resmi) tidak diubah — cuma namanya, sesuai aturan 2 di atas.

**K2 (saran ketik "Maksud Anda")** punya eksperimen sendiri sejak
2026-09-01 — `riset/eksperimen_k2.py` → `riset/hasil5/`, korpus 8 entitas
(termasuk `hasillab` + `kondisi`), terpisah dari tabel di atas. Metrik
interaksi (hit@k saran, penyelamatan query buntu), **bukan nDCG**. Aturan 2/3
tak berlaku untuk jalur K2 (bukan bagian pipeline K1–K6); klaim K2 bukan
peningkatan mutu peringkat dan bukan setara K4.


### 10. Test set haram disentuh

Query dibagi `dev, test = qs[:100], qs[100:]`. Bagian `test` **hanya boleh
dijalankan satu kali**, oleh `tugas/08b-evaluasi-test-sekali.md`, setelah
seluruh parameter dikunci.

Setiap skrip lain yang membaca `qs[100:]` adalah bug, apa pun alasannya —
termasuk "cuma mengecek", "cuma sapuan kecil", atau "biar yakin". Kalau butuh
mengukur sesuatu, pakai `qs[:100]`.

Ini pernah dilanggar: `riset/eksperimen2b.py` menjalankan keempat sapuannya di
`test`, sehingga seluruh tabel sapuan parameter di proposal tercemar. Berkas itu
sudah dihapus dan seluruh sapuan dijalankan ulang pada `qs[:100]`. Lihat
`docs/keputusan.md`.

Kalau test set terlanjur dijalankan berulang sambil menyetel, ia berhenti jadi
ukuran independen, dan satu-satunya perbaikan adalah membangkitkan query baru.
