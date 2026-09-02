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


class _FakeIdx(dict):
    """lokal8[e] minimal: teks / pem / utama sejajar."""

    @classmethod
    def dari(cls, baris):
        return cls(teks=[t for t, _, _ in baris], pem=[p for _, p, _ in baris],
                   utama=[u for _, _, u in baris])


def _saran_dgn_ent(lokal, q, ents):
    backup = K.ENT8
    K.ENT8 = ents
    try:
        return K.saran_k2(lokal, q)
    finally:
        K.ENT8 = backup


def test_saran_jaccard_dan_gerbang_min_irisan():
    # "fever" bigram: fe,ev,ve,er ; "fevr" bigram: fe,ev,vr
    # irisan {fe,ev}=2 -> lolos; union {fe,ev,ve,er,vr}=5 -> skor 2/5 = 0.4
    lokal = {"konsep": _FakeIdx.dari([
        ("Fever", "konsep:1", True),
        ("Xy", "konsep:2", True),          # bigram {xy}; irisan 0 -> dibuang
    ])}
    assert _saran_dgn_ent(lokal, "fevr", ["konsep"]) == [("konsep:1", (0.4, True))]


def test_saran_cocok_persis_lolos_walau_1_bigram():
    # query "tb" -> bigram {tb}; form "TB" -> bigram {tb}; sama-set -> lolos, skor 1.0
    lokal = {"konsep": _FakeIdx.dari([("TB", "konsep:9", False)])}
    assert _saran_dgn_ent(lokal, "tb", ["konsep"]) == [("konsep:9", (1.0, False))]


def test_saran_1_bigram_bukan_persis_dibuang():
    # query "fev" bigram {fe,ev}; form "Fe" bigram {fe}; irisan {fe}=1, bukan persis -> buang
    lokal = {"konsep": _FakeIdx.dari([("Fe", "konsep:3", True)])}
    assert _saran_dgn_ent(lokal, "fev", ["konsep"]) == []


def test_saran_tie_break_judul_sebelum_alias():
    lokal = {
        "pasien": _FakeIdx.dari([("Mark Smith", "pasien:8", True)]),
        "hasillab": _FakeIdx.dari([("Mark Smith", "hasillab:5", False)]),
    }
    hasil = _saran_dgn_ent(lokal, "mark smith", ["pasien", "hasillab"])
    assert hasil[0][0] == "pasien:8"  # via judul, di atas hasillab:5 (via alias)


def test_trunkasi_pendek():
    rnd = random.Random(1)
    q, tt = K.degradasi_k2("Fever", "trunkasi_pendek", rnd)
    assert tt == "trunkasi_pendek"
    assert 3 <= len(q) <= 5
    assert "fever".startswith(q)
    assert q != "fever"


def test_trunkasi_pendek_buang_judul_terlalu_pendek():
    rnd = random.Random(1)
    assert K.degradasi_k2("Flu", "trunkasi_pendek", rnd) == (None, None)


def test_typo_pendek_pendek_dan_berubah():
    rnd = random.Random(2)
    q, tt = K.degradasi_k2("Diabetes mellitus", "typo_pendek", rnd)
    assert tt == "typo_pendek"
    assert 3 <= len(q) <= 6
    assert not "diabetes".startswith(q)  # ada perubahan huruf, bukan trunkasi bersih


def test_bangun_query_k2_deterministik():
    a = K.bangun_query_k2(K.muat8())
    b = K.bangun_query_k2(K.muat8())
    assert [x["q"] for x in a] == [x["q"] for x in b]
    assert all(x["jenis"] in K.JENIS_K2 for x in a)
    assert all(len(x["q"]) >= 3 for x in a)
    assert {x["entitas"] for x in a} <= set(K.ENT8)


def test_metrik_akurasi_hit_dan_mrr():
    saran = [("konsep:9", (0.9, True)), ("konsep:1", (0.5, True)), ("konsep:3", (0.3, False))]
    m = K.metrik_akurasi(saran, {"konsep:1": 2})
    assert m["hit@1"] == 0
    assert m["hit@3"] == 1
    assert m["hit@6"] == 1
    assert abs(m["mrr@6"] - 0.5) < 1e-9
    assert m["saran_kosong"] == 0


def test_metrik_akurasi_saran_kosong():
    m = K.metrik_akurasi([], {"konsep:1": 2})
    assert m["hit@1"] == m["hit@3"] == m["hit@6"] == 0
    assert m["saran_kosong"] == 1


def test_penyelamatan_buntu_lalu_selamat():
    rec = {"konsep:1": {"id": "konsep:1", "entitas": "konsep", "judul": "Fever"}}

    def e3_palsu(q):
        return [("konsep:9", 0.5)] if q == "fevr" else [("konsep:1", 1.0)]

    def saran_palsu(q):
        return [("konsep:1", (0.4, True))]

    hasil = K.penyelamatan_satu("fevr", {"konsep:1": 2}, rec, e3_fn=e3_palsu, saran_fn=saran_palsu)
    assert hasil["buntu_sebelum"] is True
    assert hasil["terselamatkan"] is True
    assert hasil["q_klik"] == "Fever"


def test_penyelamatan_tidak_buntu():
    hasil = K.penyelamatan_satu(
        "fever", {"konsep:1": 2}, {}, e3_fn=lambda q: [("konsep:1", 1.0)], saran_fn=lambda q: [])
    assert hasil["buntu_sebelum"] is False
    assert hasil["terselamatkan"] is None


def test_penyelamatan_saran_kosong_gagal():
    hasil = K.penyelamatan_satu(
        "xyzq", {"konsep:1": 2}, {}, e3_fn=lambda q: [], saran_fn=lambda q: [])
    assert hasil["buntu_sebelum"] is True
    assert hasil["terselamatkan"] is False
    assert hasil["nol_hasil_sebelum"] is True
