import React, { useState } from 'react';
import { Button, InlineNotification, InlineLoading } from '@carbon/react';
import { openmrsFetch, restBaseUrl } from '@openmrs/esm-framework';
import styles from './eval-panel.scss';

type Mode = 'b0' | 'b1' | 'e1' | 'e3';

const MODES: Mode[] = ['b0', 'b1', 'e1', 'e3'];

const MODE_LABEL: Record<Mode, string> = {
  b0: 'B0 — heuristik OpenMRS',
  b1: 'B1 — TF-IDF kata',
  e1: 'E1 — TF-IDF kata + kepingan',
  e3: 'E3 — E1 + Weighted RRF',
};

const TIPE_LABEL: Record<string, string> = {
  persis: 'Persis',
  typo: 'Tipo',
  trunkasi: 'Trunkasi',
  hilang_kata: 'Hilang kata',
  urut_balik: 'Urut balik',
};
const TIPE_URUTAN = ['persis', 'typo', 'trunkasi', 'hilang_kata', 'urut_balik'];

interface PerTipe {
  n_query: number;
  ndcg10: number;
  p1: number;
}

interface EvalResponse {
  mode: string;
  n_query: number;
  gold_sha256: string;
  p1: number;
  p5: number;
  r10: number;
  mrr: number;
  map: number;
  ndcg10: number;
  pct_nol: number;
  waktu_indeks_ms: number;
  per_tipe: Record<string, PerTipe>;
}

interface HasilMode {
  data: EvalResponse;
  waktuMs: number | null;
}

function fmt(n: number | undefined, digit = 3): string {
  return typeof n === 'number' ? n.toFixed(digit) : '—';
}

const EvalPanel: React.FC = () => {
  const [hasil, setHasil] = useState<Partial<Record<Mode, HasilMode>> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const jalankan = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const entries = await Promise.all(
        MODES.map(async (mode) => {
          const res = await openmrsFetch<EvalResponse>(`${restBaseUrl}/unifiedsearch/eval?mode=${mode}`);
          const waktuHeader = res.headers.get('X-Unifiedsearch-Waktu-Ms');
          return [mode, { data: res.data, waktuMs: waktuHeader ? Number(waktuHeader) : null }] as const;
        }),
      );
      setHasil(Object.fromEntries(entries) as Record<Mode, HasilMode>);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Permintaan gagal');
      setHasil(null);
    } finally {
      setIsLoading(false);
    }
  };

  const e3 = hasil?.e3;

  return (
    <div className={styles.panel}>
      <h3 className={styles.judul}>Panel evaluasi</h3>
      <p className={styles.penjelasan}>
        Menjalankan 100 query data uji <b>pengembangan (dev)</b> — terpisah dari 180 query
        pelaporan resmi (CLAUDE.md aturan 10) — terhadap indeks yang sedang berjalan di server
        ini sekarang. Tabel di bawah kosong sampai tombol ditekan; tidak ada angka bawaan.
      </p>

      <Button kind="primary" onClick={jalankan} disabled={isLoading}>
        {isLoading ? <InlineLoading description="Menjalankan seluruh data uji..." /> : 'Jalankan seluruh data uji'}
      </Button>

      {error && (
        <InlineNotification kind="error" title="Galat" subtitle={error} lowContrast hideCloseButton className={styles.galat} />
      )}

      {hasil && e3 && (
        <div className={styles.hasilWrapper}>
          <p className={styles.metaInfo}>
            gold_sha256=<code>{e3.data.gold_sha256}</code> · waktu bangun indeks:{' '}
            {e3.data.waktu_indeks_ms} ms · {e3.data.n_query} query
          </p>

          <h4>Metrik agregat per sistem</h4>
          <div className={styles.tabelScroll}>
            <table className={styles.tabel}>
              <thead>
                <tr>
                  <th>Sistem</th>
                  <th>P@1</th>
                  <th>P@5</th>
                  <th>R@10</th>
                  <th>MRR</th>
                  <th>MAP</th>
                  <th className={styles.kolomUtama}>nDCG@10</th>
                  <th>% nol-hasil</th>
                  <th>Latensi rata-rata (ms)</th>
                </tr>
              </thead>
              <tbody>
                {MODES.map((mode) => {
                  const h = hasil[mode];
                  if (!h) {
                    return null;
                  }
                  const latensiRataRata = h.waktuMs !== null ? h.waktuMs / h.data.n_query : null;
                  return (
                    <tr key={mode}>
                      <td>{MODE_LABEL[mode]}</td>
                      <td>{fmt(h.data.p1)}</td>
                      <td>{fmt(h.data.p5)}</td>
                      <td>{fmt(h.data.r10)}</td>
                      <td>{fmt(h.data.mrr)}</td>
                      <td>{fmt(h.data.map)}</td>
                      <td className={styles.kolomUtama}>{fmt(h.data.ndcg10)}</td>
                      <td>{fmt(h.data.pct_nol, 1)}%</td>
                      <td>{latensiRataRata !== null ? latensiRataRata.toFixed(1) : '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <h4>nDCG@10 per jenis kesalahan ketik</h4>
          <div className={styles.tabelScroll}>
            <table className={styles.tabel}>
              <thead>
                <tr>
                  <th>Jenis</th>
                  {MODES.map((mode) => (
                    <th key={mode}>{mode}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {TIPE_URUTAN.map((tipe) => (
                  <tr key={tipe}>
                    <td>{TIPE_LABEL[tipe] ?? tipe}</td>
                    {MODES.map((mode) => {
                      const pt = hasil[mode]?.data.per_tipe?.[tipe];
                      return <td key={mode}>{pt ? fmt(pt.ndcg10) : '—'}</td>;
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <details className={styles.keterangan}>
            <summary>Arti tiap metrik (untuk yang belum pernah dengar)</summary>
            <dl>
              <dt>P@1</dt>
              <dd>Apakah hasil nomor satu sudah benar. 1 kalau ya, 0 kalau tidak.</dd>
              <dt>P@5 dan R@10</dt>
              <dd>
                Berapa banyak hasil relevan muncul di 5 (atau 10) teratas. Tidak peduli di
                posisi mana persisnya — cuma menghitung jumlahnya.
              </dd>
              <dt>MRR</dt>
              <dd>
                Seberapa cepat jawaban pertama yang relevan muncul. Kalau di posisi 1, nilainya
                1; posisi 2, nilainya 0,5; dan seterusnya. Hanya melihat satu hasil pertama.
              </dd>
              <dt>MAP</dt>
              <dd>
                Rata-rata presisi di sepanjang daftar 10 hasil, bukan cuma yang pertama.
                Menghukum kalau hasil relevan kedua/ketiga letaknya jauh ke bawah.
              </dd>
              <dt>nDCG@10 — metrik utama</dt>
              <dd>
                Seperti MAP, tapi juga membedakan hasil "persis yang dicari" dari yang "cuma
                berkaitan", dan menghukum lebih keras kalau hasil bagus ada di posisi bawah.
                Satu-satunya metrik di sini yang sensitif pada posisi <em>dan</em> tingkat
                relevansi sekaligus — karena itu dipakai sebagai penentu utama.
              </dd>
              <dt>% nol-hasil</dt>
              <dd>
                Berapa persen pencarian yang layar hasilnya kosong total. Kegagalan paling
                parah — petugas tidak dapat apa pun.
              </dd>
            </dl>
          </details>
        </div>
      )}
    </div>
  );
};

export default EvalPanel;
