# `article/` — naskah IEEE, susunan IMRAD

Naskah laporan penelitian untuk modul pencarian terpadu OpenMRS.
Format IEEE Transactions (kelas `ieeecolor.cls` dari template JBHI),
susunan bab mengikuti IMRAD.

## Isi folder

```
main.tex          naskah utama (bibliografi manual di dalamnya)
lampiran.tex      halaman lampiran, di-input dari main.tex paling bawah
referensi.bib     daftar pustaka versi BibTeX (pelengkap; main.tex tidak memakainya)
ieeecolor.cls     kelas dokumen IEEE Transactions (dari template)
generic.sty       paket pendamping (dari template)
logo.eps          logo bilah judul (dari template)
gambar/
  buat_gambar.py  membangun kelima gambar langsung dari riset/hasil*/*.json
  gambar1..5.pdf  keluaran skrip di atas
main.pdf          hasil kompilasi (18 halaman)
```

## Susunan bab (IMRAD)

| Bagian | Isi |
|---|---|
| Judul | judul, empat penulis, catatan kaki NIM dan afiliasi |
| Abstrak | abstrak (bahasa Indonesia) + Kata Kunci |
| I. Introduction | latar belakang → celah → **rumusan masalah (RQ1–RQ4)** → tujuan → kontribusi → sistematika |
| II. Methods | desain, korpus, algoritma (Pers. 1–8), sistem pembanding, kueri & standar emas, metrik, uji statistik, implementasi |
| III. Results | korpus & indeks, tabel utama, ablasi, per jenis kesalahan, per entitas, sapuan parameter, B0′, jalur saran ketik, verifikasi silang |
| IV. Discussion | jawaban RQ1–RQ4, mekanisme, pelajaran metodologis, perbandingan pustaka, keterbatasan, pekerjaan lanjutan, simpulan |
| Daftar pustaka | 39 rujukan |
| Lampiran A-F | repositori, tautan Drive, peta repo, langkah menjalankan, API REST, pemetaan berkas hasil |

## Cara membangun

Gambar (opsional — berkas PDF-nya sudah ada di repo):

```bash
python gambar/buat_gambar.py
```

Skrip itu membaca `riset/hasil6/sapuan_dev.json`, `riset/hasil6/hasil.json`,
`riset/hasil4/hasil.json`, dan `riset/hasil5/hasil.json`. **Tidak ada angka
yang diketik ulang di dalamnya** — kalau eksperimen dijalankan ulang dan
angkanya berubah, gambarnya ikut berubah.

Naskah (dua kali jalan supaya rujukan silang tersusun):

```bash
pdflatex main.tex && pdflatex main.tex
```

Bibliografi ditulis manual sebagai `thebibliography` di dalam `main.tex`,
jadi tidak perlu menjalankan BibTeX.

## Asal angka

Seluruh angka pada naskah bersumber dari berkas hasil di repo ini:

| Bagian naskah | Sumber |
|---|---|
| Tabel II, III, IV, V; Gambar 1, 2 | `riset/hasil6/hasil.json` (260 kueri uji, korpus 8 entitas, sekali jalan) |
| Gambar 3 (sapuan parameter) | `riset/hasil6/sapuan_dev.json` (100 kueri dev) |
| Tabel VI; Gambar 4 | `riset/hasil4/hasil.json` (34 kueri konsep, baseline B0′) |
| Tabel VII; Gambar 5 | `riset/hasil5/hasil.json` (214 kueri, jalur saran ketik) |
| Tabel VIII, latensi, determinisme | `docs/keputusan.md` bagian C1–C4, F1–F2 |

Aturan 2 `CLAUDE.md` berlaku: kalau eksperimen dijalankan ulang dan hasilnya
berbeda, yang dikoreksi adalah naskah — bukan berkas hasil.

## Verifikasi daftar pustaka

Ke-39 rujukan diperiksa satu per satu terhadap Crossref
(`api.crossref.org/works/<doi>`) atau PubMed pada 2026-09-02. Judul, jurnal,
volume, nomor, halaman, tahun, dan penulis disalin dari rekaman penerbit.
Dua rujukan tanpa DOI (prosiding AMIA) dicantumkan dengan PMID-nya.

## Penulis

| Nama | NIM |
|---|---|
| Stephen Prasetya Chrismawan | 25/563032/PPA/07093 |
| M. Syarif Hidayatullah | 25/567018/PPA/07143 |
| Sampurno Aji | 25/568826/PPA/07155 |
| Reza Purwantara Firdaus | 25/565168/PPA/07125 |

## Bahasa

Naskah sepenuhnya bahasa Indonesia. Label kelas `ieeecolor.cls` yang semula
`Abstract` dan `Index Terms` di-*override* di preamble `main.tex` menjadi
`Abstrak` dan `Kata Kunci` (makro `\abslabel` dan `\keylabel`) — kelasnya
sendiri tidak diubah. Judul bab tetap `Introduction / Methods / Results /
Discussion` sesuai kerangka IMRAD yang diminta.

## Mengisi tautan Google Drive

Dua tautan pada Lampiran B masih kosong. Isi di **satu tempat saja** —
blok `TAUTAN` di kepala [`lampiran.tex`](lampiran.tex):

```latex
\newcommand{\tautanPPT}{https://drive.google.com/...}    % kumpulan PPT progress
\newcommand{\tautanDemo}{https://drive.google.com/...}   % demo aplikasi
```

Lalu kompilasi ulang. Tabel di Lampiran B membaca makro itu, tidak perlu
disentuh.

## Cakupan korpus

Seluruh angka naskah berasal dari korpus **8 entitas** (8.045 dokumen, 35.914
surface form, 17 indeks) — sama persis dengan `DocumentRepository.ENTITAS` pada
modul Java yang dipasang. Versi 6-entitas yang angkanya lebih tinggi diarsipkan
sudah dihapus; alasan pergantian dan seluruh dampaknya tercatat di
`docs/keputusan.md` entri "F2".
