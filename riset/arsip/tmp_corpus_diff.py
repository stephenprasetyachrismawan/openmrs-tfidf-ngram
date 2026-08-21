# -*- coding: utf-8 -*-
"""Arsip investigasi C1: sama seperti tmp_compare_obat.py tapi juga menguji
apakah menambal judul obat dari SQL langsung mengubah tiga query dev pertama
dibanding query live Java — bagian dari eliminasi hipotesis sebelum akar
masalah sebenarnya (PRNG Java vs Python) ditemukan. Lihat
riset/investigasi_gap_eval.py untuk versi final investigasinya.

Compare virtual-document judul: riset/data jsonl vs live SQL rules."""
import json
import subprocess
import sys

import eksperimen2 as E

OUT = open("hasil3/corpus_judul_diff.txt", "w", encoding="utf-8")


def w(*parts):
    line = " ".join(str(p) for p in parts)
    OUT.write(line + "\n")
    print(line)


py = E.muat()

# obat from SQL
cmd = ["docker", "exec", "openmrs-distro-referenceapplication-db-1",
       "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e",
       "SELECT drug_id,name FROM drug WHERE retired=0 ORDER BY drug_id"]
rows = subprocess.check_output(cmd, text=True).strip().splitlines()
sql_obat = {int(a): b for a, b in (l.split("\t", 1) for l in rows)}
obat_diff = []
for r in py.values():
    if r["entitas"] != "obat":
        continue
    did = int(r["id"].split(":")[1])
    sj = sql_obat.get(did)
    if sj != r["judul"]:
        obat_diff.append((did, r["judul"], sj))
w("obat judul diffs", len(obat_diff), "/", len(sql_obat))
for t in obat_diff[:10]:
    w("  obat", t[0], "jsonl=", repr(t[1]), "sql=", repr(t[2]))

# patch rec with SQL obat judul and rebuild queries
rec_sql = E.muat()
for r in rec_sql.values():
    if r["entitas"] == "obat":
        did = int(r["id"].split(":")[1])
        if did in sql_obat:
            r["judul"] = sql_obat[did]

import random
rnd = random.Random(42)
qs = E.bangun_query(rec_sql, rnd)
rnd.shuffle(qs)
dev = qs[:100]
w("after SQL obat patch first3", [d["q"] for d in dev[:3]])
w("Java live first3", ["eucer", "miller", "associated nephropathy"])

OUT.close()
