# -*- coding: utf-8 -*-
"""Sapuan parameter + uji tambahan untuk eksperimen lintas-entitas."""
import json, os, random, collections
import eksperimen2 as E

OUT = E.OUT
rnd = random.Random(E.SEED)
rec = E.muat()
qs = E.bangun_query(rec, rnd)
rnd.shuffle(qs)
test = qs[100:]
print("test:", len(test))

def eval_sistem(kode, lokal, glob):
    v = []
    for it in test:
        h = E.jalankan(kode, it["q"], lokal, glob, rec)
        v.append(E.metrik(h, it["rel"])["ndcg"])
    return v

hasil = {}

# --- indeks baku (n=4) untuk uji E3 vs E1 dan sapuan k / eps
lokal, glob, t = E.bangun(rec)
v_e1 = eval_sistem("E1", lokal, glob)
v_e3 = eval_sistem("E3", lokal, glob)
v_e2 = eval_sistem("E2", lokal, glob)
hasil["E3_vs_E1"] = E.bootstrap(v_e3, v_e1)
hasil["E3_vs_E2"] = E.bootstrap(v_e3, v_e2)
print("E3 vs E1:", ["%.3f" % x for x in hasil["E3_vs_E1"]])
print("E3 vs E2:", ["%.3f" % x for x in hasil["E3_vs_E2"]])

# --- sapuan panjang n-gram
sweep_n = {}
for n in (2, 3, 4, 5, 6):
    E.NGRAM = n
    E.grams = (lambda nn: (lambda s, k=nn: E._grams_impl(s, k)))(n)
    lo, gl, _ = E.bangun(rec)
    sweep_n[n] = sum(eval_sistem("E3", lo, gl)) / len(test)
    print("  n=%d  nDCG=%.3f" % (n, sweep_n[n]))
hasil["sweep_ngram"] = sweep_n

# kembalikan ke n=4
E.NGRAM = 4
E.grams = lambda s, k=4: E._grams_impl(s, k)
lokal, glob, _ = E.bangun(rec)

# --- sapuan konstanta RRF k
sweep_k = {}
for k in (5, 10, 20, 60):
    E.K_RRF = k
    sweep_k[k] = sum(eval_sistem("E3", lokal, glob)) / len(test)
    print("  k=%d  nDCG=%.3f" % (k, sweep_k[k]))
E.K_RRF = 20
hasil["sweep_k"] = sweep_k

# --- sapuan lantai bobot eps
sweep_e = {}
for eps in (0.0, 0.05, 0.15, 0.30):
    E.EPS = eps
    sweep_e[eps] = sum(eval_sistem("E3", lokal, glob)) / len(test)
    print("  eps=%.2f  nDCG=%.3f" % (eps, sweep_e[eps]))
E.EPS = 0.05
hasil["sweep_eps"] = sweep_e

# --- sapuan alpha (bobot kata vs karakter)
sweep_a = {}
for a in (0.0, 0.25, 0.45, 0.65, 1.0):
    E.ALPHA = a
    sweep_a[a] = sum(eval_sistem("E3", lokal, glob)) / len(test)
    print("  alpha=%.2f  nDCG=%.3f" % (a, sweep_a[a]))
E.ALPHA = 0.45
hasil["sweep_alpha"] = sweep_a

json.dump(hasil, open(os.path.join(OUT, "sapuan.json"), "w", encoding="utf-8"),
          indent=1, ensure_ascii=False)
print("selesai ->", os.path.join(OUT, "sapuan.json"))
