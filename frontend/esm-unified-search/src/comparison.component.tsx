import React, { useState } from 'react';
import { Button, InlineNotification, Loading, Search } from '@carbon/react';
import { openmrsFetch, restBaseUrl, useDebounce } from '@openmrs/esm-framework';
import styles from './comparison.scss';

/**
 * Pengganti tugas 13 (disetujui pemilik repo, lihat docs/keputusan.md 2026-08-22
 * "D1"): RefApp 3 versi ini tidak punya extension slot di workspace Visit Note
 * (dibuktikan tugas 13/C1), jadi klaim penelitian ditunjukkan lewat halaman
 * perbandingan berdampingan, bukan menambal kotak diagnosis bawaan.
 *
 * Endpoint kolom kiri ditemukan lewat tab Network sungguhan (bukan dokumentasi):
 * GET /ws/rest/v1/concept?name=...&searchType=fuzzy&class=<Diagnosis>&v=custom:(uuid,display)
 * Dibatasi ke konsep berkelas Diagnosis oleh OpenMRS sendiri -- lihat docs/keputusan.md.
 */

const CLASS_DIAGNOSIS_UUID = '8d4918b0-c2cc-11de-8d13-0010c6dffd0f';

const CONTOH = ['diabete melitus', 'pulm edem', 'hypertension', 'alclo 0 05'];

interface HasilBaris {
  judul: string;
  skor: string;
}

interface KolomState {
  memuat: boolean;
  galat: string | null;
  jumlah: number | null;
  waktuMs: number | null;
  baris: HasilBaris[];
}

const KOLOM_KOSONG: KolomState = { memuat: false, galat: null, jumlah: null, waktuMs: null, baris: [] };

interface ConceptSearchResult {
  results: Array<{ uuid: string; display: string }>;
}

interface UnifiedSearchResult {
  results: Array<{ judul: string; skor: number }>;
}

async function cariBaseline(query: string): Promise<KolomState> {
  const mulai = performance.now();
  try {
    const url = `${restBaseUrl}/concept?name=${encodeURIComponent(query)}&searchType=fuzzy&class=${CLASS_DIAGNOSIS_UUID}&v=custom:(uuid,display)`;
    const res = await openmrsFetch<ConceptSearchResult>(url);
    const waktuMs = performance.now() - mulai;
    const semua = res.data.results ?? [];
    return {
      memuat: false,
      galat: null,
      jumlah: semua.length,
      waktuMs,
      baris: semua.slice(0, 10).map((r) => ({ judul: r.display, skor: '—' })),
    };
  } catch (err) {
    return { ...KOLOM_KOSONG, galat: err instanceof Error ? err.message : 'Permintaan gagal' };
  }
}

async function cariUnifiedSearch(query: string, mode: 'b0' | 'e3'): Promise<KolomState> {
  const mulai = performance.now();
  try {
    const url = `${restBaseUrl}/unifiedsearch?q=${encodeURIComponent(query)}&mode=${mode}&entitas=konsep&limit=10`;
    const res = await openmrsFetch<UnifiedSearchResult>(url);
    const waktuMs = performance.now() - mulai;
    const semua = res.data.results ?? [];
    return {
      memuat: false,
      galat: null,
      jumlah: semua.length,
      waktuMs,
      baris: semua.map((r) => ({ judul: r.judul, skor: r.skor.toString() })),
    };
  } catch (err) {
    return { ...KOLOM_KOSONG, galat: err instanceof Error ? err.message : 'Permintaan gagal' };
  }
}

function Kolom({ judul, keterangan, state }: { judul: string; keterangan: string; state: KolomState }) {
  return (
    <div className={styles.kolom}>
      <h4>{judul}</h4>
      <p className={styles.keteranganKolom}>{keterangan}</p>
      {state.memuat && <Loading small withOverlay={false} description="Mencari..." />}
      {state.galat && (
        <InlineNotification kind="error" title="Galat" subtitle={state.galat} lowContrast hideCloseButton />
      )}
      {!state.memuat && !state.galat && state.jumlah !== null && (
        <>
          <p className={styles.ringkasanKolom}>
            {state.jumlah} hasil · {state.waktuMs !== null ? state.waktuMs.toFixed(0) : '—'} ms
          </p>
          {state.jumlah === 0 && <p className={styles.kosong}>Tidak ada hasil.</p>}
          <ol className={styles.daftarHasil}>
            {state.baris.map((b, i) => (
              <li key={i}>
                {b.judul} <span className={styles.skorKecil}>skor={b.skor}</span>
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  );
}

const Comparison: React.FC = () => {
  const [query, setQuery] = useState('');
  const [kiri, setKiri] = useState<KolomState>(KOLOM_KOSONG);
  const [tengah, setTengah] = useState<KolomState>(KOLOM_KOSONG);
  const [kanan, setKanan] = useState<KolomState>(KOLOM_KOSONG);

  const debouncedQuery = useDebounce(query, 150);

  React.useEffect(() => {
    const q = debouncedQuery.trim();
    if (q.length === 0) {
      setKiri(KOLOM_KOSONG);
      setTengah(KOLOM_KOSONG);
      setKanan(KOLOM_KOSONG);
      return;
    }
    let dibatalkan = false;
    setKiri((s) => ({ ...s, memuat: true, galat: null }));
    setTengah((s) => ({ ...s, memuat: true, galat: null }));
    setKanan((s) => ({ ...s, memuat: true, galat: null }));

    cariBaseline(q).then((r) => !dibatalkan && setKiri(r));
    cariUnifiedSearch(q, 'b0').then((r) => !dibatalkan && setTengah(r));
    cariUnifiedSearch(q, 'e3').then((r) => !dibatalkan && setKanan(r));

    return () => {
      dibatalkan = true;
    };
  }, [debouncedQuery]);

  return (
    <div className={styles.container}>
      <h3 className={styles.judul}>Perbandingan Pencarian</h3>
      <p className={styles.penjelasan}>
        Satu query, tiga sistem, dijalankan serentak. Kolom kiri memanggil endpoint{' '}
        <b>pencarian konsep OpenMRS asli (fuzzy/Lucene)</b> yang sungguhan — bukan baseline
        kami — ditemukan lewat tab Network di kotak diagnosis Visit Note. Kolom kiri ini{' '}
        <b>hanya tersedia untuk entitas konsep</b>: OpenMRS tidak punya pencarian tahan-salah-ketik
        untuk lima entitas lain (obat, pasien, form, lokasi, provider) sama sekali — itu sendiri
        argumen terkuat halaman ini. Semua kolom dibatasi ke entitas konsep supaya
        perbandingannya adil. Halaman ini meniru kotak diagnosis asli (dibatasi kelas Diagnosis,
        28 query uji kesetiaan) — eksperimen terpisah tanpa batasan kelas (42 query, baseline
        B0{"′"}) ada di <code>riset/hasil4/</code>. Rincian keduanya: <code>docs/keputusan.md</code>.
      </p>

      <div className={styles.contohBaris}>
        <span>Contoh:</span>
        {CONTOH.map((c) => (
          <Button key={c} kind="tertiary" size="sm" onClick={() => setQuery(c)}>
            {c}
          </Button>
        ))}
      </div>

      <Search
        size="lg"
        labelText="Kata kunci pencarian"
        placeholder="Ketik kata kunci, mis. diabete melitus"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className={styles.kotakCari}
      />

      <div className={styles.kolomWrapper}>
        <Kolom
          judul="Pencarian konsep OpenMRS asli (fuzzy/Lucene)"
          keterangan="GET /ws/rest/v1/concept, searchType=fuzzy, kelas Diagnosis — hanya untuk konsep"
          state={kiri}
        />
        <Kolom judul="Kami — mode b0" keterangan="B0: baseline pencocokan awalan (uji kejujuran)" state={tengah} />
        <Kolom judul="Kami — mode e3" keterangan="Sistem usulan (kepingan karakter + Weighted RRF)" state={kanan} />
      </div>
    </div>
  );
};

export default Comparison;
