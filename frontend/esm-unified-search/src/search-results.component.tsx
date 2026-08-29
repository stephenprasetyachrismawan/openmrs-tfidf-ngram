import React from 'react';
import { InlineNotification, Loading, Tag } from '@carbon/react';
import { sorot } from './highlight';
import { ENTITAS_LABEL } from './search-types';
import type { SearchHit, SearchResponse } from './search-types';
import styles from './search-results.scss';

interface Props {
  data: SearchResponse | null;
  isLoading: boolean;
  error: string | null;
}

/** Daftar hasil terkelompok per entitas, dipakai bersama oleh halaman Pencarian Terpadu dan Pengujian Ablasi. */
const SearchResults: React.FC<Props> = ({ data, isLoading, error }) => {
  if (isLoading) {
    return <Loading small withOverlay={false} description="Mencari..." />;
  }

  if (error) {
    return <InlineNotification kind="error" title="Galat" subtitle={error} lowContrast hideCloseButton />;
  }

  if (!data) {
    return null;
  }

  if (data.results.length === 0) {
    return <p className={styles.statusKosong}>Tidak ada hasil untuk &quot;{data.query}&quot;.</p>;
  }

  const kelompok = new Map<string, SearchHit[]>();
  for (const hit of data.results) {
    if (!kelompok.has(hit.entitas)) {
      kelompok.set(hit.entitas, []);
    }
    kelompok.get(hit.entitas)!.push(hit);
  }

  return (
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
  );
};

export default SearchResults;
