# -*- coding: utf-8 -*-
"""Membangun seluruh gambar artikel langsung dari berkas hasil eksperimen.
Tidak ada angka yang diketik ulang di sini - semuanya dibaca dari riset/hasil*/.
Korpus 8 entitas: hasil6 (K1 + sapuan), hasil4 (B0-prime), hasil5 (saran ketik).
"""
import json, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

DIR  = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(DIR, "..", ".."))
H3   = json.load(open(os.path.join(ROOT, "riset", "hasil6", "hasil.json"), encoding="utf-8"))
H4   = json.load(open(os.path.join(ROOT, "riset", "hasil4", "hasil.json"), encoding="utf-8"))
H5   = json.load(open(os.path.join(ROOT, "riset", "hasil5", "hasil.json"), encoding="utf-8"))
SW   = json.load(open(os.path.join(ROOT, "riset", "hasil6", "sapuan_dev.json"), encoding="utf-8"))

plt.rcParams.update({
    "font.family": "serif", "font.serif": ["Times New Roman", "DejaVu Serif"],
    "font.size": 8, "axes.linewidth": 0.6, "savefig.bbox": "tight",
    "axes.spines.top": False, "axes.spines.right": False,
})

ABU, GELAP, AKSEN = "#b8b8b8", "#4a4a4a", "#1a1a1a"
NL = chr(10)


def simpan(fig, nama):
    p = os.path.join(DIR, nama)
    fig.savefig(p + ".pdf")
    plt.close(fig)
    print("tulis", p + ".pdf")


# ---- Gambar 1: nDCG@10 per sistem pada test set 180 query -------------------
def gambar_sistem():
    urut  = ["B0", "B1", "B2", "E2", "E4", "E1", "E3"]
    label = ["B0\nawalan", "B1\nkata", "B2\nBM25", "E2\nRRF baku",
             "E4\n+PRF", "E1\n+kepingan", "E3\nusulan"]
    nilai = [H3["ringkas"][s]["ndcg"] for s in urut]
    warna = [AKSEN if s in ("E1", "E3") else ABU for s in urut]
    fig, ax = plt.subplots(figsize=(3.4, 2.0))
    b = ax.bar(range(len(urut)), nilai, color=warna, width=0.68)
    for r, v in zip(b, nilai):
        ax.text(r.get_x() + r.get_width() / 2, v + 0.012, "%.3f" % v,
                ha="center", va="bottom", fontsize=6.4)
    ax.set_xticks(range(len(urut)))
    ax.set_xticklabels(label, fontsize=6.2)
    ax.set_ylabel("nDCG@10")
    ax.set_ylim(0, 0.95)
    ax.grid(axis="y", lw=0.35, color="#e2e2e2")
    ax.set_axisbelow(True)
    simpan(fig, "gambar1-sistem")


# ---- Gambar 2: nDCG per jenis degradasi query -------------------------------
def gambar_tipe():
    tipe  = ["persis", "hilang_kata", "urut_balik", "trunkasi", "typo"]
    nama  = ["persis", "hilang\nkata", "urut\nbalik", "trunkasi", "typo"]
    sist  = [("B0", ABU, "//"), ("B1", "#8c8c8c", "\\\\"), ("E3", AKSEN, "")]
    fig, ax = plt.subplots(figsize=(3.4, 2.05))
    w = 0.26
    for i, (s, c, h) in enumerate(sist):
        v = [H3["per_tipe"][s][t] for t in tipe]
        ax.bar([x + (i - 1) * w for x in range(len(tipe))], v, width=w,
               color=c, label=s, hatch=h, edgecolor="white", linewidth=0.4)
    ax.set_xticks(range(len(tipe)))
    ax.set_xticklabels(nama, fontsize=6.4)
    ax.set_ylabel("nDCG@10")
    ax.set_ylim(0, 1.0)
    ax.legend(frameon=False, fontsize=6.4, ncol=3, loc="upper center",
              bbox_to_anchor=(0.5, 1.16))
    ax.grid(axis="y", lw=0.35, color="#e2e2e2")
    ax.set_axisbelow(True)
    v_typo = H3["per_tipe"]["B0"]["typo"]
    ax.annotate("B0 runtuh (%s)" % ("%.3f" % v_typo).replace(".", ","),
                xy=(4 - w, max(v_typo, 0.10)), xytext=(2.72, 0.42),
                fontsize=5.8, ha="center", color=GELAP,
                bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none"),
                arrowprops=dict(arrowstyle="->", lw=0.5, color=GELAP))
    simpan(fig, "gambar2-tipe")


# ---- Gambar 3: sapuan parameter pada 100 query dev --------------------------
def gambar_sapuan():
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(3.4, 1.75),
                                 gridspec_kw=dict(wspace=0.40))
    al = SW["sweep_alpha_ulang_dev"]
    xs = sorted(float(k) for k in al)
    ys = [al["%g" % x] if ("%g" % x) in al else al[str(x)] for x in xs]
    a1.plot(xs, ys, "-o", color=AKSEN, lw=0.9, ms=2.4)
    a1.axvline(0.20, color=GELAP, lw=0.7, ls="--")
    a1.text(0.22, 0.72, r"$\alpha$=0,20", fontsize=6, color=GELAP)
    a1.set_xlabel(r"$\alpha$ (bobot jalur kata)", fontsize=6.6)
    a1.set_ylabel("nDCG@10 (dev)", fontsize=6.6)
    a1.tick_params(labelsize=6)
    a1.grid(lw=0.35, color="#e9e9e9")
    a1.set_axisbelow(True)

    ng = SW["sweep_ngram_dev"]
    xs2 = sorted(int(k) for k in ng)
    ys2 = [ng[str(x)] for x in xs2]
    a2.plot(xs2, ys2, "-o", color=AKSEN, lw=0.9, ms=2.4)
    a2.axvline(4, color=GELAP, lw=0.7, ls="--")
    a2.text(4.12, 0.79, "n=4", fontsize=6, color=GELAP)
    a2.set_xlabel("panjang kepingan $n$", fontsize=6.6)
    a2.tick_params(labelsize=6)
    a2.set_xticks(xs2)
    a2.grid(lw=0.35, color="#e9e9e9")
    a2.set_axisbelow(True)
    for ax in (a1, a2):
        ax.ticklabel_format(useOffset=False, style='plain', axis='y')
        ax.yaxis.get_offset_text().set_visible(False)
    simpan(fig, "gambar3-sapuan")




# ---- Gambar 4: E3 vs baseline OpenMRS asli (B0'), 42 query konsep -----------
def gambar_b0prime():
    d = H4["ringkasan"]
    urut  = ["b0", "b1", "b0prime_korpus", "e1", "e3"]
    label = ["B0" + NL + "awalan", "B1" + NL + "kata",
             "B0$'$" + NL + "OpenMRS asli", "E1" + NL + "+kepingan",
             "E3" + NL + "usulan"]
    nilai = [d[s]["ndcg"] for s in urut]
    warna = [ABU, ABU, GELAP, ABU, AKSEN]
    fig, ax = plt.subplots(figsize=(3.4, 1.95))
    b = ax.bar(range(len(urut)), nilai, color=warna, width=0.62)
    for r, v in zip(b, nilai):
        ax.text(r.get_x() + r.get_width() / 2, v + 0.014, "%.3f" % v,
                ha="center", va="bottom", fontsize=6.4)
    u = H4["uji_bootstrap_seed7_top10"]["e3_vs_b0prime_korpus"]
    ax.annotate("", xy=(2, 0.955), xytext=(4, 0.955),
                arrowprops=dict(arrowstyle="<->", lw=0.6, color=GELAP))
    ax.text(3, 0.968, "$+%.3f$, $p=%.4f$" % (u["obs"], u["p"]),
            ha="center", fontsize=6, color=GELAP)
    ax.set_xticks(range(len(urut)))
    ax.set_xticklabels(label, fontsize=6.2)
    ax.set_ylabel("nDCG@10")
    ax.set_ylim(0, 1.10)
    ax.grid(axis="y", lw=0.35, color="#e2e2e2")
    ax.set_axisbelow(True)
    simpan(fig, "gambar4-b0prime")


# ---- Gambar 5: akurasi saran K2 dan penyelamatan query buntu ----------------
def gambar_k2():
    tipe = ["persis", "typo", "trunkasi", "trunkasi_pendek", "typo_pendek"]
    nama = ["persis", "typo", "trunkasi", "trunkasi pendek", "typo pendek"]
    a = H5["akurasi"]
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(3.4, 2.05),
                                 gridspec_kw=dict(width_ratios=[1.5, 1],
                                                  wspace=0.42))
    w = 0.38
    for i, (m, c, h) in enumerate([("hit@1", ABU, "//"), ("hit@6", AKSEN, "")]):
        a1.bar([x + (i - 0.5) * w for x in range(len(tipe))],
               [a[t][m] for t in tipe], width=w, color=c, label=m,
               hatch=h, edgecolor="white", linewidth=0.4)
    a1.set_xticks(range(len(tipe)))
    a1.set_xticklabels(nama, fontsize=5.4, rotation=32, ha="right",
                       rotation_mode="anchor")
    a1.set_ylabel("akurasi saran", fontsize=6.6)
    a1.set_ylim(0, 1.12)
    a1.tick_params(labelsize=6)
    a1.legend(frameon=False, fontsize=6, ncol=2, loc="upper center",
              bbox_to_anchor=(0.5, 1.20))
    a1.grid(axis="y", lw=0.35, color="#e9e9e9")
    a1.set_axisbelow(True)

    k = H5["penyelamatan"]["keseluruhan"]
    x = ["buntu sebelum", "buntu sesudah", "0-hasil sebelum", "0-hasil sesudah"]
    y = [k["buntu_sebelum"], k["buntu_efektif_sesudah"],
         k["nol_hasil_sebelum"], k["nol_hasil_sesudah"]]
    b = a2.bar(range(4), y, color=[ABU, AKSEN, ABU, AKSEN], width=0.66)
    for r, v in zip(b, y):
        a2.text(r.get_x() + r.get_width() / 2, v + 0.004, "%.1f%%" % (100 * v),
                ha="center", va="bottom", fontsize=5.8)
    a2.set_xticks(range(4))
    a2.set_xticklabels(x, fontsize=5.2, rotation=32, ha="right",
                       rotation_mode="anchor")
    a2.set_ylabel("proporsi query", fontsize=6.6)
    a2.set_ylim(0, 0.115)
    a2.tick_params(labelsize=6)
    a2.grid(axis="y", lw=0.35, color="#e9e9e9")
    a2.set_axisbelow(True)
    simpan(fig, "gambar5-k2")


if __name__ == "__main__":
    gambar_sistem()
    gambar_tipe()
    gambar_sapuan()
    gambar_b0prime()
    gambar_k2()
