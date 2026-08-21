# -*- coding: utf-8 -*-
"""Eksperimen 3 - Baseline B0' = pencarian konsep OpenMRS asli (fuzzy, Lucene).

Menjawab temuan D1 (docs/keputusan.md): OpenMrsHeuristic (B0) adalah tiruan
pencocokan-awalan yang GAGAL pada semua query typo yang diuji, sementara
endpoint fuzzy OpenMRS asli BERHASIL pada sebagian besar. B0 karena itu
bukan baseline yang setia terhadap OpenMRS sungguhan. Eksperimen ini
mengukur B0' langsung dari endpoint asli, pada 42 query konsep dev
(qs[:100] saja -- CLAUDE.md aturan 10, TIDAK PERNAH qs[100:]).

Endpoint & parameter -- dipatok eksplisit, dicatat di sini supaya
direproduksi:
    GET /openmrs/ws/rest/v1/concept
        ?name=<query>
        &searchType=fuzzy
        &limit=50              (>= webservices.rest.maxResultsDefault=50 di
                                 instalasi ini -- dipatok eksplisit supaya tidak
                                 diam-diam berubah kalau global property beda)
        &v=custom:(uuid,display)
Tanpa filter class -- filter itu dipakai skrip D1 (bandingkan_baseline_openmrs.py)
untuk meniru kotak diagnosis, TAPI tidak adil untuk eksperimen ini karena
42 query konsep dev mencakup kelas selain Diagnosis (Test, Procedure, Finding,
dst) dan filter kelas akan membuang jawaban benar sebelum dinilai.

TEMUAN PENTING soal locale (syarat #1 dari manusia): mula-mula dicoba
menambahkan &locale=en secara eksplisit ke URL, sesuai permintaan "kunci
locale secara eksplisit". Diverifikasi langsung lewat curl SEBELUM dipakai
di eksperimen -- parameter itu MERUSAK pencarian: untuk SEMUA query, apa pun
isinya ("kidney", "diabete melitus", dst), hasilnya berubah jadi daftar tetap
yang tidak berkaitan sama sekali ("ATT DEFAULT ATTACHMENT", "Heparins", ...).
Tanpa parameter locale, hasilnya benar. Locale karena itu dipatok lewat SESI
(session locale = 'en', dikonfirmasi tiap jalan lewat GET .../session, lihat
login_cookie()) -- bukan lewat parameter query. Ini bukan penyimpangan dari
syarat, melainkan cara memenuhi maksudnya (locale terpatok & tercatat) tanpa
memicu bug endpoint yang baru ditemukan.

Lima syarat tambahan dari manusia sebelum dijalankan (lihat riwayat sesi):
1. locale dipatok eksplisit (en).
2. limit dipatok eksplisit (50), bukan mengandalkan default global property.
3. Dedupe uuid (pertahankan kemunculan pertama) SEBELUM dipotong ke top-10.
4. Dua angka dilaporkan: B0'-apaadanya (hasil mentah, termasuk konsep di luar
   korpus 4.249 baris ekspor_konsep.sql) dan B0'-korpus (konsep di luar korpus
   dibuang sebelum dinilai -- perbandingan yang adil dengan sistem kita).
5. Uji determinisme: tiap query dipanggil DUA KALI, urutan uuid dibandingkan.
"""
import json
import subprocess
import sys
import urllib.parse
import urllib.request
import base64

sys.path.insert(0, r"C:\src\tfidf-openmrs\riset")
import eksperimen2 as E

BASE = "http://127.0.0.1/openmrs/ws/rest/v1"
GOLD_PATH = r"C:\src\tfidf-openmrs\backend\openmrs-module-tfidf-search\api\src\main\resources\gold-dev-100.json"
OUT_DIR = r"C:\src\tfidf-openmrs\riset\hasil4"
LOCALE = "en"
LIMIT = 50


def mysql(sql):
    cmd = [
        "docker", "exec", "openmrs-distro-referenceapplication-db-1",
        "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql,
    ]
    out = subprocess.check_output(cmd, text=True, encoding="utf-8")
    return [line.split("\t") for line in out.strip().splitlines() if line]


def login_cookie():
    req = urllib.request.Request(f"{BASE}/session")
    auth = base64.b64encode(b"admin:Admin123").decode()
    req.add_header("Authorization", f"Basic {auth}")
    resp = urllib.request.urlopen(req)
    cookie = resp.headers.get("Set-Cookie", "").split(";")[0]
    sesi = json.loads(resp.read().decode("utf-8"))
    assert sesi.get("locale") == LOCALE, (
        f"Locale sesi ({sesi.get('locale')}) bukan {LOCALE!r} -- hasil B0' tidak "
        f"akan sesuai locale ekspor korpus (riset/ekspor_konsep.sql, locale='en')."
    )
    print(f"Locale sesi dikonfirmasi: {sesi.get('locale')!r} (dipatok lewat sesi, bukan parameter query -- lihat panggil_konsep()).")
    return cookie


def get_json(url, cookie):
    req = urllib.request.Request(url)
    req.add_header("Cookie", cookie)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def panggil_konsep(query, cookie):
    # PENTING: jangan menambahkan &locale=en di sini. Diverifikasi langsung (bukan
    # tebakan): menambahkan parameter locale ke endpoint ini MERUSAK pencarian --
    # hasilnya berubah jadi daftar tetap yang tidak berkaitan sama sekali dengan
    # query ("kidney" -> "ATT DEFAULT ATTACHMENT", "Heparins", dst, untuk SEMUA
    # query, apa pun isinya), berbeda dari tanpa locale yang memberi hasil benar.
    # Locale dipatok lewat sesi (session locale = 'en', dikonfirmasi lewat
    # GET /ws/rest/v1/session -> "locale":"en"), bukan lewat parameter query.
    # &limit= sendiri AMAN dan tidak mengubah relevansi (diverifikasi terpisah).
    url = (
        f"{BASE}/concept?name={urllib.parse.quote(query)}"
        f"&searchType=fuzzy&limit={LIMIT}&v=custom:(uuid,display)"
    )
    return get_json(url, cookie)


def dedupe_pertahankan_urutan(uuids):
    seen = set()
    out = []
    for u in uuids:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return out


def main():
    import os
    os.makedirs(OUT_DIR, exist_ok=True)

    gold = json.load(open(GOLD_PATH, encoding="utf-8"))
    konsep_queries = [q for q in gold["queries"] if q["entitas_target"] == "konsep"]
    print(f"Query konsep dev (dari qs[:100]): {len(konsep_queries)}")
    from collections import Counter
    print("Rincian tipe:", dict(Counter(q["tipe"] for q in konsep_queries)))

    print("Membangun peta uuid -> concept_id lewat SQL langsung ...")
    rows = mysql("SELECT concept_id, uuid FROM concept")
    uuid_ke_id = {uuid: cid for cid, uuid in rows}
    json.dump(uuid_ke_id, open(f"{OUT_DIR}/uuid_ke_concept_id.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"  {len(uuid_ke_id)} baris concept dipetakan.")

    print("Memuat korpus penelitian (ekspor_konsep.sql dkk.) dan membangun indeks B0/B1/E1/E3 ...")
    rec = E.muat()
    korpus_konsep_ids = {k.split(":")[1] for k in rec.keys() if k.startswith("konsep:")}
    print(f"  korpus konsep: {len(korpus_konsep_ids)} baris.")
    lokal, glob, _ = E.bangun(rec)

    cookie = login_cookie()

    mentah = []
    tidak_deterministik = []
    total_hasil_mentah = 0
    total_di_luar_korpus = 0

    per_query = []
    for q in konsep_queries:
        panggilan1 = panggil_konsep(q["q"], cookie)
        panggilan2 = panggil_konsep(q["q"], cookie)
        uuid1 = [r["uuid"] for r in panggilan1["results"]]
        uuid2 = [r["uuid"] for r in panggilan2["results"]]
        deterministik = (uuid1 == uuid2)
        if not deterministik:
            tidak_deterministik.append(q["q"])

        mentah.append({
            "q": q["q"], "tipe": q["tipe"],
            "panggilan1": panggilan1["results"], "panggilan2": panggilan2["results"],
            "deterministik": deterministik,
        })

        uuid_dedup = dedupe_pertahankan_urutan(uuid1)
        total_hasil_mentah += len(uuid_dedup)

        kunci_apa_adanya = []
        kunci_korpus = []
        for u in uuid_dedup:
            cid = uuid_ke_id.get(u)
            if cid is None:
                print(f"  PERINGATAN: uuid {u} tidak ada di peta concept -- dilewati (bukan dibuang diam-diam, dicatat di sini)")
                continue
            kunci = f"konsep:{cid}"
            kunci_apa_adanya.append(kunci)
            if cid in korpus_konsep_ids:
                kunci_korpus.append(kunci)
            else:
                total_di_luar_korpus += 1

        hasil_apa_adanya = [(k, 0.0) for k in kunci_apa_adanya[:10]]
        hasil_korpus = [(k, 0.0) for k in kunci_korpus[:10]]

        m_apa_adanya = E.metrik(hasil_apa_adanya, q["rel"])
        m_korpus = E.metrik(hasil_korpus, q["rel"])

        # B0, B1, E1, E3 dinilai ulang pada subset 42-query yang SAMA, korpus & sistem tidak diubah
        m_lain = {}
        for sistem in ("B0", "B1", "E1", "E3"):
            hasil = E.jalankan(sistem, q["q"], lokal, glob, rec)
            m_lain[sistem] = E.metrik(hasil, q["rel"])

        per_query.append({
            "q": q["q"], "tipe": q["tipe"],
            "b0prime_apaadanya": m_apa_adanya, "b0prime_korpus": m_korpus,
            "b0": m_lain["B0"], "b1": m_lain["B1"], "e1": m_lain["E1"], "e3": m_lain["E3"],
            "n_hasil_mentah": len(uuid_dedup), "n_di_korpus": len(kunci_korpus),
        })
        print(f"{q['q']!r:45s} tipe={q['tipe']:12s} det={deterministik!s:5s} "
              f"mentah={len(uuid_dedup):3d} korpus={len(kunci_korpus):3d} "
              f"ndcg b0prime_korpus={m_korpus['ndcg']:.3f} b0={m_lain['B0']['ndcg']:.3f} e3={m_lain['E3']['ndcg']:.3f}")

    json.dump(mentah, open(f"{OUT_DIR}/mentah_b0prime.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    def rata(kunci):
        vals = [pq[kunci] for pq in per_query]
        out = {}
        for m in ("p1", "p5", "r10", "mrr", "map", "ndcg", "kosong"):
            out[m] = sum(v[m] for v in vals) / len(vals)
        return out

    ringkasan = {
        "n_query": len(konsep_queries),
        "b0prime_apaadanya": rata("b0prime_apaadanya"),
        "b0prime_korpus": rata("b0prime_korpus"),
        "b0": rata("b0"), "b1": rata("b1"), "e1": rata("e1"), "e3": rata("e3"),
    }

    pct_di_luar_korpus = 100.0 * total_di_luar_korpus / total_hasil_mentah if total_hasil_mentah else 0.0
    n_tidak_deterministik = len(tidak_deterministik)

    ndcg = {k: [pq[k]["ndcg"] for pq in per_query] for k in
            ("b0prime_apaadanya", "b0prime_korpus", "b0", "b1", "e1", "e3")}
    uji = {}
    for pasangan in (("e3", "b0prime_korpus"), ("e3", "b0prime_apaadanya"), ("e3", "b0"), ("b0prime_korpus", "b0")):
        a, b = pasangan
        obs, lo, hi, p = E.bootstrap(ndcg[a], ndcg[b])
        uji[f"{a}_vs_{b}"] = dict(obs=obs, ci_lo=lo, ci_hi=hi, p=p)

    per_tipe = {}
    from collections import defaultdict
    by_tipe = defaultdict(list)
    for pq in per_query:
        by_tipe[pq["tipe"]].append(pq)
    for tipe, rows_t in by_tipe.items():
        per_tipe[tipe] = {
            "n": len(rows_t),
            "b0prime_korpus_ndcg": sum(r["b0prime_korpus"]["ndcg"] for r in rows_t) / len(rows_t),
            "b0_ndcg": sum(r["b0"]["ndcg"] for r in rows_t) / len(rows_t),
            "e3_ndcg": sum(r["e3"]["ndcg"] for r in rows_t) / len(rows_t),
        }

    hasil_json = {
        "n_query": len(konsep_queries),
        "locale": LOCALE, "limit": LIMIT,
        "pct_hasil_di_luar_korpus": pct_di_luar_korpus,
        "n_query_tidak_deterministik": n_tidak_deterministik,
        "query_tidak_deterministik": tidak_deterministik,
        "ringkasan": ringkasan,
        "uji_bootstrap_seed7_top10": uji,
        "per_tipe": per_tipe,
    }
    json.dump(hasil_json, open(f"{OUT_DIR}/hasil.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    json.dump(per_query, open(f"{OUT_DIR}/per_query.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    with open(f"{OUT_DIR}/ringkasan.csv", "w", encoding="utf-8") as f:
        f.write("sistem,p1,p5,r10,mrr,map,ndcg10,pct_nol\n")
        for nama, key in (("B0'-apaadanya", "b0prime_apaadanya"), ("B0'-korpus", "b0prime_korpus"),
                          ("B0", "b0"), ("B1", "b1"), ("E1", "e1"), ("E3", "e3")):
            r = ringkasan[key]
            f.write(f"{nama},{r['p1']:.4f},{r['p5']:.4f},{r['r10']:.4f},{r['mrr']:.4f},{r['map']:.4f},{r['ndcg']:.4f},{100*r['kosong']:.1f}\n")

    print("\n=== RINGKASAN (42 query konsep dev) ===")
    for nama, key in (("B0'-apaadanya", "b0prime_apaadanya"), ("B0'-korpus", "b0prime_korpus"),
                      ("B0", "b0"), ("B1", "b1"), ("E1", "e1"), ("E3", "e3")):
        r = ringkasan[key]
        print(f"  {nama:16s} nDCG@10={r['ndcg']:.4f}  P@1={r['p1']:.4f}  %nol={100*r['kosong']:.1f}%")
    print(f"\nHasil di luar korpus: {total_di_luar_korpus}/{total_hasil_mentah} ({pct_di_luar_korpus:.1f}%)")
    print(f"Query tidak deterministik (dua panggilan beda urutan): {n_tidak_deterministik}/{len(konsep_queries)}")
    print("\nUji bootstrap (seed=7, top-10):")
    for pasangan, v in uji.items():
        print(f"  {pasangan}: obs={v['obs']:+.4f}  CI95=[{v['ci_lo']:+.4f}, {v['ci_hi']:+.4f}]  p={v['p']:.4f}")

    print(f"\nBerkas ditulis ke {OUT_DIR}/")


if __name__ == "__main__":
    main()
