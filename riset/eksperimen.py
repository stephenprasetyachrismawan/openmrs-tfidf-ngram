# -*- coding: utf-8 -*-
"""
Eksperimen TF-IDF untuk pencarian konsep pada OpenMRS.
Korpus: demo data resmi OpenMRS (referenceapplication) dari MariaDB di Docker.
Murni Python (tanpa sklearn) supaya setiap komponen skor transparan & bisa diaudit.
"""
import json, math, re, random, time, os
from collections import defaultdict, Counter

RNG = random.Random(20260820)
BASE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(BASE, 'data', 'konsep.jsonl')
OUT = os.path.join(BASE, 'hasil')
os.makedirs(OUT, exist_ok=True)

STOP = set("a an the of and or in on for with to by is are was were as at from not no".split())

def norm(s):
    return re.sub(r'[^a-z0-9 ]+', ' ', (s or '').lower())

def stem(w):
    if len(w) > 4:
        if w.endswith('ies'):
            return w[:-3] + 'y'
        for s in ('ing', 'ed', 'es', 's'):
            if w.endswith(s) and len(w) - len(s) >= 3:
                return w[:-len(s)]
    return w

def tok(s):
    return [stem(w) for w in norm(s).split() if w and w not in STOP]


# ----------------------------------------------------------------- pemuatan
def muat():
    docs = []
    for ln in open(DATA, encoding='utf-8'):
        ln = ln.strip()
        if not ln:
            continue
        d = json.loads(ln)
        nama = d.get('nama') or ''
        syn = [x for x in (d.get('sinonim') or []) if x]
        kode = d.get('kode') or []
        if not nama and not syn:
            continue
        kode_txt = ' '.join(
            ((k.get('sumber') or '') + ' ' + (k.get('kode') or '') + ' ' + (k.get('nama') or ''))
            for k in kode)
        docs.append({
            'id': int(d['id']), 'uuid': d['uuid'], 'kelas': d.get('kelas') or '',
            'nama': nama, 'pref': [x for x in (d.get('pref') or []) if x], 'sinonim': syn,
            'kode_list': [((k.get('sumber') or ''), (k.get('kode') or '')) for k in kode],
            'f': {'nama': nama, 'sinonim': ' '.join(syn), 'kode': kode_txt,
                  'desk': d.get('deskripsi') or ''},
            'semua_nama': [nama] + syn if nama else syn,
            'n_obs': int(d.get('n_obs') or 0),
        })
    return docs


# ------------------------------------------------------------------- indeks
class Indeks:
    """TF-IDF skema ltc (log-tf * idf, dinormalisasi kosinus) + BM25 pada satu field."""

    def __init__(self, docs, field, ngram=None):
        self.N = len(docs)
        self.ngram = ngram
        self.field = field
        self.tfs, self.dl = [], []
        df = Counter()
        for d in docs:
            teks = ' '.join(d['f'].values()) if field == 'gabung' else d['f'][field]
            c = Counter(self._terms(teks))
            self.tfs.append(c)
            self.dl.append(sum(c.values()))
            for w in c:
                df[w] += 1
        self.df = df
        self.avgdl = (sum(self.dl) / self.N) if self.N else 1.0
        self.idf = {w: math.log(self.N / n) + 1.0 for w, n in df.items()}
        self.idf_bm = {w: math.log(1 + (self.N - n + 0.5) / (n + 0.5)) for w, n in df.items()}
        self.inv = defaultdict(list)
        for i, c in enumerate(self.tfs):
            v = {w: (1 + math.log(tf)) * self.idf[w] for w, tf in c.items()}
            nrm = math.sqrt(sum(x * x for x in v.values())) or 1.0
            for w, x in v.items():
                self.inv[w].append((i, x / nrm))

    def _terms(self, s):
        if self.ngram:
            s = ' ' + ' '.join(norm(s).split()) + ' '
            n = self.ngram
            return [s[k:k + n] for k in range(max(0, len(s) - n + 1))]
        return tok(s)

    def q_terms(self, q):
        return self._terms(q)

    def cosine(self, qw):
        """qw: dict term -> bobot mentah (tf, boleh pecahan untuk term hasil ekspansi)."""
        idf_oov = math.log(self.N) + 1.0
        qv = {w: (1 + math.log(t)) * self.idf.get(w, idf_oov)
              for w, t in qw.items() if t > 0}
        nq = math.sqrt(sum(x * x for x in qv.values())) or 1.0
        sc = defaultdict(float)
        for w, x in qv.items():
            for i, y in self.inv.get(w, ()):
                sc[i] += (x / nq) * y
        return sc

    def bm25(self, qw, k1=1.2, b=0.75):
        sc = defaultdict(float)
        for w, qtf in qw.items():
            idf = self.idf_bm.get(w)
            if idf is None:
                continue
            for i, _ in self.inv.get(w, ()):
                tf = self.tfs[i].get(w, 0)
                sc[i] += qtf * idf * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * self.dl[i] / self.avgdl))
        return sc


def urut(sc, k=20):
    return sorted(sc.items(), key=lambda x: (-x[1], x[0]))[:k]


# ------------------------------------------------- B0: baseline heuristik OpenMRS
class BaselineOpenMRS:
    """Reimplementasi mekanisme perangkingan bawaan OpenMRS/OCL sebagaimana
    didokumentasikan komunitas: exact mapping code +10000, exact name +1000,
    locale-preferred +500, word-prefix match +100, dan preferensi nama pendek.
    Kandidat dibatasi nama yang SELURUH kata query-nya cocok sebagai prefiks kata
    -- inilah yang membuat kasus seperti 'pulm edem' mengembalikan 0 hasil."""

    def __init__(self, docs):
        self.docs = docs
        self.by_word = defaultdict(set)
        self.kode_exact = defaultdict(set)
        self.pref = []
        for i, d in enumerate(docs):
            self.pref.append(set(' '.join(norm(x).split()) for x in d['pref']))
            for nm in d['semua_nama']:
                nn = ' '.join(norm(nm).split())
                if not nn:
                    continue
                for w in nn.split():
                    self.by_word[w].add(i)
            for _src, code in d['kode_list']:
                c = ' '.join(norm(code).split())
                if c:
                    self.kode_exact[c].add(i)
        self.vocab = list(self.by_word.keys())

    def cari(self, q, k=20):
        qn = ' '.join(norm(q).split())
        qw = qn.split()
        if not qw:
            return []
        cand = None
        for w in qw:
            s = set()
            for w2 in self.vocab:
                if w2.startswith(w):
                    s |= self.by_word[w2]
            cand = s if cand is None else (cand & s)
            if not cand:
                break
        cand = cand or set()
        skor = {}
        for i in cand:
            d = self.docs[i]
            best = -1e9
            for nm in d['semua_nama']:
                nn = ' '.join(norm(nm).split())
                if not nn:
                    continue
                s = 0.0
                if nn == qn:
                    s += 1000.0
                    if nn in self.pref[i]:
                        s += 500.0
                nt = nn.split()
                if all(any(t.startswith(w) for t in nt) for w in qw):
                    s += 100.0
                s -= 0.5 * len(nn)
                best = max(best, s)
            skor[i] = best
        for i in self.kode_exact.get(qn, ()):
            skor[i] = skor.get(i, 0.0) + 10000.0
        return urut(skor, k)


# ------------------------------------------------ ekspansi query (aset OpenMRS)
class Ekspander:
    """Query expansion memakai sinonim & mapping terminologi yang SUDAH ADA di
    kamus konsep OpenMRS (CIEL/OCL). Tidak butuh UMLS/MeSH eksternal."""

    def __init__(self, docs):
        self.docs = docs
        self.nama2idx = defaultdict(set)
        for i, d in enumerate(docs):
            for nm in d['semua_nama']:
                nn = ' '.join(norm(nm).split())
                if nn:
                    self.nama2idx[nn].add(i)

    def perluas(self, q, alpha=0.45, maks_konsep=3):
        qw = Counter(tok(q))
        qn = ' '.join(norm(q).split())
        cocok = list(self.nama2idx.get(qn, ()))[:maks_konsep]
        if not cocok:
            # cocokkan sub-frasa terpanjang
            kata = qn.split()
            for n in range(len(kata), 1, -1):
                for s in range(len(kata) - n + 1):
                    sub = ' '.join(kata[s:s + n])
                    if sub in self.nama2idx:
                        cocok = list(self.nama2idx[sub])[:maks_konsep]
                        break
                if cocok:
                    break
        for i in cocok:
            d = self.docs[i]
            for nm in d['semua_nama']:
                for w in tok(nm):
                    qw[w] = qw.get(w, 0) + alpha
            for _src, code in d['kode_list']:
                for w in tok(code):
                    qw[w] = qw.get(w, 0) + alpha
        return dict(qw), len(cocok)


# ------------------------------------------------------- query set & relevansi
KELAS_KLINIS = {'Diagnosis', 'Finding', 'Symptom', 'Symptom/Finding',
                'Procedure', 'Test', 'Drug', 'Misc', 'Question'}

def buat_query(docs, n=300):
    kand = [d for d in docs
            if d['sinonim'] and d['nama'] and d['kelas'] in KELAS_KLINIS
            and len(norm(d['nama']).split()) >= 2]
    RNG.shuffle(kand)
    qs = []
    for d in kand:
        if len(qs) >= n:
            break
        src = RNG.choice(d['sinonim'] + [d['nama']])
        ws = [w for w in norm(src).split() if w]
        if len(ws) < 2:
            continue
        jenis = RNG.choice(['trunkasi', 'typo', 'hilang_kata', 'urut_balik', 'persis'])
        if jenis == 'trunkasi':
            q = ' '.join(w[:4] for w in ws)
        elif jenis == 'typo':
            i = RNG.randrange(len(ws))
            w = ws[i]
            if len(w) < 5:
                continue
            j = RNG.randrange(1, len(w) - 2)
            q = ' '.join(ws[:i] + [w[:j] + w[j + 1] + w[j] + w[j + 2:]] + ws[i + 1:])
        elif jenis == 'hilang_kata':
            if len(ws) < 3:
                continue
            i = RNG.randrange(len(ws))
            q = ' '.join(ws[:i] + ws[i + 1:])
        elif jenis == 'urut_balik':
            q = ' '.join(reversed(ws))
        else:
            q = ' '.join(ws)
        qs.append({'q': q, 'jenis': jenis, 'target': d['id'], 'asal': src})
    return qs


def bangun_relevansi(docs):
    """rel=2 untuk konsep target; rel=1 untuk konsep yang berbagi reference term
    (kode terminologi non-CIEL) dengan target -- turunan langsung dari DB, bukan
    penilaian manual, sehingga reprodusibel."""
    kode2idx = defaultdict(set)
    for i, d in enumerate(docs):
        for src, code in d['kode_list']:
            if src and code and src.upper() != 'CIEL':
                kode2idx[(src, code)].add(i)
    terkait = defaultdict(set)
    for _kd, ids in kode2idx.items():
        if 1 < len(ids) <= 8:
            for i in ids:
                terkait[i] |= (ids - {i})
    return terkait


# ------------------------------------------------------------------- metrik
def dcg(rels):
    return sum((2 ** r - 1) / math.log2(i + 2) for i, r in enumerate(rels))

def evaluasi(hasil, rel_map, k_ndcg=10):
    """hasil: list peringkat indeks dokumen. rel_map: dict idx->relevansi."""
    rels = [rel_map.get(i, 0) for i in hasil]
    ideal = sorted(rel_map.values(), reverse=True)[:k_ndcg]
    idcg = dcg(ideal) or 1.0
    m = {}
    m['P@1'] = 1.0 if rels[:1] and rels[0] > 0 else 0.0
    m['P@5'] = sum(1 for r in rels[:5] if r > 0) / 5.0
    m['P@10'] = sum(1 for r in rels[:10] if r > 0) / 10.0
    total_rel = sum(1 for v in rel_map.values() if v > 0) or 1
    m['R@10'] = sum(1 for r in rels[:10] if r > 0) / total_rel
    m['MRR'] = 0.0
    for i, r in enumerate(rels):
        if r > 0:
            m['MRR'] = 1.0 / (i + 1)
            break
    hit = 0
    ap = 0.0
    for i, r in enumerate(rels):
        if r > 0:
            hit += 1
            ap += hit / (i + 1)
    m['MAP'] = ap / total_rel
    m['nDCG@10'] = dcg(rels[:k_ndcg]) / idcg
    return m

METRIK = ['P@1', 'P@5', 'P@10', 'R@10', 'MRR', 'MAP', 'nDCG@10']


# -------------------------------------------------------------------- sistem
FIELDS = ['nama', 'sinonim', 'kode', 'desk']

class Mesin:
    def __init__(self, docs):
        t0 = time.perf_counter()
        self.docs = docs
        self.b0 = BaselineOpenMRS(docs)
        self.gab = Indeks(docs, 'gabung')
        self.fi = {f: Indeks(docs, f) for f in FIELDS}
        self.ng = Indeks(docs, 'gabung', ngram=4)
        self.eks = Ekspander(docs)
        self.waktu_indeks = time.perf_counter() - t0
        self.bobot = {'nama': 1.0, 'sinonim': 1.0, 'kode': 1.0, 'desk': 1.0}

    # -- B0
    def s_b0(self, q, k=20):
        return [i for i, _ in self.b0.cari(q, k)]

    # -- B1 TF-IDF VSM murni
    def s_b1(self, q, k=20):
        return [i for i, _ in urut(self.gab.cosine(Counter(tok(q))), k)]

    # -- B2 BM25
    def s_b2(self, q, k=20):
        return [i for i, _ in urut(self.gab.bm25(Counter(tok(q))), k)]

    # -- E1 TF-IDF + ekspansi query dari kamus OpenMRS
    def s_e1(self, q, k=20):
        qw, _ = self.eks.perluas(q)
        return [i for i, _ in urut(self.gab.cosine(qw), k)]

    # -- E2 field-weighted TF-IDF
    def _fw(self, qw):
        sc = defaultdict(float)
        for f in FIELDS:
            w = self.bobot[f]
            if w <= 0:
                continue
            for i, s in self.fi[f].cosine(qw).items():
                sc[i] += w * s
        return sc

    def s_e2(self, q, k=20):
        return [i for i, _ in urut(self._fw(Counter(tok(q))), k)]

    # -- E3 field-weighted + ekspansi
    def s_e3(self, q, k=20):
        qw, _ = self.eks.perluas(q)
        return [i for i, _ in urut(self._fw(qw), k)]

    # -- E4 E3 + fusi char 4-gram (toleran typo & token terpotong)
    def s_e4(self, q, k=20, beta=0.35):
        qw, _ = self.eks.perluas(q)
        sc = self._fw(qw)
        maks = max(sc.values()) if sc else 1.0
        ngs = self.ng.cosine(Counter(self.ng.q_terms(q)))
        mn = max(ngs.values()) if ngs else 1.0
        gab = defaultdict(float)
        for i, v in sc.items():
            gab[i] += (1 - beta) * v / (maks or 1.0)
        for i, v in ngs.items():
            gab[i] += beta * v / (mn or 1.0)
        return [i for i, _ in urut(gab, k)]


# --------------------------------------------------------------------- runner
def jalankan(mesin, sistem, queries, id2idx, terkait, k=20):
    agg = defaultdict(list)
    lat = []
    per_q = []
    for qq in queries:
        ti = id2idx.get(qq['target'])
        if ti is None:
            continue
        rel = {ti: 2}
        for j in terkait.get(ti, ()):
            rel.setdefault(j, 1)
        t0 = time.perf_counter()
        hasil = sistem(qq['q'], k)
        lat.append((time.perf_counter() - t0) * 1000.0)
        m = evaluasi(hasil, rel)
        for a, b in m.items():
            agg[a].append(b)
        agg['_jenis_' + qq['jenis']].append(m['nDCG@10'])
        per_q.append({'q': qq['q'], 'jenis': qq['jenis'], 'target': qq['target'],
                      'ndcg': round(m['nDCG@10'], 4), 'mrr': round(m['MRR'], 4),
                      'top1': mesin.docs[hasil[0]]['nama'] if hasil else None,
                      'n_hasil': len(hasil)})
    out = {a: (sum(v) / len(v) if v else 0.0) for a, v in agg.items()}
    out['latensi_ms'] = sum(lat) / len(lat) if lat else 0.0
    out['kosong'] = sum(1 for p in per_q if p['n_hasil'] == 0) / max(1, len(per_q))
    return out, per_q


def tuning_bobot(mesin, dev, id2idx, terkait):
    """Coordinate ascent pada bobot field, dioptimalkan HANYA di split dev."""
    grid = [0.0, 0.25, 0.5, 1.0, 2.0, 4.0]
    best = dict(mesin.bobot)
    skor_best = jalankan(mesin, mesin.s_e2, dev, id2idx, terkait)[0]['nDCG@10']
    for _ronde in range(2):
        for f in FIELDS:
            asal = best[f]
            for g in grid:
                mesin.bobot = dict(best)
                mesin.bobot[f] = g
                s = jalankan(mesin, mesin.s_e2, dev, id2idx, terkait)[0]['nDCG@10']
                if s > skor_best + 1e-9:
                    skor_best, asal = s, g
            best[f] = asal
    mesin.bobot = best
    return best, skor_best


# ----------------------------------------------------------- kasus dokumentasi
KASUS = [
    ('acetaminophen', 'exact name harus di peringkat 1'),
    ('type 2 diabetes', 'sinonim persis terkubur di peringkat 17'),
    ('pulm edem', 'partial token -> 0 hasil di OpenMRS'),
    ('aspirin', 'tertarik ke acetaminophen via sinonim "aspirin free"'),
    ('malaria', 'kontrol'),
    ('hypertension', 'kontrol'),
    ('tuberculosis', 'kontrol'),
    ('diabete melitus', 'typo'),
    ('preg test', 'trunkasi'),
    ('fever headache', 'multi-gejala'),
]

def lapor_kasus(mesin):
    baris = []
    sis = [('B0-OpenMRS', mesin.s_b0), ('B1-TFIDF', mesin.s_b1),
           ('E3-QE+field', mesin.s_e3), ('E4-+ngram', mesin.s_e4)]
    for q, catatan in KASUS:
        r = {'query': q, 'catatan': catatan}
        for nama, fn in sis:
            h = fn(q, 5)
            r[nama] = ' ; '.join(mesin.docs[i]['nama'][:38] for i in h[:3]) or '(0 hasil)'
        baris.append(r)
    return baris


# ----------------------------------------------------------------------- main
def main():
    print('Memuat korpus...')
    docs = muat()
    id2idx = {d['id']: i for i, d in enumerate(docs)}
    print(f'  {len(docs)} konsep, '
          f'{sum(len(d["sinonim"]) for d in docs)} sinonim, '
          f'{sum(len(d["kode_list"]) for d in docs)} mapping terminologi')

    mesin = Mesin(docs)
    print(f'  waktu indeks: {mesin.waktu_indeks:.2f} s')

    terkait = bangun_relevansi(docs)
    queries = buat_query(docs, n=300)
    RNG.shuffle(queries)
    dev, test = queries[:100], queries[100:]
    print(f'  query: {len(dev)} dev / {len(test)} test')

    print('Tuning bobot field pada split dev...')
    bobot, s_dev = tuning_bobot(mesin, dev, id2idx, terkait)
    print(f'  bobot={bobot}  nDCG@10(dev)={s_dev:.4f}')

    sistem = [('B0  OpenMRS heuristik', mesin.s_b0),
              ('B1  TF-IDF VSM', mesin.s_b1),
              ('B2  BM25', mesin.s_b2),
              ('E1  TF-IDF + QE', mesin.s_e1),
              ('E2  TF-IDF field-weighted', mesin.s_e2),
              ('E3  field-weighted + QE', mesin.s_e3),
              ('E4  E3 + char 4-gram', mesin.s_e4)]

    hasil = {}
    per_q_all = {}
    for nama, fn in sistem:
        print(f'  menjalankan {nama} ...', flush=True)
        m, pq = jalankan(mesin, fn, test, id2idx, terkait)
        hasil[nama] = m
        per_q_all[nama] = pq

    sig = {}
    for nama in per_q_all:
        if nama.startswith('B0'):
            continue
        sig[nama] = {
            'vs_B0': bootstrap(per_q_all[nama], per_q_all['B0  OpenMRS heuristik']),
            'vs_B1': bootstrap(per_q_all[nama], per_q_all['B1  TF-IDF VSM']),
        }

    kasus = lapor_kasus(mesin)
    tulis_laporan(docs, mesin, hasil, kasus, bobot, len(dev), len(test), sig)
    with open(os.path.join(OUT, 'per_query.json'), 'w', encoding='utf-8') as f:
        json.dump(per_q_all, f, ensure_ascii=False, indent=1)
    print('\nSelesai. Lihat hasil/laporan.md')


JENIS = ['persis', 'typo', 'trunkasi', 'hilang_kata', 'urut_balik']

def bootstrap(pq_a, pq_b, n=5000, metrik='ndcg'):
    """Paired bootstrap: selisih rerata metrik A-B, CI 95% dan p dua sisi."""
    a = [p[metrik] for p in pq_a]
    b = [p[metrik] for p in pq_b]
    d = [x - y for x, y in zip(a, b)]
    m = len(d)
    if m == 0:
        return (0.0, 0.0, 0.0, 1.0)
    obs = sum(d) / m
    r = random.Random(7)
    sampel = []
    lebih = 0
    for _ in range(n):
        s = sum(d[r.randrange(m)] for _ in range(m)) / m
        sampel.append(s)
        if abs(s - obs) >= abs(obs):
            lebih += 1
    sampel.sort()
    lo = sampel[int(0.025 * n)]
    hi = sampel[int(0.975 * n)]
    return (obs, lo, hi, lebih / n)


def tulis_laporan(docs, mesin, hasil, kasus, bobot, n_dev, n_test, sig=None):
    L = []
    A = L.append
    A('# Hasil Eksperimen TF-IDF pada Demo Data OpenMRS\n')
    A(f'Korpus: **{len(docs)} konsep** dari database MariaDB OpenMRS '
      f'(distro referenceapplication, demo data resmi).  ')
    A(f'Sinonim: {sum(len(d["sinonim"]) for d in docs)} · '
      f'Mapping terminologi: {sum(len(d["kode_list"]) for d in docs)}  ')
    A(f'Waktu indeks: {mesin.waktu_indeks:.2f} s · '
      f'Query: {n_dev} dev (tuning) / {n_test} test (dilaporkan)\n')
    A(f'Bobot field hasil tuning di dev: `{bobot}`\n')

    A('## Tabel utama (split test)\n')
    A('| Sistem | ' + ' | '.join(METRIK) + ' | latensi ms | query 0-hasil |')
    A('|---|' + '---|' * (len(METRIK) + 2))
    for nama, m in hasil.items():
        A('| ' + nama + ' | ' + ' | '.join(f'{m[k]:.3f}' for k in METRIK)
          + f' | {m["latensi_ms"]:.1f} | {m["kosong"]*100:.0f}% |')

    A('\n## nDCG@10 per jenis degradasi query\n')
    A('| Sistem | ' + ' | '.join(JENIS) + ' |')
    A('|---|' + '---|' * len(JENIS))
    for nama, m in hasil.items():
        A('| ' + nama + ' | ' + ' | '.join(
            f'{m.get("_jenis_"+j, 0.0):.3f}' for j in JENIS) + ' |')

    if sig:
        A('\n## Uji signifikansi (paired bootstrap 5.000x, selisih nDCG@10)\n')
        A('| Sistem | vs B0 (OpenMRS) | CI95 | p | vs B1 (TF-IDF) | CI95 | p |')
        A('|---|---|---|---|---|---|---|')
        for nama, s in sig.items():
            a, alo, ahi, ap = s['vs_B0']
            b, blo, bhi, bp = s['vs_B1']
            A(f'| {nama} | {a:+.3f} | [{alo:+.3f}, {ahi:+.3f}] | {ap:.3f} '
              f'| {b:+.3f} | [{blo:+.3f}, {bhi:+.3f}] | {bp:.3f} |')

    A('\n## Kasus yang didokumentasikan komunitas OpenMRS\n')
    A('| Query | Catatan | B0-OpenMRS | B1-TFIDF | E3-QE+field | E4-+ngram |')
    A('|---|---|---|---|---|---|')
    for r in kasus:
        A(f"| `{r['query']}` | {r['catatan']} | {r['B0-OpenMRS']} | {r['B1-TFIDF']} "
          f"| {r['E3-QE+field']} | {r['E4-+ngram']} |")

    with open(os.path.join(OUT, 'laporan.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(L))
    with open(os.path.join(OUT, 'ringkasan.csv'), 'w', encoding='utf-8') as f:
        f.write('sistem,' + ','.join(METRIK) + ',latensi_ms\n')
        for nama, m in hasil.items():
            f.write(nama + ',' + ','.join(f'{m[k]:.4f}' for k in METRIK)
                    + f',{m["latensi_ms"]:.2f}\n')
    print('\n'.join(L[:40]))


if __name__ == '__main__':
    main()
