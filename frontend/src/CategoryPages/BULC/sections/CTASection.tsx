import React from 'react';

const HelpCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>
);

interface CTASectionProps {
  onPurchaseClick: () => void;
}

const CTASection: React.FC<CTASectionProps> = ({ onPurchaseClick }) => {
  return (
    <section id="cta" className="bulc-cta">
      <div className="bulc-cta__container">
        <h2 className="bulc-cta__title">
          Ready to automate your simulations?
        </h2>
        <p className="bulc-cta__subtitle">
          Join 500+ fire safety engineers who have switched to BUL:C.
        </p>

        <div className="bulc-cta__action">
          <button className="bulc-cta__btn" onClick={onPurchaseClick}>
            Get Started for Free
          </button>
        </div>

        <div className="bulc-cta__faq">
          <div className="bulc-cta__faq-item">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              Is there a free trial?
            </h4>
            <p className="bulc-cta__faq-answer">
              Yes, you can start for free with limited simulation hours per
              month. No credit card required.
            </p>
          </div>
          <div className="bulc-cta__faq-item">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              Which operating system do I need?
            </h4>
            <p className="bulc-cta__faq-answer">
              BUL:C runs on Windows (64-bit). A standard Windows PC is enough for typical workflows.
            </p>
          </div>
          <div className="bulc-cta__faq-item bulc-cta__faq-item--last">
            <h4 className="bulc-cta__faq-question">
              <HelpCircle className="bulc-cta__faq-icon" />
              Is it compatible with Revit?
            </h4>
            <p className="bulc-cta__faq-answer">
               Not yet. We currently don't have direct plugins for Revit, AutoCAD, or SketchUp — but it’s on our roadmap.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default CTASection;
