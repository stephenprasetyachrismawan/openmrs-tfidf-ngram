# -*- coding: utf-8 -*-
"""Arsip investigasi C1: menerjemahkan `DevQueryGoldStandard.java`
(pra-perbaikan) baris demi baris ke Python dengan `random.Random`, untuk
menguji apakah query yang dihasilkan Java — SEANDAINYA Java memakai PRNG
Python — akan cocok dengan `bangun_query()` asli. Jawabannya ya (hash sama),
yang mengonfirmasi diagnosis akhir: satu-satunya bug adalah PRNG
`java.util.Random` vs `random.Random`, bukan logika pembangkitan query itu
sendiri. Perbaikan yang benar-benar dipakai (`riset/ekspor_gold_dev.py`)
tidak membangkitkan ulang di Java sama sekali — cukup mengekspor hasil
Python. Lihat riset/investigasi_gap_eval.py dan docs/keputusan.md (C1).

Simulate DevQueryGoldStandard in Python exactly from Java source."""
import random
import hashlib
import json
import eksperimen2 as E

SEED = 42
KLINIS = {"Diagnosis", "Symptom", "Finding", "Symptom/Finding", "Procedure", "Test", "Anatomy", "Drug"}


def remove_at(arr, idx):
    return arr[:idx] + arr[idx + 1 :]


def degradasi_java(nama, tipe, rnd):
    w = list(E.words(nama))
    if not w:
        return None
    if tipe == "persis":
        return " ".join(w)
    if tipe == "urut_balik":
        if len(w) < 2:
            return None
        w.reverse()
        return " ".join(w)
    if tipe == "hilang_kata":
        if len(w) < 2:
            return None
        w.pop(rnd.randint(0, len(w) - 1))
        return " ".join(w)
    if tipe == "trunkasi":
        out = []
        changed = False
        for x in w:
            if len(x) > 5:
                cut = 4 if rnd.random() < 0.5 else 5
                out.append(x[:cut])
                changed = True
            else:
                out.append(x)
        if not changed:
            return None
        return " ".join(out)
    if tipe == "typo":
        cand = [i for i, x in enumerate(w) if len(x) >= 5]
        if not cand:
            return None
        wi = rnd.choice(cand)
        x = list(w[wi])
        if len(x) < 3:
            return None
        j = 1 + rnd.randint(0, len(x) - 3)
        if rnd.random() < 0.5:
            x = remove_at(x, j)
        else:
            x[j], x[j + 1] = x[j + 1], x[j]
        w[wi] = "".join(x)
        return " ".join(w)
    return None


def build_dev_java(rec, concept_class):
    tautan2obat = {}
    for r in rec.values():
        if r["entitas"] == "obat" and r["tautan"]:
            tautan2obat.setdefault(int(r["tautan"]), set()).add(r["id"])
    byent = {}
    for r in rec.values():
        byent.setdefault(r["entitas"], []).append(r)
    for e in byent:
        byent[e].sort(key=lambda x: x["id"])
    rencana = [("konsep", 110), ("obat", 80), ("pasien", 60), ("lokasi", 40), ("form", 10), ("provider", 6)]
    tipe = ["persis", "typo", "trunkasi", "hilang_kata", "urut_balik"]
    rnd = random.Random(SEED)
    qs = []
    n = 0
    for ent, jml in rencana:
        pool = list(byent.get(ent, []))
        if ent == "konsep":
            pool = [
                r for r in pool
                if concept_class.get(int(r["id"].split(":")[1]), r.get("kelas", "")) in KLINIS
                and len(E.words(r["judul"])) >= 1
            ]
        if not pool:
            continue
        rnd.shuffle(pool)
        amb = 0
        for r in pool:
            if amb >= jml:
                break
            t = tipe[n % 5]
            q = degradasi_java(r["judul"], t, rnd)
            if not q or len(q) < 3:
                continue
            qs.append({"q": q, "seed": r["id"]})
            amb += 1
            n += 1
    rnd.shuffle(qs)
    return qs[:100]


rec = E.muat()
cc = {}
for line in open("data/konsep.jsonl", encoding="utf-8"):
    o = json.loads(line)
    cc[o["id"]] = o.get("kelas", "")

dev_java_sim = build_dev_java(rec, cc)
rnd = random.Random(42)
qs = E.bangun_query(rec, rnd)
rnd.shuffle(qs)
dev_py = qs[:100]

jq = [d["q"] for d in dev_java_sim]
pq = [d["q"] for d in dev_py]
print("java-sim vs py bangun_query same list", jq == pq)
print("first3 java-sim", jq[:3])
print("first3 py", pq[:3])
print("hash java-sim", hashlib.sha256(json.dumps(jq, separators=(",", ":")).encode()).hexdigest()[:16])
print("hash py", hashlib.sha256(json.dumps(pq, separators=(",", ":")).encode()).hexdigest()[:16])
