import React from 'react';
import { useTranslation } from 'react-i18next';
import { useLanguage } from '../../../context/LanguageContext';
import { UserInfo } from '../types';
import { LANGUAGES, COUNTRIES } from '../constants';
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
  // 결제 국가/통화 (PaymentPanel에서 이동)
  isEditingSettings: boolean;
  tempCountry: string;
  selectedCountry: string;
  onStartEditSettings: () => void;
  onSaveSettings: () => void;
  onCancelSettings: () => void;
  onTempCountryChange: (value: string) => void;
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
  isEditingSettings,
  tempCountry,
  selectedCountry,
  onStartEditSettings,
  onSaveSettings,
  onCancelSettings,
  onTempCountryChange,
}) => {
  const { t } = useTranslation();
  const { language } = useLanguage();
  const displayCurrency = language === 'ko' ? 'KRW' : 'USD';

  return (
    <>
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
        </div>
      )}
    </div>

    {/* 언어/통화 (언어 + 결제 국가/통화) */}
    <div className="info-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.languageCurrency')}</h2>
        {!isEditingSettings && (
          <button className="edit-btn" onClick={onStartEditSettings}>
            {t('myPage.edit')}
          </button>
        )}
      </div>

      {isEditingSettings ? (
        <div className="edit-form">
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
          <div className="form-group">
            <label>{t('myPage.paymentCurrency')}</label>
            <div className="input-wrapper">
              <select
                value={tempCountry}
                onChange={(e) => onTempCountryChange(e.target.value)}
                className="country-dropdown"
              >
                {COUNTRIES.map((country) => (
                  <option key={country.code} value={country.code}>
                    {country.name} ({country.currency})
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button className="save-btn" onClick={onSaveSettings}>{t('myPage.save')}</button>
            <button className="cancel-btn" onClick={onCancelSettings}>{t('myPage.cancel')}</button>
          </div>
        </div>
      ) : (
        <div className="info-list">
          <div className="info-row">
            <span className="info-label">{t('myPage.language')}</span>
            <span className="info-value">
              {LANGUAGES.find(l => l.code === selectedLanguage)?.name || selectedLanguage}
            </span>
          </div>
          <div className="info-row">
            <span className="info-label">{t('myPage.paymentCurrency')}</span>
            <span className="info-value">
              {COUNTRIES.find(c => c.code === selectedCountry)?.name || selectedCountry}
              {' '}({displayCurrency})
            </span>
          </div>
        </div>
      )}
    </div>
    </>
  );
};

export default ProfilePanel;
