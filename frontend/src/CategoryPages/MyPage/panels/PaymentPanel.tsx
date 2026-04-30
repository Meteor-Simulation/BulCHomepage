import React from 'react';
import { useTranslation } from 'react-i18next';
import { useLanguage } from '../../../context/LanguageContext';
import { BillingKey } from '../types';
import { COUNTRIES } from '../constants';

interface PaymentPanelProps {
  isEditingSettings: boolean;
  tempCountry: string;
  selectedCountry: string;
  isLoadingBillingKeys: boolean;
  billingKeys: BillingKey[];
  onStartEditSettings: () => void;
  onSaveSettings: () => void;
  onCancelSettings: () => void;
  onTempCountryChange: (value: string) => void;
  onSetDefaultCard: (billingKeyId: number) => void;
  onDeleteCard: (billingKeyId: number) => void;
}

const PaymentPanel: React.FC<PaymentPanelProps> = ({
  isEditingSettings,
  tempCountry,
  selectedCountry,
  isLoadingBillingKeys,
  billingKeys,
  onStartEditSettings,
  onSaveSettings,
  onCancelSettings,
  onTempCountryChange,
  onSetDefaultCard,
  onDeleteCard,
}) => {
  const { t } = useTranslation();
  const { language } = useLanguage();
  // 결제 통화는 언어 설정 기준으로 결정 (Payment 페이지/PriceSection과 동일 규칙)
  const displayCurrency = language === 'ko' ? 'KRW' : 'USD';

  return (
    <div className="info-card payment-methods-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.paymentMethod')}</h2>
        {!isEditingSettings && (
          <button className="edit-btn" onClick={onStartEditSettings}>
            {t('myPage.edit')}
          </button>
        )}
      </div>

      {/* 결제 통화 설정 */}
      {isEditingSettings ? (
        <div className="edit-form currency-edit-form">
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
        <div className="currency-info-section">
          <div className="info-row">
            <span className="info-label">{t('myPage.paymentCurrency')}</span>
            <span className="info-value">
              {COUNTRIES.find(c => c.code === selectedCountry)?.name || selectedCountry}
              ({displayCurrency})
            </span>
          </div>
        </div>
      )}

      {isLoadingBillingKeys ? (
        <div className="loading-text">{t('myPage.loading')}</div>
      ) : billingKeys.length === 0 ? (
        <div className="empty-payment-methods">
          <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M21 4H3C1.89 4 1 4.89 1 6V18C1 19.11 1.89 20 3 20H21C22.11 20 23 19.11 23 18V6C23 4.89 22.11 4 21 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M1 10H23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <p>{t('myPage.noPaymentMethod')}</p>
          <span className="helper-text">{t('myPage.paymentMethodHelper')}</span>
        </div>
      ) : (
        <div className="payment-methods-list">
          {billingKeys.map((card) => (
            <div key={card.id} className={`payment-method-item ${card.isDefault ? 'default' : ''}`}>
              <div className="card-info">
                <div className="card-icon">
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M21 4H3C1.89 4 1 4.89 1 6V18C1 19.11 1.89 20 3 20H21C22.11 20 23 19.11 23 18V6C23 4.89 22.11 4 21 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M1 10H23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div className="card-details">
                  <span className="card-company">{card.cardCompany || '카드'}</span>
                  <span className="card-number">{card.cardNumber}</span>
                </div>
                {card.isDefault && <span className="default-badge">기본</span>}
              </div>
              <div className="card-actions">
                {!card.isDefault && (
                  <button className="set-default-btn" onClick={() => onSetDefaultCard(card.id)}>
                    기본으로 설정
                  </button>
                )}
                <button className="delete-card-btn" onClick={() => onDeleteCard(card.id)}>
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default PaymentPanel;
