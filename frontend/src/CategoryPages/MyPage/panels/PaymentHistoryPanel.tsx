import React from 'react';
import { useTranslation } from 'react-i18next';

export interface PaymentHistoryItem {
  /** 환불 신청에 쓰는 결제 id. 본인 결제만 내려오고 서버가 소유자를 다시 확인한다 */
  id: number;
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
  /** 환불 요청 상태 — PENDING / APPROVED / REJECTED. 신청 이력이 없으면 null */
  refundRequestStatus: string | null;
  /** 관리자 메모 (거절 사유 등) — 왜 거절됐는지 고객이 알 수 있어야 한다 */
  refundRequestNote: string | null;
}

interface PaymentHistoryPanelProps {
  isLoading: boolean;
  payments: PaymentHistoryItem[];
  /** 환불 신청 — 고객은 신청만 하고 실제 환불은 관리자가 처리한다 */
  onRequestRefund: (payment: PaymentHistoryItem) => void;
  /** 신청 전송이 진행 중인 결제 id */
  requestingRefundId: number | null;
}

// 상태값 → 배지 클래스(공통 .status-badge 색상 재사용)
const STATUS_CLASS: Record<string, string> = {
  COMPLETED: 'done',
  PENDING: 'pending',
  FAILED: 'failed',
  REFUNDED: 'canceled',
};

/** 환불 요청 상태 → 표시 문구 키 */
const REFUND_STATUS_KEY: Record<string, string> = {
  PENDING: 'refundPending',
  APPROVED: 'refundApproved',
  REJECTED: 'refundRejected',
};

const PaymentHistoryPanel: React.FC<PaymentHistoryPanelProps> = ({
  isLoading, payments, onRequestRefund, requestingRefundId,
}) => {
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
                <th>{t('myPage.paymentHistory.colRefund')}</th>
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
                    <td>
                      {/* 신청 이력이 있으면 그 상태를 보여 준다. 거절이면 사유도 함께 —
                          왜 안 됐는지 모르면 고객은 같은 신청을 반복하게 된다. */}
                      {p.refundRequestStatus ? (
                        <span className="refund-request-state">
                          {t(`myPage.paymentHistory.${REFUND_STATUS_KEY[p.refundRequestStatus] || 'refundPending'}`)}
                          {p.refundRequestStatus === 'REJECTED' && p.refundRequestNote
                            ? ` — ${p.refundRequestNote}`
                            : ''}
                        </span>
                      ) : p.status === 'COMPLETED' ? (
                        <button
                          type="button"
                          className="action-btn"
                          onClick={() => onRequestRefund(p)}
                          disabled={requestingRefundId !== null}
                        >
                          {requestingRefundId === p.id
                            ? t('myPage.paymentHistory.refundRequesting')
                            : t('myPage.paymentHistory.refundRequest')}
                        </button>
                      ) : (
                        <span>-</span>
                      )}
                    </td>
                  </tr>
                );
              }) : (
                <tr><td colSpan={6} className="empty-row">{t('myPage.paymentHistory.empty')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default PaymentHistoryPanel;
