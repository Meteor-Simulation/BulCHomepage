import React from 'react';
import { useTranslation } from 'react-i18next';
import { Promotion, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';

interface AdminPromotionsPanelProps extends AdminSearchProps {
  promotions: Promotion[];
  onTogglePromotion: (id: number) => void;
}

const formatAdminDate = (dateStr: string) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const AdminPromotionsPanel: React.FC<AdminPromotionsPanelProps> = ({
  promotions,
  searchQuery,
  appliedSearch,
  currentPage,
  onSearchQueryChange,
  onSearch,
  onSearchKeyPress,
  onClearSearch,
  onPageChange,
  isLoading,
  onTogglePromotion,
}) => {
  const { t } = useTranslation();

  const filtered = !appliedSearch ? promotions : promotions.filter(p => {
    const query = appliedSearch.toLowerCase();
    return p.code.toLowerCase().includes(query) || p.name.toLowerCase().includes(query);
  });

  const totalPages = Math.ceil(filtered.length / ADMIN_ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginated = filtered.slice(startIndex, startIndex + ADMIN_ITEMS_PER_PAGE);

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.menu.adminPromotions')}</h2>
        <span className="admin-count">{filtered.length}개</span>
      </div>
      <div className="admin-search-bar">
        <input
          type="text"
          placeholder="쿠폰 코드, 프로모션명으로 검색"
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
                  <th>쿠폰 코드</th>
                  <th>프로모션명</th>
                  <th>할인율</th>
                  <th>할인금액</th>
                  <th>사용 현황</th>
                  <th>유효 기간</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {paginated.length > 0 ? (
                  paginated.map((p) => (
                    <tr key={p.id}>
                      <td className="coupon-code">{p.code}</td>
                      <td>{p.name}</td>
                      <td>{p.discountType}%</td>
                      <td>{p.discountValue.toLocaleString()}원</td>
                      <td>{p.usageCount} / {p.usageLimit || '∞'}</td>
                      <td>
                        {p.validFrom && p.validUntil ? (
                          <>{formatAdminDate(p.validFrom).split(' ')[0]} ~ {formatAdminDate(p.validUntil).split(' ')[0]}</>
                        ) : '제한 없음'}
                      </td>
                      <td>
                        <button
                          className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                          onClick={() => onTogglePromotion(p.id)}
                        >
                          {p.isActive ? '활성' : '비활성'}
                        </button>
                      </td>
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

export default AdminPromotionsPanel;
