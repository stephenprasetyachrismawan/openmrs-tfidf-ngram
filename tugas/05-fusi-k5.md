# CATATAN PARAMETER — baca dulu

`ALPHA` **belum ditetapkan** dan tugas ini bukan tempat menetapkannya.
Lihat `docs/keputusan.md` bagian "Menunggu keputusan".

Jadi:

- Buat `ALPHA` sebagai **parameter yang bisa diubah**, bukan angka yang
  ditanam di kode. Nilai sementara: 0,45 (nilai yang dipakai eksperimen).
- Jangan menyetel `ALPHA` berdasarkan hasil query contoh mana pun. Penyetelan
  dilakukan di tugas 06 memakai 100 query dev, dan hanya di sana.
- Sertakan uji: `ALPHA=1` harus memberi skor identik dengan indeks kata saja,
  dan `ALPHA=0` identik dengan indeks kepingan saja. Itu membuktikan
  penggabungannya benar-benar linear dan kedua jalur tersambung.

Acuan Python (`riset/eksperimen2.py`, `fusi1`):

```python
a, b = idx["W"].cosine(q), idx["G"].cosine(q)
skor = ALPHA * a + (1 - ALPHA) * b
```

Verifikasi silang yang tersedia: `tools/silang_skor.py` sudah terbukti
mereproduksi jalur kepingan sampai 4 desimal. Perluas skrip itu untuk jalur
gabungan kalau perlu pembanding.

---
# Tugas 05 â€” Fusi kata + kepingan (K5)

## Tujuan
Menggabungkan dua jalur jadi satu skor per dokumen.

## Rujukan
`docs/algoritma.md` bagian 4.

## Langkah
1. Untuk tiap surface form: `skor = ALPHA*cos_kata + (1-ALPHA)*cos_kepingan`.
2. Untuk tiap dokumen: ambil **maksimum** atas surface form-nya.
3. Buang dokumen dengan skor â‰¤ 0,07.
4. Urutkan dengan kunci `(-skor, entitas+":"+id)`.

## Selesai kalau
- Query `panadol` menempatkan konsep Acetaminophen di peringkat 1 dengan skor
  mendekati skor alias "Panadol" sendiri â€” bukan rata-rata seluruh alias.
- `ALPHA = 1,0` memberi hasil identik dengan indeks kata saja.
- `ALPHA = 0,0` memberi hasil identik dengan indeks kepingan saja.
- Pengurutan stabil: 20 pemanggilan berturut-turut memberi urutan identik.

## Perhatian
Maksimum diambil **setelah** penggabungan, bukan sebelum. Mengambil maksimum
per jalur lalu menggabung memberi hasil berbeda dan salah.

