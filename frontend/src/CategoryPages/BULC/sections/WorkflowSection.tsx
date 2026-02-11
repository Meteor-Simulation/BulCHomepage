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
const PlayCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
);
const TableProperties: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 3v18"/><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M21 9H3"/><path d="M21 15H3"/></svg>
);

interface StepImages {
  [key: number]: string[];
}

const STEP_IMAGES: StepImages = {
  1: [],
  2: [],
  3: [],
  4: [],
  5: ['/images/bulc/report-1.webp'],
};

const AUTOPLAY_INTERVAL = 5000;

interface ImageCarouselProps {
  images: string[];
  label: string;
}

const ImageCarousel: React.FC<ImageCarouselProps> = ({ images, label }) => {
  const [current, setCurrent] = useState(0);
  const total = images.length;

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

  useEffect(() => {
    setCurrent(0);
  }, [images]);

  if (total === 0) {
    return (
      <div className="bulc-report__carousel bulc-report__carousel--empty">
        <p className="bulc-report__carousel-placeholder">Coming Soon</p>
      </div>
    );
  }

  return (
    <div className="bulc-report__carousel">
      <div className="bulc-report__carousel-track">
        {images.map((src, i) => (
          <img
            key={src}
            src={src}
            alt={`${label} ${i + 1}`}
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
            {images.map((_, i) => (
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
  );
};

const WorkflowSection: React.FC = () => {
  const { t } = useTranslation();
  const [activeStep, setActiveStep] = useState<number>(3);

  const steps = [
    { id: 1, titleKey: 'bulc.workflow.step1.title1' },
    { id: 2, titleKey: 'bulc.workflow.step2.title1' },
    { id: 3, titleKey: 'bulc.workflow.step3.title1' },
    { id: 4, titleKey: 'bulc.workflow.step4.title1' },
    { id: 5, titleKey: 'bulc.workflow.step5.title1' },
  ];

  const handleStepClick = (id: number) => {
    setActiveStep(id);
  };

  return (
    <section id="workflow" className="bulc-workflow">
      <div className="bulc-workflow__container">
        <div className="bulc-workflow__header">
          <h2 className="bulc-workflow__title">{t('bulc.workflow.title')}</h2>
          <p className="bulc-workflow__subtitle">
            {t('bulc.workflow.subtitle')}
          </p>
        </div>

        <div className="bulc-workflow__track">
          {/* Connector Line */}
          <div className="bulc-workflow__connector" />

          <div className="bulc-workflow__grid">
            {steps.map((step) => (
              <div
                key={step.id}
                className={`bulc-workflow__step ${activeStep === step.id ? 'bulc-workflow__step--active' : ''}`}
                onClick={() => handleStepClick(step.id)}
              >
                <div className="bulc-workflow__step-number bulc-workflow__step-number--dark">
                  {step.id}
                </div>
                <h4 className="bulc-workflow__step-title">{t(step.titleKey)}</h4>
              </div>
            ))}
          </div>
        </div>

        {/* 선택된 스텝의 상세 내용 */}
        <div className="bulc-workflow__detail">
          <div className="bulc-report__layout">
            <div className="bulc-report__text">
              <h2 className="bulc-report__title">
                {t(`bulc.workflow.step${activeStep}.title2`)}
              </h2>
              <p className="bulc-report__desc">
                {t(`bulc.workflow.step${activeStep}.desc`)}
              </p>
              <div className="bulc-report__features">
                <div className="bulc-report__feature">
                  <BarChart className="bulc-report__feature-icon" />
                  <span className="bulc-report__feature-label">
                    {t(`bulc.workflow.step${activeStep}.feature1`)}
                  </span>
                </div>
                <div className="bulc-report__feature">
                  <PlayCircle className="bulc-report__feature-icon" />
                  <span className="bulc-report__feature-label">
                    {t(`bulc.workflow.step${activeStep}.feature2`)}
                  </span>
                </div>
                <div className="bulc-report__feature">
                  <TableProperties className="bulc-report__feature-icon" />
                  <span className="bulc-report__feature-label">
                    {t(`bulc.workflow.step${activeStep}.feature3`)}
                  </span>
                </div>
              </div>
            </div>

            <ImageCarousel
              images={STEP_IMAGES[activeStep] || []}
              label={t(`bulc.workflow.step${activeStep}.title1`)}
            />
          </div>
        </div>
      </div>
    </section>
  );
};

export default WorkflowSection;
