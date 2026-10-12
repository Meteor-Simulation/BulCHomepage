import React from 'react';
import { useTranslation } from 'react-i18next';
import { AdminPayment, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';

interface AdminPaymentsPanelProps extends AdminSearchProps {
  adminPayments: AdminPayment[];
  onCancelPayment: (payment: AdminPayment) => void;
  /** 취소 요청이 진행 중인 결제 id — 해당 행의 버튼만 잠근다 */
  cancelingPaymentId: number | null;
  /** 환불 요청 사유 확인·처리 창 열기 — 환불 대기 건에만 쓰인다 */
  onReviewRefund: (payment: AdminPayment) => void;
}

/**
 * 상태 코드 → 표시 문구. init.sql 규약: P 대기 · C 완료 · F 실패 · R 환불
 *
 * REFUND_PENDING 은 DB 상태가 아니다 — 환불 요청이 대기 중인 완료 결제에 서버가 붙여 주는
 * 표시값이다(AdminController). 관리자가 처리할 일이 걸려 있다는 사실이 목록에서 바로 보여야 한다.
 */
const STATUS_LABEL: { [key: string]: string } = {
  P: '대기', C: '완료', F: '실패', R: '환불',
  PENDING: '대기', COMPLETED: '완료', FAILED: '실패', REFUNDED: '환불',
  REFUND_PENDING: '환불 대기',
};

/** 배지 색. 환불 대기는 처리가 필요한 상태라 '대기' 와 같은 계열로 눈에 띄게 둔다. */
const STATUS_CLASS: { [key: string]: string } = {
  REFUND_PENDING: 'pending',
};

/**
 * 완료된 결제만 취소할 수 있다 — 대기·실패는 취소할 승인이 없고, 환불은 이미 끝났다.
 *
 * 환불 요청이 걸린 건(REFUND_PENDING)도 취소할 수 있다. 요청을 거치지 않고 바로 처리해야
 * 할 때가 있고, 결제 자체는 여전히 완료 상태다. 다만 요청 화면에서 승인하는 쪽이 요청 상태까지
 * 함께 정리되므로 그 경로를 권한다.
 */
const isCancelable = (status: string | null) =>
  status === 'C' || status === 'COMPLETED' || status === 'REFUND_PENDING';

const formatAdminDate = (dateStr: string) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const formatAdminPrice = (price: number, currency: string) => {
  if (currency === 'KRW') return price.toLocaleString('ko-KR') + '원';
  return '$' + price.toLocaleString('en-US');
};

const formatPaymentMethod = (method: string | null) => {
  if (!method) return '-';
  const methodMap: { [key: string]: string } = {
    'CARD': '카드', 'VIRTUAL_ACCOUNT': '가상계좌', 'EASY_PAY': '간편결제',
    'TRANSFER': '계좌이체', 'MOBILE': '휴대폰',
  };
  if (method.startsWith('EASY_PAY_')) return `간편결제(${method.replace('EASY_PAY_', '')})`;
  return methodMap[method] || method;
};

const AdminPaymentsPanel: React.FC<AdminPaymentsPanelProps> = ({
  adminPayments,
  onCancelPayment,
  cancelingPaymentId,
  onReviewRefund,
  searchQuery,
  appliedSearch,
  currentPage,
  onSearchQueryChange,
  onSearch,
  onSearchKeyPress,
  onClearSearch,
  onPageChange,
  isLoading,
}) => {
  const { t } = useTranslation();

  const filtered = !appliedSearch ? adminPayments : adminPayments.filter(p => {
    const query = appliedSearch.toLowerCase();
    return p.userEmail.toLowerCase().includes(query) ||
      (p.userName && p.userName.toLowerCase().includes(query)) ||
      p.orderId.toLowerCase().includes(query);
  });

  const totalPages = Math.ceil(filtered.length / ADMIN_ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginated = filtered.slice(startIndex, startIndex + ADMIN_ITEMS_PER_PAGE);

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.menu.adminPayments')}</h2>
        <span className="admin-count">{filtered.length}건</span>
      </div>
      <div className="admin-search-bar">
        <input
          type="text"
          placeholder="이메일, 이름, 주문번호로 검색"
          value={searchQuery}
          onChange={(e) => onSearchQueryChange(e.target.value)}
          onKeyPress={onSearchKeyPress}
        />
        <button onClick={onSearch}>조회</button>
        {appliedSearch && <button className="clear" onClick={onClearSearch}>초기화</button>}
      </div>
      {isLoading ? (
        <div className="admin-loading">데이터 로딩 중...</div>
      ) : (
        <>
          <div className="admin-table-wrapper">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>주문번호</th>
                  <th>이름</th>
                  <th>이메일</th>
                  <th>결제수단</th>
                  <th>금액</th>
                  <th>상태</th>
                  <th>결제일</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {paginated.length > 0 ? (
                  paginated.map((p) => (
                    <tr key={p.id}>
                      <td className="order-id">{p.orderId}</td>
                      <td>{p.userName || '-'}</td>
                      <td>{p.userEmail}</td>
                      <td>{formatPaymentMethod(p.paymentMethod)}</td>
                      <td>{formatAdminPrice(p.amount, p.currency)}</td>
                      <td>
                        <span className={`status-badge status-${STATUS_CLASS[p.status] || p.status?.toLowerCase()}`}>
                          {STATUS_LABEL[p.status] || p.status}
                        </span>
                      </td>
                      <td>{formatAdminDate(p.createdAt)}</td>
                      <td>
                        {/* 환불 요청이 걸린 건은 사유를 보고 판단해야 한다 — 사유 없이 환불
                            버튼만 있으면 관리자가 다른 화면으로 옮겨 다녀야 했다. */}
                        {p.refundRequestId ? (
                          <button
                            className="action-btn edit"
                            onClick={() => onReviewRefund(p)}
                            disabled={cancelingPaymentId !== null}
                          >
                            사유 확인
                          </button>
                        ) : isCancelable(p.status) ? (
                          <button
                            className="action-btn delete"
                            onClick={() => onCancelPayment(p)}
                            disabled={cancelingPaymentId !== null}
                          >
                            {cancelingPaymentId === p.id ? '취소 중…' : '환불'}
                          </button>
                        ) : (
                          <span>-</span>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan={8} className="empty-row">데이터가 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div className="admin-pagination">
              <button disabled={currentPage === 1} onClick={() => onPageChange(currentPage - 1)}>&lsaquo;</button>
              <span>{currentPage} / {totalPages}</span>
              <button disabled={currentPage === totalPages} onClick={() => onPageChange(currentPage + 1)}>&rsaquo;</button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default AdminPaymentsPanel;
