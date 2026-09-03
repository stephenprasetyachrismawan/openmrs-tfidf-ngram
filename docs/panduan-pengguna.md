# Panduan pengguna — Pencarian Terpadu OpenMRS

Panduan ini untuk **pengguna akhir** modul: klinisi, petugas rekam medis, dan
siapa pun yang memakai antarmuka OpenMRS. Panduan untuk pengembang ada di
[`README.md`](../README.md) di akar repositori.

Semua alamat memakai `http://127.0.0.1`. Kalau instalasi Anda memakai host
lain, ganti bagian itu saja.

---

## 1. Apa yang ditambahkan modul ini

OpenMRS bawaan punya kotak pencarian terpisah untuk tiap jenis data, dan hanya
pencarian **konsep** yang tahan salah ketik. Modul ini menambahkan:

| Yang ditambahkan | Apa gunanya |
|---|---|
| **Satu kotak pencarian untuk delapan jenis data** | Ketik sekali, hasil konsep, obat, pasien, formulir, lokasi, penyedia layanan, hasil laboratorium, dan kondisi muncul bersama. Tidak perlu memutuskan dulu sedang mencari apa. |
| **Tahan salah ketik** | `diabete melitus`, `amoxicilin`, `pyelonefritis` tetap menemukan entri yang benar. |
| **Tahan kata terpotong** | `pulm edem` menemukan *Pulmonary edema*. |
| **Kotak saran "Maksud Anda"** | Kalau ketikan terlalu pendek atau terlalu melenceng, muncul daftar saran yang bisa diklik. |

Modul ini **tidak** mengubah data. Ia hanya membaca dan menampilkan. Tidak ada
tombol yang bisa menghapus atau mengubah rekam medis.

---

## 2. Masuk ke aplikasi

1. Buka `http://127.0.0.1/openmrs/`
2. Masuk dengan akun OpenMRS Anda. Pada instalasi demo bawaan:
   `admin` / `Admin123`.

> **Kalau yang muncul halaman "Apache2 Ubuntu Default Page":** Anda membuka
> `localhost`, bukan `127.0.0.1`. Ganti alamatnya. Ini bukan kesalahan modul.

---

## 3. Halaman Pencarian Terpadu

**Alamat:** `http://127.0.0.1/openmrs/spa/unified-search`
**Lewat menu:** ikon **App Menu** di bilah atas → **Pencarian Terpadu**

### Cara memakai

1. Ketik apa yang dicari di kotak pencarian. Tidak perlu ejaan sempurna.
2. Hasil muncul dikelompokkan, dengan **lencana jenis data** pada setiap baris
   (Konsep, Obat, Pasien, dan seterusnya).
3. Bagian ketikan Anda yang cocok **ditandai tebal** pada judul hasil, sehingga
   terlihat mengapa baris itu muncul.
4. Klik sebuah baris untuk membuka entri aslinya di OpenMRS.

### Contoh yang bisa dicoba

| Ketik ini | Yang seharusnya muncul | Kenapa menarik |
|---|---|---|
| `diabete melitus` | *Diabetes mellitus* | dua kata salah eja sekaligus |
| `pulm edem` | *Pulmonary edema* | kedua kata terpotong |
| `amoxicilin` | sediaan Amoxicillin | satu huruf kurang |
| `paracetamol` | konsep **dan** sediaan obatnya | satu ketikan, dua jenis data |

### Pemilih metode

Di halaman ini ada pemilih **mode** dengan empat pilihan. Pilihan ini ada untuk
keperluan penelitian — supaya kontribusi tiap komponen bisa diukur ulang kapan
saja. Untuk pemakaian sehari-hari, **biarkan pada `e3`** (bawaan).

| Mode | Isi | Kapan dipakai |
|---|---|---|
| `b0` | pencocokan awalan saja | pembanding; gagal total pada salah ketik |
| `b1` | TF-IDF kata saja | pembanding; gagal pada kata terpotong |
| `e1` | TF-IDF kata + kepingan karakter | hampir sebaik `e3` |
| **`e3`** | `e1` + fusi peringkat berbobot | **bawaan, dipakai sehari-hari** |

---

## 4. Kotak saran "Maksud Anda" di bilah atas

Modul menyisipkan kotak pencarian kecil di bilah navigasi atas. Ketik di situ,
lalu daftar saran muncul di bawahnya.

- Saran muncul setelah beberapa huruf, tanpa perlu menekan Enter.
- Klik satu saran untuk langsung membuka entrinya.
- Kalau tidak ada yang cocok, daftar saran kosong — itu bukan galat.

**Yang perlu diketahui:** jalur saran ini bekerja paling baik untuk **salah ketik
pada kata utuh** (misalnya `pnemonia`). Untuk ketikan yang sangat pendek
(4–6 huruf) hasilnya jauh lebih lemah — ini batas metodenya, bukan kerusakan.
Kalau saran tidak membantu, ketik lebih lengkap di halaman Pencarian Terpadu.

**Hak akses:** hasil berjenis **pasien**, **hasil laboratorium**, dan
**kondisi** hanya muncul bila akun Anda punya privilese `View Patients`. Kalau
Anda tidak melihat pasien di hasil pencarian, kemungkinan besar itu memang
pembatasan hak akses, bukan indeks yang kosong.

---

## 5. Halaman Perbandingan Pencarian

**Alamat:** `http://127.0.0.1/openmrs/spa/perbandingan-pencarian`
**Lewat menu:** **App Menu** → **Perbandingan Pencarian**

Halaman ini menampilkan **tiga kolom berdampingan** untuk satu ketikan yang
sama:

1. **Pencarian konsep OpenMRS asli** — hasil endpoint bawaan OpenMRS.
2. **Baseline pencocokan awalan** — tiruan gaya kotak pencarian lama.
3. **Sistem usulan** — modul ini.

Gunanya: melihat langsung bedanya, tanpa perlu percaya pada angka saja. Klik
salah satu contoh ketikan yang tersedia (misalnya `diabete melitus`) dan ketiga
kolom akan terisi.

---

## 6. Halaman Pengujian Ablasi

**Alamat:** `http://127.0.0.1/openmrs/spa/pengujian-ablasi`
**Lewat menu:** **App Menu** → **Pengujian Ablasi**

Halaman ini untuk keperluan penelitian, bukan untuk pekerjaan klinis. Ia
menjalankan evaluasi atas 100 kueri pengembangan dan menampilkan metrik
(P@1, P@5, R@10, MRR, MAP, nDCG@10, proporsi kueri tanpa hasil) untuk setiap
mode.

Cara memakai: klik **Jalankan seluruh data uji**, tunggu tabel terisi.

**Angka rujukan** untuk memastikan instalasi Anda sehat:

| Mode | nDCG@10 yang diharapkan |
|---|---|
| `e3` | sekitar **0,737** |
| `e1` | sekitar **0,738** |
| `b0` | sekitar **0,561** |

Kalau angkanya jauh berbeda, kemungkinan besar korpus di instalasi Anda tidak
sama dengan demo data resmi OpenMRS — bukan modulnya yang rusak.

> Angka **0,670** yang tertulis di artikel penelitian adalah hasil pada
> **himpunan uji 260 kueri**, bukan 100 kueri pengembangan yang dijalankan
> panel ini. Kedua angka itu benar; cakupannya berbeda.

---

## 7. Halaman antarmuka lama (legacy UI)

**Alamat:** `http://127.0.0.1/openmrs/module/unifiedsearch/pencarianTerpadu.form`

Untuk instalasi yang masih memakai antarmuka lama OpenMRS, tersedia halaman
JSP dengan fungsi setara: kotak pencarian, pemilih mode, dan panel evaluasi.
Tautannya juga muncul di menu **Administrasi**.

---

## 8. Yang perlu diketahui tentang keterbatasannya

Disampaikan terus terang supaya tidak menimbulkan salah harap:

- **Indeks dibangun sekali saat modul dijalankan.** Pasien, konsep, atau obat
  yang **baru ditambahkan tidak akan muncul** sampai modul (atau server)
  dijalankan ulang. Ini keterbatasan yang sudah diketahui, bukan kerusakan.
- **Pencarian hanya berdasarkan nama, sinonim, dan kode.** Keterangan tambahan
  (kolom konteks) sengaja tidak diindeks, supaya kata umum seperti "tablet"
  tidak mengacaukan peringkat.
- **Tidak ada pemahaman makna.** Modul mencocokkan tulisan, bukan arti.
  Mencari `gula darah tinggi` tidak akan menemukan *Diabetes mellitus* kalau
  frasa itu tidak terdaftar sebagai sinonimnya.
- **Untuk kueri yang sengaja dipotong pendek**, pencocokan awalan biasa
  kadang lebih baik daripada modul ini. Ini terukur dan dilaporkan apa adanya
  pada artikel penelitian.

---

## 9. Kalau ada yang tidak beres

| Gejala | Kemungkinan sebab | Yang bisa dilakukan |
|---|---|---|
| Halaman "Apache2 Ubuntu Default Page" | membuka `localhost`, bukan `127.0.0.1` | ganti alamatnya |
| Menu "Pencarian Terpadu" tidak ada di App Menu | antarmuka ESM belum terpasang | hubungi pengelola sistem; lihat `README.md` langkah pemasangan ESM |
| Hasil pencarian kosong untuk semua ketikan | indeks belum terbangun, atau modul belum berjalan | hubungi pengelola sistem; periksa daftar modul di halaman Administrasi |
| Pasien tidak pernah muncul di hasil | akun tidak punya privilese `View Patients` | minta hak akses kepada pengelola sistem |
| Entri yang baru ditambahkan tidak ketemu | indeks belum diperbarui | minta pengelola menjalankan ulang modul |
| Semua halaman memberi galat server | layanan REST OpenMRS bermasalah | lihat bagian "Penanganan galat" pada `README.md` |

---

## 10. Rujukan lanjutan

| Kebutuhan | Berkas |
|---|---|
| Memasang dan menjalankan dari nol | [`README.md`](../README.md) |
| Mengulang hasil penelitian | [`docs/reproduksi.md`](reproduksi.md) |
| Memahami algoritmanya | [`docs/algoritma.md`](algoritma.md) |
| Angka hasil dan keterbatasannya | [`docs/ringkasan-hasil.md`](ringkasan-hasil.md) |
| Laporan penelitian lengkap | [`article/main.pdf`](../article/main.pdf) |
