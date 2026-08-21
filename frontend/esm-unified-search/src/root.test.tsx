import React from 'react';
import { expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import Root from './root.component';

it('menampilkan teks penanda tugas 10', () => {
  render(<Root />);

  expect(screen.getByText(/pencarian terpadu.*modul termuat/i)).toBeInTheDocument();
});
