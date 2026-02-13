import React from 'react';
import { useTranslation } from 'react-i18next';

const HelpCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>
);

interface FaqItem {
  question: string;
  answer: string;
}

interface CTASectionProps {
  onPurchaseClick: () => void;
  onDownloadClick?: () => void;
  isLoggedIn?: boolean;
}

const CTASection: React.FC<CTASectionProps> = ({ onPurchaseClick, onDownloadClick, isLoggedIn }) => {
  const { t } = useTranslation();
  const faqs = t('bulc.cta.faqs', { returnObjects: true }) as FaqItem[];

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
          <button className="bulc-cta__btn" onClick={isLoggedIn && onDownloadClick ? onDownloadClick : onPurchaseClick}>
            {isLoggedIn ? t('bulc.cta.downloadBtn') : t('bulc.cta.button')}
          </button>
        </div>

        <div className="bulc-cta__faq">
          {Array.isArray(faqs) && faqs.map((faq, index) => (
            <div
              key={index}
              className={`bulc-cta__faq-item${index === faqs.length - 1 ? ' bulc-cta__faq-item--last' : ''}`}
            >
              <h4 className="bulc-cta__faq-question">
                <HelpCircle className="bulc-cta__faq-icon" />
                {faq.question}
              </h4>
              <p className="bulc-cta__faq-answer">
                {faq.answer}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default CTASection;
