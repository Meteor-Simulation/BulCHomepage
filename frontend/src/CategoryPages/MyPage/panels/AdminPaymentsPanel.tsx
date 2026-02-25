import React from 'react';
import { useTranslation } from 'react-i18next';
import { AdminPayment, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';

interface AdminPaymentsPanelProps extends AdminSearchProps {
  adminPayments: AdminPayment[];
}

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
                      <td><span className={`status-badge status-${p.status?.toLowerCase()}`}>{p.status}</span></td>
                      <td>{formatAdminDate(p.createdAt)}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan={7} className="empty-row">데이터가 없습니다.</td></tr>
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
