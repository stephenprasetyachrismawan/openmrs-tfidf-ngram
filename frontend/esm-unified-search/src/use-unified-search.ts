import { useEffect, useState } from 'react';
import { openmrsFetch, restBaseUrl, useDebounce } from '@openmrs/esm-framework';
import type { SearchResponse } from './search-types';

/**
 * Dipakai bersama oleh halaman Pencarian Terpadu (mode tetap e3), Pengujian
 * Ablasi (mode bisa dipilih), dan saran ketikan di kotak pencarian navbar
 * (mode b0, limit kecil, lihat nav-search-form.component.tsx).
 */
export function useUnifiedSearch(query: string, mode: string, limit = 20) {
  const [data, setData] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const debouncedQuery = useDebounce(query, 150);

  useEffect(() => {
    const q = debouncedQuery.trim();
    if (q.length === 0) {
      setData(null);
      setError(null);
      setIsLoading(false);
      return undefined;
    }
    let dibatalkan = false;
    setIsLoading(true);
    setError(null);
    const url = `${restBaseUrl}/unifiedsearch?q=${encodeURIComponent(q)}&mode=${mode}&limit=${limit}`;
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
  }, [debouncedQuery, mode, limit]);

  return { data, isLoading, error };
}
