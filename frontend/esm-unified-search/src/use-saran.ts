import { useEffect, useState } from 'react';
import { openmrsFetch, restBaseUrl, useDebounce } from '@openmrs/esm-framework';
import type { SearchResponse } from './search-types';

/**
 * Saran ketikan untuk dropdown navbar -- endpoint terpisah dari /unifiedsearch
 * (lihat UnifiedSearchService.saran di backend): Jaccard kepingan-dua-huruf,
 * bukan salah satu mode b0/b1/e1/e3, jadi tidak ambil parameter mode.
 */
export function useSaran(query: string, limit = 6) {
  const [data, setData] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const debouncedQuery = useDebounce(query, 150);

  useEffect(() => {
    const q = debouncedQuery.trim();
    if (q.length === 0) {
      setData(null);
      setIsLoading(false);
      return undefined;
    }
    let dibatalkan = false;
    setIsLoading(true);
    const url = `${restBaseUrl}/unifiedsearch/saran?q=${encodeURIComponent(q)}&limit=${limit}`;
    openmrsFetch<SearchResponse>(url)
      .then((res) => {
        if (!dibatalkan) {
          setData(res.data);
        }
      })
      .catch(() => {
        if (!dibatalkan) {
          setData(null);
        }
      })
      .finally(() => {
        if (!dibatalkan) {
          setIsLoading(false);
        }
      });
    return () => {
      dibatalkan = true;
    };
  }, [debouncedQuery, limit]);

  return { data, isLoading };
}
