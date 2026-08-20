# Kontrak data — dokumen virtual

Seluruh sistem hanya mengenal satu bentuk dokumen. Enam tabel OpenMRS
diproyeksikan ke bentuk ini (komponen K1). Setelah tahap ini, mesin peringkat
tidak lagi tahu data berasal dari tabel mana.

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
| `entitas` | string | ya | salah satu: `konsep`, `obat`, `pasien`, `form`, `lokasi`, `provider` |
| `id` | integer | ya | primary key di tabel asalnya |
| `judul` | string | ya | nama utama yang ditampilkan |
| `alias` | array string | ya (boleh kosong) | sinonim, nama lain, nama dagang |
| `kode` | array string | ya (boleh kosong) | identifier, kode terminologi, MRN |
| `konteks` | string | ya (boleh kosong) | keterangan tambahan, **tidak diindeks** |
| `tautan_konsep` | integer/null | tidak | untuk obat: `drug.concept_id` |

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
