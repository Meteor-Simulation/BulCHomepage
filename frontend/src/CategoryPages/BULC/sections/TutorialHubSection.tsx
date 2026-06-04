import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';

const PlayCircle: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polygon points="10 8 16 12 10 16 10 8" /></svg>
);
const FileText: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><path d="M14 2v6h6" /><path d="M16 13H8" /><path d="M16 17H8" /><path d="M10 9H8" /></svg>
);
const BookOpen: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" /></svg>
);
const Cpu: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" /><rect x="9" y="9" width="6" height="6" /><path d="M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3" /></svg>
);
const ChevronRight: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
);
const ArrowLeft: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m12 19-7-7 7-7" /><path d="M19 12H5" /></svg>
);
const Youtube: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22.54 6.42a2.78 2.78 0 0 0-1.94-2C18.88 4 12 4 12 4s-6.88 0-8.6.46a2.78 2.78 0 0 0-1.94 2A29 29 0 0 0 1 11.75a29 29 0 0 0 .46 5.33A2.78 2.78 0 0 0 3.4 19c1.72.46 8.6.46 8.6.46s6.88 0 8.6-.46a2.78 2.78 0 0 0 1.94-2 29 29 0 0 0 .46-5.25 29 29 0 0 0-.46-5.33z" /><polygon points="9.75 15.02 15.5 11.75 9.75 8.48 9.75 15.02" /></svg>
);
const Download: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" x2="12" y1="15" y2="3" /></svg>
);
const Clock: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
);
const ExternalLink: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 3h6v6" /><path d="M10 14 21 3" /><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /></svg>
);

const YOUTUBE_URL = 'https://www.youtube.com/@SimulationMeteor';

// 기술문서(테크니컬 가이드북) HTML 경로 — 새 창으로 열립니다. (frontend/public/tech-docs/)
const TECH_GUIDE_HTML_URL = '/tech-docs/technical-doc.html';

// PDF 자료 경로 (파일이 준비되면 아래 경로를 채우고, ITEMS의 tutorialDoc status를 'active'로 변경하세요.)
const TUTORIAL_PDF_URL = '';

type GuideView = 'hub' | 'video' | 'tutorialDoc' | 'usage' | 'techGuide';

interface GuideItem {
  key: Exclude<GuideView, 'hub'>;
  Icon: React.FC<{ className?: string }>;
  /** wip: 현재 제작중(클릭 불가), active: 상세 보기 가능 */
  status: 'active' | 'wip';
  /** 값이 있으면 in-page 상세 대신 새 창으로 해당 URL을 엽니다. */
  href?: string;
}

const ITEMS: GuideItem[] = [
  { key: 'video', Icon: PlayCircle, status: 'active' },
  { key: 'tutorialDoc', Icon: FileText, status: 'wip' },
  { key: 'usage', Icon: BookOpen, status: 'wip' },
  { key: 'techGuide', Icon: Cpu, status: 'active', href: TECH_GUIDE_HTML_URL },
];

const TutorialHubSection: React.FC = () => {
  const { t } = useTranslation();
  const [view, setView] = useState<GuideView>('hub');

  const renderPreparing = () => (
    <div className="bulc-guide__placeholder">
      <Clock className="bulc-guide__placeholder-icon" />
      <h3 className="bulc-guide__placeholder-title">{t('bulc.guide.preparingTitle')}</h3>
      <p className="bulc-guide__placeholder-desc">{t('bulc.guide.preparingDesc')}</p>
    </div>
  );

  const renderPdf = (url: string, titleKey: string) => {
    if (!url) return renderPreparing();
    return (
      <div className="bulc-guide__doc">
        <div className="bulc-guide__doc-head">
          <h3 className="bulc-guide__detail-title">{t(titleKey)}</h3>
          <a
            className="bulc-guide__doc-download"
            href={url}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Download className="bulc-guide__doc-download-icon" />
            {t('bulc.guide.download')}
          </a>
        </div>
        <iframe className="bulc-guide__doc-frame" src={url} title={t(titleKey)} />
      </div>
    );
  };

  const renderDetail = () => {
    switch (view) {
      case 'video':
        return (
          <div className="bulc-guide__video">
            <h3 className="bulc-guide__detail-title">{t('bulc.guide.items.video.title')}</h3>
            <p className="bulc-guide__detail-desc">{t('bulc.guide.items.video.detailDesc')}</p>
            <a
              className="bulc-guide__youtube-btn"
              href={YOUTUBE_URL}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Youtube className="bulc-guide__youtube-icon" />
              {t('bulc.guide.items.video.youtube')}
            </a>
          </div>
        );
      case 'tutorialDoc':
        return renderPdf(TUTORIAL_PDF_URL, 'bulc.guide.items.tutorialDoc.title');
      default:
        return null;
    }
  };

  const handleCardClick = (item: GuideItem) => {
    if (item.status === 'wip') return;
    if (item.href) {
      window.open(item.href, '_blank', 'noopener,noreferrer');
      return;
    }
    setView(item.key);
  };

  if (view !== 'hub') {
    return (
      <section id="guide" className="bulc-guide">
        <div className="bulc-guide__container">
          <button className="bulc-guide__back" onClick={() => setView('hub')}>
            <ArrowLeft className="bulc-guide__back-icon" />
            {t('bulc.guide.back')}
          </button>
          <div className="bulc-guide__detail">{renderDetail()}</div>
        </div>
      </section>
    );
  }

  return (
    <section id="guide" className="bulc-guide">
      <div className="bulc-guide__container">
        <div className="bulc-guide__header">
          <div className="bulc-guide__eyebrow">{t('bulc.guide.eyebrow')}</div>
          <h2 className="bulc-guide__title">{t('bulc.guide.title')}</h2>
          <p className="bulc-guide__subtitle">{t('bulc.guide.subtitle')}</p>
        </div>

        <div className="bulc-guide__list">
          {ITEMS.map((item) => {
            const { key, Icon, status, href } = item;
            const isWip = status === 'wip';
            return (
              <button
                key={key}
                type="button"
                className={`bulc-guide__card${isWip ? ' bulc-guide__card--disabled' : ''}`}
                onClick={() => handleCardClick(item)}
                disabled={isWip}
              >
                <span className="bulc-guide__card-icon">
                  <Icon className="bulc-guide__card-icon-svg" />
                </span>
                <span className="bulc-guide__card-body">
                  <span className="bulc-guide__card-titlerow">
                    <span className="bulc-guide__card-title">
                      {t(`bulc.guide.items.${key}.title`)}
                    </span>
                    {isWip && (
                      <span className="bulc-guide__badge">{t('bulc.guide.badgeWip')}</span>
                    )}
                  </span>
                  <span className="bulc-guide__card-desc">
                    {t(`bulc.guide.items.${key}.desc`)}
                  </span>
                </span>
                {!isWip && (
                  href
                    ? <ExternalLink className="bulc-guide__card-arrow" />
                    : <ChevronRight className="bulc-guide__card-arrow" />
                )}
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
};

export default TutorialHubSection;
