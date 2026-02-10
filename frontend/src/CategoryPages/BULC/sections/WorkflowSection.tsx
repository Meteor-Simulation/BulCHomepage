import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import ReportSection from './ReportSection';

const WorkflowSection: React.FC = () => {
  const { t } = useTranslation();
  const [activeStep, setActiveStep] = useState<number>(3);

  const steps = [
    { id: 1, titleKey: 'bulc.workflow.step1.title' },
    { id: 2, titleKey: 'bulc.workflow.step2.title' },
    { id: 3, titleKey: 'bulc.workflow.step3.title' },
    { id: 4, titleKey: 'bulc.workflow.step4.title' },
    { id: 5, titleKey: 'bulc.workflow.step5.title' },
  ];

  const handleStepClick = (id: number) => {
    setActiveStep(id);
  };

  const renderStepContent = () => {
    switch (activeStep) {
      case 5:
        return <ReportSection />;
      default:
        return (
          <div className="bulc-workflow__detail-content">
            <p className="bulc-workflow__detail-placeholder">
              {t(`bulc.workflow.step${activeStep}.title`)} - 상세 내용이 여기에 표시됩니다.
            </p>
          </div>
        );
    }
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
          {renderStepContent()}
        </div>
      </div>
    </section>
  );
};

export default WorkflowSection;
