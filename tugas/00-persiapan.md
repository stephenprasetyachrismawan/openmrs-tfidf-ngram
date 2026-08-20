# Tugas 00 — Persiapan lingkungan

## Tujuan
Memastikan OpenMRS bisa dijalankan dan dibangun ulang di mesin pengembang.

## Langkah
1. Pastikan Docker berjalan, dan instans OpenMRS Reference Application dengan
   demo data sudah hidup.
2. Verifikasi demo data benar-benar termuat:
   ```sql
   SELECT property, property_value FROM global_property
   WHERE property = 'referencedemodata.started';
   ```
3. Pasang JDK 8 (atau versi yang dipakai OpenMRS platform target) dan Maven.
4. Pasang OpenMRS SDK: `mvn openmrs-sdk:setup`.
5. Catat versi OpenMRS platform di `docs/lingkungan.md` — versi ini menentukan
   API mana yang tersedia.

## Selesai kalau
- `mvn -v` dan `java -version` berjalan.
- Query global_property di atas mengembalikan satu baris.
- Jumlah baris tiap tabel cocok dengan tabel di `docs/kontrak-data.md`.
  Kalau meleset, **berhenti** — jangan lanjut ke tugas 01.
