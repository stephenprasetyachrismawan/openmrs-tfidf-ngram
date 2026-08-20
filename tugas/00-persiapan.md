# Tugas 00 — Persiapan lingkungan

> **Status: SELESAI (20 Agustus 2026).** Disimpan sebagai rujukan.
> Dua langkah dikoreksi setelah dikerjakan — lihat catatan di bawah.

## Tujuan
Memastikan OpenMRS bisa dijalankan dan modul bisa dibangun.

## Langkah
1. Pastikan Docker berjalan dan stack `openmrs-distro-referenceapplication` hidup.
   Rinciannya di `docs/lingkungan.md`.
2. Verifikasi demo data termuat:
   ```powershell
   docker exec openmrs-distro-referenceapplication-db-1 `
     mysql -uroot -popenmrs -N -B -e "SELECT property_value FROM openmrs.global_property WHERE property='referencedemodata.started';"
   ```
3. Pastikan JDK dan Maven tersedia. **JDK 8 tidak perlu dipasang** — lihat
   koreksi 2 di bawah.
4. ~~Pasang OpenMRS SDK: `mvn openmrs-sdk:setup`~~ — **JANGAN.** Lihat koreksi 1.
5. Catat versi platform di `docs/lingkungan.md`.

## Selesai kalau
- `mvn -v` dan `java -version` berjalan.
- `referencedemodata.started` bernilai `true`.
- Jumlah baris cocok dengan `docs/kontrak-data.md`.
- `curl.exe http://127.0.0.1/openmrs/ws/rest/v1/session` → 200.

---

## Koreksi 1 — jangan jalankan `openmrs-sdk:setup`

Perintah itu membuat instans OpenMRS **baru** beserta database sendiri. Stack
Docker sudah berisi demo data yang dipakai seluruh eksperimen. Menambah server
SDK berarti dua instans OpenMRS di satu mesin — dilarang aturan 8 di `CLAUDE.md`.

Modul tetap bisa dibangun tanpa server SDK:

```powershell
mvn clean package                       # menghasilkan .omod
```

lalu dipasang dengan menyalin `.omod` ke container (lihat tugas 01).

Konsekuensinya: tidak ada `openmrs-sdk:watch`. Siklus ubah-kode jadi
build → salin → restart backend. Itu diterima.

## Koreksi 2 — target Java 8, dibangun dengan JDK 17

Berkas kelas di `openmrs-api-2.8.8.jar` bermajor version 52 = Java 8. Jadi
modul ditargetkan ke Java 8 lewat `maven.compiler.release=8`; JDK 17 yang
terpasang sudah cukup untuk mengompilasinya. Bukti dan alasan lengkap di
`docs/keputusan.md`.

## Hasil terverifikasi

| Yang diperiksa | Hasil |
|---|---|
| Maven | 3.9.16 |
| JDK build | 17.0.17 |
| JRE container backend | Corretto 21.0.11 |
| OpenMRS Platform | 2.8.8 |
| MariaDB | 10.11.7 |
| `referencedemodata.started` | `true` |
| concept (non-retired) | 4.249 |
| obat / pasien / lokasi / form / provider | 322 / 100 / 61 / 10 / 6 |
| REST `/session` via `127.0.0.1` | 200 |
