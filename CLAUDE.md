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
signifikan (kepingan karakter, +0,176, p<0,001). Weighted RRF tidak signifikan
(+0,007, p=0,207). Perluasan query memperburuk dan sudah dibuang.

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
| `NGRAM` panjang kepingan | 4 | 2â€“4 setara; 4 dipilih |
| `ALPHA` bobot jalur kata | **belum final** | eksperimen memakai 0,45; sapuan menunjukkan optimum 0,25. Lihat tugas 06. |
| `K_RRF` | 20 | |
| `EPS` lantai bobot tabel | 0,05 | |
| ambang skor minimum | **1e-6** | nilai riset (`eksperimen2.py`). 0,07 yang tertulis sebelumnya KELIRU — itu ambang mockup demo, bukan parameter penelitian. Lihat `docs/keputusan.md`. |

Jangan menetapkan `ALPHA` diam-diam. Tugas 06 mengatur cara memutuskannya.

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
| B0 heuristik OpenMRS | 0,628 | 0,689 | 18,3% |
| B1 TF-IDF kata | 0,646 | 0,572 | 11,1% |
| E1 + kepingan karakter | 0,804 | 0,783 | 0,6% |
| E3 + Weighted RRF | 0,811 | 0,806 | 0,6% |

