import React, { useState } from 'react';
import { Dropdown, InlineNotification, Loading, Search, Tag } from '@carbon/react';
import { openmrsFetch, restBaseUrl, useDebounce } from '@openmrs/esm-framework';
import styles from './unified-search.scss';

/**
 * Cocok dengan bentuk badan JSON /ws/rest/v1/unifiedsearch
 * (lihat UnifiedSearchService.toResultRow di backend).
 */
interface SearchHit {
  entitas: string;
  id: number;
  judul: string;
  konteks: string;
  skor: number;
  skor_asli: number | null;
  peringkat_di_tabel: number;
  bobot_tabel: number | null;
  url: string;
}

interface SearchResponse {
  query: string;
  mode: string;
  results: SearchHit[];
}

type Mode = 'b0' | 'b1' | 'e1' | 'e3';

const MODE_ITEMS: Array<{ id: Mode; text: string; keterangan: string }> = [
  {
    id: 'b0',
    text: 'b0 — heuristik OpenMRS',
    keterangan: 'Aturan bawaan OpenMRS: cocok jika awalan kata sama persis. Tidak tahan salah ketik.',
  },
  {
    id: 'b1',
    text: 'b1 — TF-IDF kata saja',
    keterangan: 'TF-IDF atas kata utuh saja, tanpa kepingan karakter. Basis pembanding penelitian.',
  },
  {
    id: 'e1',
    text: 'e1 — TF-IDF kata + kepingan karakter',
    keterangan:
      "TF-IDF kata digabung kepingan karakter 4-huruf (K5) — komponen yang terbukti signifikan (+0,174 nDCG@10, p<0,001).",
  },
  {
    id: 'e3',
    text: 'e3 — e1 + Weighted RRF (default)',
    keterangan:
      'e1 ditambah Weighted RRF (K6) untuk menggabungkan enam jenis data. Perbaikan kecil dibanding e1 (+0,013 nDCG@10, p=0,039) — jangan dibaca setara kepingan karakter.',
  },
];

const ENTITAS_LABEL: Record<string, string> = {
  konsep: 'Konsep',
  obat: 'Obat',
  pasien: 'Pasien',
  form: 'Form',
  lokasi: 'Lokasi',
  provider: 'Provider',
};

/** Sorot kemunculan tiap kata query (>=2 huruf) di dalam judul, tanpa peduli huruf besar/kecil. */
function sorot(judul: string, query: string): React.ReactNode {
  const kata = query
    .trim()
    .split(/\s+/)
    .filter((w) => w.length >= 2)
    .map((w) => w.replace(/[.*+?^{}()|[\]\\$]/g, '\\$&'));
  if (kata.length === 0) {
    return judul;
  }
  const re = new RegExp(`(${kata.join('|')})`, 'ig');
  const parts = judul.split(re);
  return parts.map((part, i) => (re.test(part) ? <mark key={i}>{part}</mark> : <React.Fragment key={i}>{part}</React.Fragment>));
}

const UnifiedSearch: React.FC = () => {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<Mode>('e3');
  const [data, setData] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const debouncedQuery = useDebounce(query, 150);

  React.useEffect(() => {
    const q = debouncedQuery.trim();
    if (q.length === 0) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return;
    }
    let dibatalkan = false;
    setIsLoading(true);
    setError(null);
    const url = `${restBaseUrl}/unifiedsearch?q=${encodeURIComponent(q)}&mode=${mode}&limit=20`;
    openmrsFetch<SearchResponse>(url)
      .then((res) => {
        if (dibatalkan) {
          return;
        }
        setData(res.data);
      })
      .catch((err: Error) => {
        if (dibatalkan) {
          return;
        }
        setError(err.message || 'Permintaan gagal');
        setData(null);
      })
      .finally(() => {
        if (!dibatalkan) {
          setIsLoading(false);
        }
      });
    return () => {
      dibatalkan = true;
    };
  }, [debouncedQuery, mode]);

  const modeAktif = MODE_ITEMS.find((m) => m.id === mode) ?? MODE_ITEMS[3];

  const kelompok = new Map<string, SearchHit[]>();
  for (const hit of data?.results ?? []) {
    if (!kelompok.has(hit.entitas)) {
      kelompok.set(hit.entitas, []);
    }
    kelompok.get(hit.entitas)!.push(hit);
  }

  return (
    <div className={styles.container}>
      <h3 className={styles.judul}>Pencarian Terpadu</h3>

      <div className={styles.kontrol}>
        <Search
          size="lg"
          labelText="Kata kunci pencarian"
          placeholder="Ketik kata kunci, mis. diabete melitus"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className={styles.kotakCari}
        />
        <Dropdown
          id="unified-search-mode"
          titleText="Mode"
          label={modeAktif.text}
          items={MODE_ITEMS}
          itemToString={(item) => (item ? item.text : '')}
          selectedItem={modeAktif}
          onChange={({ selectedItem }) => selectedItem && setMode(selectedItem.id)}
          className={styles.pemilihMode}
        />
      </div>
      <p className={styles.keteranganMode}>{modeAktif.keterangan}</p>

      {isLoading && <Loading small withOverlay={false} description="Mencari..." />}

      {error && (
        <InlineNotification kind="error" title="Galat" subtitle={error} lowContrast hideCloseButton />
      )}

      {!isLoading && !error && data && data.results.length === 0 && (
        <p className={styles.statusKosong}>Tidak ada hasil untuk &quot;{data.query}&quot;.</p>
      )}

      {!isLoading && !error && data && data.results.length > 0 && (
        <div className={styles.hasil}>
          {Array.from(kelompok.entries()).map(([entitas, hits]) => (
            <div key={entitas} className={styles.kelompok}>
              <h4>
                {ENTITAS_LABEL[entitas] ?? entitas} <Tag type="gray">{hits.length}</Tag>
              </h4>
              {hits.map((hit) => (
                <div key={`${hit.entitas}:${hit.id}`} className={styles.baris}>
                  <a href={hit.url}>{sorot(hit.judul, data.query)}</a>
                  <span className={styles.skor}>skor={hit.skor}</span>
                  {hit.konteks && <div className={styles.konteks}>{hit.konteks}</div>}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default UnifiedSearch;
