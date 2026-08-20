# Dari mockup ke halaman modul

Jawaban langsung untuk "bagaimana memindahkan mockup di proposal ke modul".

## Perbedaan pokok yang harus dipahami dulu

Di `docs/proposal.html`, mesin pencarian berjalan **di dalam browser**, dengan
26 konsep contoh ditulis langsung di berkas JS. Itu dilakukan supaya proposal
bisa dibuka siapa pun tanpa memasang apa-apa.

Di modul sungguhan, susunannya terbalik:

```
mockup  : browser = data + mesin peringkat + tampilan
modul   : Java    = data + mesin peringkat
          browser = tampilan saja
```

**Jangan memindahkan mesin peringkat ke browser.** Alasannya tiga: korpus
sungguhan 4.748 dokumen (indeks kepingan besar, tidak masuk akal dikirim ke tiap
klien), penyaringan hak akses pasien harus terjadi di server, dan panel evaluasi
harus memakai mesin yang sama persis dengan yang melayani pencarian — kalau ada
dua salinan mesin, angkanya akan berbeda dan reproduksibilitasnya hilang.

## Apa yang dipakai ulang dari mockup, apa yang dibuang

| Bagian mockup | Nasib |
|---|---|
| CSS (`.ac`, `.ac-r`, `.pill`, `.modebar`, …) | **Dipakai ulang apa adanya.** Salin ke `omod/src/main/webapp/resources/pencarian.css`. |
| Struktur HTML dropdown & pengelompokan | **Dipakai ulang.** Bentuk DOM-nya sudah benar. |
| Navigasi keyboard (panah/enter/esc) | **Dipakai ulang apa adanya.** Murni antarmuka. |
| Fungsi `sorotNgram()` / `sorotKata()` | **Dipakai ulang.** Penyorotan dihitung di browser dari teks hasil + query — tidak perlu server. |
| `norm`, `kata`, `kepingan`, `build`, `qv`, `cos` | **Dibuang dari browser**, ditulis ulang di Java. |
| `cariEnt`, `bobot`, `gabung` | **Dibuang dari browser**, ditulis ulang di Java. |
| Konstanta `DATA` (26 konsep contoh) | **Dibuang.** Diganti panggilan REST. |
| Pemilih metode (`.mode`) | **Dipakai ulang**, tapi nilainya dikirim ke server sebagai parameter `mode`. |

## Bentuk baru fungsi render

Yang tadinya menghitung sendiri:

```js
function renderSemua(q, el, mode){
  var all = gabung(q, mode).slice(0,10);   // <-- hitung di browser
  ...
}
```

menjadi memanggil server:

```js
async function renderSemua(q, el, mode){
  if(!q.trim()){ el.innerHTML=''; return; }
  const r = await fetch('/openmrs/ws/rest/v1/unifiedsearch'
              + '?q=' + encodeURIComponent(q)
              + '&mode=' + mode + '&limit=10',
              {headers:{'Accept':'application/json'}});
  const data = await r.json();
  el.innerHTML = susunHtml(data.results, q, mode);   // <-- sisanya sama persis
}
```

`susunHtml()` isinya identik dengan pengelompokan di mockup. Yang berubah cuma
sumber datanya.

## Yang wajib ditambahkan karena sekarang ada jaringan

Mockup tidak memerlukan ini karena perhitungannya seketika. Halaman sungguhan
memerlukannya:

1. **Debounce ~120 ms.** Tanpa ini, mengetik "diabetes" mengirim 8 permintaan.
2. **Batalkan permintaan lama.** Pakai `AbortController`. Tanpa ini, jawaban
   permintaan lama bisa datang belakangan dan menimpa hasil yang lebih baru.
3. **Keadaan memuat.** Sekadar meredupkan daftar lama sudah cukup; jangan
   mengosongkannya, itu membuat tampilan berkedip.
4. **Keadaan gagal.** Kalau permintaan gagal, tampilkan pesan — jangan diam.

## Bentuk jawaban endpoint

```json
{
  "query": "diabete melitus",
  "mode": "e3",
  "waktu_ms": 1.28,
  "results": [
    {
      "entitas": "konsep",
      "id": 5497,
      "judul": "Diabetes mellitus, type 2",
      "konteks": "ICD-10 E11",
      "skor": 0.0244,
      "skor_asli": 0.372,
      "peringkat_di_tabel": 2,
      "bobot_tabel": 0.54,
      "url": "/openmrs/dictionary/concept.htm?conceptId=5497"
    }
  ]
}
```

`skor_asli`, `peringkat_di_tabel`, dan `bobot_tabel` dipakai halaman untuk
menampilkan keterangan di bawah tiap baris — itulah yang membuat hasilnya bisa
ditelusuri, dan itu bagian dari klaim penelitian. Untuk mode selain `e3`,
`bobot_tabel` boleh `null`.

## Dua halaman yang dibuat

**Halaman 1 — search box diagnosis (mockup A).** Menggantikan widget pencarian
konsep pada form encounter. Hanya mencari entitas `konsep`. Tidak menyentuh data
pasien, jadi tidak ada urusan hak akses. **Kirim ini duluan.**

**Halaman 2 — menu "Pencarian Terpadu" (mockup B).** Halaman baru dengan entri
menu sendiri. Mencari keenam entitas. Ada pemilih metode dan panel evaluasi.
Halaman ini berstatus eksperimental dan **harus** menyaring hasil pasien menurut
privilege pengguna.
