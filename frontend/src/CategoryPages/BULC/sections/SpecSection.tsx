import React from 'react';
import { useTranslation } from 'react-i18next';

/**
 * BUL:C 핵심 스펙 스트립 — 제품 메인화면(비교 캐러셀) 아래에 표시.
 * Solver / Physics / Standard / Analysis / Library 5개 셀.
 */
const SpecSection: React.FC = () => {
  const { t } = useTranslation();

  return (
    <section id="spec" className="bulc-spec">
      <div className="bulc-spec__container">
        <div className="bulc-spec__grid">
          <div className="bulc-spec__cell">
            <div className="bulc-spec__k">{t('bulc.spec.solver.label')}</div>
            <div className="bulc-spec__v"><span className="bulc-spec__hl">GPU</span> {t('bulc.spec.solver.suffix')}</div>
            <div className="bulc-spec__d">{t('bulc.spec.solver.desc')}</div>
          </div>
          <div className="bulc-spec__cell">
            <div className="bulc-spec__k">{t('bulc.spec.physics.label')}</div>
            <div className="bulc-spec__v">{t('bulc.spec.physics.value')}</div>
            <div className="bulc-spec__d">{t('bulc.spec.physics.desc')}</div>
          </div>
          <div className="bulc-spec__cell">
            <div className="bulc-spec__k">{t('bulc.spec.standard.label')}</div>
            <div className="bulc-spec__v">{t('bulc.spec.standard.value')}</div>
            <div className="bulc-spec__d">{t('bulc.spec.standard.desc')}</div>
          </div>
          <div className="bulc-spec__cell">
            <div className="bulc-spec__k">{t('bulc.spec.analysis.label')}</div>
            <div className="bulc-spec__v">{t('bulc.spec.analysis.value')}</div>
            <div className="bulc-spec__d">{t('bulc.spec.analysis.desc')}</div>
          </div>
          <div className="bulc-spec__cell">
            <div className="bulc-spec__k">{t('bulc.spec.library.label')}</div>
            <div className="bulc-spec__v">{t('bulc.spec.library.value')}</div>
            <div className="bulc-spec__d">{t('bulc.spec.library.desc')}</div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default SpecSection;
