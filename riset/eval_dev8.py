# -*- coding: utf-8 -*-
"""Evaluasi seluruh sistem pada korpus 8 entitas -- HANYA himpunan dev.

Kenapa berkas ini ada terpisah dari `eksperimen2.main()`:
`main()` mengevaluasi `qs[100:]` (himpunan uji), dan CLAUDE.md aturan 10
mengizinkan itu dijalankan **sekali saja** setelah parameter dikunci.
Setelah pipeline diseragamkan ke 8 entitas, query set-nya baru, jadi
himpunan ujinya juga baru dan sekali-jalan lagi.

Berkas ini menjalankan seluruh sistem pada `qs[:100]` saja, supaya angka
8-entitas bisa dilihat dan dibandingkan tanpa membakar jatah sekali-jalan itu.
Ada penjagaan eksplisit di bawah: kalau ada yang mengubahnya jadi menyentuh
`qs[100:]`, skrip berhenti.

Keluaran: riset/hasil6/dev8.json + dev8.csv
"""
import collections, json, os, random, time

import eksperimen2 as E

OUT = os.path.join(E.DIR, "hasil6")
os.makedirs(OUT, exist_ok=True)

N_DEV = 100


def main():
    print("korpus 8 entitas — patokan: DocumentRepository.ENTITAS di modul Java")
    print("  ENT:", E.ENT)

    rec = E.muat()
    hit = collections.Counter(r["entitas"] for r in rec.values())
    print("  dokumen: %d %s" % (sum(hit.values()), dict(hit)))

    lokal, glob, t_idx = E.bangun(rec)
    nform = sum(len(lokal[e]["teks"]) for e in lokal)
    n_indeks = len(lokal) * 2 + 1
    print("  surface form: %d | indeks: %d | waktu: %.2f detik"
          % (nform, n_indeks, t_idx))

    rnd = random.Random(E.SEED)
    qs = E.bangun_query(rec, rnd)
    rnd.shuffle(qs)
    dev, test = qs[:N_DEV], qs[N_DEV:]
    print("  query: %d (dev %d / test %d)" % (len(qs), len(dev), len(test)))

    # --- penjagaan aturan 10 -------------------------------------------------
    # Sengaja dihapus dari memori supaya tidak bisa dipakai tanpa sengaja.
    n_test = len(test)
    del test

    per_query, ringkas = {}, {}
    for kode, nama in E.SISTEM:
        t0 = time.time()
        baris = []
        for it in dev:
            h = E.jalankan(kode, it["q"], lokal, glob, rec)
            m = E.metrik(h, it["rel"])
            m["ent5"] = [rec[k]["entitas"] for k, _ in h[:5]]
            baris.append(m)
        dt = (time.time() - t0) / len(dev) * 1000.0
        per_query[kode] = baris
        agg = dict((k, sum(b[k] for b in baris) / len(baris))
                   for k in ("p1", "p5", "r10", "mrr", "map", "ndcg", "kosong"))
        agg["lat_ms"] = dt
        ringkas[kode] = agg
        print("  %-3s %-42s nDCG=%.4f  P@1=%.3f  %.2f ms"
              % (kode, nama, agg["ndcg"], agg["p1"], dt))

    # uji signifikansi, pasangan yang sama seperti laporan utama
    uji = {}
    for a, b in (("E1", "B0"), ("E3", "B0"), ("E3", "E1"), ("E1", "B1"),
                 ("B1", "B0"), ("B2", "B0"), ("E2", "B0"), ("E4", "B0")):
        va = [x["ndcg"] for x in per_query[a]]
        vb = [x["ndcg"] for x in per_query[b]]
        uji["%s_vs_%s" % (a, b)] = E.bootstrap(va, vb)

    def rinci(kunci):
        out = {}
        for kode, _ in E.SISTEM:
            d = collections.defaultdict(list)
            for it, m in zip(dev, per_query[kode]):
                d[it[kunci]].append(m["ndcg"])
            out[kode] = dict((k, sum(v) / len(v)) for k, v in sorted(d.items()))
        return out

    dist = {}
    for kode, _ in E.SISTEM:
        c = collections.Counter()
        for m in per_query[kode]:
            c.update(m["ent5"])
        tot = sum(c.values()) or 1
        dist[kode] = dict((e, c.get(e, 0) / tot) for e in E.ENT)

    hasil = dict(
        catatan="korpus 8 entitas (seragam dengan modul Java); HANYA qs[:100] (dev)",
        entitas=list(E.ENT), dokumen=dict(hit), surface_form=nform,
        n_indeks=n_indeks, waktu_indeks=t_idx,
        n_query=len(qs), n_dev=len(dev), n_test_tidak_dijalankan=n_test,
        ringkas=ringkas, uji=uji, bootstrap_seed=7,
        per_tipe=rinci("tipe"), per_entitas=rinci("entitas_target"),
        distribusi_top5=dist,
        param=dict(alpha=E.ALPHA, ngram=E.NGRAM, k_rrf=E.K_RRF, eps=E.EPS,
                   seed=E.SEED))
    json.dump(hasil, open(os.path.join(OUT, "dev8.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)

    with open(os.path.join(OUT, "dev8.csv"), "w", encoding="utf-8") as f:
        f.write("sistem,P@1,P@5,R@10,MRR,MAP,nDCG@10,kosong,latensi_ms\n")
        for kode, _ in E.SISTEM:
            a = ringkas[kode]
            f.write("%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f\n"
                    % (kode, a["p1"], a["p5"], a["r10"], a["mrr"], a["map"],
                       a["ndcg"], a["kosong"], a["lat_ms"]))

    print("\nselesai. hasil di:", OUT)
    print("himpunan uji (%d query) TIDAK dijalankan — menunggu keputusan." % n_test)
    return hasil


if __name__ == "__main__":
    main()
