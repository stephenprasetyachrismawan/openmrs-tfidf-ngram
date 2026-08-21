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

---

## 2026-08-20 · Verifikasi silang Java vs Python pada korpus asli — COCOK

Setelah tugas 04, skor kepingan karakter modul Java dihitung ulang secara
independen di Python (`tools/silang_skor.py`) langsung dari
`riset/data/konsep.jsonl`, memakai aturan surface form yang sama dengan
`riset/eksperimen2.py`.

| Yang dibandingkan | Java (live di server) | Python (hitung ulang) |
|---|---|---|
| surface form konsep | 26.580 | **26.580** |
| `pulm edem` → Pulmonary edema | 0,5113 | **0,5113** |
| `diabete melitus` → Diabetes mellitus, type 2 | 0,4546 | **0,4546** |

Cocok sampai empat desimal pada dua query berbeda, di atas korpus penuh —
bukan korpus mainan. Ini membuktikan seluruh rantai Java (pemuatan tabel →
surface form → normalisasi → kepingan → pembobotan ltc → cosine → maksimum
per entri) mereproduksi pipeline penelitian secara numerik.

### Catatan proses: satu jebakan yang hampir jadi kesalahan

Percobaan pertama memberi 26.575 — meleset 5. Penyebabnya **bukan** Java,
melainkan aturan tebakan pada skrip verifikasi: skrip membuang alias dengan
membandingkan bentuk ternormalisasi (`norm(a) != norm(nama)`), sedangkan
`eksperimen2.py` membandingkan **string persis** (`a != nama`). Aturan yang
lebih ketat membuang 5 alias tambahan.

Pelajarannya untuk siapa pun yang memverifikasi di kemudian hari: ketika angka
verifikasi tidak cocok, **periksa dulu aturan pembandingnya**, jangan langsung
menyimpulkan implementasinya salah. Aturan yang sah ada di `riset/eksperimen2.py`,
bukan di ingatan.

### Peringatan urutan pemeringkatan

Untuk `diabete melitus`, entri teratas adalah **Diabetes mellitus** (0,5555),
bukan *type 2* (0,4546). Itu benar dan diharapkan — nama yang lebih pendek dan
lebih dekat ke query memang menang. Jangan menganggapnya bug.

---

## 2026-08-20 · Dua penyimpangan K5 dari pipeline penelitian — DIUKUR

Setelah tugas 05, implementasi Java dibandingkan dengan `riset/eksperimen2.py`.
Ditemukan dua perbedaan. Dampaknya **diukur**, tidak dikira-kira, memakai 780
query degradasi (tipo + trunkasi) dari 400 judul konsep acak (`tools/ukur_dampak.py`).

### Penyimpangan 1 — urutan operasi. Dampak BESAR. Java harus berubah.

Pipeline penelitian mengambil **maksimum per jalur dulu, baru menggabung**:

```python
def cosine(self, q):
    """kembalikan dict id_record -> skor cosine maksimum atas surface form-nya"""
    ...                                  # maks per pemilik ada DI DALAM cosine()

def fusi1(idx, q):
    a, b = idx["W"].cosine(q), idx["G"].cosine(q)      # dua-duanya sudah dimaksimumkan
    s = ALPHA * a.get(k, 0.0) + (1 - ALPHA) * b.get(k, 0.0)
```

Java tugas 05 melakukan sebaliknya: menggabung per surface form, baru
memaksimumkan. Bahkan ada uji (`maksimumDiambilSetelahDigabungBukanSebelum`)
yang menegaskan urutan itu sebagai yang benar.

Hasil pengukuran:

| | jumlah | persen |
|---|---|---|
| query dengan skor berbeda | 370 / 780 | **47,4%** |
| query dengan **10 besar berbeda** | 139 / 780 | **17,8%** |

Hampir seperlima query menghasilkan peringkat 10-besar yang berbeda. Itu pasti
menggeser nDCG@10, jadi angka Java tidak akan pernah cocok dengan 0,804 / 0,811
yang dilaporkan proposal.

**Keputusan: Java mengikuti urutan penelitian** (maks per jalur, lalu gabung).
Bukan karena pendekatan Java salah secara konsep — justru sebaliknya, menggabung
dulu bisa dibilang lebih benar secara teori karena menghormati satu surface form
sebagai satu kesatuan. Tapi seluruh angka yang sudah dipublikasikan dihasilkan
dengan urutan penelitian, dan aturan 2 `CLAUDE.md` melarang menyesuaikan angka
dokumen dengan implementasi.

**Dicatat sebagai pekerjaan lanjutan:** membandingkan kedua urutan sebagai
varian sistem yang diukur benar-benar, bukan diputuskan lewat argumen. Kalau
"gabung dulu" memang lebih baik, itu temuan yang layak dilaporkan — tapi harus
lewat eksperimen penuh, bukan diselipkan.

### Penyimpangan 2 — ambang skor 0,07. Ini KESALAHAN SAYA. Dampak kecil.

Tabel parameter di `CLAUDE.md` mencantumkan "ambang skor minimum 0,07".
**Angka itu tidak ada di mana pun dalam kode penelitian.** Pencarian `0.07` di
`eksperimen2.py` dan `eksperimen2b.py` tidak menghasilkan apa pun. Nilai yang
sebenarnya dipakai adalah **1e-6**, muncul tiga kali.

Asalnya: 0,07 adalah ambang pada mockup JavaScript di `docs/proposal.html`,
dipakai supaya daftar hasil demo tidak kepanjangan. Saya menyalinnya ke tabel
parameter seolah-olah itu parameter penelitian. Agen mematuhi tabel itu dengan
benar; kesalahannya ada pada tabelnya.

Dampak terukur — **hampir nol**:

| | ambang 1e-6 | ambang 0,07 |
|---|---|---|
| query nol-hasil | 2 / 780 (0,26%) | 2 / 780 (0,26%) |

Tetap diselaraskan ke 1e-6 demi kesetiaan pada pipeline, bukan karena berbahaya.
Tabel `CLAUDE.md` sudah dikoreksi.

### Pelajaran

Verifikasi tugas 04 cocok sampai 4 desimal, tapi **tidak menangkap penyimpangan
ini** karena hanya menguji satu jalur (kepingan saja). Penyimpangan urutan
operasi baru muncul ketika dua jalur digabungkan. Uji silang harus menguji
kombinasi, bukan hanya komponen.
