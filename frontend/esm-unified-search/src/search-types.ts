/**
 * Cocok dengan bentuk badan JSON /ws/rest/v1/unifiedsearch
 * (lihat UnifiedSearchService.toResultRow di backend). Dipakai bersama oleh
 * halaman Pencarian Terpadu dan halaman Pengujian Ablasi.
 */
export interface SearchHit {
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

export interface SearchResponse {
  query: string;
  mode: string;
  results: SearchHit[];
}

export const ENTITAS_LABEL: Record<string, string> = {
  konsep: 'Konsep',
  obat: 'Obat',
  pasien: 'Pasien',
  form: 'Form',
  lokasi: 'Lokasi',
  provider: 'Provider',
  /** Ditambahkan belakangan, di luar enam entitas riset asli -- lihat docs/kontrak-data.md "Entitas ketujuh: hasillab". */
  hasillab: 'Hasil Lab',
};
