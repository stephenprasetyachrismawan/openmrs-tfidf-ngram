/**
 * Titik masuk microfrontend. Tugas 10: kerangka kosong yang membuktikan
 * rantai build -> daftar -> render bekerja di RefApp yang sedang berjalan.
 * Pencarian sungguhan (memanggil /ws/rest/v1/unifiedsearch) ditambahkan di
 * tugas 11 — lihat backend/openmrs-module-tfidf-search untuk endpointnya.
 */
import { getAsyncLifecycle, defineConfigSchema } from '@openmrs/esm-framework';
import { configSchema } from './config-schema';

const moduleName = '@openmrs/esm-unified-search-app';

const options = {
  featureName: 'unified-search',
  moduleName,
};

export const importTranslation = require.context('../translations', false, /.json$/, 'lazy');

export function startupApp() {
  defineConfigSchema(moduleName, configSchema);
}

export const root = getAsyncLifecycle(() => import('./root.component'), options);

export const unifiedSearchMenuLink = getAsyncLifecycle(() => import('./menu-link.component'), options);

export const unifiedSearchNavForm = getAsyncLifecycle(() => import('./nav-search-form.component'), options);

/**
 * Pengganti tugas 13 (disetujui pemilik repo — lihat docs/keputusan.md "D1"):
 * RefApp 3 versi ini tidak punya extension slot di workspace Visit Note, jadi
 * klaim penelitian ditunjukkan lewat halaman perbandingan berdampingan alih-alih
 * menambal kotak diagnosis bawaan.
 */
export const comparison = getAsyncLifecycle(() => import('./comparison.component'), options);

export const comparisonMenuLink = getAsyncLifecycle(() => import('./comparison-menu-link.component'), options);

/**
 * Pengujian ablasi (pemilih mode b0/b1/e1/e3 + panel evaluasi) dipisah dari
 * halaman Pencarian Terpadu (permintaan pemilik repo) supaya halaman itu
 * tetap sederhana -- selalu mode e3 (Weighted RRF) -- untuk pemakaian
 * sehari-hari.
 */
export const ablation = getAsyncLifecycle(() => import('./ablation.component'), options);

export const ablationMenuLink = getAsyncLifecycle(() => import('./ablation-menu-link.component'), options);
