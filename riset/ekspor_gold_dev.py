# -*- coding: utf-8 -*-
"""Ekspor gold standard 100 query dev ke JSON tetap, dibaca Java saat startup.

Kenapa berkas ini ada: DevQueryGoldStandard.java sebelumnya mencoba meniru
bangun_query() dengan java.util.Random(42), tapi itu PRNG berbeda dari Python
random.Random (LCG 48-bit vs Mersenne Twister) - seed sama, urutan beda,
sehingga query dev Java dan Python tidak pernah bisa cocok. Bukti:
riset/hasil3/investigasi_gap_eval.json (SHA-256 daftar query Python
3b140c96... vs Java d2c27d1b...; peringkat Java yang sama, dievaluasi dengan
gold Python, cocok persis 0,8464).

Perbaikannya: generate query SEKALI di Python (satu-satunya sumber yang sudah
diverifikasi benar), ekspor ke JSON statis, dan Java membaca berkas itu -
tidak membangkitkan query sendiri lagi.

Hanya memakai qs[:100] (dev). Tidak pernah menyentuh qs[100:] (CLAUDE.md aturan 10).
Mengimpor eksperimen2 sebagai modul - main() tidak dipanggil, jadi blok
evaluasi test set tidak ikut berjalan.
"""
import hashlib
import json
import os
import random
import sys

DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, DIR)
import eksperimen2 as E

OUT_PATH = os.path.join(
    DIR, "..", "backend", "openmrs-module-tfidf-search", "api", "src", "main",
    "resources", "gold-dev-100.json")


def main():
    rnd = random.Random(E.SEED)
    rec = E.muat()
    qs = E.bangun_query(rec, rnd)
    rnd.shuffle(qs)
    dev = qs[:100]

    queries = []
    for it in dev:
        queries.append({
            "q": it["q"],
            "tipe": it["tipe"],
            "entitas_target": it["entitas_target"],
            "seed": it["seed"],
            "rel": it["rel"],
        })

    payload = {
        "seed": E.SEED,
        "n_query": len(queries),
        "sumber": "riset/eksperimen2.py bangun_query(), qs[:100] (dev, bukan qs[100:])",
        "queries": queries,
    }
    body = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)
    sha = hashlib.sha256(body.encode("utf-8")).hexdigest()
    payload["sha256_sumber"] = sha
    final = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)

    out_path = os.path.abspath(OUT_PATH)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(final)

    print("query dev ditulis:", len(queries))
    print("sha256:", sha)
    print("berkas:", out_path)


if __name__ == "__main__":
    main()
