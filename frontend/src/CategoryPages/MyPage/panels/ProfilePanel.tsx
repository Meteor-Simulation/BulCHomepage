import React from 'react';
import { useTranslation } from 'react-i18next';
import { UserInfo } from '../types';
import { LANGUAGES } from '../constants';
import { formatPhoneNumber, formatPhoneNumberOnInput } from '../../../utils/phoneUtils';

interface ProfilePanelProps {
  userInfo: UserInfo;
  isEditingProfile: boolean;
  editName: string;
  editPhone: string;
  tempLanguage: string;
  selectedLanguage: string;
  onStartEdit: () => void;
  onEditNameChange: (value: string) => void;
  onEditPhoneChange: (value: string) => void;
  onTempLanguageChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
}

const ProfilePanel: React.FC<ProfilePanelProps> = ({
  userInfo,
  isEditingProfile,
  editName,
  editPhone,
  tempLanguage,
  selectedLanguage,
  onStartEdit,
  onEditNameChange,
  onEditPhoneChange,
  onTempLanguageChange,
  onSave,
  onCancel,
}) => {
  const { t } = useTranslation();

  return (
    <div className="info-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.profile')}</h2>
        {!isEditingProfile && (
          <button className="edit-btn" onClick={onStartEdit}>
            {t('myPage.edit')}
          </button>
        )}
      </div>

      {isEditingProfile ? (
        <div className="edit-form">
          <div className="form-group">
            <label>{t('myPage.email')}</label>
            <div className="input-wrapper">
              <input type="email" value={userInfo.email} disabled className="disabled" />
              <span className="helper-text">{t('myPage.emailNotEditable')}</span>
            </div>
          </div>
          <div className="form-group">
            <label>{t('myPage.name')}</label>
            <input
              type="text"
              value={editName}
              onChange={(e) => onEditNameChange(e.target.value)}
              placeholder={t('myPage.namePlaceholder')}
            />
          </div>
          <div className="form-group">
            <label>{t('myPage.phone')}</label>
            <input
              type="tel"
              value={editPhone}
              onChange={(e) => onEditPhoneChange(formatPhoneNumberOnInput(e.target.value))}
              placeholder="010-0000-0000"
              maxLength={13}
            />
          </div>
          <div className="form-group">
            <label>{t('myPage.language')}</label>
            <div className="language-options">
              {LANGUAGES.map((lang) => (
                <button
                  key={lang.code}
                  type="button"
                  className={`language-btn ${tempLanguage === lang.code ? 'active' : ''}`}
                  onClick={() => onTempLanguageChange(lang.code)}
                >
                  {lang.name}
                </button>
              ))}
            </div>
          </div>
          <div className="form-actions">
            <button className="save-btn" onClick={onSave}>{t('myPage.save')}</button>
            <button className="cancel-btn" onClick={onCancel}>{t('myPage.cancel')}</button>
          </div>
        </div>
      ) : (
        <div className="info-list">
          <div className="info-row">
            <span className="info-label">{t('myPage.email')}</span>
            <span className="info-value">{userInfo.email}</span>
          </div>
          <div className="info-row">
            <span className="info-label">{t('myPage.name')}</span>
            <span className="info-value">{userInfo.name || '-'}</span>
          </div>
          <div className="info-row">
            <span className="info-label">{t('myPage.phone')}</span>
            <span className="info-value">{formatPhoneNumber(userInfo.phone) || '-'}</span>
          </div>
          <div className="info-row">
            <span className="info-label">{t('myPage.language')}</span>
            <span className="info-value">
              {LANGUAGES.find(l => l.code === selectedLanguage)?.name || selectedLanguage}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProfilePanel;
