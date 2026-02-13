import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import '../Common/CategoryPages.css';
import './Download.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';

const DOWNLOAD_URL = 'https://github.com/Meteor-Simulation/bulc-releases/releases/latest/download/BULC-latest-win-x64.exe';

const DownloadIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" y1="15" x2="12" y2="3" />
  </svg>
);

const MonitorIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" />
  </svg>
);

const CpuIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="4" width="16" height="16" rx="2" ry="2" /><rect x="9" y="9" width="6" height="6" /><line x1="9" y1="1" x2="9" y2="4" /><line x1="15" y1="1" x2="15" y2="4" /><line x1="9" y1="20" x2="9" y2="23" /><line x1="15" y1="20" x2="15" y2="23" /><line x1="20" y1="9" x2="23" y2="9" /><line x1="20" y1="14" x2="23" y2="14" /><line x1="1" y1="9" x2="4" y2="9" /><line x1="1" y1="14" x2="4" y2="14" />
  </svg>
);

const MemoryIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="6" width="20" height="12" rx="2" /><line x1="6" y1="12" x2="6" y2="12.01" /><line x1="10" y1="12" x2="10" y2="12.01" /><line x1="14" y1="12" x2="14" y2="12.01" /><line x1="18" y1="12" x2="18" y2="12.01" />
  </svg>
);

const GpuIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="4" width="16" height="16" rx="2" /><path d="M8 2v4" /><path d="M16 2v4" /><path d="M8 18v4" /><path d="M16 18v4" />
  </svg>
);

const StorageIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" /><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
  </svg>
);

const DownloadPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const requirements = [
    { icon: MonitorIcon, label: 'OS', value: t('download.systemRequirements.os') },
    { icon: CpuIcon, label: 'CPU', value: t('download.systemRequirements.cpu') },
    { icon: MemoryIcon, label: 'RAM', value: t('download.systemRequirements.ram') },
    { icon: GpuIcon, label: 'GPU', value: t('download.systemRequirements.gpu') },
    { icon: StorageIcon, label: 'Storage', value: t('download.systemRequirements.storage') },
  ];

  return (
    <div className="app">
      <Header logoText="BUL:C" />
      <main className="main-content sub-page">
        <div className="download-page">
          {/* Hero */}
          <section className="download-hero">
            <h1 className="download-hero__title">{t('download.title')}</h1>
            <p className="download-hero__subtitle">{t('download.subtitle')}</p>
            <a href={DOWNLOAD_URL} className="download-hero__btn" download>
              <DownloadIcon className="download-hero__btn-icon" />
              {t('download.downloadBtn')}
            </a>
            <div className="download-hero__meta">
              <span>{t('download.version')}</span>
              <span>{t('download.platform')}</span>
            </div>
          </section>

          {/* System Requirements */}
          <section className="download-section">
            <h2 className="download-section__title">{t('download.systemRequirements.title')}</h2>
            <div className="download-requirements">
              {requirements.map(({ icon: Icon, label, value }) => (
                <div key={label} className="download-requirements__item">
                  <Icon className="download-requirements__icon" />
                  <div>
                    <div className="download-requirements__label">{label}</div>
                    <div className="download-requirements__value">{value}</div>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* Quick Start */}
          <section className="download-section">
            <h2 className="download-section__title">{t('download.quickStart.title')}</h2>
            <div className="download-steps">
              {[1, 2, 3].map((n) => (
                <div key={n} className="download-steps__item">
                  <div className="download-steps__number">{n}</div>
                  <div className="download-steps__title">{t(`download.quickStart.step${n}Title`)}</div>
                  <div className="download-steps__desc">{t(`download.quickStart.step${n}`)}</div>
                </div>
              ))}
            </div>
          </section>

          {/* License CTA */}
          <section className="download-license">
            <h3 className="download-license__title">{t('download.license.title')}</h3>
            <p className="download-license__desc">{t('download.license.desc')}</p>
            <button className="download-license__btn" onClick={() => navigate('/payment')}>
              {t('download.license.button')}
            </button>
          </section>
        </div>
      </main>
      <Footer />
    </div>
  );
};

export default DownloadPage;
