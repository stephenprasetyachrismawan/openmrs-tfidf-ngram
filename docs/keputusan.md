# Catatan keputusan

Tiap keputusan teknis yang menyimpang dari rencana awal dicatat di sini beserta
buktinya, supaya bisa dipertanggungjawabkan saat sidang.

---

## 2026-08-20 · Tidak memakai OpenMRS SDK server

**Keputusan:** langkah `mvn openmrs-sdk:setup` **dilewati**. Modul dibangun
dengan `mvn clean package` dan dipasang dengan menyalin `.omod`.

**Alasan:** `openmrs-sdk:setup` membuat instans OpenMRS baru lengkap dengan
database sendiri. Stack Docker sudah berjalan dan berisi demo data yang dipakai
seluruh eksperimen. Menambah server SDK berarti dua instans OpenMRS di satu
mesin — persis yang dilarang aturan 8 di `CLAUDE.md`, dan persis masalah yang
baru saja dibereskan saat menghapus stack `openmrs-fresh`.

**Konsekuensi:** tidak ada `openmrs-sdk:watch` (muat ulang otomatis saat kode
berubah). Setiap perubahan modul perlu build ulang + salin `.omod` + restart
container backend. Lebih lambat beberapa detik per siklus, tapi menghindari
divergensi data antar instans.

**Berkas tugas 00 sudah dikoreksi** supaya tidak menyesatkan pembaca berikutnya.

---

## 2026-08-20 · Target bytecode Java 8, dibangun dengan JDK 17

**Keputusan:** `maven.compiler.release = 8`. Build memakai JDK 17 yang terpasang.

**Bukti:** versi major berkas kelas `org/openmrs/Concept.class` di dalam
`openmrs-api-2.8.8.jar` adalah **52**, yaitu Java 8.

```
docker exec openmrs-distro-referenceapplication-backend-1 sh -c \
  "cd /tmp && jar xf /usr/local/tomcat/webapps/openmrs/WEB-INF/lib/openmrs-api-2.8.8.jar \
   org/openmrs/Concept.class && od -An -tu1 -j6 -N2 org/openmrs/Concept.class"
→ 0 52
```

**Alasan:** OpenMRS Platform 2.8.8 sendiri masih dikompilasi ke Java 8, walaupun
container menjalankannya di atas JRE 21. Menargetkan Java 8 berarti modul kita
bisa dipasang juga pada instalasi OpenMRS lama yang masih memakai JRE 8 — dan
itu selaras dengan premis penelitian ini, yaitu "cukup ringan untuk dipasang di
puskesmas". Menargetkan 17 tidak memberi keuntungan apa pun untuk kode kita
(hanya aritmetika, koleksi, dan string) tetapi mempersempit tempat modul bisa
dipasang.

**Tidak perlu memasang JDK 8.** JDK 17 mengompilasi ke target 8 lewat `--release 8`.

**Kalau build gagal** dengan galat semacam "release version 8 not supported",
naikkan ke 11 lalu 17, dan **catat perubahannya di sini** beserta pesan galatnya.

---

## Menunggu keputusan

### ALPHA (bobot jalur kata pada K5)

Belum ditetapkan. Eksperimen memakai 0,45; sapuan parameter menunjukkan optimum
di sekitar 0,25. Diselesaikan di `tugas/06-alpha-final.md`, memakai 100 query dev
— **bukan** 180 query uji.

---

## 2026-08-21 · Direktori modul di container adalah `/openmrs/data/modules`

**Keputusan:** skrip pemasangan menyalin `.omod` ke `/openmrs/data/modules`,
bukan ke `/usr/local/tomcat/.OpenMRS/modules` seperti yang tertulis di
`tugas/01-skeleton-omod.md`.

**Bukti:** path yang disebut di berkas tugas tidak ada pada image ini.

```
docker exec ...-backend-1 sh -c 'ls -d /usr/local/tomcat/.OpenMRS'
→ ls: cannot access /usr/local/tomcat/.OpenMRS: No such file or directory

docker exec ...-backend-1 sh -c 'find / -name "*.omod" | head'
→ /openmrs/data/modules/legacyui-2.1.0.omod  (dan 29 modul lain)
```

`/openmrs/data` adalah mount volume `openmrs-distro-referenceapplication_openmrs-data`,
jadi `.omod` yang disalin bertahan walau container dibuat ulang.

---

## 2026-08-21 · Modul bergantung pada legacyui (hanya untuk lapisan web)

**Keputusan:** `omod` memakai dependensi `org.openmrs.module:legacyui-omod:2.1.0`
(scope `provided`) dan `config.xml` menyatakan `<require_modules>` pada
`org.openmrs.module.legacyui`.

**Alasan:** kelas titik-ekstensi menu administrasi,
`org.openmrs.module.web.extension.AdministrationSectionExt`, sudah **tidak** ada
di `openmrs-api`/`openmrs-web` 2.8.8. Build gagal:

```
package org.openmrs.module.web.extension does not exist
cannot find symbol: class AdministrationSectionExt
```

Kelas itu sekarang berada di `legacyui-omod-2.1.0.jar`
(`org/openmrs/module/web/extension/AdministrationSectionExt.class`), dan
`legacyui-2.1.0.omod` memang sudah terpasang di stack ini.

**Konsekuensi:** ketergantungan ini hanya untuk halaman antarmuka lama.
Logika pencarian dan endpoint REST nanti tidak boleh menyentuh legacyui, supaya
tetap bisa dipakai dari O3.

---

## 2026-08-20 · Antarmuka dibangun sebagai microfrontend O3 (bukan JSP)

**Keputusan:** kedua antarmuka — menu "Pencarian Terpadu" dan perbaikan kotak
diagnosis — dibangun sebagai **ESM (microfrontend) OpenMRS 3**, bukan halaman
JSP legacyui.

**Alasan:** RefApp 3 adalah tempat klinisi benar-benar bekerja. Halaman JSP
legacyui hanya terlihat lewat menu Administration, sehingga demo "petugas
mengetik `diabete melitus` di form Visit Note" tidak bisa ditunjukkan pada
antarmuka yang sesungguhnya. Klaim kegunaan di proposal jadi lebih kuat kalau
perbaikannya muncul di layar yang nyata.

**Kelayakan sudah diperiksa** sebelum diputuskan:

| Prasyarat | Status |
|---|---|
| Node | v26.7.0 |
| npm | 11.11.0 |
| git | 2.45.1 |
| `importmap.json` di container frontend | ada dan terbaca |

`importmap.json` adalah titik penyisipan: ESM kita dibangun jadi berkas `.js`,
disajikan dari container frontend, lalu didaftarkan sebagai entri baru di peta
itu. Tidak perlu membangun ulang seluruh frontend RefApp.

**Yang ditolak:** JSP legacyui untuk panel evaluasi. Ditolak demi konsistensi —
satu toolchain frontend lebih mudah dirawat empat orang daripada dua.

### Risiko yang diterima, dan katupnya

Ini pilihan termahal dari tiga yang tersedia. Risikonya nyata: toolchain O3
(module federation, `openmrs` CLI, versi `@openmrs/esm-framework` yang harus
cocok dengan RefApp yang berjalan) bisa memakan waktu berhari-hari sebelum satu
piksel pun muncul.

**Yang harus dipahami seluruh kelompok:** tidak satu pun klaim penelitian
bergantung pada antarmuka ini. Seluruh angka di Bab 6 dihasilkan pipeline
Python dan endpoint REST. Antarmuka hanya memperagakannya.

**Katup pengaman — gerbang di tugas 10.** Kalau setelah tugas 10 ESM "hello
world" belum juga tampil di frontend yang berjalan, kelompok **berhenti mencoba
O3** dan turun ke halaman JSP legacyui (bahan mockup sudah siap di
`docs/proposal.html`). Keputusan turun itu dicatat di berkas ini. Jangan
menghabiskan minggu terakhir pada webpack sementara laporan belum ditulis.

**Urutan pengerjaan dibalik dari rencana semula:** endpoint REST (tugas 09)
harus selesai dan teruji lewat `curl` **sebelum** frontend disentuh. Dengan
begitu, kalaupun jalur O3 gagal total, seluruh isi penelitian tetap utuh dan
bisa dilaporkan.

---

## 2026-08-20 · Bug laten: `toLowerCase()` tanpa Locale (WAJIB diperbaiki)

**Temuan.** `TextNormalizer.normalize()` memakai `value.toLowerCase()` tanpa
argumen Locale. Metode itu memakai **locale bawaan JVM**, sehingga hasilnya
berbeda antar mesin. Python `str.lower()` tidak begitu — ia tidak bergantung
locale. Jadi cermin Java–Python yang kita andalkan bocor.

**Bukti** (dijalankan pada JDK 17 mesin ini):

```
default   : [insulin glargine]
turki     : [ınsulin glargine]      <- i tanpa titik
ROOT      : [insulin glargine]
norm turki: [nsulin glargine]       <- huruf I HILANG setelah [^a-z0-9] disapu
norm ROOT : [insulin glargine]
SAMA? false
```

Pada JVM berlocale Turki/Azerbaijan, `"I".toLowerCase()` menghasilkan `ı`
(U+0131), yang bukan anggota `[a-z0-9]`, sehingga dibuang jadi spasi. Setiap
kata berhuruf kapital I terpotong. `Insulin` menjadi `nsulin`.

**Kenapa ini penting walau sekarang tidak tampak.** Container berjalan pada
locale netral, jadi angka hari ini benar. Tapi seluruh klaim reproduksibilitas
penelitian ini — aturan 1 `CLAUDE.md`, dan janji "penguji cukup memasang modul
lalu menekan satu tombol" — runtuh kalau angkanya bergantung pada setelan
regional mesin penguji. Ini kelas bug yang sama dengan hash randomization
Python yang sudah pernah menghabiskan waktu kelompok ini.

**Perbaikan wajib:**

```java
String lowered = value.toLowerCase(java.util.Locale.ROOT);
```

Berlaku untuk **setiap** `toLowerCase()` dan `toUpperCase()` di seluruh basis
kode, bukan hanya di `TextNormalizer`. Sertakan uji unit yang menormalkan
`"Insulin glargine"` di bawah `Locale.forLanguageTag("tr")` dan menuntut
hasilnya tetap `insulin glargine`.

**Status:** ditemukan sebelum indeks dibangun, jadi tidak ada angka yang perlu
dihitung ulang. Diperbaiki di awal tugas 03.
