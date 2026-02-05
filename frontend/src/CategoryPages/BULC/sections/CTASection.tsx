import React from 'react';
import { useTranslation } from 'react-i18next';

const HelpCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>
);

interface CTASectionProps {
  onPurchaseClick: () => void;
}

const CTASection: React.FC<CTASectionProps> = ({ onPurchaseClick }) => {
  const { t } = useTranslation();

  return (
    <section id="cta" className="bulc-cta">
      <div className="bulc-cta__container">
        <h2 className="bulc-cta__title">
          {t('bulc.cta.title')}
        </h2>
        <p className="bulc-cta__subtitle">
          {t('bulc.cta.subtitle')}
        </p>

        <div className="bulc-cta__action">
          <button className="bulc-cta__btn" onClick={onPurchaseClick}>
            {t('bulc.cta.button')}
          </button>
        </div>

        <div className="bulc-cta__faq">
          <div className="bulc-cta__faq-item">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              {t('bulc.cta.faq1.question')}
            </h4>
            <p className="bulc-cta__faq-answer">
              {t('bulc.cta.faq1.answer')}
            </p>
          </div>
          <div className="bulc-cta__faq-item">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              {t('bulc.cta.faq2.question')}
            </h4>
            <p className="bulc-cta__faq-answer">
              {t('bulc.cta.faq2.answer')}
            </p>
          </div>
          <div className="bulc-cta__faq-item bulc-cta__faq-item--last">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              {t('bulc.cta.faq3.question')}
            </h4>
            <p className="bulc-cta__faq-answer">
              {t('bulc.cta.faq3.answer')}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default CTASection;
