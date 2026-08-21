import React from 'react';
import { expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import Root from './root.component';

vi.mock('@openmrs/esm-framework', () => ({
  restBaseUrl: '/ws/rest/v1',
  openmrsFetch: vi.fn().mockResolvedValue({ data: { query: '', mode: 'e3', results: [] } }),
  useDebounce: <T,>(value: T) => value,
}));

it('menampilkan kotak pencarian dan pemilih mode', () => {
  render(<Root />);

  expect(screen.getByText('Pencarian Terpadu')).toBeInTheDocument();
  expect(screen.getByPlaceholderText(/ketik kata kunci/i)).toBeInTheDocument();
});
