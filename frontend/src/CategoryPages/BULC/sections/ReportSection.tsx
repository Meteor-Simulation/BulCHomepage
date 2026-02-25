import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';

const ChevronLeft: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6"/></svg>
);
const ChevronRight: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6"/></svg>
);
const BarChart: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="16"/></svg>
);
const TableProperties: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 3v18"/><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M21 9H3"/><path d="M21 15H3"/></svg>
);
const PlayCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
);

const CAROUSEL_IMAGES = [
  '/images/bulc/report-1.webp',
];

const AUTOPLAY_INTERVAL = 5000;

const ReportSection: React.FC = () => {
  const { t } = useTranslation();
  const [current, setCurrent] = useState(0);
  const total = CAROUSEL_IMAGES.length;

  const goNext = useCallback(() => {
    setCurrent((prev) => (prev + 1) % total);
  }, [total]);

  const goPrev = useCallback(() => {
    setCurrent((prev) => (prev - 1 + total) % total);
  }, [total]);

  useEffect(() => {
    if (total <= 1) return;
    const timer = setInterval(goNext, AUTOPLAY_INTERVAL);
    return () => clearInterval(timer);
  }, [total, goNext]);

  return (
    <section id="report" className="bulc-report">
      <div className="bulc-report__container">
        <div className="bulc-report__layout">
          {/* Text Content */}
          <div className="bulc-report__text">
            <h2 className="bulc-report__title">{t('bulc.report.title')}</h2>
            <p className="bulc-report__desc">
              {t('bulc.report.desc')}
            </p>

            <div className="bulc-report__features">
              <div className="bulc-report__feature">
                <BarChart className="bulc-report__feature-icon" />
                <span className="bulc-report__feature-label">
                  {t('bulc.report.feature1')}
                </span>
              </div>
              <div className="bulc-report__feature">
                <PlayCircle className="bulc-report__feature-icon" />
                <span className="bulc-report__feature-label">
                  {t('bulc.report.feature2')}
                </span>
              </div>
              <div className="bulc-report__feature">
                <TableProperties className="bulc-report__feature-icon" />
                <span className="bulc-report__feature-label">
                  {t('bulc.report.feature3')}
                </span>
              </div>
            </div>
          </div>

          {/* Image Carousel */}
          <div className="bulc-report__carousel">
            <div className="bulc-report__carousel-track">
              {CAROUSEL_IMAGES.map((src, i) => (
                <img
                  key={i}
                  src={src}
                  alt={`Report ${i + 1}`}
                  className={`bulc-report__carousel-img${i === current ? ' bulc-report__carousel-img--active' : ''}`}
                />
              ))}
            </div>

            {total > 1 && (
              <>
                <button className="bulc-report__carousel-btn bulc-report__carousel-btn--prev" onClick={goPrev}>
                  <ChevronLeft className="bulc-report__carousel-btn-icon" />
                </button>
                <button className="bulc-report__carousel-btn bulc-report__carousel-btn--next" onClick={goNext}>
                  <ChevronRight className="bulc-report__carousel-btn-icon" />
                </button>
                <div className="bulc-report__carousel-dots">
                  {CAROUSEL_IMAGES.map((_, i) => (
                    <button
                      key={i}
                      className={`bulc-report__carousel-dot${i === current ? ' bulc-report__carousel-dot--active' : ''}`}
                      onClick={() => setCurrent(i)}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default ReportSection;
