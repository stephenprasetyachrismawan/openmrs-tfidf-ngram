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
