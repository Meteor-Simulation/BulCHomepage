import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Header from '../../components/Header';
import './AdminPage.css';

interface User {
  email: string;
  name: string;
  phone: string;
  rolesCode: string;
  countryCode: string;
  createdAt: string;
}

interface Product {
  code: string;
  name: string;
  description: string;
  createdAt: string;
}

interface PricePlan {
  id: number;
  productCode: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  isActive: boolean;
  createdAt: string;
}

interface License {
  id: string;
  licenseKey: string;
  ownerType: string;
  ownerId: string;
  status: string;
  validUntil: string;
  createdAt: string;
}

interface Payment {
  id: number;
  userEmail: string;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
}

type TabType = 'users' | 'licenses' | 'payments' | 'products';

const ITEMS_PER_PAGE = 10;

const API_URL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
  ? 'http://localhost:8080'
  : `http://${window.location.hostname}:8080`;

const AdminPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, isAdmin, isAuthReady } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>('users');
  const [isLoading, setIsLoading] = useState(false);

  // 데이터 상태
  const [users, setUsers] = useState<User[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [licenses, setLicenses] = useState<License[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);

  // 검색 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');

  // 페이징 상태
  const [currentPage, setCurrentPage] = useState(1);

  // 권한 체크
  useEffect(() => {
    if (isAuthReady && (!isLoggedIn || !isAdmin)) {
      navigate('/');
    }
  }, [isAuthReady, isLoggedIn, isAdmin, navigate]);

  // 탭 변경 시 검색/페이지 초기화
  useEffect(() => {
    setSearchQuery('');
    setAppliedSearch('');
    setCurrentPage(1);
  }, [activeTab]);

  // 필터링된 데이터
  const filteredUsers = useMemo(() => {
    if (!appliedSearch) return users;
    const query = appliedSearch.toLowerCase();
    return users.filter(user =>
      user.email.toLowerCase().includes(query) ||
      (user.name && user.name.toLowerCase().includes(query)) ||
      (user.phone && user.phone.includes(query))
    );
  }, [users, appliedSearch]);

  const filteredLicenses = useMemo(() => {
    if (!appliedSearch) return licenses;
    const query = appliedSearch.toLowerCase();
    return licenses.filter(license =>
      license.licenseKey.toLowerCase().includes(query) ||
      license.ownerId.toLowerCase().includes(query) ||
      license.status.toLowerCase().includes(query)
    );
  }, [licenses, appliedSearch]);

  const filteredPayments = useMemo(() => {
    if (!appliedSearch) return payments;
    const query = appliedSearch.toLowerCase();
    return payments.filter(payment =>
      payment.userEmail.toLowerCase().includes(query) ||
      payment.orderId.toLowerCase().includes(query) ||
      payment.status.toLowerCase().includes(query)
    );
  }, [payments, appliedSearch]);

  const filteredProducts = useMemo(() => {
    if (!appliedSearch) return products;
    const query = appliedSearch.toLowerCase();
    return products.filter(product =>
      product.code.toLowerCase().includes(query) ||
      product.name.toLowerCase().includes(query)
    );
  }, [products, appliedSearch]);

  const filteredPricePlans = useMemo(() => {
    if (!appliedSearch) return pricePlans;
    const query = appliedSearch.toLowerCase();
    return pricePlans.filter(plan =>
      plan.name.toLowerCase().includes(query) ||
      plan.productCode.toLowerCase().includes(query)
    );
  }, [pricePlans, appliedSearch]);

  // 페이징 계산
  const getPaginatedData = <T,>(data: T[]): T[] => {
    const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
    return data.slice(startIndex, startIndex + ITEMS_PER_PAGE);
  };

  const getTotalPages = (totalItems: number): number => {
    return Math.ceil(totalItems / ITEMS_PER_PAGE);
  };

  // 검색 핸들러
  const handleSearch = () => {
    setAppliedSearch(searchQuery);
    setCurrentPage(1);
  };

  const handleSearchKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    setAppliedSearch('');
    setCurrentPage(1);
  };

  // 탭 변경 시 데이터 로드
  useEffect(() => {
    if (!isAuthReady || !isLoggedIn || !isAdmin) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const fetchData = async () => {
      setIsLoading(true);
      try {
        switch (activeTab) {
          case 'users':
            await fetchUsers(token);
            break;
          case 'licenses':
            await fetchLicenses(token);
            break;
          case 'payments':
            await fetchPayments(token);
            break;
          case 'products':
            await fetchProducts(token);
            await fetchPricePlans(token);
            break;
        }
      } catch (error) {
        console.error('데이터 로드 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [activeTab, isAuthReady, isLoggedIn, isAdmin]);

  const fetchUsers = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/users`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setUsers(data);
    }
  };

  const fetchProducts = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/products`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setProducts(data);
    }
  };

  const fetchPricePlans = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/price-plans`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setPricePlans(data);
    }
  };

  const fetchLicenses = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/license-list`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setLicenses(data);
    }
  };

  const fetchPayments = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/payments`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setPayments(data);
    }
  };

  const getRoleName = (code: string) => {
    switch (code) {
      case '000': return '관리자';
      case '001': return '매니저';
      case '002': return '일반';
      default: return code;
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatPrice = (price: number, currency: string) => {
    if (currency === 'KRW') {
      return price.toLocaleString('ko-KR') + '원';
    }
    return '$' + price.toLocaleString('en-US');
  };

  // 페이지네이션 컴포넌트
  const Pagination: React.FC<{ totalItems: number; filteredCount: number }> = ({ totalItems, filteredCount }) => {
    const totalPages = getTotalPages(filteredCount);
    if (totalPages <= 1) return null;

    const pageNumbers: number[] = [];
    const maxVisiblePages = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pageNumbers.push(i);
    }

    return (
      <div className="pagination">
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(1)}
          disabled={currentPage === 1}
        >
          &laquo;
        </button>
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
          disabled={currentPage === 1}
        >
          &lsaquo;
        </button>
        {startPage > 1 && (
          <>
            <button className="pagination-btn" onClick={() => setCurrentPage(1)}>1</button>
            {startPage > 2 && <span className="pagination-ellipsis">...</span>}
          </>
        )}
        {pageNumbers.map(num => (
          <button
            key={num}
            className={`pagination-btn ${currentPage === num ? 'active' : ''}`}
            onClick={() => setCurrentPage(num)}
          >
            {num}
          </button>
        ))}
        {endPage < totalPages && (
          <>
            {endPage < totalPages - 1 && <span className="pagination-ellipsis">...</span>}
            <button className="pagination-btn" onClick={() => setCurrentPage(totalPages)}>{totalPages}</button>
          </>
        )}
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
          disabled={currentPage === totalPages}
        >
          &rsaquo;
        </button>
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(totalPages)}
          disabled={currentPage === totalPages}
        >
          &raquo;
        </button>
      </div>
    );
  };

  // 검색 바 컴포넌트
  const SearchBar: React.FC<{ placeholder: string }> = ({ placeholder }) => (
    <div className="search-bar">
      <input
        type="text"
        className="search-input"
        placeholder={placeholder}
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onKeyPress={handleSearchKeyPress}
      />
      <button className="search-btn" onClick={handleSearch}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8"/>
          <path d="M21 21l-4.35-4.35"/>
        </svg>
        조회
      </button>
      {appliedSearch && (
        <button className="search-clear-btn" onClick={handleClearSearch}>
          초기화
        </button>
      )}
    </div>
  );

  if (!isAuthReady) {
    return (
      <div className="admin-container">
        <div className="admin-loading">로딩 중...</div>
      </div>
    );
  }

  if (!isLoggedIn || !isAdmin) {
    return null;
  }

  return (
    <>
      <Header logoText="METEOR" />
      <div className="admin-container">
        <div className="admin-content">
          <h1 className="admin-title">관리자 페이지</h1>

          {/* 탭 메뉴 */}
          <div className="admin-tabs">
            <button
              className={`admin-tab ${activeTab === 'users' ? 'active' : ''}`}
              onClick={() => setActiveTab('users')}
            >
              사용자 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'licenses' ? 'active' : ''}`}
              onClick={() => setActiveTab('licenses')}
            >
              라이선스 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'payments' ? 'active' : ''}`}
              onClick={() => setActiveTab('payments')}
            >
              결제 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'products' ? 'active' : ''}`}
              onClick={() => setActiveTab('products')}
            >
              상품 관리
            </button>
          </div>

          {/* 탭 컨텐츠 */}
          <div className="admin-tab-content">
            {isLoading ? (
              <div className="admin-loading">데이터 로딩 중...</div>
            ) : (
              <>
                {/* 사용자 관리 */}
                {activeTab === 'users' && (
                  <div className="admin-section">
                    <SearchBar placeholder="이메일, 이름, 전화번호로 검색" />
                    <div className="admin-section-header">
                      <h2>사용자 목록</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredUsers.length}명 / 전체 ${users.length}명` : `${users.length}명`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>이메일</th>
                            <th>이름</th>
                            <th>전화번호</th>
                            <th>권한</th>
                            <th>국가</th>
                            <th>가입일</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredUsers.length > 0 ? (
                            getPaginatedData(filteredUsers).map((user) => (
                              <tr key={user.email}>
                                <td>{user.email}</td>
                                <td>{user.name || '-'}</td>
                                <td>{user.phone || '-'}</td>
                                <td>
                                  <span className={`role-badge role-${user.rolesCode}`}>
                                    {getRoleName(user.rolesCode)}
                                  </span>
                                </td>
                                <td>{user.countryCode || '-'}</td>
                                <td>{formatDate(user.createdAt)}</td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={6} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '등록된 사용자가 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={users.length} filteredCount={filteredUsers.length} />
                  </div>
                )}

                {/* 라이선스 관리 */}
                {activeTab === 'licenses' && (
                  <div className="admin-section">
                    <SearchBar placeholder="라이선스 키, 소유자 ID, 상태로 검색" />
                    <div className="admin-section-header">
                      <h2>라이선스 목록</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredLicenses.length}개 / 전체 ${licenses.length}개` : `${licenses.length}개`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>라이선스 키</th>
                            <th>소유자 유형</th>
                            <th>소유자 ID</th>
                            <th>상태</th>
                            <th>만료일</th>
                            <th>생성일</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredLicenses.length > 0 ? (
                            getPaginatedData(filteredLicenses).map((license) => (
                              <tr key={license.id}>
                                <td>{license.id}</td>
                                <td className="license-key">{license.licenseKey}</td>
                                <td>{license.ownerType}</td>
                                <td>{license.ownerId}</td>
                                <td>
                                  <span className={`status-badge status-${license.status?.toLowerCase()}`}>
                                    {license.status}
                                  </span>
                                </td>
                                <td>{formatDate(license.validUntil)}</td>
                                <td>{formatDate(license.createdAt)}</td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={7} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '등록된 라이선스가 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={licenses.length} filteredCount={filteredLicenses.length} />
                  </div>
                )}

                {/* 결제 관리 */}
                {activeTab === 'payments' && (
                  <div className="admin-section">
                    <SearchBar placeholder="사용자 이메일, 주문번호, 상태로 검색" />
                    <div className="admin-section-header">
                      <h2>결제 내역</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredPayments.length}건 / 전체 ${payments.length}건` : `${payments.length}건`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>주문번호</th>
                            <th>사용자</th>
                            <th>금액</th>
                            <th>상태</th>
                            <th>결제일</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredPayments.length > 0 ? (
                            getPaginatedData(filteredPayments).map((payment) => (
                              <tr key={payment.id}>
                                <td>{payment.id}</td>
                                <td>{payment.orderId}</td>
                                <td>{payment.userEmail}</td>
                                <td>{formatPrice(payment.amount, payment.currency)}</td>
                                <td>
                                  <span className={`status-badge status-${payment.status?.toLowerCase()}`}>
                                    {payment.status}
                                  </span>
                                </td>
                                <td>{formatDate(payment.createdAt)}</td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={6} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '결제 내역이 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={payments.length} filteredCount={filteredPayments.length} />
                  </div>
                )}

                {/* 상품 관리 */}
                {activeTab === 'products' && (
                  <>
                    <SearchBar placeholder="상품 코드, 상품명, 요금제명으로 검색" />
                    <div className="admin-section">
                      <div className="admin-section-header">
                        <h2>상품 목록</h2>
                        <span className="admin-count">
                          {appliedSearch ? `${filteredProducts.length}개 / 전체 ${products.length}개` : `${products.length}개`}
                        </span>
                      </div>
                      <div className="admin-table-wrapper">
                        <table className="admin-table">
                          <thead>
                            <tr>
                              <th>코드</th>
                              <th>상품명</th>
                              <th>설명</th>
                              <th>생성일</th>
                            </tr>
                          </thead>
                          <tbody>
                            {filteredProducts.length > 0 ? (
                              filteredProducts.map((product) => (
                                <tr key={product.code}>
                                  <td>{product.code}</td>
                                  <td>{product.name}</td>
                                  <td>{product.description || '-'}</td>
                                  <td>{formatDate(product.createdAt)}</td>
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={4} className="empty-row">
                                  {appliedSearch ? '검색 결과가 없습니다.' : '등록된 상품이 없습니다.'}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <div className="admin-section">
                      <div className="admin-section-header">
                        <h2>요금제 목록</h2>
                        <span className="admin-count">
                          {appliedSearch ? `${filteredPricePlans.length}개 / 전체 ${pricePlans.length}개` : `${pricePlans.length}개`}
                        </span>
                      </div>
                      <div className="admin-table-wrapper">
                        <table className="admin-table">
                          <thead>
                            <tr>
                              <th>ID</th>
                              <th>상품코드</th>
                              <th>요금제명</th>
                              <th>설명</th>
                              <th>가격</th>
                              <th>활성화</th>
                              <th>생성일</th>
                            </tr>
                          </thead>
                          <tbody>
                            {filteredPricePlans.length > 0 ? (
                              getPaginatedData(filteredPricePlans).map((plan) => (
                                <tr key={plan.id}>
                                  <td>{plan.id}</td>
                                  <td>{plan.productCode}</td>
                                  <td>{plan.name}</td>
                                  <td>{plan.description || '-'}</td>
                                  <td>{formatPrice(plan.price, plan.currency)}</td>
                                  <td>
                                    <span className={`status-badge ${plan.isActive ? 'status-active' : 'status-inactive'}`}>
                                      {plan.isActive ? '활성' : '비활성'}
                                    </span>
                                  </td>
                                  <td>{formatDate(plan.createdAt)}</td>
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={7} className="empty-row">
                                  {appliedSearch ? '검색 결과가 없습니다.' : '등록된 요금제가 없습니다.'}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                      <Pagination totalItems={pricePlans.length} filteredCount={filteredPricePlans.length} />
                    </div>
                  </>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
};

export default AdminPage;
