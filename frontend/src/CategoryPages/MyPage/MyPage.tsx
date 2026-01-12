import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Header from '../../components/Header';
import LoginModal from '../../components/LoginModal';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import './MyPage.css';

interface UserInfo {
  email: string;
  name: string;
  phone: string;
  country: string;
}

interface License {
  id: string;
  productId: string;
  productName: string | null;
  planName: string | null;
  licenseType: string;
  status: string;
  validFrom: string;
  validUntil: string;
  entitlements: string[];
  usedActivations: number;
  maxActivations: number;
}

const COUNTRIES = [
  { code: 'KR', name: '대한민국', currency: 'KRW' },
  { code: 'CN', name: '중국', currency: 'USD' },
  { code: 'JP', name: '일본', currency: 'USD' },
  { code: 'US', name: '미국', currency: 'USD' },
  { code: 'EU', name: '유럽', currency: 'USD' },
  { code: 'RU', name: '러시아', currency: 'USD' },
];

const LANGUAGES = [
  { code: 'ko', name: '한국어' },
  { code: 'en', name: 'English' },
];

const API_URL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
  ? 'http://localhost:8080'
  : `http://${window.location.hostname}:8080`;

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, isAuthReady, logout, user } = useAuth();

  // 관리자 여부 (rolesCode '000'만)
  const isSystemAdmin = user?.rolesCode === '000';

  // 사용자 정보
  const [userInfo, setUserInfo] = useState<UserInfo>({ email: '', name: '', phone: '', country: 'KR' });
  const [isLoading, setIsLoading] = useState(true);

  // 라이선스 정보
  const [licenses, setLicenses] = useState<License[]>([]);
  const [isLoadingLicenses, setIsLoadingLicenses] = useState(false);

  // 프로필 수정 모드
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [editName, setEditName] = useState('');
  const [editPhone, setEditPhone] = useState('');

  // 비밀번호 변경
  const [isEditingPassword, setIsEditingPassword] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // 설정 (국가/언어)
  const [isEditingSettings, setIsEditingSettings] = useState(false);
  const [selectedCountry, setSelectedCountry] = useState('KR');
  const [selectedLanguage, setSelectedLanguage] = useState(() => {
    return localStorage.getItem('language') || 'ko';
  });
  // 임시 설정값 (저장 전)
  const [tempCountry, setTempCountry] = useState('KR');
  const [tempLanguage, setTempLanguage] = useState(() => {
    return localStorage.getItem('language') || 'ko';
  });

  // 메시지
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // 개발자 미리보기 (관리자 전용)
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  // 계정 삭제
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  // 로그인 모달
  const [loginModalOpen, setLoginModalOpen] = useState(false);

  // 로그인 체크 - 미로그인 시 로그인 모달 표시
  useEffect(() => {
    if (isAuthReady && !isLoggedIn) {
      setLoginModalOpen(true);
    }
  }, [isAuthReady, isLoggedIn]);

  // 사용자 정보 로드
  useEffect(() => {
    const fetchUserInfo = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      try {
        const response = await fetch(`${API_URL}/api/users/me`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setUserInfo(data);
          setEditName(data.name || '');
          // 전화번호를 포맷팅하여 표시
          setEditPhone(formatPhoneNumber(data.phone) || '');
          setSelectedCountry(data.country || 'KR');
        }
      } catch (error) {
        console.error('사용자 정보 로드 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    if (isLoggedIn) {
      fetchUserInfo();
    }
  }, [isLoggedIn]);

  // 라이선스 정보 로드
  useEffect(() => {
    const fetchLicenses = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      setIsLoadingLicenses(true);
      try {
        const response = await fetch(`${API_URL}/api/me/licenses`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setLicenses(data.licenses || []);
        }
      } catch (error) {
        console.error('라이선스 정보 로드 실패:', error);
      } finally {
        setIsLoadingLicenses(false);
      }
    };

    if (isLoggedIn) {
      fetchLicenses();
    }
  }, [isLoggedIn]);

  // 비밀번호 유효성 검사
  const validatePassword = (password: string): string | null => {
    if (password.length < 8) {
      return '비밀번호는 8자 이상이어야 합니다.';
    }
    if (!/[a-zA-Z]/.test(password)) {
      return '비밀번호는 영문을 포함해야 합니다.';
    }
    if (!/[0-9]/.test(password)) {
      return '비밀번호는 숫자를 포함해야 합니다.';
    }
    if (!/[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(password)) {
      return '비밀번호는 특수문자를 포함해야 합니다.';
    }
    return null;
  };

  // 프로필 저장
  const handleSaveProfile = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: editName,
          // 저장할 때는 숫자만 추출하여 저장
          phone: cleanPhoneNumber(editPhone),
          country: userInfo.country,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setUserInfo(data);
        setIsEditingProfile(false);
        showSuccess('프로필이 저장되었습니다.');
      } else {
        showError('프로필 저장에 실패했습니다.');
      }
    } catch (error) {
      showError('프로필 저장 중 오류가 발생했습니다.');
    }
  };

  // 설정 편집 시작
  const handleStartEditSettings = () => {
    setTempCountry(selectedCountry);
    setTempLanguage(selectedLanguage);
    setIsEditingSettings(true);
  };

  // 설정 저장
  const handleSaveSettings = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      // 국가 저장 (서버)
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: userInfo.name,
          phone: userInfo.phone,
          country: tempCountry,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setUserInfo(data);
        setSelectedCountry(tempCountry);

        // 언어 저장 (로컬)
        setSelectedLanguage(tempLanguage);
        localStorage.setItem('language', tempLanguage);

        setIsEditingSettings(false);
        showSuccess('설정이 저장되었습니다.');
      } else {
        showError('설정 저장에 실패했습니다.');
      }
    } catch (error) {
      showError('설정 저장 중 오류가 발생했습니다.');
    }
  };

  // 설정 취소
  const handleCancelSettings = () => {
    setTempCountry(selectedCountry);
    setTempLanguage(selectedLanguage);
    setIsEditingSettings(false);
  };

  // 비밀번호 변경
  const handleChangePassword = async () => {
    const validationError = validatePassword(newPassword);
    if (validationError) {
      setPasswordError(validationError);
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/users/me/password`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setIsEditingPassword(false);
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
        setPasswordError('');
        // 비밀번호 변경 후 로그아웃
        alert('비밀번호가 변경되었습니다. 보안을 위해 다시 로그인해주세요.');
        logout();
        navigate('/');
        return;
      } else {
        setPasswordError(data.message || '비밀번호 변경에 실패했습니다.');
      }
    } catch (error) {
      setPasswordError('비밀번호 변경 중 오류가 발생했습니다.');
    }
  };

  const handleCancelProfile = () => {
    setEditName(userInfo.name || '');
    setEditPhone(formatPhoneNumber(userInfo.phone) || '');
    setIsEditingProfile(false);
  };

  const handleCancelPassword = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordError('');
    setShowCurrentPassword(false);
    setShowNewPassword(false);
    setShowConfirmPassword(false);
    setIsEditingPassword(false);
  };

  // 로그아웃
  const handleLogout = () => {
    logout();
    navigate('/');
  };

  // 계정 삭제
  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== '계정삭제') {
      showError('확인 문구를 정확히 입력해주세요.');
      return;
    }

    setIsDeleting(true);
    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch(`${API_URL}/api/users/me/deactivate`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        logout();
        navigate('/', { state: { message: '계정이 삭제되었습니다.' } });
      } else {
        const errorData = await response.json();
        showError(errorData.message || '계정 삭제에 실패했습니다.');
      }
    } catch (error) {
      showError('계정 삭제 중 오류가 발생했습니다.');
    } finally {
      setIsDeleting(false);
      setIsDeleteModalOpen(false);
      setDeleteConfirmText('');
    }
  };

  const showSuccess = (message: string) => {
    setSuccessMessage(message);
    setTimeout(() => setSuccessMessage(''), 3000);
  };

  const showError = (message: string) => {
    setErrorMessage(message);
    setTimeout(() => setErrorMessage(''), 3000);
  };

  if (!isAuthReady || isLoading) {
    return (
      <div className="mypage-container">
        <div className="mypage-loading">로딩 중...</div>
      </div>
    );
  }

  return (
    <>
      <Header logoText="METEOR" hideUserMenu={true} />
      <div className="mypage-container">
        <div className="mypage-content">
          <button className="back-btn" onClick={() => navigate(-1)}>
            <svg className="back-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 12H5M5 12L12 19M5 12L12 5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            되돌아가기
          </button>
          <h1 className="mypage-title">마이페이지</h1>

        {successMessage && (
          <div className="message success">{successMessage}</div>
        )}
        {errorMessage && (
          <div className="message error">{errorMessage}</div>
        )}

        <div className="mypage-grid">
          {/* 프로필 정보 */}
          <div className="info-card">
              <div className="card-header">
                <h2 className="card-title">프로필 정보</h2>
                {!isEditingProfile && (
                  <button className="edit-btn" onClick={() => setIsEditingProfile(true)}>
                    수정
                  </button>
                )}
              </div>

              {isEditingProfile ? (
                <div className="edit-form">
                  <div className="form-group">
                    <label>이메일</label>
                    <div className="input-wrapper">
                      <input
                        type="email"
                        value={userInfo.email}
                        disabled
                        className="disabled"
                      />
                      <span className="helper-text">이메일은 변경할 수 없습니다.</span>
                    </div>
                  </div>
                  <div className="form-group">
                    <label>이름</label>
                    <input
                      type="text"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      placeholder="이름 입력"
                    />
                  </div>
                  <div className="form-group">
                    <label>전화번호</label>
                    <input
                      type="tel"
                      value={editPhone}
                      onChange={(e) => setEditPhone(formatPhoneNumberOnInput(e.target.value))}
                      placeholder="010-0000-0000"
                      maxLength={13}
                    />
                  </div>
                  <div className="form-actions">
                    <button className="save-btn" onClick={handleSaveProfile}>저장</button>
                    <button className="cancel-btn" onClick={handleCancelProfile}>취소</button>
                  </div>
                </div>
              ) : (
                <div className="info-list">
                  <div className="info-row">
                    <span className="info-label">이메일</span>
                    <span className="info-value">{userInfo.email}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">이름</span>
                    <span className="info-value">{userInfo.name || '-'}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">전화번호</span>
                    <span className="info-value">{formatPhoneNumber(userInfo.phone) || '-'}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">비밀번호</span>
                    <button className="password-change-btn" onClick={() => setIsEditingPassword(true)}>
                      비밀번호 변경
                    </button>
                  </div>
                </div>
              )}

              {/* 비밀번호 변경 폼 */}
              {isEditingPassword && (
                <div className="password-edit-section">
                  <div className="password-edit-header">
                    <h3>비밀번호 변경</h3>
                  </div>
                  <div className="edit-form">
                    <div className="form-group">
                      <label>현재 비밀번호</label>
                      <div className="password-input-wrapper">
                        <input
                          type={showCurrentPassword ? 'text' : 'password'}
                          value={currentPassword}
                          onChange={(e) => setCurrentPassword(e.target.value)}
                          placeholder="현재 비밀번호 입력"
                        />
                        <button
                          type="button"
                          className="password-toggle-btn"
                          onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                        >
                          {showCurrentPassword ? (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                              <line x1="1" y1="1" x2="23" y2="23"/>
                            </svg>
                          ) : (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                              <circle cx="12" cy="12" r="3"/>
                            </svg>
                          )}
                        </button>
                      </div>
                    </div>
                    <div className="form-group">
                      <label>새 비밀번호</label>
                      <div className="password-input-wrapper">
                        <input
                          type={showNewPassword ? 'text' : 'password'}
                          value={newPassword}
                          onChange={(e) => {
                            setNewPassword(e.target.value);
                            setPasswordError('');
                          }}
                          placeholder="8자 이상, 영문+숫자+특수문자"
                        />
                        <button
                          type="button"
                          className="password-toggle-btn"
                          onClick={() => setShowNewPassword(!showNewPassword)}
                        >
                          {showNewPassword ? (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                              <line x1="1" y1="1" x2="23" y2="23"/>
                            </svg>
                          ) : (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                              <circle cx="12" cy="12" r="3"/>
                            </svg>
                          )}
                        </button>
                      </div>
                    </div>
                    <div className="form-group">
                      <label>새 비밀번호 확인</label>
                      <div className="password-input-wrapper">
                        <input
                          type={showConfirmPassword ? 'text' : 'password'}
                          value={confirmPassword}
                          onChange={(e) => {
                            setConfirmPassword(e.target.value);
                            setPasswordError('');
                          }}
                          placeholder="새 비밀번호 다시 입력"
                        />
                        <button
                          type="button"
                          className="password-toggle-btn"
                          onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        >
                          {showConfirmPassword ? (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                              <line x1="1" y1="1" x2="23" y2="23"/>
                            </svg>
                          ) : (
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                              <circle cx="12" cy="12" r="3"/>
                            </svg>
                          )}
                        </button>
                      </div>
                    </div>
                    {passwordError && (
                      <div className="form-error">{passwordError}</div>
                    )}
                    <div className="form-actions">
                      <button className="save-btn" onClick={handleChangePassword}>변경하기</button>
                      <button className="cancel-btn" onClick={handleCancelPassword}>취소</button>
                    </div>
                  </div>
                </div>
              )}
          </div>

          {/* 계정 정보 (언어/국가) */}
          <div className="info-card">
            <div className="card-header">
              <h2 className="card-title">계정 정보</h2>
              {!isEditingSettings && (
                <button className="edit-btn" onClick={handleStartEditSettings}>
                  수정
                </button>
              )}
            </div>

            {isEditingSettings ? (
              <div className="edit-form">
                <div className="form-group">
                  <label>언어</label>
                  <div className="language-options">
                    {LANGUAGES.map((lang) => (
                      <button
                        key={lang.code}
                        type="button"
                        className={`language-btn ${tempLanguage === lang.code ? 'active' : ''}`}
                        onClick={() => setTempLanguage(lang.code)}
                      >
                        {lang.name}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="form-group">
                  <label>국가</label>
                  <div className="input-wrapper">
                    <select
                      value={tempCountry}
                      onChange={(e) => setTempCountry(e.target.value)}
                      className="country-dropdown"
                    >
                      {COUNTRIES.map((country) => (
                        <option key={country.code} value={country.code}>
                          {country.name}
                        </option>
                      ))}
                    </select>
                    <span className="helper-text">
                      적용 통화: {COUNTRIES.find(c => c.code === tempCountry)?.name} - {COUNTRIES.find(c => c.code === tempCountry)?.currency}
                    </span>
                  </div>
                </div>
                <div className="form-actions">
                  <button className="save-btn" onClick={handleSaveSettings}>저장</button>
                  <button className="cancel-btn" onClick={handleCancelSettings}>취소</button>
                </div>
              </div>
            ) : (
              <div className="info-list">
                <div className="info-row">
                  <span className="info-label">언어</span>
                  <span className="info-value">
                    {LANGUAGES.find(l => l.code === selectedLanguage)?.name || selectedLanguage}
                  </span>
                </div>
                <div className="info-row">
                  <span className="info-label">국가(결제통화)</span>
                  <span className="info-value">
                    {COUNTRIES.find(c => c.code === selectedCountry)?.name || selectedCountry}
                    ({COUNTRIES.find(c => c.code === selectedCountry)?.currency || 'KRW'})
                  </span>
                </div>
              </div>
            )}

            {/* 계정 삭제 섹션 */}
            <div className="delete-account-section">
              <div className="delete-account-info">
                <span className="delete-label">계정 삭제</span>
                <span className="delete-description">계정을 삭제하면 모든 데이터가 영구적으로 삭제됩니다.</span>
              </div>
              <button
                className="delete-account-btn"
                onClick={() => setIsDeleteModalOpen(true)}
              >
                계정 삭제
              </button>
            </div>
          </div>

          {/* 라이선스 정보 */}
          <div className="info-card license-card">
            <div className="card-header">
              <h2 className="card-title">라이선스 정보</h2>
            </div>
            {isLoadingLicenses ? (
              <div className="loading-text">라이선스 정보를 불러오는 중...</div>
            ) : licenses.length === 0 ? (
              <div className="empty-licenses">
                <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M9 12L11 14L15 10M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <p>보유한 라이선스가 없습니다.</p>
                <button className="purchase-btn" onClick={() => navigate('/payment')}>
                  라이선스 구매하기
                </button>
              </div>
            ) : (
              <div className="license-list">
                {licenses.map((license) => (
                  <div key={license.id} className={`license-item ${license.status.toLowerCase()}`}>
                    <div className="license-header">
                      <span className="license-product">
                        {license.productName || license.planName || 'BUL:C'}
                      </span>
                      <span className={`license-status status-${license.status.toLowerCase()}`}>
                        {license.status === 'ACTIVE' ? '활성' :
                         license.status === 'PENDING' ? '대기' :
                         license.status === 'EXPIRED_GRACE' ? '만료 유예' :
                         license.status === 'EXPIRED_HARD' ? '만료됨' :
                         license.status === 'SUSPENDED' ? '정지됨' :
                         license.status === 'REVOKED' ? '취소됨' : license.status}
                      </span>
                    </div>
                    <div className="license-details">
                      <div className="license-detail-row">
                        <span className="detail-label">라이선스 유형</span>
                        <span className="detail-value">
                          {license.licenseType === 'SUBSCRIPTION' ? '구독형' :
                           license.licenseType === 'PERPETUAL' ? '영구' :
                           license.licenseType === 'TRIAL' ? '체험판' : license.licenseType}
                        </span>
                      </div>
                      <div className="license-detail-row">
                        <span className="detail-label">유효 기간</span>
                        <span className="detail-value">
                          {license.validFrom ? new Date(license.validFrom).toLocaleDateString('ko-KR') : '-'}
                          {' ~ '}
                          {license.validUntil ? new Date(license.validUntil).toLocaleDateString('ko-KR') : '무제한'}
                        </span>
                      </div>
                      <div className="license-detail-row">
                        <span className="detail-label">기기 등록</span>
                        <span className="detail-value">
                          {license.usedActivations} / {license.maxActivations}대
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 개발자 미리보기 (관리자 전용) */}
          {isSystemAdmin && (
            <div className="info-card dev-preview-card">
              <div className="card-header">
                <h2 className="card-title">
                  <svg className="dev-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M16 18L22 12L16 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M8 6L2 12L8 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  페이지 미리보기
                </h2>
                <button
                  className={`preview-toggle-btn ${isPreviewOpen ? 'open' : ''}`}
                  onClick={() => setIsPreviewOpen(!isPreviewOpen)}
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </button>
              </div>

              {isPreviewOpen && (
                <div className="preview-menu">
                  <div className="preview-section">
                    <h4 className="preview-section-title">오류 페이지 미리보기</h4>
                    <div className="preview-buttons">
                      <button
                        className="preview-btn error-btn-400"
                        onClick={() => navigate('/error', { state: { errorCode: 400 } })}
                      >
                        400 잘못된 요청
                      </button>
                      <button
                        className="preview-btn error-btn-401"
                        onClick={() => navigate('/error', { state: { errorCode: 401 } })}
                      >
                        401 인증 필요
                      </button>
                      <button
                        className="preview-btn error-btn-403"
                        onClick={() => navigate('/error', { state: { errorCode: 403 } })}
                      >
                        403 접근 거부
                      </button>
                      <button
                        className="preview-btn error-btn-404"
                        onClick={() => navigate('/error', { state: { errorCode: 404 } })}
                      >
                        404 페이지 없음
                      </button>
                      <button
                        className="preview-btn error-btn-500"
                        onClick={() => navigate('/error', { state: { errorCode: 500 } })}
                      >
                        500 서버 오류
                      </button>
                      <button
                        className="preview-btn error-btn-502"
                        onClick={() => navigate('/error', { state: { errorCode: 502 } })}
                      >
                        502 연결 실패
                      </button>
                      <button
                        className="preview-btn error-btn-503"
                        onClick={() => navigate('/error', { state: { errorCode: 503 } })}
                      >
                        503 서비스 불가
                      </button>
                    </div>
                  </div>
                  <div className="preview-note">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="16" x2="12" y2="12"></line>
                      <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                    <span>이 메뉴는 관리자에게만 표시됩니다.</span>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 로그아웃 */}
          <div className="info-card logout-card">
            <button className="logout-btn" onClick={handleLogout}>
              <svg className="logout-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M9 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M16 17L21 12L16 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M21 12H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              로그아웃
            </button>
          </div>
        </div>
      </div>
    </div>

    {/* 로그인 모달 - 미로그인 시 표시 */}
    <LoginModal
      isOpen={loginModalOpen}
      onClose={() => {
        setLoginModalOpen(false);
        navigate('/');
      }}
      onSuccess={() => {
        setLoginModalOpen(false);
        window.location.reload();
      }}
    />

    {/* 계정 삭제 확인 모달 */}
    {isDeleteModalOpen && (
      <div className="delete-modal-overlay" onClick={() => setIsDeleteModalOpen(false)}>
        <div className="delete-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <svg className="warning-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 9V13M12 17H12.01M5.07183 19H18.9282C20.4678 19 21.4301 17.3333 20.6603 16L13.7321 4C12.9623 2.66667 11.0377 2.66667 10.2679 4L3.33975 16C2.56995 17.3333 3.53223 19 5.07183 19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <h3>계정 삭제</h3>
          </div>
          <div className="delete-modal-body">
            <p className="warning-text">
              정말로 계정을 삭제하시겠습니까?<br />
              이 작업은 되돌릴 수 없습니다.
            </p>
            <ul className="delete-warning-list">
              <li>모든 개인 정보가 삭제됩니다</li>
              <li>보유한 라이선스가 모두 비활성화됩니다</li>
              <li>결제 내역은 법적 보관 기간 동안 유지됩니다</li>
            </ul>
            <div className="confirm-input-group">
              <label>확인을 위해 <strong>'계정삭제'</strong>를 입력해주세요</label>
              <input
                type="text"
                value={deleteConfirmText}
                onChange={(e) => setDeleteConfirmText(e.target.value)}
                placeholder="계정삭제"
                className="confirm-input"
              />
            </div>
          </div>
          <div className="delete-modal-footer">
            <button
              className="cancel-btn"
              onClick={() => {
                setIsDeleteModalOpen(false);
                setDeleteConfirmText('');
              }}
            >
              취소
            </button>
            <button
              className="confirm-delete-btn"
              onClick={handleDeleteAccount}
              disabled={deleteConfirmText !== '계정삭제' || isDeleting}
            >
              {isDeleting ? '삭제 중...' : '계정 삭제'}
            </button>
          </div>
        </div>
      </div>
    )}
    </>
  );
};

export default MyPage;
