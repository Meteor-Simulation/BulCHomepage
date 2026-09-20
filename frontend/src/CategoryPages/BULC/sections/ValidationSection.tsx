import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import '../../Validation/Validation.css';
import { fetchValidationIndex, STATUS_ORDER, ValidationIndexEntry } from '../../Validation/validationData';
import { QuadrantBar, StatusBadge } from '../../Validation/ValidationWidgets';

/** BULC 페이지 "Validation" 섹션 — 최신 게시 버전의 요약 카드 + /validation 링크 (BULC-VV Sprint 6 초기 게시). */
const ValidationSection: React.FC = () => {
  const { t } = useTranslation();
  const [latest, setLatest] = useState<ValidationIndexEntry | null | undefined>(undefined);

  useEffect(() => {
    let alive = true;
    fetchValidationIndex().then((idx) => { if (alive) setLatest(idx?.versions?.[0] ?? null); });
    return () => { alive = false; };
  }, []);

  return (
    <section id="validation" className="bulc-cta" style={{ paddingTop: 48, paddingBottom: 48 }}>
      <div className="bulc-cta__container vv-container">
        <h2 className="bulc-cta__title">{t('validation.section.title')}</h2>
        <p className="bulc-cta__subtitle">{t('validation.section.subtitle')}</p>
        {latest === undefined && <p className="vv-muted">{t('validation.loading')}</p>}
        {latest === null && <p className="vv-muted">{t('validation.empty')}</p>}
        {latest && (
          <Link to={`/validation/${latest.version}`} className="vv-version-card" style={{ textAlign: 'left', maxWidth: 720, margin: '16px auto' }}>
            <div className="vv-version-head">
              <span className="vv-version-name">{latest.engine} {latest.version}</span>
              {latest.preliminary && <span className="vv-tag">{t('validation.preliminary')}</span>}
              <span className="vv-muted vv-date">{latest.generated_at.slice(0, 10)}</span>
            </div>
            <div className="vv-version-counts">
              <span className="vv-muted">{t('validation.phase1Short')} · {latest.counts.cases} {t('validation.cases')}:</span>
              {STATUS_ORDER.filter((s) => latest.counts[s]).map((s) => <StatusBadge key={s} status={s} count={latest.counts[s]} />)}
            </div>
            {latest.phase2_counts && (
              <div className="vv-version-counts">
                <span className="vv-muted">{t('validation.phase2Short')}:</span>
                <QuadrantBar counts={latest.phase2_counts} compact />
              </div>
            )}
          </Link>
        )}
        <div className="bulc-cta__action">
          <Link to="/validation" className="vv-linkbtn">{t('validation.section.more')} →</Link>
        </div>
      </div>
    </section>
  );
};

export default ValidationSection;
