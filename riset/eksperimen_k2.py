# -*- coding: utf-8 -*-
"""Eksperimen K2 - saran ketik "Maksud Anda" (BigramJaccardSuggester).

Dua metrik (docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md):
  1. Akurasi saran - hit@k, MRR@6 daftar saran vs dokumen yang dimaksud.
  2. Penyelamatan   - query yang E3 beri 0 relevan -> 1 klik saran -> relevan.

Korpus 8 entitas: 6 dari eksperimen2 + hasillab + kondisi (riset/data/*.jsonl,
di-commit). TIDAK memanggil eksperimen2.main() -> qs[100:] tak tersentuh
(CLAUDE.md aturan 10). Snapshot korpus hasillab/kondisi (isi setelah main() jalan):
  hasillab.jsonl  2018  sha256 d2d96a838f809159add7a638946ae88711523f404934c67fd3dfc3b84b09d3c9
  kondisi.jsonl   1279  sha256 093eaa7c32649170697eb80d09c10da2b61ee9eb5b2267ff3f23588644c6355e
"""
import collections
import hashlib
import json
import os
import random
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eksperimen2  # noqa: E402  -- diimpor sebagai modul; main() TIDAK dipanggil

DIR = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(DIR, "data")
OUT = os.path.join(DIR, "hasil5")
os.makedirs(OUT, exist_ok=True)

ENT8 = eksperimen2.ENT + ["hasillab", "kondisi"]
SEED_K2 = 20260901

# Parameter K2 dikunci ke nilai modul Java (BigramJaccardSuggester), TIDAK disapu.
NGRAM_K2 = 2
MIN_IRISAN = 2
LIMIT_SARAN = 6


def muat8():
    """rec 6-entitas dari eksperimen2.muat(), plus hasillab + kondisi."""
    rec = eksperimen2.muat()
    for fn in ("hasillab.jsonl", "kondisi.jsonl"):
        path = os.path.join(DATA, fn)
        for line in open(path, encoding="utf-8-sig"):
            line = line.strip()
            if not line:
                continue
            o = json.loads(line)
            if not o.get("judul"):
                continue
            rec[o["id"]] = dict(
                id=o["id"], entitas=o["entitas"], judul=o["judul"],
                alias=eksperimen2._lst(o.get("alias")),
                kode=[str(k) for k in eksperimen2._lst(o.get("kode"))],
                konteks=o.get("konteks") or "", kelas="", refs=set(),
                tautan=None, n_obs=0,
            )
    return rec


# --------------------------------------------------------- jalur peringkat 8 entitas
# Salinan eksperimen2.bangun / bobot_koleksi / cari dengan ENT -> ENT8. Fungsi asli
# mengiterasi eksperimen2.ENT global, jadi harus disalin, bukan dipanggil. Diuji
# setia ke eksperimen2 pada 6 entitas (test_eksperimen_k2.py).

def bangun8(rec):
    t0 = time.time()
    lokal, glob_t, glob_o = {}, [], []
    for e in ENT8:
        teks, pem, utama = [], [], []
        for r in rec.values():
            if r["entitas"] != e:
                continue
            for j, f in enumerate(eksperimen2.bentuk_form(r)):
                teks.append(f)
                pem.append(r["id"])
                utama.append(j == 0)
        if not teks:
            continue
        lokal[e] = dict(
            W=eksperimen2.Indeks(teks, pem, eksperimen2.words),
            G=eksperimen2.Indeks(teks, pem, eksperimen2.grams),
            teks=teks, pem=pem, utama=utama,
        )
        glob_t += teks
        glob_o += pem
    glob = dict(
        W=eksperimen2.Indeks(glob_t, glob_o, eksperimen2.words),
        G=eksperimen2.Indeks(glob_t, glob_o, eksperimen2.grams),
    )
    return lokal, glob, time.time() - t0


def bobot_koleksi8(glob, rec, q):
    a, b = glob["W"].cosine(q), glob["G"].cosine(q)
    g = dict((e, 0.0) for e in ENT8)
    for k in sorted(set(a) | set(b)):
        s = eksperimen2.ALPHA * a.get(k, 0.0) + (1 - eksperimen2.ALPHA) * b.get(k, 0.0)
        e = rec[k]["entitas"]
        if s > g[e]:
            g[e] = s
    tot = sum(g.values())
    if tot <= 0:
        return dict((e, 1.0) for e in ENT8), g
    return dict((e, eksperimen2.EPS + (1 - eksperimen2.EPS) * g[e] / tot) for e in ENT8), g


def cari8(sistem, q, lokal, glob, rec, topk=10):
    """Sama logika eksperimen2.cari() tapi atas ENT8. Hanya B0/B1/E1/E3 dipakai K2."""
    per = {}
    for e, idx in lokal.items():
        if sistem == "B0":
            per[e] = eksperimen2.heuristik_openmrs(idx, q)
        elif sistem == "B1":
            per[e] = idx["W"].cosine(q)
        else:
            per[e] = eksperimen2.fusi1(idx, q)
    if sistem in ("B0", "B1", "E1"):
        semua = [(k, v) for e in ENT8 if e in per
                 for k, v in per[e].items() if v > 1e-6]
    else:
        w, _ = bobot_koleksi8(glob, rec, q)
        semua = []
        for e in ENT8:
            if e not in per:
                continue
            urut = sorted(per[e].items(), key=lambda kv: (-kv[1], kv[0]))
            for r, (k, v) in enumerate(urut):
                if v <= 1e-6:
                    continue
                semua.append((k, w[e] * 1.0 / (eksperimen2.K_RRF + r + 1)))
    semua.sort(key=lambda kv: (-kv[1], kv[0]))
    return semua[:topk]
