import React from 'react';
import { useTranslation } from 'react-i18next';

const Flag: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" x2="4" y1="22" y2="15"/></svg>
);

const WorkflowSection: React.FC = () => {
  const { t } = useTranslation();

  const steps = [
    { id: 1, titleKey: 'bulc.workflow.step1.title', descKey: 'bulc.workflow.step1.desc' },
    { id: 2, titleKey: 'bulc.workflow.step2.title', descKey: 'bulc.workflow.step2.desc' },
    { id: 3, titleKey: 'bulc.workflow.step3.title', descKey: 'bulc.workflow.step3.desc' },
    { id: 4, titleKey: 'bulc.workflow.step4.title', descKey: 'bulc.workflow.step4.desc' },
  ];

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
              <div key={step.id} className="bulc-workflow__step">
                <div
                  className={`bulc-workflow__step-number ${
                    step.id === 1
                      ? 'bulc-workflow__step-number--accent'
                      : 'bulc-workflow__step-number--dark'
                  }`}
                >
                  {step.id}
                </div>
                <h4 className="bulc-workflow__step-title">{t(step.titleKey)}</h4>
                <p className="bulc-workflow__step-desc">{t(step.descKey)}</p>
              </div>
            ))}

            {/* Final Step Highlighted */}
            <div className="bulc-workflow__step bulc-workflow__step--final">
              <div className="bulc-workflow__step-bg" />
              <div className="bulc-workflow__step-number bulc-workflow__step-number--success">
                <Flag className="bulc-workflow__step-flag" />
              </div>
              <h4 className="bulc-workflow__step-title">{t('bulc.workflow.step5.title')}</h4>
              <p className="bulc-workflow__step-desc">
                {t('bulc.workflow.step5.desc')}
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default WorkflowSection;
