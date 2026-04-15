import React from 'react';
import { useTranslation } from 'react-i18next';
import { AdminLicense, LicensePlan, Product, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';

interface AdminLicensesPanelProps extends AdminSearchProps {
  adminLicenses: AdminLicense[];
  onActivateLicense: (licenseId: string) => void;
  onSuspendLicense: (licenseId: string) => void;
  // 라이선스 플랜
  licensePlans: LicensePlan[];
  products: Product[];
  // 플랜 검색 (독립)
  licensePlanSearchQuery: string;
  licensePlanAppliedSearch: string;
  licensePlanCurrentPage: number;
  onLicensePlanSearchQueryChange: (value: string) => void;
  onLicensePlanSearch: () => void;
  onLicensePlanSearchKeyPress: (e: React.KeyboardEvent) => void;
  onLicensePlanClearSearch: () => void;
  onLicensePlanPageChange: (page: number) => void;
  // 플랜 CRUD
  onOpenLicensePlanModal: (plan?: LicensePlan) => void;
  onToggleLicensePlan: (id: string, currentActive: boolean) => void;
  onDeleteLicensePlan: (id: string) => void;
  // 플랜 모달
  isLicensePlanModalOpen: boolean;
  editingLicensePlan: LicensePlan | null;
  licensePlanForm: {
    productId: string;
    code: string;
    name: string;
    description: string;
    licenseType: 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL';
    durationDays: number;
    graceDays: number;
    maxActivations: number;
    maxConcurrentSessions: number;
    allowOfflineDays: number;
    entitlements: string;
  };
  onLicensePlanFormChange: (form: AdminLicensesPanelProps['licensePlanForm']) => void;
  onCloseLicensePlanModal: () => void;
  onLicensePlanSubmit: () => void;
}

const formatAdminDate = (dateStr: string) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const AdminLicensesPanel: React.FC<AdminLicensesPanelProps> = ({
  adminLicenses,
  searchQuery,
  appliedSearch,
  currentPage,
  onSearchQueryChange,
  onSearch,
  onSearchKeyPress,
  onClearSearch,
  onPageChange,
  isLoading,
  onActivateLicense,
  onSuspendLicense,
  licensePlans,
  products,
  licensePlanSearchQuery,
  licensePlanAppliedSearch,
  licensePlanCurrentPage,
  onLicensePlanSearchQueryChange,
  onLicensePlanSearch,
  onLicensePlanSearchKeyPress,
  onLicensePlanClearSearch,
  onLicensePlanPageChange,
  onOpenLicensePlanModal,
  onToggleLicensePlan,
  onDeleteLicensePlan,
  isLicensePlanModalOpen,
  editingLicensePlan,
  licensePlanForm,
  onLicensePlanFormChange,
  onCloseLicensePlanModal,
  onLicensePlanSubmit,
}) => {
  const { t } = useTranslation();

  // 라이선스 필터링
  const filteredLicenses = !appliedSearch ? adminLicenses : adminLicenses.filter(l => {
    const query = appliedSearch.toLowerCase();
    return l.licenseKey.toLowerCase().includes(query) ||
      l.ownerId.toLowerCase().includes(query) ||
      l.status.toLowerCase().includes(query);
  });

  const licenseTotalPages = Math.ceil(filteredLicenses.length / ADMIN_ITEMS_PER_PAGE);
  const licenseStartIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginatedLicenses = filteredLicenses.slice(licenseStartIndex, licenseStartIndex + ADMIN_ITEMS_PER_PAGE);

  // 플랜 필터링
  const filteredPlans = !licensePlanAppliedSearch ? licensePlans : licensePlans.filter(plan => {
    const query = licensePlanAppliedSearch.toLowerCase();
    return plan.code.toLowerCase().includes(query) ||
      plan.name.toLowerCase().includes(query) ||
      plan.licenseType.toLowerCase().includes(query);
  });

  const planTotalPages = Math.ceil(filteredPlans.length / ADMIN_ITEMS_PER_PAGE);
  const planStartIndex = (licensePlanCurrentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginatedPlans = filteredPlans.slice(planStartIndex, planStartIndex + ADMIN_ITEMS_PER_PAGE);

  const getProductNameById = (productId: string) => {
    const product = products.find(p => p.id === productId);
    return product ? `${product.code} - ${product.name}` : productId;
  };

  return (
    <>
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.menu.adminLicenses')}</h2>
          <span className="admin-count">{filteredLicenses.length}개</span>
        </div>
        <div className="admin-search-bar">
          <input
            type="text"
            placeholder="라이선스 키, 소유자 ID, 상태로 검색"
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
                    <th>라이선스 키</th>
                    <th>소유자 이메일</th>
                    <th>상태</th>
                    <th>만료일</th>
                    <th>생성일</th>
                    <th>관리</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedLicenses.length > 0 ? (
                    paginatedLicenses.map((l) => (
                      <tr key={l.id}>
                        <td className="license-key">{l.licenseKey}</td>
                        <td>{l.ownerId}</td>
                        <td><span className={`status-badge status-${l.status?.toLowerCase()}`}>{l.status}</span></td>
                        <td>{formatAdminDate(l.validUntil)}</td>
                        <td>{formatAdminDate(l.createdAt)}</td>
                        <td>
                          {l.status === 'ACTIVE' ? (
                            <button className="action-btn delete" onClick={() => onSuspendLicense(l.id)}>비활성화</button>
                          ) : l.status === 'SUSPENDED' ? (
                            <button className="action-btn edit" onClick={() => onActivateLicense(l.id)}>활성화</button>
                          ) : (
                            <span>-</span>
                          )}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={6} className="empty-row">데이터가 없습니다.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            {licenseTotalPages > 1 && (
              <div className="admin-pagination">
                <button disabled={currentPage === 1} onClick={() => onPageChange(currentPage - 1)}>&lsaquo;</button>
                <span>{currentPage} / {licenseTotalPages}</span>
                <button disabled={currentPage === licenseTotalPages} onClick={() => onPageChange(currentPage + 1)}>&rsaquo;</button>
              </div>
            )}
          </>
        )}
      </div>

      {/* 라이선스 플랜 관리 */}
      <div className="info-card admin-section-card wide" style={{ marginTop: '24px' }}>
        <div className="card-header">
          <h2 className="card-title">라이선스 플랜 관리</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="admin-count">
              {licensePlanAppliedSearch
                ? `${filteredPlans.length}개 / 전체 ${licensePlans.length}개`
                : `${licensePlans.length}개`
              }
            </span>
            <button className="edit-btn" onClick={() => onOpenLicensePlanModal()}>+ 플랜 추가</button>
          </div>
        </div>
        <div className="admin-search-bar">
          <input
            type="text"
            placeholder="플랜 코드, 플랜명, 라이선스 유형으로 검색"
            value={licensePlanSearchQuery}
            onChange={(e) => onLicensePlanSearchQueryChange(e.target.value)}
            onKeyPress={onLicensePlanSearchKeyPress}
          />
          <button onClick={onLicensePlanSearch}>조회</button>
          {licensePlanAppliedSearch && <button className="clear" onClick={onLicensePlanClearSearch}>초기화</button>}
        </div>
        {isLoading ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <>
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>코드</th>
                    <th>플랜명</th>
                    <th>상품</th>
                    <th>라이선스 유형</th>
                    <th>유효기간</th>
                    <th>최대 기기</th>
                    <th>동시 세션</th>
                    <th>상태</th>
                    <th>관리</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedPlans.length > 0 ? (
                    paginatedPlans.map((plan) => (
                      <tr key={plan.id} className={plan.deleted ? 'deleted-row' : ''}>
                        <td><span className="coupon-code">{plan.code}</span></td>
                        <td>{plan.name}</td>
                        <td>{getProductNameById(plan.productId)}</td>
                        <td>
                          <span className={`status-badge status-${plan.licenseType.toLowerCase()}`}>
                            {plan.licenseType}
                          </span>
                        </td>
                        <td>{plan.durationDays}일</td>
                        <td>{plan.maxActivations}</td>
                        <td>{plan.maxConcurrentSessions}</td>
                        <td>
                          {plan.deleted ? (
                            <span className="status-badge status-deleted">삭제됨</span>
                          ) : (
                            <button
                              className={`status-toggle-btn ${plan.active ? 'active' : 'inactive'}`}
                              onClick={() => onToggleLicensePlan(plan.id, plan.active)}
                            >
                              {plan.active ? '활성' : '비활성'}
                            </button>
                          )}
                        </td>
                        <td>
                          {!plan.deleted && (
                            <div className="action-btn-group">
                              <button className="action-btn edit" onClick={() => onOpenLicensePlanModal(plan)}>수정</button>
                              <button className="action-btn delete" onClick={() => onDeleteLicensePlan(plan.id)}>삭제</button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={9} className="empty-row">
                        {licensePlanAppliedSearch ? '검색 결과가 없습니다.' : '등록된 라이선스 플랜이 없습니다.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {planTotalPages > 1 && (
              <div className="admin-pagination">
                <button disabled={licensePlanCurrentPage === 1} onClick={() => onLicensePlanPageChange(licensePlanCurrentPage - 1)}>&lsaquo;</button>
                <span>{licensePlanCurrentPage} / {planTotalPages}</span>
                <button disabled={licensePlanCurrentPage === planTotalPages} onClick={() => onLicensePlanPageChange(licensePlanCurrentPage + 1)}>&rsaquo;</button>
              </div>
            )}
          </>
        )}
      </div>

      {/* 라이선스 플랜 모달 */}
      {isLicensePlanModalOpen && (
        <div className="admin-modal-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) onCloseLicensePlanModal(); }}>
          <div className="admin-modal">
            <button className="admin-modal-close" onClick={onCloseLicensePlanModal}>&times;</button>
            <div className="admin-modal-header">
              <h3>{editingLicensePlan ? '라이선스 플랜 수정' : '라이선스 플랜 추가'}</h3>
            </div>
            <div className="admin-modal-body">
              <div className="form-group">
                <label>상품 <span>*</span></label>
                <select className="admin-modal-input" value={licensePlanForm.productId}
                  onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, productId: e.target.value })}>
                  <option value="">상품 선택</option>
                  {products.map(p => (<option key={p.id} value={p.id}>{p.code} - {p.name}</option>))}
                </select>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>플랜 코드 <span>*</span></label>
                  <input type="text" className="admin-modal-input" value={licensePlanForm.code}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, code: e.target.value })}
                    placeholder="예: BULC-PRO-1Y" maxLength={64} />
                </div>
                <div className="form-group">
                  <label>플랜명 <span>*</span></label>
                  <input type="text" className="admin-modal-input" value={licensePlanForm.name}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, name: e.target.value })}
                    placeholder="예: BUL:C PRO 1년" />
                </div>
              </div>
              <div className="form-group">
                <label>라이선스 유형 <span>*</span></label>
                <select className="admin-modal-input" value={licensePlanForm.licenseType}
                  onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, licenseType: e.target.value as 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL' })}>
                  <option value="TRIAL">TRIAL (체험판)</option>
                  <option value="SUBSCRIPTION">SUBSCRIPTION (구독)</option>
                  <option value="PERPETUAL">PERPETUAL (영구)</option>
                </select>
              </div>
              <div className="admin-modal-form-row">
                <div className="form-group">
                  <label>최대 기기 수 <span>*</span></label>
                  <input type="number" className="admin-modal-input" value={licensePlanForm.maxActivations}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, maxActivations: parseInt(e.target.value) || 1 })} min={1} />
                </div>
                <div className="form-group">
                  <label>최대 세션 <span>*</span></label>
                  <input type="number" className="admin-modal-input" value={licensePlanForm.maxConcurrentSessions}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, maxConcurrentSessions: parseInt(e.target.value) || 1 })} min={1} />
                </div>
              </div>
              <div className="admin-modal-form-row three-col">
                <div className="form-group">
                  <label>유효기간 <span>*</span></label>
                  <input type="number" className="admin-modal-input" value={licensePlanForm.durationDays}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, durationDays: parseInt(e.target.value) || 0 })} min={0} />
                </div>
                <div className="form-group">
                  <label>유예기간</label>
                  <input type="number" className="admin-modal-input" value={licensePlanForm.graceDays}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, graceDays: parseInt(e.target.value) || 0 })} min={0} />
                </div>
                <div className="form-group">
                  <label>오프라인</label>
                  <input type="number" className="admin-modal-input" value={licensePlanForm.allowOfflineDays}
                    onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, allowOfflineDays: parseInt(e.target.value) || 0 })} min={0} />
              </div>
              <div className="form-group vertical">
                <label>설명</label>
                <textarea className="admin-modal-input" value={licensePlanForm.description}
                  onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, description: e.target.value })}
                  placeholder="플랜 설명을 입력하세요" rows={2} />
              </div>
              <div className="form-group vertical">
                <label>Entitlements</label>
                <textarea className="admin-modal-input" value={licensePlanForm.entitlements}
                  onChange={(e) => onLicensePlanFormChange({ ...licensePlanForm, entitlements: e.target.value })}
                  placeholder="쉼표로 구분 (예: core-simulation, advanced-visualization)" rows={2} />
                <span className="admin-modal-input-hint">기능 식별자를 쉼표(,)로 구분하여 입력하세요</span>
              </div>
            </div>
            <div className="admin-modal-footer">
              <button className="cancel-btn" onClick={onCloseLicensePlanModal}>취소</button>
              <button className="save-btn" onClick={onLicensePlanSubmit}>
                {editingLicensePlan ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminLicensesPanel;
