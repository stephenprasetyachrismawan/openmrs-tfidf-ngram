import React from 'react';
import { navigate } from '@openmrs/esm-framework';

/** Entri menu kedua, terdaftar di slot app-menu-slot (sama seperti tugas 10). */
const ComparisonMenuLink: React.FC = () => {
  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    navigate({ to: '${openmrsSpaBase}/perbandingan-pencarian' });
  };

  return (
    <a href="${openmrsSpaBase}/perbandingan-pencarian" onClick={handleClick}>
      Perbandingan Pencarian
    </a>
  );
};

export default ComparisonMenuLink;
