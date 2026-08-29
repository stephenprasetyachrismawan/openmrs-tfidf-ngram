# Kontrak data — dokumen virtual

Seluruh sistem hanya mengenal satu bentuk dokumen. Enam tabel OpenMRS
diproyeksikan ke bentuk ini (komponen K1) — ini korpus penelitian asli, sumber
seluruh angka B0/B1/E1/E3 di `docs/`. Dua entitas tambahan, `hasillab` dan
`kondisi`, ditambahkan belakangan atas permintaan eksplisit pemilik repo (di
luar penelitian Python, tanpa pembanding baseline) — lihat dua bagian terpisah
di bawah, "Entitas ketujuh: hasillab" dan "Entitas kedelapan: kondisi".
Setelah tahap proyeksi ini, mesin peringkat tidak lagi tahu data berasal dari
tabel mana.

## Bentuk

```json
{
  "entitas": "konsep",
  "id": 5497,
  "judul": "Diabetes mellitus, type 2",
  "alias": ["Type 2 diabetes", "T2DM", "NIDDM"],
  "kode":  ["E11", "44054006"],
  "konteks": "Diagnosis",
  "tautan_konsep": null
}
```

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `entitas` | string | ya | salah satu: `konsep`, `obat`, `pasien`, `form`, `lokasi`, `provider`, `hasillab`, `kondisi` |
| `id` | integer | ya | primary key di tabel asalnya (untuk hasillab: `obs.obs_id`; untuk kondisi: `conditions.condition_id`) |
| `judul` | string | ya | nama utama yang ditampilkan |
| `alias` | array string | ya (boleh kosong) | sinonim, nama lain, nama dagang |
| `kode` | array string | ya (boleh kosong) | identifier, kode terminologi, MRN |
| `konteks` | string | ya (boleh kosong) | keterangan tambahan, **tidak diindeks** |
| `tautan_konsep` | integer/null | tidak | untuk obat: `drug.concept_id` |
| `tautan_pasien` | integer/null | tidak | untuk hasillab: `obs.person_id`; untuk kondisi: `conditions.patient_id`. Dipakai membangun tautan ke chart pasien |

## Aturan penting

**`konteks` tidak diindeks.** Ia hanya untuk ditampilkan. Memasukkannya ke
indeks akan mencemari IDF dengan kata-kata umum seperti "tablet" dan "Diagnosis".

**Kunci unik lintas tabel** adalah `entitas + ":" + id`, misalnya `konsep:5497`.
Jangan pernah memakai `id` saja sebagai kunci — `id` bertabrakan antar tabel.
Kunci inilah yang dipakai sebagai tie-break pengurutan (lihat CLAUDE.md aturan 1).

## Yang diindeks: surface form (K2)

Tiap dokumen dipecah jadi beberapa unit pencarian terpisah:

```
judul                 → 1 surface form
tiap elemen alias     → 1 surface form masing-masing
tiap elemen kode      → 1 surface form masing-masing
```

Contoh di atas menghasilkan 6 surface form: `"Diabetes mellitus, type 2"`,
`"Type 2 diabetes"`, `"T2DM"`, `"NIDDM"`, `"E11"`, `"44054006"`.

Skor dokumen = **skor tertinggi** di antara seluruh surface form-nya, bukan
rata-rata dan bukan jumlah.

Alasannya: konsep *Acetaminophen* punya 129 alias. Kalau semua alias dilebur
jadi satu teks panjang, normalisasi panjang cosine akan mengencerkan bobot tiap
katanya sampai konsep itu praktis tidak bisa ditemukan.

## Pemetaan dari tabel OpenMRS

| entitas | tabel sumber | judul | alias | kode |
|---|---|---|---|---|
| `konsep` | `concept` + `concept_name` | nama utama (locale preferred) | sinonim dari `concept_name` | dari `concept_reference_map` |
| `obat` | `drug` | `drug.name` | sinonim konsep yang ditunjuk | kode terminologi konsep itu |
| `pasien` | `person_name` + `patient_identifier` | nama lengkap | nama alternatif | identifier |
| `form` | `form` | `form.name` | — | — |
| `lokasi` | `location` | `location.name` | — | — |
| `provider` | `provider` + `person_name` | nama | — | `identifier` |

Berkas SQL rujukan ada di repo penelitian: `ekspor_konsep.sql`, `ekspor_obat.sql`,
`ekspor_pasien.sql`, `ekspor_lain.sql`. Modul Java harus menghasilkan bentuk yang
**sama persis**, supaya hasil modul bisa dibandingkan langsung dengan hasil
eksperimen Python.

## Jumlah yang diharapkan pada demo data

| entitas | jumlah |
|---|---|
| konsep | 4.249 |
| obat | 322 |
| pasien | 100 |
| lokasi | 61 |
| form | 10 |
| provider | 6 |
| **total dokumen** | **4.748** |
| **total surface form** | **29.320** |

Kalau angka Anda meleset jauh dari ini pada demo data resmi, ada yang salah di
pemetaan — berhenti dan periksa, jangan lanjut ke tahap indeks.

**Tabel di atas cuma untuk enam entitas asli** (korpus penelitian Python).
Menambahkan `hasillab` dan `kondisi` sengaja tidak mengubah angka-angka ini —
itu tetap angka penelitian yang terkunci (CLAUDE.md aturan 2). Jumlah dokumen
sesungguhnya di indeks yang jalan sekarang lebih besar dari 4.748 setelah
`hasillab` dan `kondisi` ikut dimuat; itu wajar, bukan penyimpangan yang perlu
diperbaiki.

## Entitas ketujuh: hasillab

Ditambahkan di luar urutan tugas 00-13, atas permintaan eksplisit pemilik
repo, setelah proyek awalnya selesai. **Tidak ada padanan di
`riset/eksperimen2.py`**, jadi tidak ada angka B0/B1/E1/E3 untuk entitas ini
— klaim signifikansi K4 (+0,174 nDCG) dan seluruh angka lain di `docs/`
tetap hanya berlaku untuk enam entitas asli.

| entitas | tabel sumber | judul | alias | kode | tautan_pasien |
|---|---|---|---|---|---|
| `hasillab` | `obs` (kelas konsep `Test`/`LabSet`) + `concept_name` + `person_name` | nama tes (nama konsep) | nama lengkap pasien | — | `obs.person_id` |

Kenapa alias-nya nama pasien, bukan sinonim tes: `konteks` tidak diindeks
(lihat "Aturan penting" di atas), jadi kalau nama pasien cuma taruh di
`konteks`, mencari nama pasien tidak akan pernah menemukan hasil labnya.
Menaruhnya di `alias` membuatnya jadi surface form sendiri (K2) — dokumen
yang sama bisa ditemukan lewat nama tes ATAU nama pasien, skor diambil yang
tertinggi (K5), persis mekanisme yang sudah ada untuk entitas lain.

`konteks` berisi ringkasan tampilan saja (nama pasien, nilai, tanggal),
tidak diindeks — konsisten dengan field ini di entitas lain.

Cakupan sengaja dibatasi ke kelas konsep `Test` dan `LabSet` (diverifikasi
langsung ke data demo yang jalan, bukan ditebak) — itu yang berisi nilai
numerik/terkode sungguhan seperti "Serum potassium: 3,9". Kelas `Finding`
(observasi klinis umum, mis. tanda vital) dan `Question` (jawaban form)
sengaja tidak diikutkan; itu bukan "hasil laboratorium".

## Entitas kedelapan: kondisi

Ditambahkan setelah `hasillab`, juga di luar urutan tugas 00-13 dan atas
permintaan eksplisit pemilik repo. **Tidak ada padanan di
`riset/eksperimen2.py`**, jadi tidak ada angka B0/B1/E1/E3 untuk entitas ini
— klaim signifikansi K4 (+0,174 nDCG) dan seluruh angka lain di `docs/`
tetap hanya berlaku untuk enam entitas asli.

Sumbernya tabel `conditions` — daftar masalah pasien, data yang sama yang
ditampilkan widget "Conditions" di chart O3. (Bukan `encounter_diagnosis`;
kedua tabel sama-sama berisi 1.279 baris pada demo data, tetapi yang diminta
adalah domain Conditions.)

| entitas | tabel sumber | judul | alias | kode | tautan_pasien |
|---|---|---|---|---|---|
| `kondisi` | `conditions` + `concept_name` + `person_name` | nama konsep `condition_coded` (fallback: `condition_non_coded` bila tak berkode) | nama lengkap pasien | — | `conditions.patient_id` |

Alasan alias-nya nama pasien sama persis dengan `hasillab`: `konteks` tidak
diindeks, jadi nama pasien harus jadi surface form sendiri (K2) supaya
mencari nama pasien menemukan daftar kondisinya. `clinical_status` dan
`onset_date` masuk `konteks` saja — mengindeks kata "ACTIVE" di 1.200-an
baris akan mencemari IDF persis seperti kata "tablet".

Catatan data demo: kolom `condition_non_coded` di demo data terisi teks
placeholder "Some non-coded condition" bahkan pada baris yang punya
`condition_coded`. Karena itu kolom tersebut hanya dibaca ketika
`condition_coded` bernilai NULL.

Hak akses: baris `kondisi` membawa nama pasien dan `tautan_pasien`, jadi
CLAUDE.md aturan 5 berlaku penuh — disaring lewat privilege "View Patients"
di `UnifiedSearchService`, sama seperti `pasien` dan `hasillab`.
