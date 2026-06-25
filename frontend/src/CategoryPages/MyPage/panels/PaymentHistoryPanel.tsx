import React from 'react';
import { useTranslation } from 'react-i18next';

export interface PaymentHistoryItem {
  orderId: string | null;
  orderName: string | null;
  amount: number;
  currency: string;
  status: string; // PENDING / COMPLETED / FAILED / REFUNDED
  paymentMethod: string | null;
  cardCompany: string | null;
  cardNumber: string | null;
  paidAt: string | null;
  createdAt: string | null;
  refundedAt: string | null;
  refundAmount: number | null;
}

interface PaymentHistoryPanelProps {
  isLoading: boolean;
  payments: PaymentHistoryItem[];
}

// 상태값 → 배지 클래스(공통 .status-badge 색상 재사용)
const STATUS_CLASS: Record<string, string> = {
  COMPLETED: 'done',
  PENDING: 'pending',
  FAILED: 'failed',
  REFUNDED: 'canceled',
};

const PaymentHistoryPanel: React.FC<PaymentHistoryPanelProps> = ({ isLoading, payments }) => {
  const { t } = useTranslation();

  const formatDate = (s: string | null) => {
    if (!s) return '-';
    return new Date(s).toLocaleDateString('ko-KR', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    });
  };

  const formatPrice = (amount: number, currency: string) =>
    currency === 'KRW' ? amount.toLocaleString('ko-KR') + '원' : '$' + amount.toLocaleString('en-US');

  const formatMethod = (m: string | null) => {
    if (!m) return '-';
    if (m.startsWith('EASY_PAY_')) return `${t('myPage.paymentHistory.method.EASY_PAY')} (${m.replace('EASY_PAY_', '')})`;
    return t(`myPage.paymentHistory.method.${m}`, { defaultValue: m });
  };

  return (
    <div className="info-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.paymentHistory.title')}</h2>
        <span className="admin-count">{t('myPage.paymentHistory.count', { count: payments.length })}</span>
      </div>
      {isLoading ? (
        <div className="admin-loading">{t('myPage.paymentHistory.loading')}</div>
      ) : (
        <div className="admin-table-wrapper">
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('myPage.paymentHistory.colOrder')}</th>
                <th>{t('myPage.paymentHistory.colMethod')}</th>
                <th>{t('myPage.paymentHistory.colAmount')}</th>
                <th>{t('myPage.paymentHistory.colStatus')}</th>
                <th>{t('myPage.paymentHistory.colDate')}</th>
              </tr>
            </thead>
            <tbody>
              {payments.length > 0 ? payments.map((p, i) => {
                const cls = STATUS_CLASS[p.status] || '';
                const label = t(`myPage.paymentHistory.status.${p.status}`, { defaultValue: p.status });
                return (
                  <tr key={p.orderId || i}>
                    <td>{p.orderName || '-'}</td>
                    <td>{formatMethod(p.paymentMethod)}{p.cardNumber ? ` (${p.cardNumber})` : ''}</td>
                    <td>
                      {formatPrice(p.amount, p.currency)}
                      {p.status === 'REFUNDED' && p.refundAmount
                        ? ` (${t('myPage.paymentHistory.refundedSuffix', { amount: formatPrice(p.refundAmount, p.currency) })})`
                        : ''}
                    </td>
                    <td><span className={`status-badge status-${cls}`}>{label}</span></td>
                    <td>{formatDate(p.paidAt || p.createdAt)}</td>
                  </tr>
                );
              }) : (
                <tr><td colSpan={5} className="empty-row">{t('myPage.paymentHistory.empty')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default PaymentHistoryPanel;
