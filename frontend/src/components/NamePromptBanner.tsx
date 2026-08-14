import React from 'react';
import { useTranslation } from 'react-i18next';
import './NamePromptBanner.css';

interface NamePromptBannerProps {
  onFix: () => void;
  onDismiss: () => void;
}

/**
 * MDP-722 이전에 가입한 사용자는 이름이 비어 있다(가입 폼에 입력란 자체가 없었다).
 * 강제 모달 대신 배너로 유도한다 — 이름이 없어서 지금 막히는 기능은 없으므로
 * 이탈을 만들면서까지 받아낼 이유가 없다.
 */
const NamePromptBanner: React.FC<NamePromptBannerProps> = ({ onFix, onDismiss }) => {
  const { t } = useTranslation();

  return (
    <div className="name-prompt-banner" role="status">
      <div className="name-prompt-banner__content">
        <svg
          className="name-prompt-banner__icon"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <circle cx="12" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.8" />
          <path
            d="M4.5 20c0-3.6 3.4-6 7.5-6s7.5 2.4 7.5 6"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </svg>
        <div className="name-prompt-banner__text">
          <strong className="name-prompt-banner__title">{t('myPage.namePrompt.title')}</strong>
          <span className="name-prompt-banner__desc">{t('myPage.namePrompt.description')}</span>
        </div>
      </div>
      <div className="name-prompt-banner__actions">
        <button type="button" className="name-prompt-banner__cta" onClick={onFix}>
          {t('myPage.namePrompt.cta')}
        </button>
        <button
          type="button"
          className="name-prompt-banner__dismiss"
          onClick={onDismiss}
          aria-label={t('myPage.namePrompt.dismiss')}
        >
          {t('myPage.namePrompt.dismiss')}
        </button>
      </div>
    </div>
  );
};

export default NamePromptBanner;
