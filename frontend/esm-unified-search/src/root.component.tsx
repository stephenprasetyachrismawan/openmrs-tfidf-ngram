import React from 'react';
import styles from './root.scss';

/**
 * Halaman kosong tugas 10 — hanya membuktikan rantai build -> daftar -> render
 * microfrontend O3 bekerja. Pencarian sungguhan ditambahkan di tugas 11.
 */
const Root: React.FC = () => {
  return (
    <div className={styles.container}>
      <p className={styles.penanda}>Pencarian Terpadu &mdash; modul termuat</p>
    </div>
  );
};

export default Root;
