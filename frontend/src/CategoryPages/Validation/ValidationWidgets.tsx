import React from 'react';
import { useTranslation } from 'react-i18next';
import { Quadrant, QUADRANT_ORDER } from './validationData';

export const StatusBadge: React.FC<{ status: string; count?: number; title?: string }> = ({ status, count, title }) => (
  <span className={`vv-badge vv-status-${status.toLowerCase()}`} title={title}>
    {status}{count !== undefined ? ` ${count}` : ''}
  </span>
);

export const QuadrantBadge: React.FC<{ q: Quadrant }> = ({ q }) => {
  const { t } = useTranslation();
  return <span className={`vv-badge vv-q-${q}`} title={t(`validation.quadrant.${q}.desc`)}>{t(`validation.quadrant.${q}.label`)}</span>;
};

export const QuadrantBar: React.FC<{ counts: Record<Quadrant, number>; compact?: boolean }> = ({ counts, compact }) => {
  const { t } = useTranslation();
  const total = QUADRANT_ORDER.reduce((s, q) => s + (counts[q] || 0), 0) || 1;
  return (
    <span className={`vv-qbar${compact ? ' vv-qbar-compact' : ''}`}>
      {QUADRANT_ORDER.filter((q) => counts[q]).map((q) => (
        <span key={q} className={`vv-qseg vv-q-${q}`} style={{ flexGrow: counts[q] / total }}
          title={`${t(`validation.quadrant.${q}.label`)} ${counts[q]}`}>
          {compact ? counts[q] : `${t(`validation.quadrant.${q}.label`)} ${counts[q]}`}
        </span>
      ))}
    </span>
  );
};

export const Sha: React.FC<{ value: string | null | undefined; n?: number }> = ({ value, n = 12 }) =>
  value ? <code className="vv-sha" title={value}>{value.slice(0, n)}…</code> : <span className="vv-muted">—</span>;
