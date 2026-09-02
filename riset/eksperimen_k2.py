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


# --------------------------------------------------------- jalur peringkat 8 entitas
# Salinan eksperimen2.bangun / bobot_koleksi / cari dengan ENT -> ENT8. Fungsi asli
# mengiterasi eksperimen2.ENT global, jadi harus disalin, bukan dipanggil. Diuji
# setia ke eksperimen2 pada 6 entitas (test_eksperimen_k2.py).

def bangun8(rec):
    t0 = time.time()
    lokal, glob_t, glob_o = {}, [], []
    for e in ENT8:
        teks, pem, utama = [], [], []
        for r in rec.values():
            if r["entitas"] != e:
                continue
            for j, f in enumerate(eksperimen2.bentuk_form(r)):
                teks.append(f)
                pem.append(r["id"])
                utama.append(j == 0)
        if not teks:
            continue
        lokal[e] = dict(
            W=eksperimen2.Indeks(teks, pem, eksperimen2.words),
            G=eksperimen2.Indeks(teks, pem, eksperimen2.grams),
            teks=teks, pem=pem, utama=utama,
        )
        glob_t += teks
        glob_o += pem
    glob = dict(
        W=eksperimen2.Indeks(glob_t, glob_o, eksperimen2.words),
        G=eksperimen2.Indeks(glob_t, glob_o, eksperimen2.grams),
    )
    return lokal, glob, time.time() - t0


def bobot_koleksi8(glob, rec, q):
    a, b = glob["W"].cosine(q), glob["G"].cosine(q)
    g = dict((e, 0.0) for e in ENT8)
    for k in sorted(set(a) | set(b)):
        s = eksperimen2.ALPHA * a.get(k, 0.0) + (1 - eksperimen2.ALPHA) * b.get(k, 0.0)
        e = rec[k]["entitas"]
        if s > g[e]:
            g[e] = s
    tot = sum(g.values())
    if tot <= 0:
        return dict((e, 1.0) for e in ENT8), g
    return dict((e, eksperimen2.EPS + (1 - eksperimen2.EPS) * g[e] / tot) for e in ENT8), g


def cari8(sistem, q, lokal, glob, rec, topk=10):
    """Sama logika eksperimen2.cari() tapi atas ENT8. Hanya B0/B1/E1/E3 dipakai K2."""
    per = {}
    for e, idx in lokal.items():
        if sistem == "B0":
            per[e] = eksperimen2.heuristik_openmrs(idx, q)
        elif sistem == "B1":
            per[e] = idx["W"].cosine(q)
        else:
            per[e] = eksperimen2.fusi1(idx, q)
    if sistem in ("B0", "B1", "E1"):
        semua = [(k, v) for e in ENT8 if e in per
                 for k, v in per[e].items() if v > 1e-6]
    else:
        w, _ = bobot_koleksi8(glob, rec, q)
        semua = []
        for e in ENT8:
            if e not in per:
                continue
            urut = sorted(per[e].items(), key=lambda kv: (-kv[1], kv[0]))
            for r, (k, v) in enumerate(urut):
                if v <= 1e-6:
                    continue
                semua.append((k, w[e] * 1.0 / (eksperimen2.K_RRF + r + 1)))
    semua.sort(key=lambda kv: (-kv[1], kv[0]))
    return semua[:topk]


# ------------------------------------------------------------ reimpl suggester K2

def saran_k2(lokal8, q, limit=LIMIT_SARAN):
    """Reimplementasi BigramJaccardSuggester.search + UnifiedSearchService.saran.

    Skor tiap surface form = Jaccard irisan bigram karakter |A n B| / |A u B|.
    Gerbang: |irisan| >= MIN_IRISAN, KECUALI himpunan bigram query == form persis.
    Terbaik per kunci dokumen; seri skor -> surface form judul menang.
    Urut: (-skor, via_judul dulu, kunci naik). Sama KUNCI_COMPARATOR Java.
    Jalan tanpa filter privilege (indeks penuh); filter = perilaku endpoint,
    dibuktikan di cek_cross_k2.py.
    """
    gq = frozenset(eksperimen2._grams_impl(q, NGRAM_K2))
    if not gq:
        return []
    terbaik = {}  # kunci -> (skor, via_judul)
    for e in ENT8:
        idx = lokal8.get(e)
        if not idx:
            continue
        for teks, pem, utama in zip(idx["teks"], idx["pem"], idx["utama"]):
            gf = frozenset(eksperimen2._grams_impl(teks, NGRAM_K2))
            if not gf:
                continue
            iris = gq & gf
            if not iris:
                continue
            persis = gq == gf
            if len(iris) < MIN_IRISAN and not persis:
                continue
            skor = len(iris) / len(gq | gf)
            prev = terbaik.get(pem)
            if prev is None or skor > prev[0] or (skor == prev[0] and utama and not prev[1]):
                terbaik[pem] = (skor, utama)
    urut = sorted(terbaik.items(), key=lambda kv: (-kv[1][0], not kv[1][1], kv[0]))
    return urut[:limit]


# ---------------------------------------------------------------- query set K2

JENIS_K2 = ["persis", "typo", "trunkasi", "trunkasi_pendek", "typo_pendek"]

# proporsi siklus (spec Bagian 3): typo 25%, trunkasi 20%, trunkasi_pendek 20%,
# typo_pendek 15%, persis 20% -> pola panjang 20
_SIKLUS_K2 = (["typo"] * 5 + ["trunkasi"] * 4 + ["trunkasi_pendek"] * 4
              + ["typo_pendek"] * 3 + ["persis"] * 4)

_RENCANA_K2 = [("konsep", 70), ("obat", 40), ("pasien", 30), ("kondisi", 25),
               ("hasillab", 25), ("lokasi", 15), ("form", 5), ("provider", 5)]


def _sisip_typo(w, rnd):
    """sisip / tukar / gandakan satu huruf di string pendek w (>=3)."""
    x = list(w)
    i = rnd.randrange(len(x))
    aksi = rnd.choice(("gandakan", "tukar", "sisip"))
    if aksi == "gandakan":
        x.insert(i, x[i])
    elif aksi == "tukar" and i + 1 < len(x):
        x[i], x[i + 1] = x[i + 1], x[i]
    else:
        x.insert(i, rnd.choice("aeiourtns"))
    return "".join(x)


def degradasi_k2(nama, jenis, rnd):
    """degradasi() eksperimen2 + 2 jenis pendek. Kembalikan (q, jenis) atau (None, None)."""
    if jenis in ("persis", "typo", "trunkasi"):
        return eksperimen2.degradasi(nama, jenis, rnd)
    kata = eksperimen2.words(nama)
    if not kata:
        return None, None
    if jenis == "trunkasi_pendek":
        pool = [w for w in kata if len(w) > 5] or [w for w in kata if len(w) >= 4]
        if not pool:
            return None, None
        w = pool[0]
        opsi = [c for c in (3, 4, 5) if c < len(w)]
        if not opsi:
            return None, None
        potong = w[:rnd.choice(opsi)]
        if potong == w:
            return None, None
        return potong, "trunkasi_pendek"
    if jenis == "typo_pendek":
        dasar, _ = degradasi_k2(nama, "trunkasi_pendek", rnd)
        if not dasar:
            return None, None
        out = _sisip_typo(dasar, rnd)
        if out == dasar:
            return None, None
        return out, "typo_pendek"
    return None, None


def gold_k2(rec, r):
    """gold() eksperimen2 untuk 6 entitas asli; seed grade-2 saja untuk hasillab/kondisi."""
    if r["entitas"] in ("hasillab", "kondisi"):
        return {r["id"]: 2}
    ref2c = collections.defaultdict(set)
    for x in rec.values():
        for t in x["refs"]:
            ref2c[t].add(x["id"])
    tautan2obat = collections.defaultdict(set)
    for x in rec.values():
        if x["entitas"] == "obat" and x["tautan"]:
            tautan2obat[int(x["tautan"])].add(x["id"])
    g = {r["id"]: 2}
    if r["entitas"] == "konsep":
        cid = int(r["id"].split(":")[1])
        for d in tautan2obat.get(cid, ()):
            g.setdefault(d, 1)
        for t in r["refs"]:
            for c in ref2c[t]:
                if c != r["id"]:
                    g.setdefault(c, 1)
    elif r["entitas"] == "obat" and r["tautan"]:
        k = "konsep:%s" % r["tautan"]
        if k in rec:
            g.setdefault(k, 1)
        for d in tautan2obat.get(int(r["tautan"]), ()):
            if d != r["id"]:
                g.setdefault(d, 1)
    return g


def bangun_query_k2(rec):
    rnd = random.Random(SEED_K2)
    byent = collections.defaultdict(list)
    for r in rec.values():
        byent[r["entitas"]].append(r)
    for e in byent:
        byent[e].sort(key=lambda r: r["id"])
    qs, n = [], 0
    for e, jml in _RENCANA_K2:
        pool = list(byent.get(e, []))
        if e == "konsep":
            pool = [r for r in pool if r["kelas"] in eksperimen2.KLINIS
                    and len(eksperimen2.words(r["judul"])) >= 1]
        if not pool:
            continue
        rnd.shuffle(pool)
        amb = 0
        for r in pool:
            if amb >= jml:
                break
            jenis = _SIKLUS_K2[n % len(_SIKLUS_K2)]
            q, tt = degradasi_k2(r["judul"], jenis, rnd)
            n += 1
            # degradasi() sudah kembalikan (None, None) kalau tak ada yang berubah;
            # untuk 'persis' q memang == judul (itu kontrolnya) -> jangan dibuang.
            if not q or len(q) < 3:
                continue
            if tt != "persis" and q == eksperimen2.norm(r["judul"]):
                continue
            qs.append(dict(qid=len(qs), q=q, jenis=tt, entitas=e,
                           seed=r["id"], rel=gold_k2(rec, r)))
            amb += 1
    return qs


# --------------------------------------------------------- metrik 1: akurasi saran

def metrik_akurasi(saran, rel):
    """hit@{1,3,6}, MRR@6, saran_kosong dari daftar saran vs gold rel."""
    ids = [kunci for kunci, _ in saran]
    rel_di = lambda i: rel.get(i, 0) > 0
    mrr = 0.0
    for peringkat, i in enumerate(ids[:6], start=1):
        if rel_di(i):
            mrr = 1.0 / peringkat
            break
    return {
        "hit@1": 1.0 if ids[:1] and rel_di(ids[0]) else 0.0,
        "hit@3": 1.0 if any(rel_di(i) for i in ids[:3]) else 0.0,
        "hit@6": 1.0 if any(rel_di(i) for i in ids[:6]) else 0.0,
        "mrr@6": mrr,
        "saran_kosong": 1.0 if not ids else 0.0,
    }
