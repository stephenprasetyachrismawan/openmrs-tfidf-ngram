# -*- coding: utf-8 -*-
"""Sapuan NGRAM, K_RRF, EPS (lalu ALPHA ulang) pada 100 query DEV saja.

Lahir dari temuan: riset/eksperimen2b.py menjalankan KEEMPAT sapuannya di
potongan qs mulai indeks ke-100 -- 180 query UJI, bukan dev. Berkas itu arsip,
tidak dijalankan lagi. Lihat docs/keputusan.md dan aturan 10 CLAUDE.md.

Skrip ini TIDAK PERNAH mengiris qs mulai indeks ke-100. qs dibangun dengan
seed dan urutan shuffle yang identik dengan main() di eksperimen2.py dan
dengan sapuan_alpha_dev.py, sehingga dev di sini adalah 100 query dev yang
SAMA. Satu-satunya potongan qs yang dipakai di seluruh berkas ini adalah
qs[:100].

Pola: satu-faktor-pada-satu-waktu di sekitar baseline saat ini
(NGRAM=4, K_RRF=20, EPS=0.05, ALPHA=0.20), dengan uji bootstrap berpasangan
(E.bootstrap, sama seperti dipakai eksperimen2.py) untuk kandidat NGRAM yang
tampak unggul -- supaya "beda" tidak disamakan dengan "signifikan" begitu saja.
Ditutup dengan sapuan ALPHA ulang pada kombinasi NGRAM/K_RRF/EPS final.
"""
import json, os, random
import eksperimen2 as E

OUT = E.OUT
rnd = random.Random(E.SEED)
rec = E.muat()
qs = E.bangun_query(rec, rnd)
rnd.shuffle(qs)
dev = qs[:100]
assert len(dev) == 100
print("dev:", len(dev))

# Baseline saat ini -- lihat CLAUDE.md tabel parameter dan docs/keputusan.md.
BASELINE_NGRAM, BASELINE_K_RRF, BASELINE_EPS, BASELINE_ALPHA = 4, 20, 0.05, 0.20

def set_ngram(n):
    E.NGRAM = n
    # grams(s, k=NGRAM) mengikat default k saat definisi, bukan saat dipanggil,
    # jadi mengubah E.NGRAM saja tidak cukup -- E.grams sendiri harus diikat ulang.
    E.grams = (lambda nn: (lambda s, k=nn: E._grams_impl(s, k)))(n)

def eval_vec(kode, lokal, glob):
    v = []
    for it in dev:
        h = E.jalankan(kode, it["q"], lokal, glob, rec)
        v.append(E.metrik(h, it["rel"])["ndcg"])
    return v

def ndcg_dev(lokal, glob):
    v = eval_vec("E3", lokal, glob)
    return sum(v) / len(v), v

hasil = {"n_dev": len(dev)}

# --- reset ke baseline ---
set_ngram(BASELINE_NGRAM)
E.K_RRF = BASELINE_K_RRF
E.EPS = BASELINE_EPS
E.ALPHA = BASELINE_ALPHA

# --- sapuan NGRAM (K_RRF, EPS, ALPHA dipegang di baseline) ---
sweep_ngram, vec_ngram = {}, {}
for n in (2, 3, 4, 5, 6):
    set_ngram(n)
    lokal, glob, _ = E.bangun(rec)
    m, v = ndcg_dev(lokal, glob)
    sweep_ngram[n] = m
    vec_ngram[n] = v
    print("  NGRAM=%d  nDCG(dev)=%.4f" % (n, m))
set_ngram(BASELINE_NGRAM)
hasil["sweep_ngram_dev"] = sweep_ngram

# Kandidat yang tampak unggul dari baseline (NGRAM=3) diuji signifikansinya
# dengan bootstrap berpasangan, bukan sekadar dibandingkan rata-ratanya.
obs, lo, hi, p = E.bootstrap(vec_ngram[3], vec_ngram[BASELINE_NGRAM])
print("  bootstrap NGRAM 3 vs %d: obs=%.4f  CI95=[%.4f, %.4f]  p=%.4f" % (BASELINE_NGRAM, obs, lo, hi, p))
hasil["bootstrap_ngram_3_vs_4"] = {"obs": obs, "ci95_lo": lo, "ci95_hi": hi, "p": p}

# --- indeks baseline dipakai untuk sapuan K_RRF dan EPS (keduanya tidak
#     mengubah bentuk indeks, hanya cara K6 menskor, jadi indeks cukup dibangun
#     sekali di NGRAM baseline) ---
lokal, glob, _ = E.bangun(rec)

# --- sapuan K_RRF (NGRAM, EPS, ALPHA dipegang di baseline) ---
sweep_k = {}
for k in (5, 10, 20, 60):
    E.K_RRF = k
    sweep_k[k], _ = ndcg_dev(lokal, glob)
    print("  K_RRF=%d  nDCG(dev)=%.4f" % (k, sweep_k[k]))
E.K_RRF = BASELINE_K_RRF
hasil["sweep_k_rrf_dev"] = sweep_k

# --- sapuan EPS (NGRAM, K_RRF, ALPHA dipegang di baseline) ---
sweep_eps = {}
for eps in (0.0, 0.05, 0.15, 0.30):
    E.EPS = eps
    sweep_eps[eps], _ = ndcg_dev(lokal, glob)
    print("  EPS=%.2f  nDCG(dev)=%.4f" % (eps, sweep_eps[eps]))
E.EPS = BASELINE_EPS
hasil["sweep_eps_dev"] = sweep_eps

# --- keputusan final (lihat docs/keputusan.md untuk alasan lengkap tiap satu) ---
# NGRAM=3 unggul di rata-rata (+0.0091) tapi CI95 bootstrap [0.0007, 0.0209]
# nyaris menyentuh nol dan p=0.0686 (tidak signifikan pada ambang 0.05 baku) --
# pada 100 query, ini derau, bukan sinyal yang cukup kuat untuk menimpa nilai
# yang sudah dipakai di seluruh kode dan pengujian. K_RRF dan EPS bahkan lebih
# datar (selisih <=0.0018 di seluruh titik). Ketiganya dipertahankan.
FINAL_NGRAM, FINAL_K_RRF, FINAL_EPS = BASELINE_NGRAM, BASELINE_K_RRF, BASELINE_EPS
hasil["final_ngram"] = FINAL_NGRAM
hasil["final_k_rrf"] = FINAL_K_RRF
hasil["final_eps"] = FINAL_EPS

# --- sapuan ALPHA ulang pada kombinasi NGRAM/K_RRF/EPS final ---
set_ngram(FINAL_NGRAM)
E.K_RRF = FINAL_K_RRF
E.EPS = FINAL_EPS
lokal, glob, _ = E.bangun(rec)

sweep_alpha = {}
for a in (0.0, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.45, 0.65, 1.0):
    E.ALPHA = a
    sweep_alpha[a], _ = ndcg_dev(lokal, glob)
    print("  ALPHA=%.2f  nDCG(dev)=%.4f" % (a, sweep_alpha[a]))
E.ALPHA = BASELINE_ALPHA
hasil["sweep_alpha_ulang_dev"] = sweep_alpha

terbaik = max(sweep_alpha.items(), key=lambda kv: kv[1])
print("\nargmax ALPHA pada kombinasi final: ALPHA=%.2f  nDCG=%.4f" % terbaik)
hasil["alpha_argmax_ulang"] = terbaik[0]
hasil["alpha_final"] = 0.20

json.dump(hasil, open(os.path.join(OUT, "sapuan_dev.json"), "w", encoding="utf-8"),
          indent=1, ensure_ascii=False)
print("\nselesai ->", os.path.join(OUT, "sapuan_dev.json"))
