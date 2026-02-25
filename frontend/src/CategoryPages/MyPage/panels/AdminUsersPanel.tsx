import React from 'react';
import { useTranslation } from 'react-i18next';
import { AdminUser, AdminSearchProps } from '../types';
import { ADMIN_ITEMS_PER_PAGE } from '../constants';
import { formatPhoneNumber } from '../../../utils/phoneUtils';

interface AdminUsersPanelProps extends AdminSearchProps {
  adminUsers: AdminUser[];
}

const getRoleName = (code: string) => {
  switch (code) {
    case '000': return '관리자';
    case '001': return '매니저';
    case '002': return '일반';
    default: return code;
  }
};

const formatAdminDate = (dateStr: string) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const AdminUsersPanel: React.FC<AdminUsersPanelProps> = ({
  adminUsers,
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

  const filtered = !appliedSearch ? adminUsers : adminUsers.filter(u => {
    const query = appliedSearch.toLowerCase();
    return u.email.toLowerCase().includes(query) ||
      (u.name && u.name.toLowerCase().includes(query)) ||
      (u.phone && u.phone.includes(query));
  });

  const totalPages = Math.ceil(filtered.length / ADMIN_ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
  const paginated = filtered.slice(startIndex, startIndex + ADMIN_ITEMS_PER_PAGE);

  return (
    <div className="info-card admin-section-card wide">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.menu.adminUsers')}</h2>
        <span className="admin-count">{filtered.length}명</span>
      </div>
      <div className="admin-search-bar">
        <input
          type="text"
          placeholder="이메일, 이름, 전화번호로 검색"
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
                  <th>이메일</th>
                  <th>이름</th>
                  <th>전화번호</th>
                  <th>권한</th>
                  <th>가입일</th>
                </tr>
              </thead>
              <tbody>
                {paginated.length > 0 ? (
                  paginated.map((u) => (
                    <tr key={u.id}>
                      <td>{u.email}</td>
                      <td>{u.name || '-'}</td>
                      <td>{formatPhoneNumber(u.phone) || '-'}</td>
                      <td><span className={`role-badge role-${u.rolesCode}`}>{getRoleName(u.rolesCode)}</span></td>
                      <td>{formatAdminDate(u.createdAt)}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan={5} className="empty-row">데이터가 없습니다.</td></tr>
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

export default AdminUsersPanel;
