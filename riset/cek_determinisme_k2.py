# -*- coding: utf-8 -*-
"""Jalankan eksperimen_k2.py di dua proses baru + banding dengan hasil5/ yang
sudah ada. Semua metrik wajib identik byte; hanya waktu_indeks yang boleh beda.
CLAUDE.md aturan 1."""
import json
import os
import subprocess
import sys

DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(DIR, "hasil5")


def _tanpa_waktu(h):
    h = dict(h)
    h.pop("waktu_indeks", None)
    return h


def _baca(nama):
    return json.load(open(os.path.join(OUT, nama), encoding="utf-8"))


def main():
    h0 = _tanpa_waktu(_baca("hasil.json"))
    q0 = _baca("query_k2.json")
    pq0 = _baca("per_query_k2.json")
    for i in (1, 2):
        subprocess.check_call([sys.executable, os.path.join(DIR, "eksperimen_k2.py")],
                              stdout=subprocess.DEVNULL)
        assert json.dumps(_tanpa_waktu(_baca("hasil.json")), sort_keys=True) == \
            json.dumps(h0, sort_keys=True), f"hasil.json berubah di proses {i}"
        assert _baca("query_k2.json") == q0, f"query_k2.json berubah di proses {i}"
        assert _baca("per_query_k2.json") == pq0, f"per_query_k2.json berubah di proses {i}"
    print("determinisme OK: hasil.json + query_k2.json + per_query_k2.json identik di 3 proses")


if __name__ == "__main__":
    main()
