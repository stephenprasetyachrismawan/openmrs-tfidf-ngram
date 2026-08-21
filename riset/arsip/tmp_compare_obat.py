# Arsip investigasi C1: pertanyaan yang dijawab skrip ini adalah "apakah judul
# obat di riset/data/obat.jsonl cocok dengan tabel `drug` SQL langsung?" —
# dipakai saat menelusuri kenapa gold dev Java tidak cocok dengan Python
# (lihat riset/investigasi_gap_eval.py dan riset/hasil3/investigasi_gap_eval.json
# untuk kesimpulan akhirnya: bukan masalah judul/SQL, tapi PRNG Java vs Python).
import json, subprocess
import eksperimen2 as E

py = E.muat()
cmd = ["docker", "exec", "openmrs-distro-referenceapplication-db-1",
       "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e",
       "SELECT drug_id,name FROM drug WHERE retired=0 ORDER BY drug_id"]
rows = subprocess.check_output(cmd, text=True).strip().splitlines()
sql_obat = {int(a): b for a, b in (l.split("\t", 1) for l in rows)}
diff = 0
for r in py.values():
    if r["entitas"] != "obat":
        continue
    did = int(r["id"].split(":")[1])
    if sql_obat.get(did) != r["judul"]:
        diff += 1
        if diff <= 5:
            print("obat diff", did, repr(r["judul"]), repr(sql_obat.get(did)))
print("obat judul diffs", diff, "/", len(sql_obat))
