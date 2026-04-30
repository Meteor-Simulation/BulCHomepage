import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';

const slides = [
  { src: '/pics/BULC_main.png', alt: 'BUL:C Main' },
  { src: '/pics/cad.png', alt: 'CAD' },
  { src: '/pics/result.png', alt: 'Result' },
];

const logosRow1 = [
  { src: '/logos/samsung.png', alt: 'Samsung' },
  { src: '/logos/LH.jpg', alt: 'LH' },
  { src: '/logos/GS_logo_(South_Korean_company).svg.png', alt: 'GS' },
  { src: '/logos/seoul.jpg', alt: 'Seoul' },
  { src: '/logos/inchen.jpg', alt: 'Incheon' },
  { src: '/logos/hB_FNC.png', alt: 'hB FNC' },
  { src: '/logos/fire_buster.png', alt: 'Fire Buster', dark: true },
  { src: '/logos/filk.png', alt: 'FILK' },
] as const;

const logosRow2 = [
  { src: '/logos/deffence.jpg', alt: 'Defence' },
  { src: '/logos/sobang.png', alt: 'Sobang' },
  { src: '/logos/shinhwa.jpg', alt: 'Shinhwa' },
  { src: '/logos/sea.png', alt: 'Sea' },
  { src: '/logos/japan.jpg', alt: 'Japan' },
  { src: '/logos/woosuk.jpg', alt: 'Woosuk' },
  { src: '/logos/mv_step1_txt1.png', alt: 'MV' },
];

const ComparisonSection: React.FC = () => {
  const { t } = useTranslation();
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const goTo = useCallback((index: number) => {
    setCurrent((index + slides.length) % slides.length);
  }, []);

  useEffect(() => {
    if (paused) {
      if (timerRef.current) clearInterval(timerRef.current);
      return;
    }
    timerRef.current = setInterval(() => {
      setCurrent(prev => (prev + 1) % slides.length);
    }, 3000);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [paused]);

  return (
    <section id="comparison" className="bulc-comparison">
      <div className="bulc-comparison__container">
        <div className="bulc-comparison__header">
          <h3 className="bulc-comparison__title">
            {t('bulc.comparison.title')}
          </h3>
          <p className="bulc-comparison__subtitle">
            {t('bulc.comparison.subtitle')}
          </p>
        </div>

        <div className="bulc-comparison__marquee-wrap">
          <div className="bulc-comparison__marquee-fade" />
          <div className="bulc-comparison__marquee bulc-comparison__marquee--left">
            <div className="bulc-comparison__marquee-track">
              {[...logosRow1, ...logosRow1].map((logo, i) => (
                <div className={`bulc-comparison__marquee-item${'dark' in logo && logo.dark ? ' bulc-comparison__marquee-item--dark' : ''}`} key={i}>
                  <img src={logo.src} alt={logo.alt} />
                </div>
              ))}
            </div>
          </div>
          <div className="bulc-comparison__marquee bulc-comparison__marquee--right">
            <div className="bulc-comparison__marquee-track">
              {[...logosRow2, ...logosRow2].map((logo, i) => (
                <div className="bulc-comparison__marquee-item" key={i}>
                  <img src={logo.src} alt={logo.alt} />
                </div>
              ))}
            </div>
          </div>
        </div>

        <div
          className="bulc-comparison__carousel"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
        >
          <div className="bulc-comparison__carousel-viewport">
            {slides.map((slide, i) => (
              <img
                key={i}
                src={slide.src}
                alt={slide.alt}
                className={`bulc-comparison__carousel-img ${i === current ? 'bulc-comparison__carousel-img--active' : ''}`}
              />
            ))}
          </div>
          <button
            className="bulc-comparison__carousel-btn bulc-comparison__carousel-btn--prev"
            onClick={() => goTo(current - 1)}
            aria-label="Previous"
          >
            &#8249;
          </button>
          <button
            className="bulc-comparison__carousel-btn bulc-comparison__carousel-btn--next"
            onClick={() => goTo(current + 1)}
            aria-label="Next"
          >
            &#8250;
          </button>
          <div className="bulc-comparison__carousel-dots">
            {slides.map((_, i) => (
              <button
                key={i}
                className={`bulc-comparison__carousel-dot ${i === current ? 'bulc-comparison__carousel-dot--active' : ''}`}
                onClick={() => goTo(i)}
                aria-label={`Slide ${i + 1}`}
              />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};

export default ComparisonSection;
