import React from 'react';

export interface AdminRefundRequest {
  id: number;
  paymentId: number;
  orderId: string | null;
  userEmail: string | null;
  userName: string | null;
  amount: number | null;
  currency: string | null;
  reason: string;
  status: string; // PENDING / APPROVED / REJECTED
  adminNote: string | null;
  createdAt: string | null;
  processedAt: string | null;
}

interface AdminRefundRequestsPanelProps {
  requests: AdminRefundRequest[];
  isLoading: boolean;
  /** 승인 — 토스 승인취소까지 수행된다 */
  onApprove: (request: AdminRefundRequest) => void;
  /** 거절 — 돈은 움직이지 않는다 */
  onReject: (request: AdminRefundRequest) => void;
  /** 처리 중인 요청 id */
  processingId: number | null;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: '대기',
  APPROVED: '환불 완료',
  REJECTED: '거절',
};

const STATUS_CLASS: Record<string, string> = {
  PENDING: 'pending',
  APPROVED: 'canceled',
  REJECTED: 'failed',
};

const formatDate = (s: string | null) => {
  if (!s) return '-';
  return new Date(s).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const formatPrice = (amount: number | null, currency: string | null) => {
  if (amount === null) return '-';
  return currency === 'KRW' ? `${amount.toLocaleString('ko-KR')}원` : `$${amount.toLocaleString('en-US')}`;
};

/**
 * 환불 요청 목록 (관리자).
 *
 * 고객이 낸 요청을 확인하고 승인·거절한다. 승인하면 토스 승인취소까지 수행되고, 실패하면
 * 요청은 대기 상태로 남는다 — 환불이 안 됐는데 "완료" 로 적히면 고객은 받은 줄 알고 기다린다.
 *
 * 대기 건을 위로 올린다. 처리해야 할 일이 목록 아래에 묻히면 응대가 늦어진다.
 */
const AdminRefundRequestsPanel: React.FC<AdminRefundRequestsPanelProps> = ({
  requests, isLoading, onApprove, onReject, processingId,
}) => {
  const sorted = [...requests].sort((a, b) => {
    if (a.status === 'PENDING' && b.status !== 'PENDING') return -1;
    if (a.status !== 'PENDING' && b.status === 'PENDING') return 1;
    return (b.createdAt || '').localeCompare(a.createdAt || '');
  });
  const pendingCount = requests.filter((r) => r.status === 'PENDING').length;

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">환불 요청</h2>
        <span className="admin-count">
          {pendingCount > 0 ? `대기 ${pendingCount}건 / 전체 ${requests.length}건` : `${requests.length}건`}
        </span>
      </div>
      {isLoading ? (
        <div className="admin-loading">데이터 로딩 중...</div>
      ) : (
        <div className="admin-table-wrapper">
          <table className="admin-table">
            <thead>
              <tr>
                <th>주문번호</th>
                <th>신청자</th>
                <th>금액</th>
                <th>사유</th>
                <th>상태</th>
                <th>신청일</th>
                <th>처리</th>
              </tr>
            </thead>
            <tbody>
              {sorted.length > 0 ? sorted.map((r) => (
                <tr key={r.id}>
                  <td className="order-id">{r.orderId || `#${r.paymentId}`}</td>
                  <td>{r.userName || '-'}<br /><small>{r.userEmail || '-'}</small></td>
                  <td>{formatPrice(r.amount, r.currency)}</td>
                  <td className="refund-reason-cell">
                    {r.reason}
                    {r.adminNote ? <><br /><small>메모: {r.adminNote}</small></> : null}
                  </td>
                  <td>
                    <span className={`status-badge status-${STATUS_CLASS[r.status] || ''}`}>
                      {STATUS_LABEL[r.status] || r.status}
                    </span>
                  </td>
                  <td>{formatDate(r.createdAt)}</td>
                  <td>
                    {r.status === 'PENDING' ? (
                      <div className="action-btn-group">
                        <button
                          className="action-btn delete"
                          onClick={() => onApprove(r)}
                          disabled={processingId !== null}
                        >
                          {processingId === r.id ? '처리 중…' : '환불 승인'}
                        </button>
                        <button
                          className="action-btn"
                          onClick={() => onReject(r)}
                          disabled={processingId !== null}
                        >
                          거절
                        </button>
                      </div>
                    ) : (
                      <span>{formatDate(r.processedAt)}</span>
                    )}
                  </td>
                </tr>
              )) : (
                <tr><td colSpan={7} className="empty-row">환불 요청이 없습니다.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminRefundRequestsPanel;
