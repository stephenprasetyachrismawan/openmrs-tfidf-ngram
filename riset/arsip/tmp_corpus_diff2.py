# -*- coding: utf-8 -*-
"""Arsip investigasi C1: sama seperti tmp_corpus_diff.py, diperluas ke judul
pasien dan lokasi (bukan hanya obat) dibanding SQL langsung, lalu menambal
ketiganya sekaligus untuk melihat apakah itu mengubah query dev pertama.
Bagian dari eliminasi hipotesis sebelum akar masalah sebenarnya (PRNG Java
vs Python, bukan data korpus) ditemukan — lihat riset/investigasi_gap_eval.py.
"""
import json
import random
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
import eksperimen2 as E

py = E.muat()

# PatientSource preferred names from SQL
sql = """
SELECT p.patient_id,
  (SELECT TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name))
   FROM person_name pn WHERE pn.person_id = p.patient_id AND pn.voided = 0 AND pn.preferred = 1
   ORDER BY pn.person_name_id LIMIT 1)
FROM patient p WHERE p.voided = 0 ORDER BY p.patient_id
"""
cmd = ["docker", "exec", "openmrs-distro-referenceapplication-db-1",
       "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql]
rows = subprocess.check_output(cmd, text=True).strip().splitlines()
sql_pas = {}
for line in rows:
    pid, name = line.split("\t", 1)
    sql_pas[int(pid)] = name.strip()

pdiff = []
for r in py.values():
    if r["entitas"] != "pasien":
        continue
    pid = int(r["id"].split(":")[1])
    sj = sql_pas.get(pid, "")
    if E.norm(sj) != E.norm(r["judul"]):
        pdiff.append((pid, r["judul"], sj))
print("pasien judul diffs", len(pdiff), "/ 100")
for t in pdiff[:8]:
    print(" ", t[0], "| jsonl:", t[1], "| sql:", t[2])

# lokasi
cmd = ["docker", "exec", "openmrs-distro-referenceapplication-db-1",
       "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e",
       "SELECT location_id,name FROM location WHERE retired=0 ORDER BY location_id"]
rows = subprocess.check_output(cmd, text=True).strip().splitlines()
sql_loc = {int(a): b for a, b in (l.split("\t", 1) for l in rows)}
ldiff = []
for r in py.values():
    if r["entitas"] != "lokasi":
        continue
    lid = int(r["id"].split(":")[1])
    if sql_loc.get(lid) != r["judul"]:
        ldiff.append((lid, r["judul"], sql_loc.get(lid)))
print("lokasi judul diffs", len(ldiff), "/ 61")

# patch pasien+lokasi+obat judul from SQL and rebuild queries
rec = E.muat()
cmd = ["docker", "exec", "openmrs-distro-referenceapplication-db-1",
       "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e",
       "SELECT drug_id,name FROM drug WHERE retired=0"]
rows = subprocess.check_output(cmd, text=True).strip().splitlines()
sql_obat = {int(a): b for a, b in (l.split("\t", 1) for l in rows)}
for r in rec.values():
    if r["entitas"] == "obat":
        r["judul"] = sql_obat[int(r["id"].split(":")[1])]
    elif r["entitas"] == "pasien":
        pid = int(r["id"].split(":")[1])
        if pid in sql_pas:
            r["judul"] = sql_pas[pid]
    elif r["entitas"] == "lokasi":
        lid = int(r["id"].split(":")[1])
        if lid in sql_loc:
            r["judul"] = sql_loc[lid]

rnd = random.Random(42)
qs = E.bangun_query(rec, rnd)
rnd.shuffle(qs)
dev = qs[:100]
print("SQL-patched all judul first3:", [d["q"] for d in dev[:3]])
print("Java live first3:", ["eucer", "miller", "associated nephropathy"])
