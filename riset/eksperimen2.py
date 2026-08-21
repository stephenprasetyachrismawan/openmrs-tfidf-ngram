# -*- coding: utf-8 -*-
"""Eksperimen 2 - Pencarian terpadu LINTAS-ENTITAS pada OpenMRS.
Menguji: TF-IDF hibrida kata-karakter (fusi tingkat 1)
         + Weighted Reciprocal Rank Fusion antar entitas (fusi tingkat 2).
Data: demo data resmi OpenMRS Reference Application (MariaDB, Docker lokal).
"""
import json, math, os, re, random, time, bisect, collections

DIR  = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(DIR, "data")
OUT  = os.path.join(DIR, "hasil3")
os.makedirs(OUT, exist_ok=True)
ENT  = ["konsep", "obat", "pasien", "form", "lokasi", "provider"]
SEED = 42
ALPHA, NGRAM, K_RRF, EPS = 0.20, 4, 20, 0.05

_re_non = re.compile(r"[^a-z0-9]+")
_re_sp  = re.compile(r"\s+")

def norm(s):
    if not s: return ""
    return _re_sp.sub(" ", _re_non.sub(" ", str(s).lower())).strip()

def words(s):
    n = norm(s)
    return n.split() if n else []

def _grams_impl(s, k):
    t = norm(s).replace(" ", "_")
    if not t: return []
    if len(t) < k: return [t]
    return [t[i:i + k] for i in range(len(t) - k + 1)]

def grams(s, k=NGRAM):
    return _grams_impl(s, k)

# ---------------------------------------------------------------- pemuatan
def _lst(x):
    if x is None: return []
    if isinstance(x, list): return [i for i in x if i]
    return [x]

def muat():
    rec = {}
    for line in open(os.path.join(DATA, "konsep.jsonl"), encoding="utf-8"):
        line = line.strip()
        if not line: continue
        o = json.loads(line)
        rid = "konsep:%s" % o["id"]
        nama = o.get("nama") or (_lst(o.get("pref")) or [""])[0]
        if not nama: continue
        alias = [a for a in _lst(o.get("sinonim")) + _lst(o.get("pref")) if a != nama]
        kode, refs = [], set()
        for k in _lst(o.get("kode")):
            if isinstance(k, dict):
                if k.get("kode"): kode.append(str(k["kode"]))
                if k.get("sumber") and k.get("sumber") != "CIEL" and k.get("kode"):
                    refs.add("%s|%s" % (k["sumber"], k["kode"]))
        rec[rid] = dict(id=rid, entitas="konsep", judul=nama, alias=alias, kode=kode,
                        konteks=o.get("deskripsi") or "", kelas=o.get("kelas") or "",
                        refs=refs, tautan=None, n_obs=o.get("n_obs") or 0)
    for fn in ("obat.jsonl", "pasien.jsonl", "lain.jsonl"):
        p = os.path.join(DATA, fn)
        if not os.path.exists(p): continue
        for line in open(p, encoding="utf-8-sig"):
            line = line.strip()
            if not line: continue
            o = json.loads(line)
            if not o.get("judul"): continue
            rec[o["id"]] = dict(id=o["id"], entitas=o["entitas"], judul=o["judul"],
                                alias=_lst(o.get("alias")), kode=[str(k) for k in _lst(o.get("kode"))],
                                konteks=o.get("konteks") or "", kelas="", refs=set(),
                                tautan=o.get("tautan_konsep"), n_obs=0)
    return rec

# ------------------------------------------------------- indeks terbalik
class Indeks(object):
    """Indeks terbalik ltc atas 'surface form' (nama / sinonim / kode)."""
    def __init__(self, teks, pemilik, tok):
        self.tok, self.pemilik, self.teks = tok, pemilik, teks
        N = len(teks)
        df = collections.Counter()
        tokenized = []
        for t in teks:
            ts = tok(t)
            tokenized.append(ts)
            df.update(set(ts))
        self.idf = {t: math.log(N / d) + 1.0 for t, d in df.items()}
        self.post = collections.defaultdict(list)
        self.dl, self.avgdl = [], 0.0
        for i, ts in enumerate(tokenized):
            tf = collections.Counter(ts)
            self.dl.append(len(ts))
            v = {t: (1 + math.log(c)) * self.idf[t] for t, c in tf.items()}
            nrm = math.sqrt(sum(x * x for x in v.values())) or 1.0
            for t, w in v.items():
                self.post[t].append((i, w / nrm))
        self.avgdl = (sum(self.dl) / len(self.dl)) if self.dl else 1.0
        self.tf = [collections.Counter(ts) for ts in tokenized]
        self.df = df
        self.N = N
        self.vocab = sorted(df.keys())

    def _qvec(self, q):
        tf = collections.Counter(self.tok(q))
        v = {t: (1 + math.log(c)) * self.idf[t] for t, c in tf.items() if t in self.idf}
        nrm = math.sqrt(sum(x * x for x in v.values())) or 1.0
        return {t: w / nrm for t, w in v.items()}

    def cosine(self, q):
        """kembalikan dict id_record -> skor cosine maksimum atas surface form-nya"""
        qv = self._qvec(q)
        acc = collections.defaultdict(float)
        for t, qw in qv.items():
            for i, dw in self.post[t]:
                acc[i] += qw * dw
        best = {}
        for i, s in acc.items():
            o = self.pemilik[i]
            if s > best.get(o, 0.0): best[o] = s
        return best

    def bm25(self, q, k1=1.2, b=0.75):
        qt = [t for t in self.tok(q) if t in self.df]
        acc = collections.defaultdict(float)
        for t in qt:
            idf = math.log(1 + (self.N - self.df[t] + 0.5) / (self.df[t] + 0.5))
            for i, _ in self.post[t]:
                f = self.tf[i][t]
                acc[i] += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * self.dl[i] / self.avgdl))
        best = {}
        for i, s in acc.items():
            o = self.pemilik[i]
            if s > best.get(o, 0.0): best[o] = s
        return best

    def prefiks(self, w):
        """indeks surface form yang punya kata berawalan w"""
        lo = bisect.bisect_left(self.vocab, w)
        out = set()
        while lo < len(self.vocab) and self.vocab[lo].startswith(w):
            for i, _ in self.post[self.vocab[lo]]: out.add(i)
            lo += 1
        return out

# --------------------------------------------------------- pembangunan
def bentuk_form(r):
    out = [r["judul"]] + list(r["alias"]) + list(r["kode"])
    return [f for f in out if f and norm(f)]

def bangun(rec):
    t0 = time.time()
    lokal, glob_t, glob_o = {}, [], []
    for e in ENT:
        teks, pem, utama = [], [], []
        for r in rec.values():
            if r["entitas"] != e: continue
            for j, f in enumerate(bentuk_form(r)):
                teks.append(f); pem.append(r["id"]); utama.append(j == 0)
        if not teks: continue
        lokal[e] = dict(W=Indeks(teks, pem, words), G=Indeks(teks, pem, grams),
                        teks=teks, pem=pem, utama=utama)
        glob_t += teks; glob_o += pem
    glob = dict(W=Indeks(glob_t, glob_o, words), G=Indeks(glob_t, glob_o, grams))
    return lokal, glob, time.time() - t0

def fusi1(idx, q):
    """fusi tingkat 1: alpha * kata + (1-alpha) * gram, di dalam satu entitas"""
    a, b = idx["W"].cosine(q), idx["G"].cosine(q)
    out = {}
    for k in sorted(set(a) | set(b)):
        s = ALPHA * a.get(k, 0.0) + (1 - ALPHA) * b.get(k, 0.0)
        if s > 1e-6: out[k] = s
    return out

def heuristik_openmrs(idx, q):
    """reimplementasi skema skor OpenMRS/OCL, dijalankan per entitas"""
    qw = words(q)
    if not qw: return {}
    kand = None
    for t in qw:
        s = idx["W"].prefiks(t)
        kand = s if kand is None else (kand & s)
        if not kand: return {}
    nq = norm(q)
    best = {}
    for i in kand:
        teks = idx["teks"][i]; nt = norm(teks); tw = nt.split()
        sc = 0.0
        if nt == nq: sc += 1000.0
        if idx["utama"][i]: sc += 500.0
        for t in qw:
            sc += 200.0 if t in tw else 100.0
        sc -= len(nt) * 0.6
        o = idx["pem"][i]
        if sc > best.get(o, -1e9): best[o] = sc
    return best

# ------------------------------------------------------------- sistem
def bobot_koleksi(glob, rec, q):
    a, b = glob["W"].cosine(q), glob["G"].cosine(q)
    g = dict((e, 0.0) for e in ENT)
    for k in sorted(set(a) | set(b)):
        s = ALPHA * a.get(k, 0.0) + (1 - ALPHA) * b.get(k, 0.0)
        e = rec[k]["entitas"]
        if s > g[e]: g[e] = s
    tot = sum(g.values())
    if tot <= 0: return dict((e, 1.0) for e in ENT), g
    return dict((e, EPS + (1 - EPS) * g[e] / tot) for e in ENT), g

def cari(sistem, q, lokal, glob, rec, topk=10):
    per = {}
    for e, idx in lokal.items():
        if sistem == "B0":   per[e] = heuristik_openmrs(idx, q)
        elif sistem == "B1": per[e] = idx["W"].cosine(q)
        elif sistem == "B2": per[e] = idx["W"].bm25(q)
        else:                per[e] = fusi1(idx, q)
    if sistem in ("B0", "B1", "B2", "E1"):
        semua = [(k, v) for e in ENT if e in per
                 for k, v in per[e].items() if v > 1e-6]
    else:
        if sistem == "E2":
            w = dict((e, 1.0) for e in ENT)
        else:
            w, _ = bobot_koleksi(glob, rec, q)
        semua = []
        for e in ENT:
            if e not in per: continue
            urut = sorted(per[e].items(), key=lambda kv: (-kv[1], kv[0]))
            for r, (k, v) in enumerate(urut):
                if v <= 1e-6: continue
                semua.append((k, w[e] * 1.0 / (K_RRF + r + 1)))
    # tie-break deterministik menurut id -> hasil dapat direproduksi persis
    semua.sort(key=lambda kv: (-kv[1], kv[0]))
    return semua[:topk]

def cari_e4(q, lokal, glob, rec, topk=10):
    """E3 + pseudo-relevance feedback memakai sinonim & kode hasil pass pertama"""
    awal = cari("E3", q, lokal, glob, rec, topk=5)
    tam = []
    for k, _ in awal:
        r = rec[k]
        for a in list(r["alias"])[:3]: tam += words(a)[:4]
        for c in list(r["kode"])[:2]:  tam += words(c)[:2]
    tam = tam[:30]
    q2 = q + " " + " ".join(tam) if tam else q
    return cari("E3", q2, lokal, glob, rec, topk=topk)

SISTEM = [("B0", "Heuristik OpenMRS + gabung skor"),
          ("B1", "TF-IDF kata + gabung skor"),
          ("B2", "BM25 + gabung skor"),
          ("E1", "TF-IDF hibrida kata+4gram + gabung skor"),
          ("E2", "E1 + RRF baku"),
          ("E3", "E1 + Weighted RRF  (usulan)"),
          ("E4", "E3 + query expansion (PRF)")]

def jalankan(sistem, q, lokal, glob, rec):
    if sistem == "E4": return cari_e4(q, lokal, glob, rec)
    return cari(sistem, q, lokal, glob, rec)

# --------------------------------------------------- query & relevansi
KLINIS = {"Diagnosis", "Symptom", "Finding", "Symptom/Finding", "Procedure",
          "Test", "Anatomy", "Drug"}

def degradasi(nama, tipe, rnd):
    w = words(nama)
    if not w: return None, None
    if tipe == "persis":     return " ".join(w), "persis"
    if tipe == "urut_balik":
        if len(w) < 2: return None, None
        return " ".join(reversed(w)), "urut_balik"
    if tipe == "hilang_kata":
        if len(w) < 2: return None, None
        i = rnd.randrange(len(w)); return " ".join(w[:i] + w[i + 1:]), "hilang_kata"
    if tipe == "trunkasi":
        out = [x[:rnd.choice([4, 5])] if len(x) > 5 else x for x in w]
        if out == w: return None, None
        return " ".join(out), "trunkasi"
    if tipe == "typo":
        cand = [i for i, x in enumerate(w) if len(x) >= 5]
        if not cand: return None, None
        i = rnd.choice(cand); x = list(w[i]); j = rnd.randrange(1, len(x) - 1)
        if rnd.random() < 0.5: del x[j]
        else: x[j], x[j + 1] = x[j + 1], x[j]
        w = list(w); w[i] = "".join(x); return " ".join(w), "typo"
    return None, None

def bangun_query(rec, rnd):
    ref2c = collections.defaultdict(set)
    for r in rec.values():
        for t in r["refs"]: ref2c[t].add(r["id"])
    tautan2obat = collections.defaultdict(set)
    for r in rec.values():
        if r["entitas"] == "obat" and r["tautan"]:
            tautan2obat[int(r["tautan"])].add(r["id"])
    byent = collections.defaultdict(list)
    for r in rec.values(): byent[r["entitas"]].append(r)
    for e in byent: byent[e].sort(key=lambda r: r["id"])

    def gold(r):
        g = {r["id"]: 2}
        if r["entitas"] == "konsep":
            cid = int(r["id"].split(":")[1])
            for d in tautan2obat.get(cid, ()): g.setdefault(d, 1)
            for t in r["refs"]:
                for c in ref2c[t]:
                    if c != r["id"]: g.setdefault(c, 1)
        elif r["entitas"] == "obat" and r["tautan"]:
            k = "konsep:%s" % r["tautan"]
            if k in rec: g.setdefault(k, 1)
            for d in tautan2obat.get(int(r["tautan"]), ()):
                if d != r["id"]: g.setdefault(d, 1)
        return g

    rencana = [("konsep", 110), ("obat", 80), ("pasien", 60),
               ("lokasi", 40), ("form", 10), ("provider", 6)]
    tipe_siklus = ["persis", "typo", "trunkasi", "hilang_kata", "urut_balik"]
    qs, n = [], 0
    for e, jml in rencana:
        pool = byent.get(e, [])
        if e == "konsep":
            pool = [r for r in pool if r["kelas"] in KLINIS and len(words(r["judul"])) >= 1]
        if not pool: continue
        rnd.shuffle(pool)
        amb = 0
        for r in pool:
            if amb >= jml: break
            t = tipe_siklus[n % len(tipe_siklus)]
            q, tt = degradasi(r["judul"], t, rnd)
            if not q or len(q) < 3: continue
            g = gold(r)
            qs.append(dict(qid=len(qs), q=q, tipe=tt, entitas_target=e,
                           seed=r["id"], rel=g))
            amb += 1; n += 1
    return qs

# ------------------------------------------------------------- metrik
def metrik(hasil, rel):
    ids = [k for k, _ in hasil]
    R = len(rel)
    g = lambda i: rel.get(i, 0)
    p1 = 1.0 if ids and g(ids[0]) > 0 else 0.0
    h5 = sum(1 for i in ids[:5] if g(i) > 0)
    h10 = sum(1 for i in ids[:10] if g(i) > 0)
    mrr = 0.0
    for r, i in enumerate(ids[:10]):
        if g(i) > 0: mrr = 1.0 / (r + 1); break
    ap, c = 0.0, 0
    for r, i in enumerate(ids[:10]):
        if g(i) > 0:
            c += 1; ap += c / (r + 1.0)
    ap = ap / R if R else 0.0
    dcg = sum((2 ** g(i) - 1) / math.log2(r + 2) for r, i in enumerate(ids[:10]))
    ideal = sorted(rel.values(), reverse=True)[:10]
    idcg = sum((2 ** v - 1) / math.log2(r + 2) for r, v in enumerate(ideal))
    return dict(p1=p1, p5=h5 / 5.0, r10=(h10 / R if R else 0.0), mrr=mrr,
                map=ap, ndcg=(dcg / idcg if idcg else 0.0),
                kosong=1.0 if not ids else 0.0)

def bootstrap(a, b, n=5000, seed=7):
    """paired bootstrap atas selisih rata-rata (a - b)"""
    rnd = random.Random(seed)
    m = len(a)
    d = [a[i] - b[i] for i in range(m)]
    obs = sum(d) / m
    sam = []
    for _ in range(n):
        s = 0.0
        for _ in range(m): s += d[rnd.randrange(m)]
        sam.append(s / m)
    sam.sort()
    lo, hi = sam[int(0.025 * n)], sam[int(0.975 * n) - 1]
    pusat = [x - obs for x in sam]
    p = sum(1 for x in pusat if abs(x) >= abs(obs)) / float(n)
    return obs, lo, hi, p

# --------------------------------------------------------------- main
def main():
    rnd = random.Random(SEED)
    print("memuat korpus ...")
    rec = muat()
    hit = collections.Counter(r["entitas"] for r in rec.values())
    print("  dokumen:", sum(hit.values()), dict(hit))
    lokal, glob, t_idx = bangun(rec)
    nform = sum(len(lokal[e]["teks"]) for e in lokal)
    print("  surface form: %d | waktu indeks: %.2f detik" % (nform, t_idx))

    qs = bangun_query(rec, rnd)
    rnd.shuffle(qs)
    dev, test = qs[:100], qs[100:]
    print("  query: %d (dev %d / test %d)" % (len(qs), len(dev), len(test)))

    per_query, ringkas, lat = {}, {}, {}
    for kode, nama in SISTEM:
        t0 = time.time(); baris = []
        for it in test:
            h = jalankan(kode, it["q"], lokal, glob, rec)
            m = metrik(h, it["rel"])
            m["top"] = [k for k, _ in h[:5]]
            m["ent5"] = [rec[k]["entitas"] for k, _ in h[:5]]
            baris.append(m)
        dt = (time.time() - t0) / len(test) * 1000.0
        per_query[kode] = baris; lat[kode] = dt
        agg = dict((k, sum(b[k] for b in baris) / len(baris))
                   for k in ("p1", "p5", "r10", "mrr", "map", "ndcg", "kosong"))
        agg["lat_ms"] = dt
        ringkas[kode] = agg
        print("  %-3s %-42s nDCG=%.3f  MRR=%.3f  %.1f ms" %
              (kode, nama, agg["ndcg"], agg["mrr"], dt))

    # uji signifikansi terhadap B0 dan terhadap E2 (RRF baku)
    uji = {}
    for kode, _ in SISTEM:
        if kode == "B0": continue
        a = [b["ndcg"] for b in per_query[kode]]
        for basis in ("B0", "E2"):
            if kode == basis: continue
            b = [x["ndcg"] for x in per_query[basis]]
            uji["%s_vs_%s" % (kode, basis)] = bootstrap(a, b)
    # pasangan tambahan untuk proposal (bootstrap seed=7, top-10 penuh)
    for a, b in (("E3", "E1"), ("E1", "B1")):
        va = [x["ndcg"] for x in per_query[a]]
        vb = [x["ndcg"] for x in per_query[b]]
        uji["%s_vs_%s" % (a, b)] = bootstrap(va, vb)

    ndcg_test = dict((kode, [b["ndcg"] for b in per_query[kode]]) for kode, _ in SISTEM)

    # rincian per jenis degradasi dan per entitas target
    def rinci(kunci):
        out = {}
        for kode, _ in SISTEM:
            d = collections.defaultdict(list)
            for it, m in zip(test, per_query[kode]):
                d[it[kunci]].append(m["ndcg"])
            out[kode] = dict((k, sum(v) / len(v)) for k, v in d.items())
        return out
    per_tipe = rinci("tipe")
    per_ent = rinci("entitas_target")

    # distribusi entitas pada top-5 (bukti bias koleksi kecil)
    dist = {}
    for kode, _ in SISTEM:
        c = collections.Counter()
        for m in per_query[kode]: c.update(m["ent5"])
        tot = sum(c.values()) or 1
        dist[kode] = dict((e, c.get(e, 0) / tot) for e in ENT)

    hasil = dict(dokumen=dict(hit), surface_form=nform, waktu_indeks=t_idx,
                 n_query=len(qs), n_test=len(test), ringkas=ringkas, uji=uji,
                 ndcg_test=ndcg_test, bootstrap_seed=7,
                 per_tipe=per_tipe, per_entitas=per_ent, distribusi_top5=dist,
                 param=dict(alpha=ALPHA, ngram=NGRAM, k_rrf=K_RRF, eps=EPS, seed=SEED))
    json.dump(hasil, open(os.path.join(OUT, "hasil.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    json.dump([dict(q=it["q"], tipe=it["tipe"], ent=it["entitas_target"],
                    seed=it["seed"], rel=it["rel"],
                    hasil=dict((k, per_query[k][i]["top"]) for k, _ in SISTEM))
               for i, it in enumerate(test)],
              open(os.path.join(OUT, "per_query.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    with open(os.path.join(OUT, "ringkasan.csv"), "w", encoding="utf-8") as f:
        f.write("sistem,P@1,P@5,R@10,MRR,MAP,nDCG@10,kosong,latensi_ms\n")
        for kode, _ in SISTEM:
            a = ringkas[kode]
            f.write("%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f\n" %
                    (kode, a["p1"], a["p5"], a["r10"], a["mrr"], a["map"],
                     a["ndcg"], a["kosong"], a["lat_ms"]))
    print("\nselesai. hasil di:", OUT)
    return hasil

if __name__ == "__main__":
    main()
