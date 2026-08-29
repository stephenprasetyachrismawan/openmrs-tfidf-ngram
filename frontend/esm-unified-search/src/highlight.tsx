import React from 'react';

/** Sorot kemunculan tiap kata query (>=2 huruf) di dalam judul, tanpa peduli huruf besar/kecil. */
export function sorot(judul: string, query: string): React.ReactNode {
  const kata = query
    .trim()
    .split(/\s+/)
    .filter((w) => w.length >= 2)
    .map((w) => w.replace(/[.*+?^{}()|[\]\\$]/g, '\\$&'));
  if (kata.length === 0) {
    return judul;
  }
  const re = new RegExp(`(${kata.join('|')})`, 'ig');
  const parts = judul.split(re);
  return parts.map((part, i) => (re.test(part) ? <mark key={i}>{part}</mark> : <React.Fragment key={i}>{part}</React.Fragment>));
}
