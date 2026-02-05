import React from 'react';
import { useTranslation } from 'react-i18next';

const BarChart: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="16"/></svg>
);
const TableProperties: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 3v18"/><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M21 9H3"/><path d="M21 15H3"/></svg>
);
const PlayCircle: React.FC<{className?: string}> = ({className}) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
);

const ReportSection: React.FC = () => {
  const { t } = useTranslation();

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

          {/* Visual Report Mockup */}
          <div className="bulc-report__mockup-wrap">
            <div className="bulc-report__mockup">
              {/* Window Bar */}
              <div className="bulc-report__mockup-bar">
                <div className="bulc-report__mockup-dot bulc-report__mockup-dot--red" />
                <div className="bulc-report__mockup-dot bulc-report__mockup-dot--yellow" />
                <div className="bulc-report__mockup-dot bulc-report__mockup-dot--green" />
                <div className="bulc-report__mockup-bar-title">
                  Compliance_Report_v2.pdf
                </div>
              </div>

              <div className="bulc-report__mockup-body">
                {/* Header of Report */}
                <div className="bulc-report__mockup-header">
                  <div>
                    <h3 className="bulc-report__mockup-heading">
                      {t('bulc.report.mockupTitle')}
                    </h3>
                    <p className="bulc-report__mockup-meta">
                      {t('bulc.report.mockupMeta')}
                    </p>
                  </div>
                  <div className="bulc-report__mockup-status">
                    <span className="bulc-report__mockup-badge">{t('bulc.report.mockupPass')}</span>
                  </div>
                </div>

                {/* Report Body */}
                <div className="bulc-report__mockup-charts">
                  {/* Bar Chart Mockup */}
                  <div className="bulc-report__mockup-chart">
                    <div className="bulc-report__mockup-chart-label">
                      {t('bulc.report.chartTemp')}
                    </div>
                    <div className="bulc-report__mockup-bars">
                      <div className="bulc-report__bar" style={{ height: '25%', background: '#93c5fd' }} />
                      <div className="bulc-report__bar" style={{ height: '50%', background: '#60a5fa' }} />
                      <div className="bulc-report__bar" style={{ height: '50%', background: '#3b82f6' }} />
                      <div className="bulc-report__bar" style={{ height: '75%', background: '#2563eb' }} />
                      <div className="bulc-report__bar bulc-report__bar--accent" style={{ height: '100%' }} />
                    </div>
                  </div>

                  {/* Donut Chart Mockup */}
                  <div className="bulc-report__mockup-chart">
                    <div className="bulc-report__mockup-chart-label">
                      {t('bulc.report.chartEvac')}
                    </div>
                    <div className="bulc-report__mockup-donut-wrap">
                      <div className="bulc-report__mockup-donut">
                        <svg className="bulc-report__donut-svg" viewBox="0 0 80 80">
                          <circle
                            cx="40"
                            cy="40"
                            r="36"
                            fill="none"
                            stroke="#e2e8f0"
                            strokeWidth="6"
                          />
                          <circle
                            cx="40"
                            cy="40"
                            r="36"
                            fill="none"
                            stroke="var(--accent)"
                            strokeWidth="6"
                            strokeDasharray="220"
                            strokeDashoffset="45"
                            strokeLinecap="round"
                          />
                        </svg>
                        <div className="bulc-report__donut-label">80%</div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Fake Text Lines */}
                <div className="bulc-report__mockup-lines">
                  <div className="bulc-report__mockup-line" style={{ width: '75%' }} />
                  <div className="bulc-report__mockup-line" style={{ width: '100%' }} />
                  <div className="bulc-report__mockup-line" style={{ width: '83%' }} />
                  <div className="bulc-report__mockup-line" style={{ width: '50%' }} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default ReportSection;
