import React, { useEffect, useState } from 'react';
import { Button } from '@carbon/react';
import SearchResults from './search-results.component';
import { useUnifiedSearch } from './use-unified-search';
import styles from './unified-search.scss';

const CONTOH = ['diabete melitus', 'pnemonia', 'pulm edem', 'hypertension'];

/**
 * Halaman default pengguna sehari-hari. Kotak pencarian ada di navbar
 * (nav-search-form.component.tsx), bukan di halaman ini -- lihat
 * pengalihan lewat parameter ?q= di bawah. Selalu memakai mode e3 (E1 +
 * Weighted RRF); lihat halaman Pengujian Ablasi untuk membandingkan
 * komponen K3-K6 satu per satu.
 */
const UnifiedSearch: React.FC = () => {
  const [query, setQuery] = useState('');

  /**
   * Isi dari ?q= saat halaman dibuka lewat kotak pencarian di navbar, dan
   * dipakai ulang tiap kali single-spa mencatat navigasi baru -- navigasi
   * dari navbar tidak me-remount komponen ini karena route-nya sama persis
   * (cuma query string yang berbeda), jadi efek sekali-jalan saja tidak
   * pernah terpicu ulang untuk pencarian kedua dan seterusnya. App ini tidak
   * dibungkus <Router> react-router-dom (tidak dipakai di mana pun di repo
   * ini), jadi tidak bisa pakai useLocation() -- dengarkan
   * single-spa:routing-event yang dipancarkan navigate()-nya esm-framework
   * (lihat node_modules/@openmrs/esm-navigation/src/navigation/navigate.ts).
   */
  useEffect(() => {
    const syncFromUrl = () => {
      const q = new URLSearchParams(window.location.search).get('q');
      setQuery(q ?? '');
    };
    syncFromUrl();
    window.addEventListener('single-spa:routing-event', syncFromUrl);
    return () => window.removeEventListener('single-spa:routing-event', syncFromUrl);
  }, []);

  const { data, isLoading, error } = useUnifiedSearch(query, 'e3');
  const queryKosong = query.trim().length === 0;

  return (
    <div className={styles.container}>
      <h3 className={styles.judul}>Pencarian Terpadu</h3>
      <p className={styles.penjelasan}>
        Pencarian tahan salah ketik lintas tujuh jenis data OpenMRS: konsep, obat, pasien,
        form, lokasi, provider, dan hasil lab. Ketik di kotak pencarian pada bilah navigasi
        di atas untuk mulai.
      </p>

      {queryKosong && (
        <div className={styles.kosong}>
          <p className={styles.kosongTeks}>Belum ada pencarian. Coba salah satu contoh berikut:</p>
          <div className={styles.contohBaris}>
            {CONTOH.map((c) => (
              <Button key={c} kind="tertiary" size="sm" onClick={() => setQuery(c)}>
                {c}
              </Button>
            ))}
          </div>
        </div>
      )}

      {!queryKosong && (
        <p className={styles.hasilUntuk}>
          Hasil untuk <strong>&quot;{query}&quot;</strong>
        </p>
      )}

      <SearchResults data={data} isLoading={isLoading} error={error} />
    </div>
  );
};

export default UnifiedSearch;
