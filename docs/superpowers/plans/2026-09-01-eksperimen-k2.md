# Eksperimen K2 (saran ketik Jaccard bigram) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ukur kontribusi K2 (saran "Maksud Anda" / `BigramJaccardSuggester`) dengan dua metrik — akurasi saran dan penyelamatan query buntu — pada korpus 8 entitas, tanpa menyentuh angka K1.

**Architecture:** Reimplementasi Python murni dari suggester (`saran_k2`), dijalankan atas korpus 8-entitas yang diperluas dari `eksperimen2.py` (impor sebagai modul, `main()` tak pernah dipanggil — aturan 10). Query set K2 sendiri (~215, dibangkitkan deterministik dari `SEED_K2`). Keluaran ke `riset/hasil5/`. Cross-check terpisah ke endpoint live `/unifiedsearch/saran`.

**Tech Stack:** Python 3.14 (stdlib saja + `pytest` 9 untuk uji unit), MariaDB via `docker exec`, `urllib` untuk REST.

**Spec:** `docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md` — baca dulu.

**Konvensi repo yang wajib diikuti:**
- Kode/komentar Inggris, dokumen/pesan Indonesia (CLAUDE.md "Bahasa").
- Determinisme: kunci urut majemuk `(-skor, kunci)`, iterasi himpunan selalu `sorted()` dulu (CLAUDE.md aturan 1).
- `eksperimen_k2.py` mengimpor `eksperimen2`, **tak pernah** memanggil `eksperimen2.main()` (aturan 10). Pola aman ada di `riset/ekspor_gold_dev.py` dan `riset/eksperimen3_baseline_asli.py`.
- Semua path absolut Windows: prefix `C:\src\tfidf-openmrs\`.
- DB: `docker exec openmrs-distro-referenceapplication-db-1 mysql -uroot -popenmrs openmrs -N -B -e "<sql>"`.
- REST: `http://127.0.0.1/openmrs/ws/rest/v1/` (JANGAN `localhost` — CLAUDE.md aturan 7). Login basic `admin:Admin123` ke `/session`, pakai cookie (lihat `eksperimen3_baseline_asli.py:login_cookie`).

---

## File Structure

| File | Tanggung jawab |
|---|---|
| `riset/ekspor_hasillab.sql` | Satu query `SELECT JSON_OBJECT(...)` per baris hasillab, cermin `HasilLabSource.java`. |
| `riset/ekspor_kondisi.sql` | Idem untuk `ConditionSource.java`. |
| `riset/data/hasillab.jsonl` | Snapshot korpus hasillab (di-commit). |
| `riset/data/kondisi.jsonl` | Snapshot korpus kondisi (di-commit). |
| `riset/eksperimen_k2.py` | Eksperimen utama: `muat8`, `bangun8`, `bobot_koleksi8`, `cari8`, `saran_k2`, `degradasi_k2`, `bangun_query_k2`, `gold_k2`, `metrik_akurasi`, `metrik_penyelamatan`, `main`. Tak butuh Docker. |
| `riset/test_eksperimen_k2.py` | Uji unit `pytest`: `saran_k2` (Jaccard/gerbang/urut), `degradasi_k2` (2 jenis baru), `bangun8` (setia ke `eksperimen2` pada 6 entitas), metrik. |
| `riset/cek_cross_k2.py` | Cross-check ke endpoint live `/unifiedsearch/saran` + sub-cek privilege. Butuh Docker. |
| `riset/hasil5/` | `query_k2.json`, `hasil.json`, `per_query_k2.json`, `ringkasan.csv`, `cross_check_java.json`, `laporan.md`. |
| `tugas/14-eksperimen-k2.md` | Protokol eksperimen (dokumen). |
| Doc: `algoritma.md`, `keputusan.md`, `proposal.html`, `ringkasan-hasil.md`, `kontrak-data.md`, `CLAUDE.md` | Tulisan hasil. |

Branch: `eksperimen-k2` (sudah dibuat, spec sudah di-commit di situ).

---

## Task 1: SQL ekspor hasillab + kondisi

**Files:**
- Create: `riset/ekspor_hasillab.sql`
- Create: `riset/ekspor_kondisi.sql`
- Baca dulu: `backend/openmrs-module-tfidf-search/api/src/main/java/org/openmrs/module/unifiedsearch/source/HasilLabSource.java`
- Baca dulu: `backend/openmrs-module-tfidf-search/api/src/main/java/org/openmrs/module/unifiedsearch/source/ConditionSource.java`
- Baca dulu: `riset/ekspor_pasien.sql` (format `JSON_OBJECT` + subquery `JSON_ARRAYAGG` untuk alias)
- Baca dulu: `docs/kontrak-data.md` bagian "Entitas ketujuh: hasillab" dan "Entitas kedelapan: kondisi"

- [ ] **Step 1: Baca ketiga file sumber**

Buka `HasilLabSource.java` dan `ConditionSource.java`. Catat persis: tabel, klausa `WHERE`, join concept_name (`locale='en' AND concept_name_type='FULLY_SPECIFIED' AND voided=0`), person_name (`voided=0 AND preferred=1`), dan aturan fallback `condition_non_coded` (hanya bila `condition_coded IS NULL`).

- [ ] **Step 2: Tulis `riset/ekspor_hasillab.sql`**

```sql
-- Cermin HasilLabSource.java. Satu baris JSON per obs kelas konsep Test/LabSet.
-- judul = nama tes (FULLY_SPECIFIED, en); alias = [nama lengkap pasien preferred];
-- kode = NULL; konteks = ringkasan tampilan (TIDAK diindeks); tautan_pasien = obs.person_id.
SELECT JSON_OBJECT(
  'id',            CONCAT('hasillab:', o.obs_id),
  'entitas',       'hasillab',
  'judul',         cn.name,
  'alias',         JSON_ARRAY(TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name))),
  'kode',          NULL,
  'konteks',       CONCAT_WS(' ',
                     COALESCE(CAST(o.value_numeric AS CHAR), o.value_text, ''),
                     DATE(o.obs_datetime)),
  'tautan_pasien', o.person_id
) AS j
FROM obs o
JOIN concept c        ON c.concept_id = o.concept_id
JOIN concept_class cc ON cc.concept_class_id = c.class_id
JOIN concept_name cn  ON cn.concept_id = o.concept_id
                      AND cn.voided = 0 AND cn.locale = 'en'
                      AND cn.concept_name_type = 'FULLY_SPECIFIED'
JOIN person_name pn   ON pn.person_id = o.person_id
                      AND pn.voided = 0 AND pn.preferred = 1
WHERE o.voided = 0
  AND cc.name IN ('Test', 'LabSet')
ORDER BY o.obs_id;
```

- [ ] **Step 3: Tulis `riset/ekspor_kondisi.sql`**

```sql
-- Cermin ConditionSource.java. Satu baris JSON per baris conditions (voided=0).
-- judul = nama konsep condition_coded, fallback condition_non_coded HANYA bila
-- condition_coded IS NULL; alias = [nama lengkap pasien]; konteks = status + onset.
SELECT JSON_OBJECT(
  'id',            CONCAT('kondisi:', c.condition_id),
  'entitas',       'kondisi',
  'judul',         COALESCE(cn.name, c.condition_non_coded),
  'alias',         JSON_ARRAY(TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name))),
  'kode',          NULL,
  'konteks',       CONCAT_WS(' ', c.clinical_status, DATE(c.onset_date)),
  'tautan_pasien', c.patient_id
) AS j
FROM conditions c
LEFT JOIN concept_name cn ON cn.concept_id = c.condition_coded
                          AND cn.voided = 0 AND cn.locale = 'en'
                          AND cn.concept_name_type = 'FULLY_SPECIFIED'
JOIN person_name pn       ON pn.person_id = c.patient_id
                          AND pn.voided = 0 AND pn.preferred = 1
WHERE c.voided = 0
ORDER BY c.condition_id;
```

- [ ] **Step 4: Verifikasi kolom terhadap skema live**

Run:
```
docker exec openmrs-distro-referenceapplication-db-1 mysql -uroot -popenmrs openmrs -N -B -e "DESCRIBE conditions; DESCRIBE obs;"
```
Expected: kolom `conditions.condition_coded`, `conditions.condition_non_coded`, `conditions.clinical_status`, `conditions.onset_date`, `conditions.patient_id`, `obs.value_numeric`, `obs.value_text`, `obs.obs_datetime` ada. Kalau nama beda (mis. `conditions.status`), sesuaikan SQL sekarang.

- [ ] **Step 5: Commit**

```bash
git add riset/ekspor_hasillab.sql riset/ekspor_kondisi.sql
git commit -m "riset: SQL ekspor hasillab + kondisi untuk korpus K2"
```

---

## Task 2: Jalankan ekspor → data files

**Files:**
- Create: `riset/data/hasillab.jsonl`
- Create: `riset/data/kondisi.jsonl`

- [ ] **Step 1: Pastikan stack Docker hidup**

Run:
```
docker compose -f C:/src/tfidf-openmrs/openmrs-distro-referenceapplication/docker-compose.yml up -d
```
(Tanpa `-p` — CLAUDE.md aturan 8. Nama project dari nama folder.)
Verifikasi:
```
docker ps --format "{{.Names}}\t{{.Status}}"
```
Expected: 4 container `openmrs-distro-referenceapplication-*` status `Up`.

- [ ] **Step 2: Ekspor hasillab**

Run (PowerShell, output UTF-8 dengan BOM seperti file lain di `riset/data/`):
```
docker exec -i openmrs-distro-referenceapplication-db-1 mysql -uroot -popenmrs openmrs -N -B -e "$(Get-Content -Raw C:/src/tfidf-openmrs/riset/ekspor_hasillab.sql)" | Out-File -Encoding utf8 C:/src/tfidf-openmrs/riset/data/hasillab.jsonl
```
Expected: file berisi satu objek JSON per baris, tiap baris punya `"entitas":"hasillab"`.

- [ ] **Step 3: Ekspor kondisi**

Run:
```
docker exec -i openmrs-distro-referenceapplication-db-1 mysql -uroot -popenmrs openmrs -N -B -e "$(Get-Content -Raw C:/src/tfidf-openmrs/riset/ekspor_kondisi.sql)" | Out-File -Encoding utf8 C:/src/tfidf-openmrs/riset/data/kondisi.jsonl
```

- [ ] **Step 4: Validasi tiap baris JSON valid + catat metrik**

Run:
```
python -c "import json,hashlib; [print(f, sum(1 for _ in open(f,encoding='utf-8-sig')), hashlib.sha256(open(f,'rb').read()).hexdigest()) for f in ['C:/src/tfidf-openmrs/riset/data/hasillab.jsonl','C:/src/tfidf-openmrs/riset/data/kondisi.jsonl']]"
python -c "import json; [json.loads(l) for l in open('C:/src/tfidf-openmrs/riset/data/hasillab.jsonl',encoding='utf-8-sig') if l.strip()]; print('hasillab OK')"
python -c "import json; [json.loads(l) for l in open('C:/src/tfidf-openmrs/riset/data/kondisi.jsonl',encoding='utf-8-sig') if l.strip()]; print('kondisi OK')"
```
Expected: dua baris metrik (path, jumlah baris, SHA-256), lalu `hasillab OK` dan `kondisi OK`. Catat jumlah baris + SHA — dipakai di Task 9 header dan Task 12 (`keputusan.md`).

- [ ] **Step 5: Sanity — bandingkan jumlah dengan hitungan langsung**

Run:
```
docker exec openmrs-distro-referenceapplication-db-1 mysql -uroot -popenmrs openmrs -N -B -e "SELECT COUNT(*) FROM conditions WHERE voided=0;"
```
Expected: `kontrak-data.md` menyebut tabel `conditions` = 1.279 baris pada demo data. Jumlah baris `kondisi.jsonl` harus ≤ itu (baris tanpa preferred person_name ter-drop oleh JOIN). Kalau jauh lebih kecil, periksa JOIN `person_name`.

- [ ] **Step 6: Commit**

```bash
git add riset/data/hasillab.jsonl riset/data/kondisi.jsonl
git commit -m "riset: snapshot korpus hasillab + kondisi (demo data, di-commit untuk reproduksi tanpa Docker)"
```

---

## Task 3: `eksperimen_k2.py` — loader 8 entitas

**Files:**
- Create: `riset/eksperimen_k2.py`
- Create: `riset/test_eksperimen_k2.py`
- Baca dulu: `riset/eksperimen2.py` fungsi `muat()` (baris 43-74), `_lst()` (38-41)

- [ ] **Step 1: Tulis skeleton + `muat8()`**

`riset/eksperimen_k2.py`:
```python
# -*- coding: utf-8 -*-
"""Eksperimen K2 - saran ketik "Maksud Anda" (BigramJaccardSuggester).

Dua metrik (docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md):
  1. Akurasi saran   - hit@k, MRR@6 daftar saran vs dokumen yang dimaksud.
  2. Penyelamatan     - query yang E3 beri 0 relevan -> 1 klik saran -> relevan.

Korpus 8 entitas: 6 dari eksperimen2 + hasillab + kondisi (riset/data/*.jsonl,
di-commit). TIDAK memanggil eksperimen2.main() -> qs[100:] tak tersentuh
(CLAUDE.md aturan 10). Snapshot korpus hasillab/kondisi:
  hasillab.jsonl  <N>  sha256 <...>     (isi setelah Task 2)
  kondisi.jsonl   <N>  sha256 <...>
"""
import collections
import hashlib
import json
import os
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
```

- [ ] **Step 2: Tulis uji loader**

`riset/test_eksperimen_k2.py`:
```python
# -*- coding: utf-8 -*-
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eksperimen_k2 as K


def test_muat8_punya_8_entitas():
    rec = K.muat8()
    ent = {r["entitas"] for r in rec.values()}
    assert ent == set(K.ENT8)


def test_muat8_tak_ubah_6_entitas_asli():
    rec6 = K.eksperimen2.muat()
    rec8 = K.muat8()
    for kunci, r in rec6.items():
        assert rec8[kunci]["judul"] == r["judul"]
        assert rec8[kunci]["alias"] == r["alias"]


def test_hasillab_alias_nama_pasien():
    rec = K.muat8()
    lab = [r for r in rec.values() if r["entitas"] == "hasillab"]
    assert lab, "tak ada dokumen hasillab"
    assert all(len(r["alias"]) >= 1 for r in lab)
```

- [ ] **Step 3: Jalankan uji, harus lulus**

Run: `python -m pytest riset/test_eksperimen_k2.py -v`
Expected: 3 PASS. Kalau `test_muat8_punya_8_entitas` gagal karena entitas kurang → periksa `.jsonl` dari Task 2.

- [ ] **Step 4: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: eksperimen_k2 loader korpus 8 entitas"
```

---

## Task 4: Indeks + pencarian 8 entitas (`bangun8`, `bobot_koleksi8`, `cari8`)

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Modify: `riset/test_eksperimen_k2.py`
- Baca dulu: `riset/eksperimen2.py` fungsi `bangun()` (151-165), `fusi1()` (167-174), `bobot_koleksi()` (200-209), `cari()` (211-235), `heuristik_openmrs()` (176-197)

- [ ] **Step 1: Salin fungsi versi 8-entitas**

Tambah ke `eksperimen_k2.py`. Ini salinan `eksperimen2` dengan `ENT` → `ENT8` (fungsi asli mengiterasi `eksperimen2.ENT` global, jadi harus disalin, bukan dipanggil):

```python
def bangun8(rec):
    """Sama seperti eksperimen2.bangun() tapi atas ENT8 (8 entitas)."""
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
```

- [ ] **Step 2: Uji kesetiaan — `cari8` atas 6 entitas = `eksperimen2.cari`**

Tambah ke `test_eksperimen_k2.py`:
```python
import random


def _rec6_dan_query():
    rec = K.eksperimen2.muat()
    rnd = random.Random(K.eksperimen2.SEED)
    qs = K.eksperimen2.bangun_query(rec, rnd)
    rnd.shuffle(qs)
    return rec, qs[:5]  # 5 query dev, tak menyentuh qs[100:]


def test_cari8_setia_ke_eksperimen2_pada_6_entitas(monkeypatch):
    # paksa ENT8 = 6 entitas asli, bangun8 harus reproduksi eksperimen2.bangun
    monkeypatch.setattr(K, "ENT8", list(K.eksperimen2.ENT))
    rec, queries = _rec6_dan_query()
    lokal, glob, _ = K.bangun8(rec)
    lokal2, glob2, _ = K.eksperimen2.bangun(rec)
    for it in queries:
        for sistem in ("B0", "B1", "E1", "E3"):
            a = K.cari8(sistem, it["q"], lokal, glob, rec)
            b = K.eksperimen2.cari(sistem, it["q"], lokal2, glob2, rec)
            assert a == b, f"{sistem} / {it['q']!r}: {a} != {b}"
```

- [ ] **Step 3: Jalankan uji**

Run: `python -m pytest riset/test_eksperimen_k2.py::test_cari8_setia_ke_eksperimen2_pada_6_entitas -v`
Expected: PASS. Kalau gagal → `bangun8`/`cari8` menyimpang dari salinan; banding baris demi baris dengan `eksperimen2`.

- [ ] **Step 4: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: bangun8/cari8 - jalur peringkat 8 entitas, diuji setia ke eksperimen2"
```

---

## Task 5: `saran_k2()` — reimplementasi suggester

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Modify: `riset/test_eksperimen_k2.py`
- Baca dulu: `backend/.../BigramJaccardSuggester.java` (seluruh 109 baris), `backend/.../UnifiedSearchService.java:72-107` (`saran`), `backend/.../Tokenizer.java:charGrams`

- [ ] **Step 1: Tulis uji `saran_k2` lebih dulu (TDD)**

Tambah ke `test_eksperimen_k2.py`:
```python
class _FakeIdx(dict):
    """lokal8[e] minimal: teks / pem / utama sejajar."""
    @classmethod
    def dari(cls, baris):
        teks = [t for t, _, _ in baris]
        pem = [p for _, p, _ in baris]
        utama = [u for _, _, u in baris]
        return cls(teks=teks, pem=pem, utama=utama)


def test_saran_jaccard_dan_gerbang_min_irisan():
    # "fever" bigram: fe,ev,ve,er  ; "fevr" bigram: fe,ev,vr
    # irisan {fe,ev} = 2 -> lolos; union {fe,ev,ve,er,vr}=5 -> skor 2/5 = 0.4
    lokal = {"konsep": _FakeIdx.dari([
        ("Fever", "konsep:1", True),
        ("Xy", "konsep:2", True),          # bigram {xy}; irisan 0 -> dibuang
    ])}
    K_ENT_BACKUP = K.ENT8
    K.ENT8 = ["konsep"]
    try:
        hasil = K.saran_k2(lokal, "fevr")
    finally:
        K.ENT8 = K_ENT_BACKUP
    assert hasil == [("konsep:1", (0.4, True))]


def test_saran_cocok_persis_lolos_walau_1_bigram():
    # query "tb" -> bigram {tb}; form "TB" -> bigram {tb}; sama-set -> lolos, skor 1.0
    lokal = {"konsep": _FakeIdx.dari([("TB", "konsep:9", False)])}
    K_ENT_BACKUP = K.ENT8
    K.ENT8 = ["konsep"]
    try:
        assert K.saran_k2(lokal, "tb") == [("konsep:9", (1.0, False))]
    finally:
        K.ENT8 = K_ENT_BACKUP


def test_saran_1_bigram_bukan_persis_dibuang():
    # query "fev" bigram {fe,ev}; form "Fe" bigram {fe}; irisan {fe}=1, bukan persis -> buang
    lokal = {"konsep": _FakeIdx.dari([("Fe", "konsep:3", True)])}
    K_ENT_BACKUP = K.ENT8
    K.ENT8 = ["konsep"]
    try:
        assert K.saran_k2(lokal, "fev") == []
    finally:
        K.ENT8 = K_ENT_BACKUP


def test_saran_tie_break_judul_sebelum_alias():
    # dua form dokumen beda, skor sama -> via_judul menang, lalu kunci
    lokal = {"pasien": _FakeIdx.dari([
        ("Mark Smith", "pasien:8", True),
    ]), "hasillab": _FakeIdx.dari([
        ("Mark Smith", "hasillab:5", False),   # nama pasien sebagai alias lab
    ])}
    K_ENT_BACKUP = K.ENT8
    K.ENT8 = ["pasien", "hasillab"]
    try:
        hasil = K.saran_k2(lokal, "mark smith")
    finally:
        K.ENT8 = K_ENT_BACKUP
    assert hasil[0][0] == "pasien:8"  # via judul, di atas hasillab:5 (via alias)
```

- [ ] **Step 2: Jalankan uji — harus GAGAL (fungsi belum ada)**

Run: `python -m pytest riset/test_eksperimen_k2.py -k saran -v`
Expected: FAIL, `AttributeError: module 'eksperimen_k2' has no attribute 'saran_k2'`.

- [ ] **Step 3: Implementasi `saran_k2`**

Tambah ke `eksperimen_k2.py`:
```python
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
```

- [ ] **Step 4: Jalankan uji — harus LULUS**

Run: `python -m pytest riset/test_eksperimen_k2.py -k saran -v`
Expected: 4 PASS.

- [ ] **Step 5: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: saran_k2 - reimpl Python BigramJaccardSuggester (Jaccard bigram + gerbang + urut)"
```

---

## Task 6: Query set K2 (`degradasi_k2`, `bangun_query_k2`, `gold_k2`)

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Modify: `riset/test_eksperimen_k2.py`
- Baca dulu: `riset/eksperimen2.py` `degradasi()` (265-286), `bangun_query()` (288-335), `gold()` bersarang (300-313), `KLINIS` (262-263)

- [ ] **Step 1: Tulis uji 2 jenis degradasi baru (TDD)**

Tambah ke `test_eksperimen_k2.py`:
```python
def test_trunkasi_pendek():
    rnd = random.Random(1)
    q, tt = K.degradasi_k2("Fever", "trunkasi_pendek", rnd)
    assert tt == "trunkasi_pendek"
    assert 3 <= len(q) <= 5
    assert "fever".startswith(q)


def test_trunkasi_pendek_buang_judul_terlalu_pendek():
    rnd = random.Random(1)
    assert K.degradasi_k2("Flu", "trunkasi_pendek", rnd) == (None, None)


def test_typo_pendek_pendek_dan_berubah():
    rnd = random.Random(2)
    q, tt = K.degradasi_k2("Diabetes mellitus", "typo_pendek", rnd)
    assert tt == "typo_pendek"
    assert 3 <= len(q) <= 6
    assert q != "diabe"  # ada perubahan huruf


def test_bangun_query_k2_deterministik():
    a = K.bangun_query_k2(K.muat8())
    b = K.bangun_query_k2(K.muat8())
    assert [x["q"] for x in a] == [x["q"] for x in b]
    assert all(x["jenis"] in K.JENIS_K2 for x in a)
    assert all(len(x["q"]) >= 3 for x in a)
```

- [ ] **Step 2: Jalankan — harus GAGAL**

Run: `python -m pytest riset/test_eksperimen_k2.py -k "degradasi or trunkasi or typo_pendek or bangun_query_k2" -v`
Expected: FAIL (fungsi belum ada).

- [ ] **Step 3: Implementasi generator query**

Tambah ke `eksperimen_k2.py`:
```python
import random  # noqa: E402

JENIS_K2 = ["persis", "typo", "trunkasi", "trunkasi_pendek", "typo_pendek"]

# proporsi siklus (spec Bagian 3): typo 25, trunkasi 20, trunkasi_pendek 20,
# typo_pendek 15, persis 20  -> pola panjang 20
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
        potong = w[:rnd.choice([3, 4, 5])]
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
    # panggil ulang logika gold() eksperimen2 lewat bangun_query? tidak - salin inti:
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
            if not q or len(q) < 3 or q == eksperimen2.norm(r["judul"]):
                continue
            qs.append(dict(qid=len(qs), q=q, jenis=tt, entitas=e,
                           seed=r["id"], rel=gold_k2(rec, r)))
            amb += 1
    return qs
```

- [ ] **Step 4: Jalankan uji — harus LULUS**

Run: `python -m pytest riset/test_eksperimen_k2.py -k "degradasi or trunkasi or typo_pendek or bangun_query_k2" -v`
Expected: semua PASS. Kalau `test_trunkasi_pendek_buang_judul_terlalu_pendek` gagal: "Flu" punya 1 kata 3 huruf, `pool` kosong → harus `(None, None)`.

- [ ] **Step 5: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: query set K2 - degradasi_k2 (2 jenis pendek baru) + bangun_query_k2 + gold_k2"
```

---

## Task 7: Metrik 1 — akurasi saran

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Modify: `riset/test_eksperimen_k2.py`

- [ ] **Step 1: Tulis uji (TDD)**

```python
def test_metrik_akurasi_hit_dan_mrr():
    # daftar saran (kunci, (skor, judul)); rel {konsep:1: 2}
    saran = [("konsep:9", (0.9, True)), ("konsep:1", (0.5, True)), ("konsep:3", (0.3, False))]
    m = K.metrik_akurasi(saran, {"konsep:1": 2})
    assert m["hit@1"] == 0
    assert m["hit@3"] == 1
    assert m["hit@6"] == 1
    assert abs(m["mrr@6"] - 0.5) < 1e-9   # relevan di peringkat 2
    assert m["saran_kosong"] == 0


def test_metrik_akurasi_saran_kosong():
    m = K.metrik_akurasi([], {"konsep:1": 2})
    assert m["hit@1"] == m["hit@3"] == m["hit@6"] == 0
    assert m["saran_kosong"] == 1
```

- [ ] **Step 2: Jalankan — GAGAL**

Run: `python -m pytest riset/test_eksperimen_k2.py -k metrik_akurasi -v`
Expected: FAIL.

- [ ] **Step 3: Implementasi**

```python
def metrik_akurasi(saran, rel):
    ids = [kunci for kunci, _ in saran]
    rel_di = lambda i: rel.get(i, 0) > 0
    mrr = 0.0
    for peringkat, i in enumerate(ids[:6], start=1):
        if rel_di(i):
            mrr = 1.0 / peringkat
            break
    return dict(
        **{"hit@1": 1.0 if ids[:1] and rel_di(ids[0]) else 0.0,
           "hit@3": 1.0 if any(rel_di(i) for i in ids[:3]) else 0.0,
           "hit@6": 1.0 if any(rel_di(i) for i in ids[:6]) else 0.0},
        **{"mrr@6": mrr, "saran_kosong": 1.0 if not ids else 0.0},
    )
```

- [ ] **Step 4: Jalankan — LULUS**

Run: `python -m pytest riset/test_eksperimen_k2.py -k metrik_akurasi -v`
Expected: 2 PASS.

- [ ] **Step 5: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: metrik_akurasi - hit@k / MRR@6 daftar saran"
```

---

## Task 8: Metrik 2 — penyelamatan query buntu

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Modify: `riset/test_eksperimen_k2.py`

- [ ] **Step 1: Tulis uji (TDD)**

```python
def test_penyelamatan_buntu_lalu_selamat():
    rec = {"konsep:1": {"id": "konsep:1", "entitas": "konsep", "judul": "Fever"}}

    def e3_palsu(q):
        return [("konsep:9", 0.5)] if q == "fevr" else [("konsep:1", 1.0)]

    def saran_palsu(q):
        return [("konsep:1", (0.4, True))]

    hasil = K.penyelamatan_satu(
        "fevr", {"konsep:1": 2}, rec, e3_fn=e3_palsu, saran_fn=saran_palsu)
    assert hasil["buntu_sebelum"] is True
    assert hasil["terselamatkan"] is True
    assert hasil["q_klik"] == "Fever"


def test_penyelamatan_tidak_buntu():
    def e3_palsu(q):
        return [("konsep:1", 1.0)]

    hasil = K.penyelamatan_satu(
        "fever", {"konsep:1": 2}, {}, e3_fn=e3_palsu, saran_fn=lambda q: [])
    assert hasil["buntu_sebelum"] is False
    assert hasil["terselamatkan"] is None


def test_penyelamatan_saran_kosong_gagal():
    hasil = K.penyelamatan_satu(
        "xyzq", {"konsep:1": 2}, {}, e3_fn=lambda q: [], saran_fn=lambda q: [])
    assert hasil["buntu_sebelum"] is True
    assert hasil["terselamatkan"] is False
```

- [ ] **Step 2: Jalankan — GAGAL**

Run: `python -m pytest riset/test_eksperimen_k2.py -k penyelamatan -v`
Expected: FAIL.

- [ ] **Step 3: Implementasi**

```python
def _ada_relevan(hasil, rel, topk=10):
    return any(rel.get(k, 0) > 0 for k, _ in hasil[:topk])


def penyelamatan_satu(q, rel, rec, e3_fn, saran_fn):
    """Satu query: buntu? -> 1 klik saran top-1 -> selamat?

    e3_fn(query) -> list[(kunci, skor)] ; saran_fn(query) -> list[(kunci, (skor, judul))].
    Disuntik supaya bisa diuji tanpa indeks.
    """
    buntu = not _ada_relevan(e3_fn(q), rel)
    out = dict(buntu_sebelum=buntu, nol_hasil_sebelum=(len(e3_fn(q)) == 0),
               terselamatkan=None, q_klik=None, nol_hasil_sesudah=None)
    if not buntu:
        return out
    saran = saran_fn(q)
    if not saran:
        out["terselamatkan"] = False
        out["nol_hasil_sesudah"] = out["nol_hasil_sebelum"]
        return out
    kunci_top1 = saran[0][0]
    q_klik = rec[kunci_top1]["judul"] if kunci_top1 in rec else kunci_top1
    hasil2 = e3_fn(q_klik)
    out["q_klik"] = q_klik
    out["terselamatkan"] = _ada_relevan(hasil2, rel)
    out["nol_hasil_sesudah"] = (len(hasil2) == 0)
    return out
```

- [ ] **Step 4: Jalankan — LULUS**

Run: `python -m pytest riset/test_eksperimen_k2.py -k penyelamatan -v`
Expected: 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add riset/eksperimen_k2.py riset/test_eksperimen_k2.py
git commit -m "riset: penyelamatan_satu - buntu -> 1 klik saran -> selamat (fungsi disuntik untuk uji)"
```

---

## Task 9: `main()` — rakit + tulis `hasil5/`

**Files:**
- Modify: `riset/eksperimen_k2.py`
- Baca dulu: `riset/eksperimen2.py` `main()` (378-469) untuk pola penulisan `hasil.json` / `ringkasan.csv`

- [ ] **Step 1: Tulis `main()`**

```python
def _agg(baris, kunci):
    return sum(b[kunci] for b in baris) / len(baris) if baris else 0.0


def main():
    print("memuat korpus 8 entitas ...")
    rec = muat8()
    hit = collections.Counter(r["entitas"] for r in rec.values())
    print("  dokumen:", sum(hit.values()), dict(hit))
    lokal, glob, t_idx = bangun8(rec)
    nform = sum(len(lokal[e]["teks"]) for e in lokal)
    print("  surface form: %d | waktu indeks: %.2f detik" % (nform, t_idx))

    qs = bangun_query_k2(rec)
    print("  query K2:", len(qs), dict(collections.Counter(x["jenis"] for x in qs)))

    e3_cache = {}

    def e3(q):
        if q not in e3_cache:
            e3_cache[q] = cari8("E3", q, lokal, glob, rec, topk=10)
        return e3_cache[q]

    def saran(q):
        return saran_k2(lokal, q, limit=50)

    per_query = []
    for it in qs:
        srn = saran_k2(lokal, it["q"], limit=50)
        akr = metrik_akurasi(srn, it["rel"])
        pyl = penyelamatan_satu(it["q"], it["rel"], rec, e3_fn=e3, saran_fn=saran)
        per_query.append(dict(
            qid=it["qid"], q=it["q"], jenis=it["jenis"], entitas=it["entitas"],
            seed=it["seed"], rel=it["rel"],
            saran5=[k for k, _ in srn[:5]], akurasi=akr, penyelamatan=pyl))

    def rinci(kunci_metrik, ambil):
        out = {}
        for j in JENIS_K2 + ["keseluruhan"]:
            baris = [p for p in per_query if j == "keseluruhan" or p["jenis"] == j]
            out[j] = dict(n=len(baris), **ambil(baris))
        return out

    akurasi_tab = rinci("akurasi", lambda B: {
        k: _agg([p["akurasi"] for p in B], k)
        for k in ("hit@1", "hit@3", "hit@6", "mrr@6", "saran_kosong")})

    def _pyl_agg(B):
        buntu = [p for p in B if p["penyelamatan"]["buntu_sebelum"]]
        selamat = [p for p in buntu if p["penyelamatan"]["terselamatkan"]]
        nol0 = [p for p in B if p["penyelamatan"]["nol_hasil_sebelum"]]
        nol1 = [p for p in B if p["penyelamatan"]["nol_hasil_sesudah"]]
        n = len(B) or 1
        return dict(
            buntu_sebelum=len(buntu) / n,
            terselamatkan=(len(selamat) / len(buntu)) if buntu else None,
            buntu_efektif_sesudah=(len(buntu) - len(selamat)) / n,
            nol_hasil_sebelum=len(nol0) / n,
            nol_hasil_sesudah=len(nol1) / n)

    penyelamatan_tab = rinci("penyelamatan", _pyl_agg)

    per_entitas = {}
    for e in ENT8:
        B = [p for p in per_query if p["entitas"] == e]
        per_entitas[e] = {k: _agg([p["akurasi"] for p in B], k)
                          for k in ("hit@1", "hit@3", "hit@6", "mrr@6")} if B else {}

    hasil = dict(
        korpus=dict(hit), surface_form=nform, waktu_indeks=t_idx,
        n_query=len(qs), param=dict(ngram=NGRAM_K2, min_irisan=MIN_IRISAN,
                                    limit_saran=LIMIT_SARAN, seed_k2=SEED_K2),
        akurasi=akurasi_tab, penyelamatan=penyelamatan_tab, per_entitas=per_entitas,
        snapshot=dict((fn, _sha(os.path.join(DATA, fn)))
                      for fn in ("hasillab.jsonl", "kondisi.jsonl")))

    json.dump(qs, open(os.path.join(OUT, "query_k2.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False, default=list)
    json.dump(hasil, open(os.path.join(OUT, "hasil.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    json.dump(per_query, open(os.path.join(OUT, "per_query_k2.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False, default=list)
    with open(os.path.join(OUT, "ringkasan.csv"), "w", encoding="utf-8") as f:
        f.write("jenis,n,hit@1,hit@3,hit@6,mrr@6,saran_kosong,buntu_sebelum,terselamatkan,buntu_efektif\n")
        for j in JENIS_K2 + ["keseluruhan"]:
            a, p = akurasi_tab[j], penyelamatan_tab[j]
            ts = "" if p["terselamatkan"] is None else "%.4f" % p["terselamatkan"]
            f.write("%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%.4f\n" % (
                j, a["n"], a["hit@1"], a["hit@3"], a["hit@6"], a["mrr@6"],
                a["saran_kosong"], p["buntu_sebelum"], ts, p["buntu_efektif_sesudah"]))
    print("selesai. hasil di:", OUT)
    return hasil


def _sha(path):
    return dict(baris=sum(1 for _ in open(path, encoding="utf-8-sig")),
               sha256=hashlib.sha256(open(path, "rb").read()).hexdigest())


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Jalankan eksperimen**

Run: `python C:/src/tfidf-openmrs/riset/eksperimen_k2.py`
Expected: cetak jumlah dokumen (8 entitas), jumlah surface form, jumlah query K2 (~200+), lalu `selesai. hasil di: .../hasil5`. Empat file di `riset/hasil5/`.

- [ ] **Step 3: Isi SHA snapshot ke docstring header**

Buka `riset/hasil5/hasil.json`, salin blok `snapshot` (baris + sha256 hasillab/kondisi) ke docstring `eksperimen_k2.py` (ganti `<N>` / `<...>`).

- [ ] **Step 4: Jalankan seluruh uji unit**

Run: `python -m pytest riset/test_eksperimen_k2.py -v`
Expected: semua PASS.

- [ ] **Step 5: Commit**

```bash
git add riset/eksperimen_k2.py riset/hasil5/
git commit -m "riset: eksperimen_k2 main() + hasil5/ (akurasi saran + penyelamatan query buntu, 8 entitas)"
```

---

## Task 10: Verifikasi determinisme

**Files:**
- Create: `riset/cek_determinisme_k2.py`

- [ ] **Step 1: Tulis pemeriksa**

```python
# -*- coding: utf-8 -*-
"""Jalankan eksperimen_k2.main() dua kali di proses ini + banding dengan hasil5/
yang sudah ada (hasil dari proses terpisah). Semua metrik wajib identik byte;
hanya waktu_indeks yang boleh beda. CLAUDE.md aturan 1."""
import json
import os
import subprocess
import sys

DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(DIR, "hasil5")


def _tanpa_waktu(h):
    h = dict(h)
    h.pop("waktu_indeks", None)
    return h


def main():
    lama = _tanpa_waktu(json.load(open(os.path.join(OUT, "hasil.json"), encoding="utf-8")))
    # proses terpisah:
    subprocess.check_call([sys.executable, os.path.join(DIR, "eksperimen_k2.py")])
    baru = _tanpa_waktu(json.load(open(os.path.join(OUT, "hasil.json"), encoding="utf-8")))
    assert json.dumps(lama, sort_keys=True) == json.dumps(baru, sort_keys=True), \
        "hasil.json BERUBAH antar proses -- ada kebocoran non-determinisme"
    q1 = json.load(open(os.path.join(OUT, "query_k2.json"), encoding="utf-8"))
    subprocess.check_call([sys.executable, os.path.join(DIR, "eksperimen_k2.py")])
    q2 = json.load(open(os.path.join(OUT, "query_k2.json"), encoding="utf-8"))
    assert q1 == q2, "query_k2.json berubah antar proses"
    print("determinisme OK: hasil.json + query_k2.json identik di 3 proses")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Jalankan**

Run: `python C:/src/tfidf-openmrs/riset/cek_determinisme_k2.py`
Expected: `determinisme OK: ...`. Kalau `AssertionError` → cari iterasi `set`/`dict` tanpa `sorted()` di `saran_k2`/`bangun8`/`bangun_query_k2`, perbaiki, ulang dari Task 9 Step 2.

- [ ] **Step 3: Commit**

```bash
git add riset/cek_determinisme_k2.py riset/hasil5/
git commit -m "riset: cek_determinisme_k2 - hasil5 identik di 3 proses"
```

---

## Task 11: Cross-check ke endpoint live + sub-cek privilege

**Files:**
- Create: `riset/cek_cross_k2.py`
- Baca dulu: `riset/eksperimen3_baseline_asli.py:login_cookie` (72-84), `get_json` (87-92)
- Baca dulu: `backend/.../UnifiedSearchRestController.java:56-64` (path `/saran`, param `q`, `limit`)

- [ ] **Step 1: Pastikan modul terpasang di OpenMRS**

Run:
```
curl.exe -s "http://127.0.0.1/openmrs/ws/rest/v1/unifiedsearch/saran?q=fever&limit=5" -u admin:Admin123
```
Expected: JSON `{"query":"fever","mode":"saran","results":[...]}`. Kalau 404 → modul belum terpasang: build `.omod` (`cd backend/openmrs-module-tfidf-search && mvn -q -o package`) lalu salin `omod/target/*.omod` ke volume modul OpenMRS dan restart backend (JANGAN ubah isi `openmrs-distro-referenceapplication/` — aturan 9). Kalau `.omod` yang lama tanpa `/saran` → butuh build ulang dari main.

- [ ] **Step 2: Tulis skrip cross-check**

```python
# -*- coding: utf-8 -*-
"""Cross-check saran_k2 (Python) vs endpoint /unifiedsearch/saran (Java live).

Baca subset tetap dari riset/hasil5/query_k2.json (TIDAK dibangkitkan ulang),
banding daftar kunci dokumen + urutan. Harapan: identik (dua-duanya
deterministik). Selisih -> selidiki seperti keputusan.md "C1".

Plus sub-cek privilege (CLAUDE.md aturan 5): panggil /saran sebagai admin;
verifikasi entitas pasien/hasillab/kondisi hanya muncul untuk pemanggil
ber-privilege. (Uji unit modul menutup arah sebaliknya.)
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
    # tiap result row: rekonstruksi kunci "entitas:id"
    return [f"{r['entitas']}:{r['id']}" for r in data["results"]]


def main():
    qs = json.load(open(os.path.join(OUT, "query_k2.json"), encoding="utf-8"))
    # subset tetap: pertama tiap (entitas, jenis), lalu isi sampai N_SUBSET urut qid
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

    # sub-cek privilege: query yang python-nya memuat dokumen pasien/hasillab/kondisi
    priv_rows = []
    for r in rows:
        kena = [k for k in r["python"] if k.split(":")[0] in ("pasien", "hasillab", "kondisi")]
        if kena:
            priv_rows.append(dict(q=r["q"], admin_kena=kena,
                                  catatan="peran tanpa 'View Patients' harus 0 dari ketiga entitas ini "
                                          "(bukti utama = uji unit modul BigramJaccardSuggesterTest)"))

    out = dict(n=len(rows), cocok=cocok, mismatch=[r for r in rows if not r["cocok"]],
               semua=rows, privilege=priv_rows)
    json.dump(out, open(os.path.join(OUT, "cross_check_java.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    print(f"cross-check: {cocok}/{len(rows)} cocok persis (daftar + urutan)")
    if cocok != len(rows):
        print("MISMATCH -- selidiki sebelum lanjut (norm? himpunan surface form? urutan seri?)")
        sys.exit(1)


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Jalankan**

Run: `python C:/src/tfidf-openmrs/riset/cek_cross_k2.py`
Expected: `cross-check: 30/30 cocok persis`. Kalau mismatch: bandingkan `python` vs `java` list di `hasil5/cross_check_java.json`; penyebab lazim = beda snapshot korpus (DB live vs `.jsonl`) — cek jumlah dokumen per entitas dari `GET /unifiedsearch?q=...&mode=e3` debug atau langsung SQL count.

- [ ] **Step 4: Sub-cek privilege via uji unit modul**

Run:
```
cd C:/src/tfidf-openmrs/backend/openmrs-module-tfidf-search
"C:/src/tfidf-openmrs/tools/apache-maven-3.9.16/bin/mvn.cmd" -q -o test -Dtest=BigramJaccardSuggesterTest,UnifiedSearchServiceTest
```
Expected: uji lulus. Kalau tak ada uji yang mem-mock `Context.hasPrivilege("View Patients")=false` dan meng-assert baris pasien/hasillab/kondisi hilang dari `saran()` → tambah satu di `api/src/test/.../UnifiedSearchServiceTest.java` (pola: mock `Context`, panggil `saran("mark", 10)`, assert tak ada `entitas` in {pasien,hasillab,kondisi}). Commit terpisah.

- [ ] **Step 5: Commit**

```bash
git add riset/cek_cross_k2.py riset/hasil5/cross_check_java.json
git commit -m "riset: cek_cross_k2 - saran_k2 Python vs endpoint live + sub-cek privilege"
```

---

## Task 12: Tulisan — laporan, keputusan, algoritma, proposal, ringkasan, kontrak, CLAUDE, tugas

**Files:**
- Create: `riset/hasil5/laporan.md`
- Create: `tugas/14-eksperimen-k2.md`
- Modify: `docs/keputusan.md` (tambah entri di ATAS bagian kronologis terbaru — cek konvensi tanggal file)
- Modify: `docs/algoritma.md` (bagian baru setelah "## 6. B0", sebelum "## 7. Mode")
- Modify: `docs/kontrak-data.md` (di bagian "Entitas ketujuh/kedelapan": tambah 1 kalimat)
- Modify: `docs/ringkasan-hasil.md` (bagian "Angka apa yang didapat" + "Apa yang dibangun")
- Modify: `docs/proposal.html` (subbagian baru di §6)
- Modify: `CLAUDE.md` (setelah tabel "Angka rujukan", 1 paragraf)

- [ ] **Step 1: `riset/hasil5/laporan.md`**

Isi dari `hasil5/hasil.json`: header (tanggal, korpus 8 entitas + jumlah dokumen, snapshot SHA, jumlah query K2, parameter dikunci), Tabel 1 akurasi (spec Bagian 5), Tabel 2 penyelamatan + tabel sempit 0-hasil (Bagian 6), hasil cross-check (`X/30 cocok`), pernyataan determinisme (`cek_determinisme_k2.py` OK di 3 proses), dan kotak "yang TIDAK diklaim" (Bagian 1 / Bagian 9). Bahasa Indonesia.

- [ ] **Step 2: `tugas/14-eksperimen-k2.md`**

Format seperti `tugas/` lain: Tujuan, Rujukan (`docs/superpowers/specs/2026-09-01-eksperimen-k2-design.md`), Langkah (ringkasan 12 task ini), bagian **"Selesai kalau"**:
- `python riset/eksperimen_k2.py` menghasilkan `hasil5/{hasil.json,query_k2.json,per_query_k2.json,ringkasan.csv}`.
- `python riset/cek_determinisme_k2.py` → OK (3 proses identik).
- `python riset/cek_cross_k2.py` → 30/30 cocok.
- `python -m pytest riset/test_eksperimen_k2.py` → semua lulus.
- Angka di `hasil5/laporan.md`, `docs/proposal.html` §6 subbagian K2, `docs/ringkasan-hasil.md` semua bersumber `hasil5/hasil.json`, tak ada yang diketik lepas.
- `hasil3/`, `hasil4/` tak berubah (`git status` bersih untuk keduanya).

- [ ] **Step 3: `docs/keputusan.md` — entri baru**

Judul: `## 2026-09-01 · Eksperimen K2 (saran ketik) — hasil5/`. Isi: motivasi (K2 punya kode di main, nol bukti), korpus 8 entitas + snapshot SHA hasillab/kondisi (dari `hasil.json`), dua tabel hasil, hasil cross-check + determinisme, dan pernyataan tegas: angka K1 (`hasil3/`, `hasil4/`) tak tersentuh; K2 bukan peningkatan mutu peringkat; K2 = S1 (Interactive QE) bukan K7.

- [ ] **Step 4: `docs/algoritma.md` — bagian K2**

Sisipkan sebelum `## 7. Mode yang harus didukung endpoint`:
```markdown
## 6b. K2 — saran ketik ("Maksud Anda"), untuk mempermudah pengguna

Jalur terpisah dari peringkat. Endpoint `GET /unifiedsearch/saran`, bukan
salah satu `mode`. Tak menyentuh nDCG.

Untuk tiap surface form:
```
bigram(s)  = kepingan karakter n=2 dari norm(s) (spasi -> "_")
skor       = |bigram(query) ∩ bigram(form)| / |bigram(query) ∪ bigram(form)|   (Jaccard)
```
Gerbang: surface form diskor hanya bila `|irisan| ≥ 2`, kecuali
`bigram(query) == bigram(form)` persis (cocok eksak lolos walau 1 bigram).

Skor dokumen = tertinggi atas surface form-nya; seri skor → surface form
**judul** menang atas alias/kode. Urut akhir `(-skor, via_judul dulu, kunci)`
(aturan 1). Hasil pasien/hasillab/kondisi disaring privilege "View Patients"
(aturan 5). Eksperimen: `riset/eksperimen_k2.py` → `riset/hasil5/`.
```

- [ ] **Step 5: `docs/kontrak-data.md`**

Di bagian hasillab DAN kondisi, setelah kalimat "Tidak ada padanan di `riset/eksperimen2.py`...", tambah:
> Sejak 2026-09-01, `riset/eksperimen_k2.py` (eksperimen K2, saran ketik) memuat kedua entitas ini pada korpus 8-entitas — itu pengukuran K2, terpisah dari angka K1 yang tetap hanya berlaku untuk enam entitas asli.

- [ ] **Step 6: `docs/ringkasan-hasil.md`**

Di tabel "Angka apa yang didapat", tambah baris sumber keempat: `hasil5/` (K2, ~215 query, 8 entitas) — metrik akurasi saran + penyelamatan query buntu. Di "Apa yang dibangun", perbarui deskripsi navbar/suggester dengan angka penyelamatan dari `hasil.json`. Semua angka dari `hasil5/hasil.json`.

- [ ] **Step 7: `docs/proposal.html` — subbagian §6**

Setelah tabel per-jenis-typo K1, tambah `<h3>Kontribusi 2 — saran ketik "Maksud Anda"</h3>` + paragraf framing (S1 Interactive QE, bukan K7) + dua `<table>` (akurasi, penyelamatan) diisi dari `hasil.json` + kotak `<p class="small">` "yang tidak diklaim". Angka **persis** dari `hasil5/hasil.json` (4 desimal → 3 di tabel).

- [ ] **Step 8: `CLAUDE.md`**

Setelah catatan B0/B0′ di bawah tabel "Angka rujukan", tambah paragraf:
> **K2 (saran ketik)** punya eksperimen sendiri sejak 2026-09-01 —
> `riset/eksperimen_k2.py` → `riset/hasil5/`, korpus 8 entitas, terpisah dari
> tabel di atas. Aturan 2/3 tetap tak berlaku untuk jalur K2 (bukan bagian
> pipeline K1-K6); K2 tak punya angka nDCG dan klaimnya bukan peningkatan
> mutu peringkat.

- [ ] **Step 9: Verifikasi tak ada angka K1 berubah**

Run: `git status --porcelain riset/hasil3 riset/hasil4 docs/proposal.html`
Kemudian `git diff docs/proposal.html` — pastikan HANYA penambahan subbagian K2, tak ada baris tabel K1 yang berubah.
Run: `python -m pytest riset/test_eksperimen_k2.py -q` → semua lulus.

- [ ] **Step 10: Commit**

```bash
git add riset/hasil5/laporan.md tugas/14-eksperimen-k2.md docs/keputusan.md docs/algoritma.md docs/kontrak-data.md docs/ringkasan-hasil.md docs/proposal.html CLAUDE.md
git commit -m "docs: hasil eksperimen K2 (saran ketik) - laporan, proposal, keputusan, algoritma, tugas 14"
```

---

## Task 13: PR

- [ ] **Step 1: Push + buka PR**

```bash
git push -u origin eksperimen-k2
```
Lalu `gh pr create --base main --head eksperimen-k2 --title "feat: eksperimen K2 (saran ketik Jaccard bigram)" --body-file <ringkasan>`.

Body: dua metrik + angka headline dari `hasil5/hasil.json`, pernyataan "K1 tak tersentuh", link spec + plan.

- [ ] **Step 2: Tunggu review manusia.** Jangan merge sendiri.

---

## Self-Review (penulis plan)

**Spec coverage:**
- Bagian 1 (klaim + non-klaim + invarian S1 + aturan 5) → Task 12 Step 3/7/8 (tulisan), Task 11 Step 4 (privilege).
- Bagian 2 (korpus 8 entitas, ekspor, loader) → Task 1, 2, 3.
- Bagian 3 (query set: rencana, 5 jenis, 2 baru, gold, determinisme) → Task 6.
- Bagian 4 (reimpl `saran_k2`, 3 titik kesetiaan, privilege) → Task 5, Task 11.
- Bagian 5 (metrik akurasi hit@k/MRR/saran-kosong, tabel per jenis) → Task 7, Task 9.
- Bagian 6 (penyelamatan: buntu, 1 klik top-1, q'=judul, E3 8-entitas non-comparable) → Task 8, Task 9.
- Bagian 7 (determinisme 3 proses + cross-check Java + privilege) → Task 10, Task 11.
- Bagian 8 (semua berkas + doc) → Task 9, Task 12.
- Bagian 9 (pagar: K7, aturan 2/3/10/6, komponen tak baru) → Task 12 Step 3/7/8, dan `eksperimen_k2.py` tak panggil `main()` (Task 3 Step 1).

**Placeholder scan:** Task 12 sengaja deskriptif untuk prosa (bukan kode) — tiap step menyebut sumber angka (`hasil5/hasil.json`) dan lokasi sisip persis. Tak ada "TODO" di langkah kode.

**Type consistency:**
- `saran_k2(lokal8, q, limit)` → `list[(kunci, (skor, via_judul))]` — dipakai konsisten di Task 7 (`metrik_akurasi(saran, rel)` baca `kunci` dari `kv[0]`), Task 9 (`saran5=[k for k,_ in srn[:5]]`), Task 11.
- `cari8(sistem, q, lokal, glob, rec, topk)` → `list[(kunci, skor)]` — konsisten Task 4, 8 (`e3_fn` → sama bentuk), 9.
- `penyelamatan_satu(q, rel, rec, e3_fn, saran_fn)` → dict dengan `buntu_sebelum/terselamatkan/q_klik/nol_hasil_*` — dipakai konsisten Task 9 `_pyl_agg`.
- `degradasi_k2(nama, jenis, rnd)` → `(q, jenis) | (None, None)` — konsisten Task 6.
- `metrik_akurasi` mengembalikan kunci `hit@1/hit@3/hit@6/mrr@6/saran_kosong` — dipakai sama di Task 9 `_agg`.
