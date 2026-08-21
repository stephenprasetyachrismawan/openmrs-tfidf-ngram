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

---

## 2026-08-21 · ALPHA ditetapkan 0,20 dari sapuan 100 query dev

**Konteks.** `tugas/06-alpha-final.md` mewajibkan dua perbaikan K5 lebih dulu
(urutan operasi maks-per-jalur, dan ambang 1e-6), lalu penyetelan ALPHA
memakai **100 query dev saja**, bukan 180 query uji.

**Pelanggaran yang ditemukan sebelum menyapu ulang.** `riset/eksperimen2b.py`
menyapu ALPHA dengan `test = qs[100:]` — yaitu memakai 180 query **uji**
(`qs` berisi 280 query total; `main()` di `eksperimen2.py` membelahnya
`dev = qs[:100]`, `test = qs[100:]`, jadi `test` di sana adalah 180 baris,
bukan 80). Hasil sapuan lama (`riset/hasil2/sapuan.json`, kolom `sweep_alpha`)
karena itu membocorkan test set — sesuai larangan tugas 06.

**Sapuan ulang.** `riset/sapuan_alpha_dev.py` dibuat: query, seed, dan urutan
shuffle identik dengan `main()`, tapi hanya memakai `qs[:100]` (100 query dev
yang sama yang dipakai laporan utama). Sistem yang dievaluasi: E3 (K5 + K6
Weighted RRF), metrik nDCG@10 rata-rata atas 100 query dev.

| ALPHA | nDCG (dev) |
|---|---|
| 0,00 | 0,8279 |
| 0,05 | 0,8423 |
| 0,10 | 0,8423 |
| **0,15** | **0,8465** |
| 0,20 | 0,8464 |
| 0,25 | 0,8409 |
| 0,30 | 0,8415 |
| 0,35 | 0,8397 |
| 0,45 | 0,8344 |
| 0,65 | 0,8235 |
| 1,00 | 0,7003 |

Hasil lengkap: `riset/hasil2/sapuan_alpha_dev.json`.

**Temuan.** Puncaknya bukan di 0,25 seperti dikira sebelumnya (itu dari sapuan
yang bocor test set) — pada 100 query dev, puncaknya di sekitar **0,15–0,20**,
dengan plateau datar dari 0,10 sampai 0,20 (selisih 0,8423→0,8465, semuanya
dalam rentang noise sampling 100 query). ALPHA=0,45 (nilai eksperimen asli)
memberi nDCG 0,8344 — jelas di bawah plateau optimum.

**Keputusan: ALPHA = 0,20.** Argmax mentah adalah 0,15 (nDCG 0,8465), tapi
selisihnya ke 0,20 (0,8464) hanya 0,0001 — di dalam noise. 0,20 dipilih karena
duduk di tengah plateau 0,10–0,20 (lebih tahan terhadap variasi sampling
dibanding titik ujung 0,15) dan angka yang lebih bulat.

**Implementasi.**
- `AlphaConfig.DEFAULT_ALPHA = 0.20` (`api/.../AlphaConfig.java`).
- Global property OpenMRS `unifiedsearch.alpha`, didaftarkan di `config.xml`
  dengan `defaultValue=0.20`, bisa diubah dari Admin > Settings tanpa
  membangun ulang modul. Diverifikasi ada di `global_property` setelah modul
  dipasang: `unifiedsearch.alpha = 0.20`.
- Halaman placeholder (`UnifiedSearchPageController`) memakai
  `AlphaConfig.current()` untuk demo K5 operasional — dikonfirmasi live
  membaca 0,2 dari database.

**Bukan diputuskan dari 180 query uji.** `riset/sapuan_alpha_dev.py` tidak
menyentuh `qs[100:]` sama sekali.

**Pekerjaan lanjutan yang belum dikerjakan:** sapuan `ngram`, `k` (RRF), dan
`eps` di `eksperimen2b.py` masih memakai `test = qs[100:]` (180 query uji).
Tugas 06 hanya meminta perbaikan ALPHA; tiga sapuan lain itu di luar cakupan
tugas ini dan **belum diperbaiki** — perlu tugas terpisah kalau mau
diselaraskan dengan aturan dev/test yang sama.

---

## 2026-08-20 · TEMUAN SERIUS: seluruh sapuan parameter memakai test set

Ditemukan saat tugas 06, diverifikasi langsung ke kode penelitian.

`riset/eksperimen2.py` baris 390 membagi query:

```python
dev, test = qs[:100], qs[100:]
```

`riset/eksperimen2b.py` baris 11 memakai:

```python
test = qs[100:]
```

dan **keempat sapuan** dijalankan di atasnya:

| baris | sapuan | dijalankan pada |
|---|---|---|
| 39 | panjang n-gram (2,3,4,5,6) | `test` |
| 52 | `K_RRF` (5,10,20,60) | `test` |
| 61 | `EPS` (0; 0,05; 0,15; 0,30) | `test` |
| 70 | `ALPHA` | `test` |

Artinya seluruh tabel sapuan parameter di `docs/proposal.html` — termasuk tabel
"Kenapa 4 huruf, bukan 3 atau 6" (bagian 2.3) dan angka sapuan ALPHA di
bagian K5 — dihitung pada **query uji yang seharusnya disimpan**.

### Seberapa parah — dibaca dengan jujur, tidak dibesarkan dan tidak dikecilkan

**Yang TIDAK tercemar:** tabel hasil utama (B0/B1/B2/E1/E2/E3/E4 pada 180 query
uji, berikut bootstrap-nya). Itu dijalankan sekali dengan parameter tetap.

**Yang tercemar:** justifikasinya. Proposal menyajikan sapuan itu seolah dasar
pemilihan parameter. Kalau parameter dipilih dengan melihat test set, test set
berhenti menjadi held-out, dan penguji berhak mempersoalkannya.

**Peringan yang jujur:** parameter yang benar-benar dipakai di eksperimen utama
sebagian besar **bukan** hasil sapuan itu. `ALPHA = 0,45` diwarisi dari
eksperimen pertama, bukan dari sapuan (yang justru menunjuk 0,25). `NGRAM = 4`
juga sudah dipakai sejak eksperimen pertama. `K_RRF = 20` dan `EPS = 0,05`
adalah nilai konvensional. Jadi kebocorannya lebih merusak **narasi** daripada
angkanya.

### Sapuan ALPHA bersih (100 query dev)

Dijalankan ulang lewat `riset/sapuan_alpha_dev.py`, hanya `qs[:100]`:

| ALPHA | nDCG@10 dev |
|---|---|
| 0,00 | 0,8279 |
| 0,10 | 0,8423 |
| **0,15** | **0,8465** (argmax) |
| **0,20** | **0,8464** (dipilih) |
| 0,25 | 0,8409 |
| 0,45 | 0,8344 |
| 1,00 | 0,7003 |

Puncaknya **0,15–0,20**, bukan 0,25. Angka 0,25 yang tertulis di proposal
berasal dari sapuan yang bocor. `ALPHA = 0,20` dipilih karena berada di tengah
dataran puncak; selisihnya dari argmax 0,0001, jauh di dalam derau 100 query.

### Konsekuensi yang belum diselesaikan

Modul sekarang memakai `ALPHA = 0,20`, sedangkan seluruh angka di proposal
(0,804 / 0,811 / +0,176 / +0,183) dihasilkan pada `ALPHA = 0,45`. **Modul tidak
lagi menggambarkan sistem yang dilaporkan.** Ini harus diselesaikan sebelum
panel evaluasi (tugas 12) dibangun, karena panel itu menjanjikan angka yang
bisa direproduksi.

Keputusan cara menyelesaikannya diambil manusia — dicatat pada entri berikutnya.

---

## 2026-08-20 · Keputusan: kunci parameter di dev, jalankan test sekali

Menjawab dua konsekuensi temuan kebocoran test set di atas.

### Keputusan 1 — semua parameter dikunci memakai 100 query dev

Sapuan `NGRAM`, `K_RRF`, `EPS` diulang pada `qs[:100]`, sama seperti yang sudah
dilakukan untuk `ALPHA`. Tabel sapuan di `docs/proposal.html` diganti dengan
versi dev ini. Dikerjakan di `tugas/06b-sapuan-dev.md`.

Alasan: justifikasi pemilihan parameter jadi bersih. Kalau penguji bertanya
"dari mana angka 4 huruf", jawabannya "dari 100 query pengembangan yang terpisah
dari query pelaporan" — bukan "dari query yang sama yang kami pakai melaporkan
hasil".

### Keputusan 2 — evaluasi test dijalankan SEKALI, setelah semua parameter terkunci

Seluruh angka Bab 6 dihasilkan ulang dalam satu kali jalan, dengan parameter
final. Dikerjakan di `tugas/08b-evaluasi-test-sekali.md`, setelah tugas 07
(Weighted RRF) dan 08 (baseline B0) selesai — karena keduanya dibutuhkan untuk
menghasilkan tabel perbandingan lengkap.

Konsekuensi yang diterima: **seluruh tabel angka di `docs/proposal.html` harus
ditulis ulang**, termasuk nDCG, selisih, selang kepercayaan, nilai p, tabel per
jenis kesalahan ketik, dan ringkasan di halaman depan. Angka 0,804 / 0,811 /
+0,176 / +0,183 kemungkinan besar berubah.

Berdasarkan sapuan dev, arah perubahannya kemungkinan **naik** (dev naik dari
0,8344 di ALPHA 0,45 ke 0,8464 di 0,20). Tapi itu dugaan, bukan janji — dan
angka berapa pun yang keluar adalah angka yang dilaporkan. Aturan 2 `CLAUDE.md`
tetap berlaku: tidak ada penyesuaian angka supaya cocok dengan harapan.

### Aturan baru — test set haram disentuh

Ditambahkan sebagai aturan 7 di `CLAUDE.md`. Ringkasnya: `qs[100:]` hanya boleh
dijalankan oleh `tugas/08b`, satu kali. Setiap skrip lain yang menyentuhnya
adalah bug, apa pun alasannya.

Ini yang membedakan penelitian yang bisa dipertahankan dari yang tidak. Kalau
test set dijalankan berulang sambil menyetel, ia berhenti menjadi ukuran
independen — dan tidak ada cara memperbaikinya selain membuat query baru.

---

## 2026-08-21 · NGRAM, K_RRF, EPS dikunci di 100 query dev (tugas 06b)

**Konteks.** Aturan 10 `CLAUDE.md`: `riset/eksperimen2b.py` menjalankan
**keempat** sapuannya (n-gram, k, eps, alpha) di `qs[100:]` — 180 query **uji**
(`qs` berisi 280 query total; `test = qs[100:]` di sana adalah 180 baris,
bukan 80). Berkas itu dipertahankan sebagai arsip dan **tidak dijalankan
lagi**. `riset/sapuan_dev.py` dibuat dari nol, mengulang tiga sapuan yang
tersisa (ALPHA sudah diulang di tugas 06) memakai `qs[:100]` — 100 query dev
yang sama dipakai laporan utama dan sapuan ALPHA sebelumnya.

Baseline yang dipegang selama tiap sapuan satu-faktor: NGRAM=4, K_RRF=20,
EPS=0,05, ALPHA=0,20. Metrik: nDCG@10 sistem E3, 100 query dev.

### Sapuan NGRAM

| NGRAM | nDCG (dev) |
|---|---|
| 2 | 0,8467 |
| **3** | **0,8555** |
| 4 (baseline) | 0,8464 |
| 5 | 0,8108 |
| 6 | 0,7475 |

NGRAM=3 tampak unggul (+0,0091 dari baseline). Diuji bootstrap berpasangan
(`E.bootstrap`, 5000 resample, seed 7) sebelum dipercaya:

```
NGRAM 3 vs 4: obs=0,0091  CI95=[0,0007, 0,0209]  p=0,0686
```

CI 95% nyaris menyentuh nol, dan p=0,0686 tidak signifikan pada ambang baku
0,05. Pada 100 query, ini derau yang lemah, bukan sinyal yang cukup kuat
untuk menimpa nilai yang sudah dipakai di seluruh kode, uji unit, dan fixture
silang-Python (`Tokenizer.charGrams`, `TfIdfIndexTest`,
`CharGramsSilangPythonTest`, halaman demo K4).

**Keputusan: NGRAM tetap 4.** Alasan eksplisit (bukan menyembunyikan sinyal):
buktinya borderline (CI hampir menyentuh nol, p>0,05) dan kesinambungan dengan
implementasi yang sudah teruji lebih berat daripada peluang perbaikan ~0,01
nDCG yang belum pasti nyata. Ini **berbeda** dari klaim proposal "2–4 setara"
— proposal itu dari sampel jauh lebih besar (780 query degradasi); temuan
NGRAM=3 sedikit unggul di 100 query dev dicatat di sini apa adanya, bukan
diselipkan diam-diam, sesuai aturan 2 `CLAUDE.md`.

### Sapuan K_RRF

| K_RRF | nDCG (dev) |
|---|---|
| 5 | 0,8482 |
| 10 | 0,8482 |
| 20 (baseline) | 0,8464 |
| 60 | 0,8453 |

Rentang seluruh titik hanya 0,0029 — jelas di dalam derau sampling 100 query
(jauh lebih kecil dari selisih NGRAM 3 vs 4 yang sendiri sudah borderline).
**Keputusan: K_RRF tetap 20**, demi kesinambungan — dinyatakan terang-terangan,
bukan karena kebetulan cocok dengan proposal.

### Sapuan EPS

| EPS | nDCG (dev) |
|---|---|
| 0,00 | 0,8464 |
| 0,05 (baseline) | 0,8464 |
| 0,15 | 0,8467 |
| 0,30 | 0,8482 |

Rentang 0,0018 — sama, di dalam derau. **Keputusan: EPS tetap 0,05**, demi
kesinambungan, dinyatakan terang-terangan.

### Sapuan ALPHA ulang (kombinasi final: NGRAM=4, K_RRF=20, EPS=0,05)

Karena ketiga parameter di atas tidak berubah dari nilai yang dipakai tugas
06, sapuan ALPHA ulang di sini **mereproduksi persis** angka tugas 06 (bukti
konsistensi silang antar skrip, bukan kebetulan):

| ALPHA | nDCG (dev) |
|---|---|
| 0,00 | 0,8279 |
| 0,15 | 0,8465 (argmax) |
| **0,20** | **0,8464 (dipilih, sama seperti tugas 06)** |
| 0,45 | 0,8344 |
| 1,00 | 0,7003 |

Tidak ada perubahan pada keputusan ALPHA=0,20.

**Hasil lengkap (empat sapuan + bootstrap):** `riset/hasil2/sapuan_dev.json`.
Dijalankan dua kali berturut-turut, keluaran (stdout dan JSON) **identik
byte-per-byte** — memenuhi aturan 1 `CLAUDE.md`.

**Nilai final, seluruhnya bersumber 100 query dev:**

| Parameter | Nilai final |
|---|---|
| `NGRAM` | 4 |
| `ALPHA` | 0,20 |
| `K_RRF` | 20 |
| `EPS` | 0,05 |

Tidak ada parameter lagi yang nilainya berasal dari test set atau dari
proposal tanpa diverifikasi ulang di dev.

---

## 2026-08-20 · CI95 dan nilai p yang tampak bertentangan — bukan bug, tapi jebakan penyajian

Sapuan NGRAM di tugas 06b melaporkan n=3 vs n=4: `obs=+0,0091`,
`CI95=[0,0007, 0,0209]`, `p=0,0686`.

Sekilas ini kontradiktif: selang kepercayaan 95% **tidak memuat nol**, tetapi
p > 0,05. Pembaca umumnya menganggap keduanya setara. Diperiksa ke fungsinya
(`bootstrap()` di `eksperimen2.py`, dipakai ulang oleh `sapuan_dev.py`):

```python
sam.sort()
lo, hi = sam[int(0.025 * n)], sam[int(0.975 * n) - 1]   # persentil distribusi MENTAH
pusat = [x - obs for x in sam]                           # digeser ke nol
p = sum(1 for x in pusat if abs(x) >= abs(obs)) / float(n)
```

Keduanya benar, tetapi menjawab pertanyaan berbeda:

- **CI** adalah persentil distribusi sampling penaksir — sebaran nilai selisih.
- **p** dihitung pada distribusi yang **digeser ke nol**, yaitu pendekatan
  terhadap hipotesis nol. Ini uji hipotesis yang sebenarnya.

Di dekat batas, keduanya memang bisa berbeda arah. Nilai p yang lebih
konservatif adalah yang tepat untuk pernyataan signifikansi.

**Konsekuensi untuk laporan:** menyajikan `CI95=[0,0007, 0,0209]` bersebelahan
dengan `p=0,0686` dan menyimpulkan "tidak signifikan" akan terlihat seperti
kesalahan bagi penguji, walaupun benar. Dua pilihan, keduanya jujur:

1. Sajikan keduanya, tambahkan satu kalimat bahwa CI adalah persentil distribusi
   sampling sedangkan p berasal dari distribusi yang digeser ke nol.
2. Laporkan CI dari distribusi tergeser juga, supaya konsisten dengan p.

Ini berlaku untuk **seluruh** tabel signifikansi di proposal, bukan hanya baris
NGRAM — fungsi bootstrap yang sama menghasilkan semuanya. Diputuskan saat
menulis Bab 6 di tugas 08b.

## Catatan tentang memilih tetap NGRAM = 4

Sapuan dev menunjuk n=3 (0,8555) di atas n=4 (0,8464). Kelompok tetap memakai 4
dengan alasan kesinambungan, dinyatakan terang-terangan.

Framing yang jujur sekaligus menguntungkan: memakai 4 berarti sistem yang
dilaporkan **bukan** yang paling optimal menurut data pengembangan. Artinya
angka hasil, kalau pun bergeser, cenderung **meremehkan** kemampuan metode —
bukan melebihkannya. Itu posisi yang aman dipertahankan di sidang, dan jauh
lebih baik daripada terlihat memilih 4 karena kebetulan cocok dengan dokumen
yang sudah ditulis.

Kalau penguji menanyakannya, jawabannya lengkap: n=3 sedikit lebih baik pada
100 query dev (+0,0091), tidak signifikan (p = 0,0686), dan tidak dipakai
supaya seluruh kode dan pengujian tetap konsisten dengan eksperimen pertama.

---

## 2026-08-20 · Bobot K6 terverifikasi, dan koreksi atas penjelasannya

Bobot koleksi Java diverifikasi terhadap pipeline Python (`tools/cek_bobot.py`,
mengimpor `eksperimen2` sebagai modul — `main()` tidak dijalankan, jadi test set
tidak tersentuh).

| query `diabete` | bobot | skor global |
|---|---|---|
| konsep | **0,8831** | 0,4897 |
| obat | **0,1669** | 0,0687 |
| pasien / form / lokasi / provider | 0,0500 | 0,0000 (lantai EPS) |

Angka `konsep = 0,8831` **cocok persis** dengan hasil live modul Java.

### Koreksi: penjelasan "tidak ada kecocokan non-konsep" tidak lengkap

Laporan tugas 07 menyimpulkan, dari kueri SQL `LIKE 'diabet%'` yang nihil, bahwa
tidak ada entitas non-konsep yang cocok. Kesimpulan itu **tidak berlaku untuk
kepingan karakter**. Buktinya: `obat` tidak berada di lantai EPS — skornya
0,0687.

Kalau benar hanya konsep yang berskor, bobotnya akan jadi
`0,05 + 0,95 × 1 = 1,0`, bukan 0,8831. Angka 0,8831 sendiri sudah membuktikan
ada entitas lain yang menyumbang.

Pelajarannya: **pencocokan harfiah SQL bukan alat yang sah untuk menyimpulkan
sesuatu tentang skor kepingan karakter.** Seluruh premis metode ini justru
kecocokan tanpa kesamaan kata utuh.

### Dua contoh konkret — keduanya layak masuk laporan

**`diabete` → Metformin (obat), skor 0,0687.** Kecocokannya **bukan** lewat
judul, melainkan lewat alias/kode. Metformin memang obat diabetes, jadi ini
kaitan yang benar secara klinis, muncul dari desain surface form (K2) tanpa
pengetahuan medis apa pun ditanamkan. Contoh bagus untuk menunjukkan gunanya
mengindeks alias secara terpisah.

**`pulm edem` → Pulmicort (obat), skor 0,0999, lewat kepingan `pulm`.**
Pulmicort adalah budesonide untuk asma, **tidak** ada hubungannya dengan edema
paru. Ini contoh jujur dari sifat terlalu murah hati kepingan karakter — persis
alasan K5 mempertahankan jalur kata sebagai penyeimbang, dan persis yang ditulis
di proposal bagian K5 ("kepingan bisa terlalu murah hati").

Sepasang contoh ini menunjukkan kedua sisi komponen yang sama dalam satu tarikan
napas: satu kaitan yang benar dan tak terduga, satu kaitan yang salah dan bisa
dijelaskan. Jauh lebih meyakinkan daripada hanya menampilkan yang berhasil.

### Batas yang perlu diperjelas untuk aturan 10

Mengimpor `riset/eksperimen2.py` sebagai modul dan memanggil fungsinya untuk
kueri ad-hoc **boleh** — `main()` dijaga `if __name__ == "__main__"`, dan query
ad-hoc bukan bagian dari `qs`. Yang dilarang adalah menjalankan skripnya
sehingga blok evaluasi `for it in test` ikut berjalan.

---

## 2026-08-21 · Evaluasi test set resmi (`hasil3/`, ALPHA=0,20)

**Keputusan metodologis bootstrap.** Pasangan E3-vs-E1 dan E1-vs-B1 awalnya
direkonstruksi dari top-5 di `per_query.json` — basis berbeda dari E1-vs-B0
(top-10 penuh). Dipilih **opsi (A)**: `eksperimen2.py` kini menyimpan
`ndcg_test` (vektor nDCG@10 per query, ketujuh sistem) dan pasangan bootstrap
E3_vs_E1 / E1_vs_B1 di blok `uji`. Pipeline test dijalankan ulang **sekali**
dengan parameter identik (ALPHA=0,20, NGRAM=4, K_RRF=20, EPS=0,05, seed=42);
bootstrap seed=7.

**Bukti determinisme sebelum menimpa `hasil3/`.** Ringkasan agregat, `per_tipe`,
dan pasangan bootstrap yang sudah ada **identik byte-per-byte** antara
`hasil3/` lama dan baru — hanya menambah `ndcg_test`, `bootstrap_seed`, dan
pasangan `E3_vs_E1` / `E1_vs_B1`.

**Arsip.** `riset/hasil2/` tetap arsip evaluasi test pada ALPHA=0,45 (tidak
ditimpa). `riset/eksperimen2.py` sekarang menulis ke `hasil3/` dengan
ALPHA=0,20. Commit sebelum perubahan: `9c71797` (tugas 08).

### Tabel lama vs baru — 180 query test

| Sistem | nDCG@10 lama (`hasil2/`, α=0,45) | nDCG@10 baru (`hasil3/`, α=0,20) | Δ |
|---|---|---|---|
| B0 | 0,628 | 0,628 | 0,000 |
| B1 | 0,646 | 0,646 | 0,000 |
| B2 | 0,615 | 0,615 | 0,000 |
| E1 | 0,804 | 0,802 | −0,002 |
| E2 | 0,580 | 0,582 | +0,003 |
| E3 | 0,811 | 0,815 | +0,004 |
| E4 | 0,589 | 0,598 | +0,009 |

| Uji bootstrap (seed=7, top-10) | Lama | Baru |
|---|---|---|
| E1 vs B0 | +0,176, p=0 | +0,174, p=0 |
| E3 vs B0 | +0,183, p=0 | +0,187, p=0 |
| E3 vs E1 | — | +0,013, p=0,039 |
| E1 vs B1 | — | +0,156, p=0 |

**Temuan signifikansi:** E1-vs-B0 tetap kuat (p&lt;0,001). Weighted RRF (E3-vs-E1)
+0,013 di test (p=0,039): signifikan secara nominal pada α=0,05, efek kecil;
tanpa koreksi multi-perbandingan tidak setara klaim kepingan karakter (+0,174).
Angka p=0,207 pernah muncul di proposal lama — sumber tercemar, lihat di bawah.

**Sumber resmi proposal:** `riset/hasil3/hasil.json`, `ringkasan.csv`,
`per_query.json`.

### Pekerjaan terbuka (penyimpangan Java)

| # | Item | Status |
|---|---|---|
| 5 | B1, B2, E4 belum diimplementasi di modul Java | Terbuka (tugas 09+) |
| 6 | Perbandingan peringkat Java-vs-Python pada 180 query test | Tertunda — Docker/OpenMRS mati saat evaluasi |
| 7 | `tools/silang_fusi.py` masih ALPHA=0,45 (acuan halaman admin saja) | Diketahui, sengaja |

---

## 2026-08-21 · Koreksi klaim E3-vs-E1: angka p=0,207 dari arsip tercemar

**Masalah.** Proposal sempat memakai pasangan E3-vs-E1 **+0,007, p=0,207** di
beberapa tempat — termasuk atribusi palsu ke "dev". Angka itu berasal dari
`riset/hasil2/sapuan.json`, dihitung `riset/eksperimen2b.py` pada **180 query
test** dengan ALPHA=0,45. Aturan 10 `CLAUDE.md`: `eksperimen2b.py` menjalankan
seluruh sapuannya di test set; berkas itu **tercemar, arsip, tidak dijalankan
lagi**. Bukan pembanding sah untuk melunakkan hasil resmi.

**Angka sah (satu-satunya).** `riset/hasil3/hasil.json` → `uji.E3_vs_E1`:
+0,013, CI95 [+0,001; +0,025], p=0,039 (bootstrap seed=7, top-10 penuh).

**Keputusan narasi (rumusan B).** E3 vs E1 signifikan secara nominal pada
α=0,05, tetapi efek kecil (+0,013) dan tidak sebanding E1 vs B0 (+0,174,
p=0,000). Tanpa koreksi multi-perbandingan, p=0,039 dibaca hati-hati. Klaim
penelitian tetap pada kepingan karakter; Weighted RRF peran arsitektural, bukan
peningkatan kualitas setara K4.

**Perubahan dokumen:** `docs/proposal.html`, `CLAUDE.md` — seluruh klaim hidup
+0,007 / p=0,207 dihapus; tidak ada atribusi dev untuk pasangan ini.
