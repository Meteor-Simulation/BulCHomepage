import React from 'react';
import { useTranslation } from 'react-i18next';
import { Product, PricePlan, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';

interface AdminProductsPanelProps extends AdminSearchProps {
  products: Product[];
  pricePlans: PricePlan[];
  onToggleProduct: (code: string) => void;
  onTogglePricePlan: (id: number) => void;
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

const AdminProductsPanel: React.FC<AdminProductsPanelProps> = ({
  products,
  pricePlans,
  searchQuery,
  appliedSearch,
  currentPage,
  onSearchQueryChange,
  onSearch,
  onSearchKeyPress,
  onClearSearch,
  onPageChange,
  isLoading,
  onToggleProduct,
  onTogglePricePlan,
}) => {
  const { t } = useTranslation();

  const filteredProducts = !appliedSearch ? products : products.filter(p => {
    const query = appliedSearch.toLowerCase();
    return p.code.toLowerCase().includes(query) || p.name.toLowerCase().includes(query);
  });

  const filteredPricePlans = !appliedSearch ? pricePlans : pricePlans.filter(p => {
    const query = appliedSearch.toLowerCase();
    return p.name.toLowerCase().includes(query) || p.productCode.toLowerCase().includes(query);
  });

  const totalPages = Math.ceil(filteredPricePlans.length / ADMIN_ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginatedPricePlans = filteredPricePlans.slice(startIndex, startIndex + ADMIN_ITEMS_PER_PAGE);

  return (
    <>
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.menu.adminProducts')}</h2>
          <span className="admin-count">{filteredProducts.length}개</span>
        </div>
        <div className="admin-search-bar">
          <input
            type="text"
            placeholder="상품 코드, 상품명으로 검색"
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
          <div className="admin-table-wrapper">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>코드</th>
                  <th>상품명</th>
                  <th>설명</th>
                  <th>상태</th>
                  <th>생성일</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.length > 0 ? (
                  filteredProducts.map((p) => (
                    <tr key={p.code}>
                      <td>{p.code}</td>
                      <td>{p.name}</td>
                      <td>{p.description || '-'}</td>
                      <td>
                        <button
                          className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                          onClick={() => onToggleProduct(p.code)}
                        >
                          {p.isActive ? '활성' : '비활성'}
                        </button>
                      </td>
                      <td>{formatAdminDate(p.createdAt)}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan={5} className="empty-row">데이터가 없습니다.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">요금제 목록</h2>
          <span className="admin-count">{filteredPricePlans.length}개</span>
        </div>
        {isLoading ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <>
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>상품코드</th>
                    <th>요금제명</th>
                    <th>가격</th>
                    <th>상태</th>
                    <th>생성일</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedPricePlans.length > 0 ? (
                    paginatedPricePlans.map((p) => (
                      <tr key={p.id}>
                        <td>{p.id}</td>
                        <td>{p.productCode}</td>
                        <td>{p.name}</td>
                        <td>{formatAdminPrice(p.price, p.currency)}</td>
                        <td>
                          <button
                            className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                            onClick={() => onTogglePricePlan(p.id)}
                          >
                            {p.isActive ? '활성' : '비활성'}
                          </button>
                        </td>
                        <td>{formatAdminDate(p.createdAt)}</td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={6} className="empty-row">데이터가 없습니다.</td></tr>
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
    </>
  );
};

export default AdminProductsPanel;
