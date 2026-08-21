import React from 'react';
import { navigate } from '@openmrs/esm-framework';

/**
 * Satu entri menu, terdaftar di slot app-menu-item-slot (tugas 10).
 *
 * Memakai `navigate()` dari esm-framework, bukan `ConfigurableLink` dari
 * esm-styleguide — versi @openmrs/esm-framework@10.0.0 yang dipakai RefApp
 * ini (dicek langsung dari node_modules, bukan dokumentasi) tidak
 * mengekspor `ConfigurableLink` secara publik.
 */
const UnifiedSearchMenuLink: React.FC = () => {
  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    navigate({ to: '${openmrsSpaBase}/unified-search' });
  };

  return (
    <a href="${openmrsSpaBase}/unified-search" onClick={handleClick}>
      Pencarian Terpadu
    </a>
  );
};

export default UnifiedSearchMenuLink;
