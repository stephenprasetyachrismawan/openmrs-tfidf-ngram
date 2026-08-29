import React from 'react';
import { navigate } from '@openmrs/esm-framework';

/** Entri menu ketiga, terdaftar di slot app-menu-slot (sama seperti menu-link.component.tsx). */
const AblationMenuLink: React.FC = () => {
  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    navigate({ to: '${openmrsSpaBase}/pengujian-ablasi' });
  };

  return (
    <a href="${openmrsSpaBase}/pengujian-ablasi" onClick={handleClick}>
      Pengujian Ablasi
    </a>
  );
};

export default AblationMenuLink;
