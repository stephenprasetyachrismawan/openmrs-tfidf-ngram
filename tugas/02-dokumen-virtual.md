# Tugas 02 — Dokumen virtual (K1) dan surface form (K2)

## Tujuan
Mengubah 6 tabel OpenMRS jadi satu bentuk seragam.

## Rujukan
`docs/kontrak-data.md` — ikuti persis, termasuk nama field.

## Langkah
1. Buat `VirtualDocument` (POJO) sesuai kontrak.
2. Buat antarmuka `DocumentSource` dengan satu metode `List<VirtualDocument> load()`.
3. Implementasikan enam sumber: `ConceptSource`, `DrugSource`, `PatientSource`,
   `FormSource`, `LocationSource`, `ProviderSource`.
   Pakai Service API OpenMRS bila memadai; turun ke query langsung bila perlu
   demi kecepatan — muat massal, jangan satu per satu.
4. Buat `SurfaceFormExtractor`: dari satu dokumen menghasilkan daftar
   surface form (judul + tiap alias + tiap kode), masing-masing menyimpan
   rujukan ke dokumen induknya dan penanda apakah ia judul.

## Selesai kalau
- Total dokumen = 4.748 pada demo data.
- Total surface form = 29.320.
- Konsep Acetaminophen menghasilkan ≥ 129 surface form.
- Uji unit: dokumen tanpa alias dan tanpa kode tetap menghasilkan 1 surface form.
- `konteks` **tidak** ikut jadi surface form.

## Jangan
Jangan menggabungkan alias jadi satu string. Itu membatalkan seluruh gunanya K2.
