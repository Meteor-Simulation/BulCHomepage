import React from 'react';
import { useTranslation } from 'react-i18next';

const Download: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
);

interface HeroSectionProps {
  onPurchaseClick: () => void;
  onDownloadClick: () => void;
  isLoggedIn: boolean;
}

const HeroSection: React.FC<HeroSectionProps> = ({ onPurchaseClick, onDownloadClick, isLoggedIn }) => {
  const { t } = useTranslation();

  return (
    <section id="hero" className="bulc-hero">
      {/* Background Effects */}
      <div className="bulc-hero__bg-pattern" />
      <div className="bulc-hero__bg-blob bulc-hero__bg-blob--right" />
      <div className="bulc-hero__bg-blob bulc-hero__bg-blob--left" />

      <div className="bulc-hero__container">
        <div className="bulc-hero__content">
          {!isLoggedIn && (
            <div className="bulc-hero__badge">
              <span className="bulc-hero__badge-dot">
                <span className="bulc-hero__badge-ping" />
                <span className="bulc-hero__badge-core" />
              </span>
              {t('bulc.hero.badge')}
            </div>
          )}

          <h1 className="bulc-hero__title">
            <span className="bulc-hero__title-gradient">
              {t('bulc.hero.title2')}
            </span>
            <br />
            {t('bulc.hero.title1')}
          </h1>

          <p className="bulc-hero__description">
            {t('bulc.hero.description')}
          </p>

          <div className="bulc-hero__actions">
            <button className="bulc-hero__btn bulc-hero__btn--primary" onClick={onDownloadClick}>
              <Download className="bulc-hero__btn-icon bulc-hero__btn-icon--left" />
              {isLoggedIn ? t('download.downloadBtn') : t('bulc.hero.startFree')}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};

export default HeroSection;
