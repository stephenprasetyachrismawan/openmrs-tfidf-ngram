import React, { useState } from 'react';
import { Search } from '@carbon/react';
import { navigate } from '@openmrs/esm-framework';
import { sorot } from './highlight';
import { useSaran } from './use-saran';
import styles from './nav-search-form.scss';

/** Jumlah saran ketikan di dropdown. */
const LIMIT_SARAN = 6;

/**
 * Kotak pencarian langsung di navbar (slot top-nav-actions-slot -- slot
 * yang sama dipakai tombol cari pasien bawaan RefApp, lihat
 * docs/arsip/routes.registry.json.sebelum-unifiedsearch).
 *
 * Dropdown-nya BUKAN pratinjau hasil pencarian -- cuma saran ketikan, sumber
 * datanya judul semua dokumen (konsep, obat, pasien, form, lokasi, provider,
 * hasil lab, kondisi) lewat endpoint /unifiedsearch/saran (Jaccard kepingan-dua-huruf,
 * BigramJaccardSuggester di backend -- bukan salah satu mode riset
 * b0/b1/e1/e3, dan bukan K5/NGRAM=4 yang butuh minimal 4 huruf query). Beda
 * dari K5, kepingan-2-huruf plus Jaccard mentolerir prefiks pendek DAN typo
 * satu huruf -- lihat Javadoc BigramJaccardSuggester untuk perbandingannya
 * dengan b0 (pencocokan awalan, gaya legacy UI). Filter hak akses pasien di
 * UnifiedSearchService.saran berlaku sama seperti /unifiedsearch biasa, jadi
 * baris pasien/hasillab/kondisi otomatis tersaring untuk pengguna tanpa privilege --
 * dropdown ini tidak perlu logika privilege sendiri. Klik satu saran cuma
 * mengambil teksnya dan menjalankan pencarian penuh (mode e3) untuk kata
 * itu -- bukan tautan langsung ke satu dokumen, jadi tidak perlu label
 * jenis entitas di sini. Enter (atau tombol "Lihat semua hasil") melakukan
 * hal yang sama untuk apa pun yang sudah diketik.
 */
const UnifiedSearchNavForm: React.FC = () => {
  const [query, setQuery] = useState('');
  const [terfokus, setTerfokus] = useState(false);

  const { data, isLoading } = useSaran(query, LIMIT_SARAN);

  const arahkanKeHalamanHasil = (kataKunci: string) => {
    const q = kataKunci.trim();
    navigate({
      to: q ? `\${openmrsSpaBase}/unified-search?q=${encodeURIComponent(q)}` : '${openmrsSpaBase}/unified-search',
    });
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setTerfokus(false);
    arahkanKeHalamanHasil(query);
  };

  const pilihSaran = (saran: string) => {
    setTerfokus(false);
    setQuery(saran);
    arahkanKeHalamanHasil(saran);
  };

  const queryTerisi = query.trim().length > 0;
  const tampilkanDropdown = terfokus && queryTerisi;
  const saran = data ? Array.from(new Set(data.results.map((hit) => hit.judul))) : [];

  return (
    <form className={styles.container} onSubmit={handleSubmit}>
      <div className={styles.wrapper}>
        <Search
          size="sm"
          labelText="Pencarian Terpadu"
          placeholder="Pencarian Terpadu..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setTerfokus(true)}
          onBlur={() => setTimeout(() => setTerfokus(false), 150)}
          className={styles.search}
        />
        {tampilkanDropdown && (
          <div className={styles.dropdown} role="listbox" aria-label="Saran ketikan">
            {isLoading && <div className={styles.status}>Mencari saran...</div>}
            {!isLoading &&
              saran.map((s) => (
                <button key={s} type="button" className={styles.saran} onMouseDown={() => pilihSaran(s)}>
                  {sorot(s, query)}
                </button>
              ))}
            <button type="submit" className={styles.lihatSemua}>
              Lihat semua hasil untuk &quot;{query.trim()}&quot;
            </button>
          </div>
        )}
      </div>
    </form>
  );
};

export default UnifiedSearchNavForm;
