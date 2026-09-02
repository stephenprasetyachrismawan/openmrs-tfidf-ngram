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
