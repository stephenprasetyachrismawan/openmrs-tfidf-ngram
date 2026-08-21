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

/**
 * Pengganti tugas 13 (disetujui pemilik repo — lihat docs/keputusan.md "D1"):
 * RefApp 3 versi ini tidak punya extension slot di workspace Visit Note, jadi
 * klaim penelitian ditunjukkan lewat halaman perbandingan berdampingan alih-alih
 * menambal kotak diagnosis bawaan.
 */
export const comparison = getAsyncLifecycle(() => import('./comparison.component'), options);

export const comparisonMenuLink = getAsyncLifecycle(() => import('./comparison-menu-link.component'), options);
