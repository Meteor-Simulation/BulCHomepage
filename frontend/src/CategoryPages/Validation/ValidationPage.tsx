import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './Validation.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import Seo from '../../components/Seo';
import { fetchValidationIndex, STATUS_ORDER, ValidationIndexEntry } from './validationData';
import { StatusBadge, QuadrantBar } from './ValidationWidgets';

/** /validation — 게시된 V&V 버전 목록 (BULC-VV Sprint 6 초기 게시, 수동). */
const ValidationPage: React.FC = () => {
  const { t } = useTranslation();
  const [versions, setVersions] = useState<ValidationIndexEntry[] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    fetchValidationIndex().then((idx) => {
      if (!alive) return;
      setVersions(idx?.versions ?? []);
      setLoading(false);
    });
    return () => { alive = false; };
  }, []);

  return (
    <div className="app">
      <Seo title={t('validation.seo.listTitle')} description={t('validation.seo.listDescription')} path="/validation" noindex />
      <Header logoText="BUL:C" />
      <main className="main-content sub-page">
        <div className="docs-container vv-container">
          <h1>{t('validation.title')}</h1>
          <p className="docs-lead">{t('validation.lead')}</p>
          <div className="vv-notice">{t('validation.preliminaryNotice')}</div>

          {loading && <p className="vv-muted">{t('validation.loading')}</p>}
          {!loading && (!versions || versions.length === 0) && <p className="vv-muted">{t('validation.empty')}</p>}

          {versions && versions.length > 0 && (
            <div className="vv-version-list">
              {versions.map((v) => (
                <Link key={v.version} to={`/validation/${v.version}`} className="vv-version-card">
                  <div className="vv-version-head">
                    <span className="vv-version-name">{v.engine} {v.version}</span>
                    {v.preliminary && <span className="vv-tag">{t('validation.preliminary')}</span>}
                    <span className="vv-muted vv-date">{v.generated_at.slice(0, 10)}</span>
                  </div>
                  <div className="vv-version-counts">
                    <span className="vv-muted">{t('validation.phase1Short')} · {v.counts.cases} {t('validation.cases')}:</span>
                    {STATUS_ORDER.filter((s) => v.counts[s]).map((s) => (
                      <StatusBadge key={s} status={s} count={v.counts[s]} />
                    ))}
                  </div>
                  {v.phase2_counts && (
                    <div className="vv-version-counts">
                      <span className="vv-muted">{t('validation.phase2Short')}:</span>
                      <QuadrantBar counts={v.phase2_counts} compact />
                    </div>
                  )}
                </Link>
              ))}
            </div>
          )}

          <section className="vv-about">
            <h2>{t('validation.about.title')}</h2>
            <p>{t('validation.about.p1')}</p>
            <p>{t('validation.about.p2')}</p>
            <p>{t('validation.about.p3')}</p>
          </section>
        </div>
      </main>
      <Footer />
    </div>
  );
};

export default ValidationPage;
