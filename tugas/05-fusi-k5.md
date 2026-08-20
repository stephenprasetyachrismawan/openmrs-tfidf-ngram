# Tugas 05 — Fusi kata + kepingan (K5)

## Tujuan
Menggabungkan dua jalur jadi satu skor per dokumen.

## Rujukan
`docs/algoritma.md` bagian 4.

## Langkah
1. Untuk tiap surface form: `skor = ALPHA*cos_kata + (1-ALPHA)*cos_kepingan`.
2. Untuk tiap dokumen: ambil **maksimum** atas surface form-nya.
3. Buang dokumen dengan skor ≤ 0,07.
4. Urutkan dengan kunci `(-skor, entitas+":"+id)`.

## Selesai kalau
- Query `panadol` menempatkan konsep Acetaminophen di peringkat 1 dengan skor
  mendekati skor alias "Panadol" sendiri — bukan rata-rata seluruh alias.
- `ALPHA = 1,0` memberi hasil identik dengan indeks kata saja.
- `ALPHA = 0,0` memberi hasil identik dengan indeks kepingan saja.
- Pengurutan stabil: 20 pemanggilan berturut-turut memberi urutan identik.

## Perhatian
Maksimum diambil **setelah** penggabungan, bukan sebelum. Mengambil maksimum
per jalur lalu menggabung memberi hasil berbeda dan salah.
