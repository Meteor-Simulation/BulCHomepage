import React from 'react';
import { useTranslation } from 'react-i18next';

const X: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
);
const CheckCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="m9 11 3 3L22 4"/></svg>
);
const Code2: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m18 16 4-4-4-4"/><path d="m6 8-4 4 4 4"/><path d="m14.5 4-5 16"/></svg>
);
const Zap: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/></svg>
);
const FileBarChart2: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M12 18v-6"/><path d="M8 18v-1"/><path d="M16 18v-3"/></svg>
);

const ComparisonSection: React.FC = () => {
  const { t } = useTranslation();

  return (
    <section id="comparison" className="bulc-comparison">
      <div className="bulc-comparison__container">
        <div className="bulc-comparison__header">
          <h2 className="bulc-comparison__label">{t('bulc.comparison.label')}</h2>
          <h3 className="bulc-comparison__title">
            {t('bulc.comparison.title')}
          </h3>
          <p className="bulc-comparison__subtitle">
            {t('bulc.comparison.subtitle')}
          </p>
        </div>

        <div className="bulc-comparison__grid">
          {/* Card 1: Manual */}
          <div className="bulc-comparison__card">
            <div className="bulc-comparison__card-accent" />
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--muted">
              <Code2 className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">{t('bulc.comparison.card1.title')}</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card1.problem1')}
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card1.problem2')}
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                {t('bulc.comparison.card1.solutionTitle')}
              </p>
              <p className="bulc-comparison__card-solution-desc">
                {t('bulc.comparison.card1.solutionDesc')}
              </p>
            </div>
          </div>

          {/* Card 2: Speed (Highlighted) */}
          <div className="bulc-comparison__card bulc-comparison__card--featured">
            <div className="bulc-comparison__card-badge">{t('bulc.comparison.card2.badge')}</div>
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--accent">
              <Zap className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">{t('bulc.comparison.card2.title')}</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card2.problem1')}
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card2.problem2')}
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                {t('bulc.comparison.card2.solutionTitle')}
              </p>
              <p className="bulc-comparison__card-solution-desc">
                {t('bulc.comparison.card2.solutionDesc')}
              </p>
            </div>
          </div>

          {/* Card 3: Static Reporting */}
          <div className="bulc-comparison__card">
            <div className="bulc-comparison__card-accent" />
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--muted">
              <FileBarChart2 className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">{t('bulc.comparison.card3.title')}</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card3.problem1')}
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                {t('bulc.comparison.card3.problem2')}
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                {t('bulc.comparison.card3.solutionTitle')}
              </p>
              <p className="bulc-comparison__card-solution-desc">
                {t('bulc.comparison.card3.solutionDesc')}
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default ComparisonSection;
