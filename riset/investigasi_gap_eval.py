# -*- coding: utf-8 -*-
"""Investigasi selisih eval Java vs Python (dev 100, E3). Hanya qs[:100]."""
import base64
import hashlib
import json
import os
import random
import subprocess
import urllib.parse
import urllib.request

import eksperimen2 as E

DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(DIR, "hasil3", "investigasi_gap_eval.json")
AUTH = base64.b64encode(b"admin:Admin123").decode()


def sha256_text(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def canonical_dev_queries(dev):
    rows = []
    for i, it in enumerate(dev):
        rel = dict(sorted(it["rel"].items()))
        rows.append({
            "i": i,
            "q": it["q"],
            "seed": it["seed"],
            "entitas_target": it["entitas_target"],
            "tipe": it["tipe"],
            "rel": rel,
        })
    return sha256_text(json.dumps(rows, ensure_ascii=False, separators=(",", ":")))


def rest_get(path):
    req = urllib.request.Request("http://127.0.0.1" + path)
    req.add_header("Authorization", "Basic " + AUTH)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


def java_search_keys(q, limit=10):
    qs = urllib.parse.urlencode({"q": q, "mode": "e3", "limit": str(limit)})
    data = rest_get("/openmrs/ws/rest/v1/unifiedsearch?" + qs)
    keys = []
    for hit in data.get("results") or []:
        keys.append("%s:%s" % (hit["entitas"], hit["id"]))
    return keys


def gold_java_style(rec):
    tautan2obat = {}
    for r in rec.values():
        if r["entitas"] == "obat" and r["tautan"]:
            tautan2obat.setdefault(int(r["tautan"]), set()).add(r["id"])

    def gold(r):
        g = {r["id"]: 2}
        if r["entitas"] == "konsep":
            cid = int(r["id"].split(":")[1])
            for d in tautan2obat.get(cid, ()):
                g.setdefault(d, 1)
        elif r["entitas"] == "obat" and r["tautan"]:
            k = "konsep:%s" % r["tautan"]
            if k in rec:
                g.setdefault(k, 1)
            for d in tautan2obat.get(int(r["tautan"]), ()):
                if d != r["id"]:
                    g.setdefault(d, 1)
        return g

    return gold


def docker_sql_counts():
    sql = """
SELECT 'konsep' AS e, COUNT(*) FROM concept WHERE retired=0
UNION ALL SELECT 'obat', COUNT(*) FROM drug WHERE retired=0
UNION ALL SELECT 'pasien', COUNT(*) FROM patient p JOIN person pe ON pe.person_id=p.patient_id WHERE p.voided=0
UNION ALL SELECT 'form', COUNT(*) FROM form WHERE retired=0
UNION ALL SELECT 'lokasi', COUNT(*) FROM location WHERE retired=0
UNION ALL SELECT 'provider', COUNT(*) FROM provider WHERE retired=0;
"""
    cmd = [
        "docker", "exec", "openmrs-distro-referenceapplication-db-1",
        "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql,
    ]
    out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True)
    counts = {}
    for line in out.strip().splitlines():
        ent, n = line.split("\t")
        counts[ent] = int(n)
    return counts


def docker_virtual_doc_counts():
    """Approximate virtual-document counts from live DB (ConceptSource rules)."""
    sql = """
SELECT 'konsep' AS e, COUNT(*) FROM (
  SELECT c.concept_id FROM concept c WHERE c.retired=0
) x
UNION ALL SELECT 'obat', COUNT(*) FROM drug WHERE retired=0
UNION ALL SELECT 'pasien', COUNT(*) FROM patient WHERE voided=0
UNION ALL SELECT 'form', COUNT(*) FROM form WHERE retired=0
UNION ALL SELECT 'lokasi', COUNT(*) FROM location WHERE retired=0
UNION ALL SELECT 'provider', COUNT(*) FROM provider WHERE retired=0;
"""
    cmd = [
        "docker", "exec", "openmrs-distro-referenceapplication-db-1",
        "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql,
    ]
    out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True)
    return {ent: int(n) for ent, n in (line.split("\t") for line in out.strip().splitlines())}


def openmrs_runtime_params():
    sql = "SELECT property, property_value FROM global_property WHERE property='unifiedsearch.alpha';"
    cmd = [
        "docker", "exec", "openmrs-distro-referenceapplication-db-1",
        "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql,
    ]
    alpha_gp = None
    try:
        out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True).strip()
        if out:
            alpha_gp = out.split("\t", 1)[-1]
    except subprocess.CalledProcessError:
        pass
    return {
        "ALPHA_global_property_raw": alpha_gp,
        "ALPHA_effective_java": float(alpha_gp) if alpha_gp else 0.20,
        "NGRAM_java_constant": 4,
        "K_RRF_java_constant": 20,
        "EPS_java_constant": 0.05,
        "score_floor_both": 1e-6,
        "python_E_ALPHA": E.ALPHA,
        "python_E_NGRAM": E.NGRAM,
        "python_E_K_RRF": E.K_RRF,
        "python_E_EPS": E.EPS,
        "SEED": E.SEED,
    }


def main():
    rnd = random.Random(E.SEED)
    rec = E.muat()
    hit = {}
    for r in rec.values():
        hit[r["entitas"]] = hit.get(r["entitas"], 0) + 1

    qs = E.bangun_query(rec, rnd)
    rnd.shuffle(qs)
    dev = qs[:100]

    lokal, glob, _ = E.bangun(rec)
    g_ja = gold_java_style(rec)

    per = []
    ndcg_py_vec = []
    ndcg_py_on_java_rank_vec = []
    ndcg_ja_gold_py_rank_vec = []

    for it in dev:
        h_py = E.jalankan("E3", it["q"], lokal, glob, rec)
        m_py = E.metrik(h_py, it["rel"])
        ndcg_py_vec.append(m_py["ndcg"])

        rel_ja = g_ja(rec[it["seed"]])
        m_ja_gold = E.metrik(h_py, rel_ja)
        ndcg_ja_gold_py_rank_vec.append(m_ja_gold["ndcg"])

        java_keys = java_search_keys(it["q"], 10)
        m_java_rank = E.metrik([(k, 0) for k in java_keys], it["rel"])
        ndcg_py_on_java_rank_vec.append(m_java_rank["ndcg"])

        per.append({
            "q": it["q"],
            "seed": it["seed"],
            "entitas_target": it["entitas_target"],
            "tipe": it["tipe"],
            "ndcg_python_rank_py_gold": m_py["ndcg"],
            "ndcg_python_rank_ja_gold": m_ja_gold["ndcg"],
            "ndcg_java_rank_py_gold": m_java_rank["ndcg"],
            "delta_java_minus_py": m_java_rank["ndcg"] - m_py["ndcg"],
            "top10_python": [k for k, _ in h_py[:10]],
            "top10_java": java_keys,
        })

    py_mean = sum(ndcg_py_vec) / len(ndcg_py_vec)
    java_rank_py_gold_mean = sum(ndcg_py_on_java_rank_vec) / len(ndcg_py_on_java_rank_vec)

    per_sorted = sorted(per, key=lambda x: x["delta_java_minus_py"])
    worst10 = per_sorted[:10]

    java_live = rest_get("/openmrs/ws/rest/v1/unifiedsearch/eval?mode=e3")

    report = {
        "dev_n_query": len(dev),
        "dev_query_sha256_python_corpus": canonical_dev_queries(dev),
        "python_E3_ndcg_mean_py_gold": py_mean,
        "sapuan_dev_reference": 0.8464038215755585,
        "java_live_eval_aggregate": {
            "ndcg10": java_live.get("ndcg10"),
            "p1": java_live.get("p1"),
            "n_query": java_live.get("n_query"),
            "gold_field": java_live.get("gold"),
        },
        "java_rank_on_python_queries_py_gold_mean": java_rank_py_gold_mean,
        "java_style_gold_python_rank_mean": sum(ndcg_ja_gold_py_rank_vec) / len(ndcg_ja_gold_py_rank_vec),
        "corpus": {
            "python_jsonl_virtual_docs": hit,
            "python_total": sum(hit.values()),
            "openmrs_sql_table_rows": docker_sql_counts(),
            "openmrs_virtual_doc_approx": docker_virtual_doc_counts(),
        },
        "runtime_params": openmrs_runtime_params(),
        "top10_largest_java_minus_python_ndcg": worst10,
        "per_query": per,
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print("=== MASALAH 1: investigasi gap eval ===")
    print("n dev query:", len(dev))
    print("SHA256 dev queries (Python corpus):", report["dev_query_sha256_python_corpus"])
    print("Python E3 mean nDCG (py gold, py rank):", py_mean)
    print("Reference sapuan_dev:", 0.8464038215755585)
    print("Java live /eval ndcg10:", java_live.get("ndcg10"))
    print("Java rank on SAME python queries, py gold mean:", java_rank_py_gold_mean)
    print("Selisih agregat java_rank_py_gold - reference:", java_rank_py_gold_mean - 0.8464038215755585)
    print("Corpus python:", hit)
    print("Corpus SQL rows:", report["corpus"]["openmrs_sql_table_rows"])
    print("Runtime params:", report["runtime_params"])
    print("\n10 query terburuk (Java rank - Python rank, py gold):")
    for row in worst10:
        print("  delta=%+.4f q=%r seed=%s" % (row["delta_java_minus_py"], row["q"], row["seed"]))
        print("    py top3:", row["top10_python"][:3])
        print("    ja top3:", row["top10_java"][:3])
    print("\nFull report:", OUT)


if __name__ == "__main__":
    main()
