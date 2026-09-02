# -*- coding: utf-8 -*-
import os
import random
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
