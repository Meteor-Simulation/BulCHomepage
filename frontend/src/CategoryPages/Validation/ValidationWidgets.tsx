import React from 'react';
import { useTranslation } from 'react-i18next';
import { PreviousBlock, PreviousDelta, Quadrant, QUADRANT_ORDER } from './validationData';

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

const fmt = (v: number | null | undefined, d = 4): string => (v === null || v === undefined ? '—' : Math.abs(v) >= 100 ? v.toFixed(1) : v.toPrecision(d));

const DeltaTable: React.FC<{ rows: PreviousDelta[]; max?: number }> = ({ rows, max = 40 }) => {
  const { t } = useTranslation();
  const shown = rows.slice(0, max);
  return (
    <table className="vv-table vv-small">
      <thead><tr><th>{t('validation.cases')}</th><th>metric</th><th>{t('validation.prev.prevCol')}</th><th>{t('validation.prev.curCol')}</th><th>Δ</th><th>×envelope</th><th>status</th></tr></thead>
      <tbody>
        {shown.map((r, i) => (
          <tr key={`${r.case}-${r.metric}-${i}`}>
            <td>{r.case}</td><td><code>{r.metric}</code></td><td>{fmt(r.prev)}</td><td>{fmt(r.cur)}</td>
            <td>{r.delta > 0 ? '+' : ''}{fmt(r.delta)}</td><td>{r.multiple === null ? '—' : r.multiple.toFixed(1)}</td>
            <td>{r.prev_status ?? '—'} → {r.cur_status ?? '—'}</td>
          </tr>
        ))}
        {rows.length > max && <tr><td colSpan={7} className="vv-muted">… {t('validation.prev.more', { n: rows.length - max })}</td></tr>}
      </tbody>
    </table>
  );
};

/** summary.json `previous`(compare.regression): 이전 버전 대비 status 변화 · 회귀 · 개선 · 항상 보고 metric. */
export const PreviousBlockView: React.FC<{ prev: PreviousBlock }> = ({ prev }) => {
  const { t } = useTranslation();
  return (
    <div className="vv-prev">
      <h3>{t('validation.prev.title', { version: prev.version ?? '?' })}</h3>
      <p className="vv-muted vv-small">{t('validation.prev.lead', { n: prev.counts.cases_compared, r: prev.counts.regressed, i: prev.counts.improved, s: prev.counts.status_changes })}
        {prev.cases_new.length > 0 && <> · {t('validation.prev.casesNew')}: {prev.cases_new.join(', ')}</>}
        {prev.cases_dropped.length > 0 && <> · {t('validation.prev.casesDropped')}: {prev.cases_dropped.join(', ')}</>}
      </p>
      {prev.status_changes.length > 0 && (
        <table className="vv-table vv-small">
          <thead><tr><th>{t('validation.cases')}</th><th>{t('validation.prev.from')}</th><th>{t('validation.prev.to')}</th><th>{t('validation.prev.direction')}</th></tr></thead>
          <tbody>
            {prev.status_changes.map((c) => (
              <tr key={c.case}><td>{c.case}</td><td><StatusBadge status={c.from} /></td><td><StatusBadge status={c.to} /></td><td><span className={`vv-badge vv-dir-${c.direction}`}>{t(`validation.prev.dir.${c.direction}`)}</span></td></tr>
            ))}
          </tbody>
        </table>
      )}
      <details className="vv-details"><summary>{t('validation.prev.alwaysReport')} ({prev.always_report.length})</summary><DeltaTable rows={prev.always_report} max={80} /></details>
      <details className="vv-details"><summary>{t('validation.prev.regressed')} ({prev.regressed.length})</summary><DeltaTable rows={prev.regressed} /></details>
      <details className="vv-details"><summary>{t('validation.prev.improved')} ({prev.improved.length})</summary><DeltaTable rows={prev.improved} /></details>
    </div>
  );
};
