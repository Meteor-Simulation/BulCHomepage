import React from 'react';
import { useTranslation } from 'react-i18next';
import { POLICY_SECTIONS, PolicyLang, PolicyType } from './policyContent';
import './PolicyModal.css';

interface PolicyModalProps {
  isOpen: boolean;
  onClose: () => void;
  type: PolicyType;
}

const PolicyModal: React.FC<PolicyModalProps> = ({ isOpen, onClose, type }) => {
  const { t, i18n } = useTranslation();
  if (!isOpen) return null;

  const lang: PolicyLang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const title = t(`footer.${type}`);
  const sections = POLICY_SECTIONS[type][lang];

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="policy-modal" onClick={(e) => e.stopPropagation()}>
        <div className="policy-modal-header">
          <h2>{title}</h2>
          <button className="modal-close-btn" onClick={onClose} aria-label={t('common.close', 'Close')}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="policy-modal-content">
          {sections.map((section, i) => (
            <React.Fragment key={i}>
              <h3>{section.title}</h3>
              {section.bodies.map((body, j) => (
                <p key={j} dangerouslySetInnerHTML={{ __html: body }} />
              ))}
            </React.Fragment>
          ))}
        </div>
        <div className="policy-modal-footer">
          <button className="policy-close-btn" onClick={onClose}>
            {t('common.close', { defaultValue: lang === 'en' ? 'Close' : '닫기' })}
          </button>
        </div>
      </div>
    </div>
  );
};

export type { PolicyType };
export default PolicyModal;
