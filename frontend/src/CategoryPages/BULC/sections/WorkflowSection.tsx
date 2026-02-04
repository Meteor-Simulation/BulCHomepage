import React from 'react';

const Flag: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" x2="4" y1="22" y2="15"/></svg>
);

const steps = [
  { id: 1, title: 'Import', desc: 'Upload CAD/BIM models directly.(Comming Soon)' },
  { id: 2, title: 'Setup', desc: 'Set fire loads & boundaries using an LLM-powered AI assistant.' },
  { id: 3, title: 'Simulate', desc: '100x times Fast GPU Processing Compared to CPU.' },
  { id: 4, title: 'Analyze', desc: 'Review AI insights & data.' },
];

const WorkflowSection: React.FC = () => {
  return (
    <section id="workflow" className="bulc-workflow">
      <div className="bulc-workflow__container">
        <div className="bulc-workflow__header">
          <h2 className="bulc-workflow__title">Seamless 5-Step Workflow</h2>
          <p className="bulc-workflow__subtitle">
            From concept to compliance in record time.
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
                <h4 className="bulc-workflow__step-title">{step.title}</h4>
                <p className="bulc-workflow__step-desc">{step.desc}</p>
              </div>
            ))}

            {/* Final Step Highlighted */}
            <div className="bulc-workflow__step bulc-workflow__step--final">
              <div className="bulc-workflow__step-bg" />
              <div className="bulc-workflow__step-number bulc-workflow__step-number--success">
                <Flag className="bulc-workflow__step-flag" />
              </div>
              <h4 className="bulc-workflow__step-title">Report</h4>
              <p className="bulc-workflow__step-desc">
                Auto-generate compliance docs.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default WorkflowSection;
