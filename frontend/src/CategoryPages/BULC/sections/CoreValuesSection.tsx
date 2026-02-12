import React from 'react';
import { useTranslation } from 'react-i18next';

const ShieldCheck: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>
);
const Check: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
);
const Server: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="20" height="8" x="2" y="2" rx="2" ry="2"/><rect width="20" height="8" x="2" y="14" rx="2" ry="2"/><line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/></svg>
);
const Workflow: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="8" height="8" x="3" y="3" rx="2"/><path d="M7 11v4a2 2 0 0 0 2 2h4"/><rect width="8" height="8" x="13" y="13" rx="2"/></svg>
);

const CoreValuesSection: React.FC = () => {
  const { t } = useTranslation();

  return (
    <section id="core-values" className="bulc-values">
      <div className="bulc-values__container">
        {t('bulc.coreValues.title') && (
          <div className="bulc-values__header">
            <h2 className="bulc-values__title">{t('bulc.coreValues.title')}</h2>
          </div>
        )}

        <div className="bulc-values__list">
          {/* Value 1: Forensic Accuracy */}
          <div className="bulc-values__item">
            <div className="bulc-values__image-wrap">
              <img
                src="/images/bulc/accuracy.webp"
                alt={t('bulc.coreValues.accuracy.title')}
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <ShieldCheck className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">{t('bulc.coreValues.accuracy.title')}</h3>
              </div>
              <p className="bulc-values__desc">
                {t('bulc.coreValues.accuracy.desc')}
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  {t('bulc.coreValues.accuracy.check1')}
                </li>
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  {t('bulc.coreValues.accuracy.check2')}
                </li>
              </ul>
            </div>
          </div>

          {/* Value 2: Unrivaled Speed */}
          <div className="bulc-values__item bulc-values__item--reverse">
            <div className="bulc-values__image-wrap">
              <img
                src="/images/bulc/speed.webp"
                alt={t('bulc.coreValues.speed.title')}
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <Server className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">{t('bulc.coreValues.speed.title')}</h3>
              </div>
              <p className="bulc-values__desc">
                {t('bulc.coreValues.speed.desc')}
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  {t('bulc.coreValues.speed.check1')}
                </li>
              </ul>
            </div>
          </div>

          {/* Value 3: Automation */}
          <div className="bulc-values__item">
            <div className="bulc-values__image-wrap">
              <img
                src="/images/bulc/automation.webp"
                alt={t('bulc.coreValues.automation.title')}
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <Workflow className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">{t('bulc.coreValues.automation.title')}</h3>
              </div>
              <p className="bulc-values__desc">
                {t('bulc.coreValues.automation.desc')}
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  {t('bulc.coreValues.automation.check1')}
                </li>
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  {t('bulc.coreValues.automation.check2')}
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default CoreValuesSection;
