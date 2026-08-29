import React, { useState } from 'react';
import { Dropdown, Search } from '@carbon/react';
import EvalPanel from './eval-panel.component';
import SearchResults from './search-results.component';
import { useUnifiedSearch } from './use-unified-search';
import styles from './ablation.scss';

type Mode = 'b0' | 'b1' | 'e1' | 'e3';

const MODE_ITEMS: Array<{ id: Mode; text: string; keterangan: string }> = [
  {
    id: 'b0',
    text: 'b0 — pencocokan awalan (gaya legacy UI)',
    keterangan:
      'Baseline kami, bukan tiruan setia mesin pencarian OpenMRS: cocok jika awalan kata sama persis. Tidak tahan salah ketik. Diuji lebih lemah dari fuzzy-search OpenMRS asli (docs/keputusan.md "E1") — lihat halaman Perbandingan Pencarian.',
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
    text: 'e3 — e1 + Weighted RRF (default halaman Pencarian Terpadu)',
    keterangan:
      'e1 ditambah Weighted RRF (K6) untuk menggabungkan enam jenis data. Perbaikan kecil dibanding e1 (+0,013 nDCG@10, p=0,039) — jangan dibaca setara kepingan karakter.',
  },
];

/**
 * Pemilih mode b0/b1/e1/e3 dan panel evaluasi, dipisah dari halaman Pencarian
 * Terpadu supaya halaman itu tetap sederhana (selalu e3) untuk pemakaian
 * sehari-hari. Halaman ini untuk membandingkan komponen K3-K6 satu per satu.
 */
const Ablation: React.FC = () => {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<Mode>('e3');

  const { data, isLoading, error } = useUnifiedSearch(query, mode);

  const modeAktif = MODE_ITEMS.find((m) => m.id === mode) ?? MODE_ITEMS[3];

  return (
    <div className={styles.container}>
      <h3 className={styles.judul}>Pengujian Ablasi</h3>
      <p className={styles.penjelasan}>
        Membandingkan komponen K3-K6 satu per satu pada query hidup. Halaman
        "Pencarian Terpadu" selalu memakai mode e3 (Weighted RRF) secara default —
        halaman ini untuk keperluan pengujian, bukan pemakaian sehari-hari.
      </p>

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
          id="ablation-mode"
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

      <SearchResults data={data} isLoading={isLoading} error={error} />

      <EvalPanel />
    </div>
  );
};

export default Ablation;
