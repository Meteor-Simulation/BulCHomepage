import React from 'react';
import { useTranslation } from 'react-i18next';
import { BillingKey } from '../types';

interface PaymentPanelProps {
  isLoadingBillingKeys: boolean;
  billingKeys: BillingKey[];
  onAddCard: () => void;
  onSetDefaultCard: (billingKeyId: number) => void;
  onDeleteCard: (billingKeyId: number) => void;
}

const PaymentPanel: React.FC<PaymentPanelProps> = ({
  isLoadingBillingKeys,
  billingKeys,
  onAddCard,
  onSetDefaultCard,
  onDeleteCard,
}) => {
  const { t } = useTranslation();

  return (
    <div className="info-card payment-methods-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.paymentMethod')}</h2>
      </div>

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

      {!isLoadingBillingKeys && (
        <button type="button" className="add-card-btn" onClick={onAddCard}>
          + {t('myPage.addCard')}
        </button>
      )}
    </div>
  );
};

export default PaymentPanel;
