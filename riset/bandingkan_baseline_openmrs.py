# -*- coding: utf-8 -*-
"""Uji kesetiaan OpenMrsHeuristic (mode b0) terhadap pencarian konsep bawaan
OpenMRS yang sungguhan (bukan tiruan) -- disyaratkan D1 (Prompt Lanjutan 2).

Endpoint bawaan ditemukan lewat tab Network sungguhan di browser saat mengetik
di kotak diagnosis Visit Note (bukan dokumentasi/ingatan):

    GET /openmrs/ws/rest/v1/concept
        ?name=<query>
        &searchType=fuzzy
        &class=8d4918b0-c2cc-11de-8d13-0010c6dffd0f   (= concept class "Diagnosis")
        &v=custom:(uuid,display)

Endpoint itu membatasi hasil ke kelas konsep "Diagnosis" saja. Supaya
perbandingan adil, skrip ini hanya memakai query dev (gold-dev-100.json)
yang concept seed-nya BENAR-BENAR berkelas Diagnosis -- bukan Symptom,
Finding, Procedure, Test, Anatomy, atau Drug (yang juga masuk daftar
KLINIS dipakai bangun_query(), tapi tidak akan pernah muncul di kotak
diagnosis bawaan walau query-nya sempurna).

Hanya memakai qs[:100] (dev). Tidak menyentuh qs[100:] (CLAUDE.md aturan 10).
"""
import json
import subprocess
import sys
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1/openmrs/ws/rest/v1"
CLASS_DIAGNOSIS_UUID = "8d4918b0-c2cc-11de-8d13-0010c6dffd0f"
GOLD_PATH = r"C:\src\tfidf-openmrs\backend\openmrs-module-tfidf-search\api\src\main\resources\gold-dev-100.json"


def mysql(sql):
    cmd = [
        "docker", "exec", "openmrs-distro-referenceapplication-db-1",
        "mysql", "-uroot", "-popenmrs", "openmrs", "-N", "-B", "-e", sql,
    ]
    out = subprocess.check_output(cmd, text=True, encoding="utf-8")
    return [line.split("\t") for line in out.strip().splitlines() if line]


def login_cookie():
    import base64
    req = urllib.request.Request(f"{BASE}/session")
    auth = base64.b64encode(b"admin:Admin123").decode()
    req.add_header("Authorization", f"Basic {auth}")
    resp = urllib.request.urlopen(req)
    cookie = resp.headers.get("Set-Cookie", "").split(";")[0]
    return cookie


def get_json(url, cookie):
    req = urllib.request.Request(url)
    req.add_header("Cookie", cookie)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    gold = json.load(open(GOLD_PATH, encoding="utf-8"))
    konsep_queries = [q for q in gold["queries"] if q["entitas_target"] == "konsep"]

    ids = [q["seed"].split(":")[1] for q in konsep_queries]
    id_list_sql = ",".join(ids)
    rows = mysql(
        f"SELECT c.concept_id, cn.name, cc.name "
        f"FROM concept c "
        f"JOIN concept_name cn ON cn.concept_id = c.concept_id AND cn.voided = 0 "
        f"  AND cn.concept_name_type = 'FULLY_SPECIFIED' AND cn.locale = 'en' "
        f"JOIN concept_class cc ON cc.concept_class_id = c.class_id "
        f"WHERE c.concept_id IN ({id_list_sql})"
    )
    judul_by_id = {}
    kelas_by_id = {}
    for cid, nama, kelas in rows:
        judul_by_id[cid] = nama
        kelas_by_id[cid] = kelas

    diagnosis_queries = [
        q for q in konsep_queries
        if kelas_by_id.get(q["seed"].split(":")[1]) == "Diagnosis"
    ]
    print(f"Query konsep dev total: {len(konsep_queries)}")
    print(f"Berkelas Diagnosis (bisa dibandingkan adil dengan endpoint bawaan): {len(diagnosis_queries)}")

    cookie = login_cookie()
    hasil = []
    setuju = 0
    for q in diagnosis_queries:
        cid = q["seed"].split(":")[1]
        target = judul_by_id[cid]

        url_asli = (
            f"{BASE}/concept?name={urllib.parse.quote(q['q'])}"
            f"&searchType=fuzzy&class={CLASS_DIAGNOSIS_UUID}&v=custom:(uuid,display)"
        )
        asli = get_json(url_asli, cookie)
        top_asli = asli["results"][0]["display"] if asli["results"] else None

        url_b0 = (
            f"{BASE}/unifiedsearch?q={urllib.parse.quote(q['q'])}"
            f"&mode=b0&entitas=konsep&limit=1"
        )
        b0 = get_json(url_b0, cookie)
        top_b0 = b0["results"][0]["judul"] if b0["results"] else None

        cocok_asli = (top_asli == target)
        cocok_b0 = (top_b0 == target)
        sepakat = (cocok_asli == cocok_b0)
        if sepakat:
            setuju += 1

        hasil.append({
            "q": q["q"], "tipe": q["tipe"], "target": target,
            "top_asli": top_asli, "top_b0": top_b0,
            "asli_benar": cocok_asli, "b0_benar": cocok_b0, "sepakat": sepakat,
        })
        print(f"{q['q']!r:45s} target={target!r:45s} asli={top_asli!r:45s} b0={top_b0!r:45s} sepakat={sepakat}")

    print(f"\nKesepakatan asli vs b0 (keduanya benar atau keduanya salah): {setuju}/{len(diagnosis_queries)}")
    json.dump(
        {"n": len(diagnosis_queries), "setuju": setuju, "detail": hasil},
        open(r"C:\src\tfidf-openmrs\riset\hasil3\baseline_openmrs_vs_b0.json", "w", encoding="utf-8"),
        indent=2, ensure_ascii=False,
    )


if __name__ == "__main__":
    main()
