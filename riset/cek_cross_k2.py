# -*- coding: utf-8 -*-
"""Cross-check saran_k2 (Python) vs endpoint /unifiedsearch/saran (Java live).

Baca subset tetap dari riset/hasil5/query_k2.json (TIDAK dibangkitkan ulang),
banding daftar kunci dokumen + urutan. Harapan: identik (dua-duanya
deterministik). Selisih -> selidiki seperti docs/keputusan.md "C1".

Plus sub-cek privilege (CLAUDE.md aturan 5): panggil /saran sebagai admin;
laporkan entitas pasien/hasillab/kondisi yang muncul. Bukti utama arah
sebaliknya (peran tanpa "View Patients" -> 0 baris) menunggu uji unit modul
(UnifiedSearchService.saran, gerbang mayViewPatients + isDataPasien).

STATUS: menunggu stack OpenMRS sehat. Per 2026-09-02 modul webservices.rest
gagal load setelah container backend di-recreate (ClassNotFoundException
MainResourceController) -> seluruh REST 500. Jalankan skrip ini setelah
stack pulih.
"""
import base64
import json
import os
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eksperimen_k2 as K

BASE = "http://127.0.0.1/openmrs/ws/rest/v1"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hasil5")
N_SUBSET = 30


def login(user, pw):
    req = urllib.request.Request(f"{BASE}/session")
    req.add_header("Authorization", "Basic " + base64.b64encode(f"{user}:{pw}".encode()).decode())
    resp = urllib.request.urlopen(req)
    return resp.headers.get("Set-Cookie", "").split(";")[0]


def saran_live(q, cookie, limit=50):
    url = f"{BASE}/unifiedsearch/saran?q={urllib.parse.quote(q)}&limit={limit}"
    req = urllib.request.Request(url)
    req.add_header("Cookie", cookie)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return [f"{r['entitas']}:{r['id']}" for r in data["results"]]


def main():
    qs = json.load(open(os.path.join(OUT, "query_k2.json"), encoding="utf-8"))
    dilihat, subset = set(), []
    for it in qs:
        k = (it["entitas"], it["jenis"])
        if k not in dilihat:
            dilihat.add(k)
            subset.append(it)
    for it in qs:
        if len(subset) >= N_SUBSET:
            break
        if it not in subset:
            subset.append(it)
    subset = subset[:N_SUBSET]

    rec = K.muat8()
    lokal, _, _ = K.bangun8(rec)
    cookie = login("admin", "Admin123")

    rows, cocok = [], 0
    for it in subset:
        py = [k for k, _ in K.saran_k2(lokal, it["q"], limit=50)]
        jv = saran_live(it["q"], cookie, limit=50)
        sama = py == jv
        cocok += sama
        rows.append(dict(q=it["q"], jenis=it["jenis"], entitas=it["entitas"],
                         python=py, java=jv, cocok=sama))

    priv = []
    for r in rows:
        kena = [k for k in r["python"] if k.split(":")[0] in ("pasien", "hasillab", "kondisi")]
        if kena:
            priv.append(dict(q=r["q"], admin_kena=kena,
                             catatan="peran tanpa 'View Patients' harus 0 dari ketiga entitas ini "
                                     "(bukti utama = uji unit UnifiedSearchService.saran)"))

    out = dict(n=len(rows), cocok=cocok, mismatch=[r for r in rows if not r["cocok"]],
               semua=rows, privilege=priv)
    json.dump(out, open(os.path.join(OUT, "cross_check_java.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    print(f"cross-check: {cocok}/{len(rows)} cocok persis (daftar + urutan)")
    if cocok != len(rows):
        print("MISMATCH -- selidiki cross_check_java.json sebelum lanjut")
        sys.exit(1)


if __name__ == "__main__":
    main()
