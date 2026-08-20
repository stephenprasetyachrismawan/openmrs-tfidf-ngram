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
