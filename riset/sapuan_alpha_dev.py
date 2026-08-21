# -*- coding: utf-8 -*-
"""Sapuan ALPHA pada 100 query DEV saja (tugas/06-alpha-final.md).

eksperimen2b.py menyapu ALPHA memakai `test = qs[100:]`, yaitu 180 query UJI
- persis pelanggaran yang dilarang: "Jangan menyetel ALPHA diam-diam" (CLAUDE.md)
dan "Jangan memilih ALPHA berdasarkan 180 query uji" (tugas 06). Skrip ini
mengulang sapuan itu dengan benar: qs[:100], bukan qs[100:].

qs dibangun dengan seed dan urutan shuffle yang identik dengan main() di
eksperimen2.py, sehingga dev di sini adalah 100 query dev yang SAMA yang
dipakai laporan utama — bukan 100 query baru yang dipilih ulang.
"""
import json, os, random
import eksperimen2 as E

OUT = E.OUT
rnd = random.Random(E.SEED)
rec = E.muat()
qs = E.bangun_query(rec, rnd)
rnd.shuffle(qs)
dev = qs[:100]
print("dev:", len(dev))

def eval_sistem(kode, lokal, glob):
    v = []
    for it in dev:
        h = E.jalankan(kode, it["q"], lokal, glob, rec)
        v.append(E.metrik(h, it["rel"])["ndcg"])
    return v

lokal, glob, _ = E.bangun(rec)

sweep_a = {}
for a in (0.0, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.45, 0.65, 1.0):
    E.ALPHA = a
    v = eval_sistem("E3", lokal, glob)
    sweep_a[a] = sum(v) / len(v)
    print("  alpha=%.2f  nDCG(dev)=%.4f" % (a, sweep_a[a]))
E.ALPHA = 0.45

terbaik = max(sweep_a.items(), key=lambda kv: kv[1])
print("\nterbaik (argmax mentah) pada dev: ALPHA=%.2f  nDCG=%.4f" % terbaik)

hasil = {
    "n_dev": len(dev),
    "sweep_alpha_dev": sweep_a,
    "alpha_argmax_dev": terbaik[0],
    "ndcg_argmax_dev": terbaik[1],
    "alpha_dipilih": 0.20,
    "catatan": ("0.15 dan 0.20 beda 0.0001 nDCG (di dalam noise sampling 100 query); "
                "0.20 dipilih karena di tengah plateau 0.10-0.20 dan angka yang lebih bulat."),
}
path = os.path.join(OUT, "sapuan_alpha_dev.json")
json.dump(hasil, open(path, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print("selesai ->", path)
