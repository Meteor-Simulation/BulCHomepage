import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { API_URL } from '../utils/api';
import LoginModal from './LoginModal';
import SignupModal from './SignupModal';
import ContactModal from './ContactModal';
import AlertModal from './AlertModal';
import './Header.css';

interface HeaderProps {
  showSubNav?: boolean;
  subNavItems?: Array<{
    id: string;
    label: string;
  }>;
  activeSubNav?: string;
  onSubNavChange?: (id: string) => void;
  contactLabel?: string;
  logoLink?: string;
  onLogoClick?: () => void;
  logoText?: string;
  hideUserMenu?: boolean;
}

const Header: React.FC<HeaderProps> = ({
  showSubNav = false,
  subNavItems = [],
  activeSubNav = '',
  onSubNavChange,
  contactLabel = '문의하기',
  logoLink = '/',
  onLogoClick,
  logoText = 'METEOR',
  hideUserMenu = false
}) => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { isLoggedIn, isAdmin, logout } = useAuth();
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [signupModalOpen, setSignupModalOpen] = useState(false);
  const [contactModalOpen, setContactModalOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [trialDaysLeft, setTrialDaysLeft] = useState<number | null>(null);
  const [alertModal, setAlertModal] = useState<{
    isOpen: boolean;
    title?: string;
    message: string;
    type: 'success' | 'error' | 'info' | 'warning';
  }>({ isOpen: false, message: '', type: 'info' });

  // TRIAL 라이선스 남은 일수 조회
  useEffect(() => {
    const fetchTrialLicense = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      try {
        const response = await fetch(`${API_URL}/api/v1/me/licenses`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          const trialLicense = (data.licenses || []).find(
            (license: any) => license.licenseType === 'TRIAL' && license.status === 'ACTIVE'
          );
          if (trialLicense?.validUntil) {
            const days = Math.ceil(
              (new Date(trialLicense.validUntil).getTime() - Date.now()) / 86400000
            );
            setTrialDaysLeft(days > 0 ? days : null);
          } else {
            setTrialDaysLeft(null);
          }
        }
      } catch (error) {
        console.error('Trial license fetch failed:', error);
      }
    };

    if (isLoggedIn) {
      fetchTrialLicense();
    } else {
      setTrialDaysLeft(null);
    }
  }, [isLoggedIn]);

  const handleSwitchToSignup = () => {
    setLoginModalOpen(false);
    setSignupModalOpen(true);
  };

  const handleSwitchToLogin = () => {
    setSignupModalOpen(false);
    setLoginModalOpen(true);
  };

  const handleLogout = () => {
    logout();
    // 로그아웃 후 히스토리 정리 - 뒤로가기 시 로그인 상태 페이지로 가지 않도록
    window.history.replaceState({ loggedOut: true }, '', window.location.href);
    // 로그아웃 알림
    setAlertModal({
      isOpen: true,
      title: t('auth.logoutTitle'),
      message: t('auth.logoutMessage'),
      type: 'success',
    });
  };

  // 로그인 성공 콜백 - 마이페이지로 이동
  const handleLoginSuccess = () => {
    setLoginModalOpen(false);
    setAlertModal({
      isOpen: true,
      title: t('auth.loginSuccessTitle'),
      message: t('auth.loginSuccess'),
      type: 'success',
    });
    navigate('/mypage');
  };

  const closeAlert = () => {
    setAlertModal({ ...alertModal, isOpen: false });
  };

  const handleLogoClick = () => {
    if (onLogoClick) {
      onLogoClick();
    }
    navigate(logoLink);
  };

  return (
    <>
      <header className={`header visible ${showSubNav ? 'with-nav' : ''}`}>
        <div className="header-logo" onClick={handleLogoClick} style={{ cursor: 'pointer' }}>
          <img src="/logo_transparent.png" alt="METEOR" className="header-logo-img" />
          <span className="header-logo-text">{logoText}</span>
        </div>

        <div className="header-right">
          {/* 내 정보 / 로그인 메뉴 */}
          {isLoggedIn ? (
            // 로그인 상태: hideUserMenu가 false일 때만 내 정보 버튼 표시
            !hideUserMenu && (
              <>
                {trialDaysLeft !== null && (
                  <div className="header-trial-badge">
                    {t('header.trialBadge', { days: trialDaysLeft })}
                  </div>
                )}
                <button className="header-action-btn" onClick={() => navigate('/mypage')}>
                  <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span className="header-action-text">{t('header.myPage')}</span>
                </button>
              </>
            )
          ) : (
            // 비로그인 상태: 로그인 + 회원가입 버튼 표시
            <>
              <button className="header-action-btn" onClick={() => setLoginModalOpen(true)}>
                <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <span className="header-action-text">{t('header.login')}</span>
              </button>
              <button className="header-signup-btn" onClick={() => setSignupModalOpen(true)}>
                <span>{t('header.signup')}</span>
              </button>
            </>
          )}
        </div>
      </header>

      {/* 서브 네비게이션 (데스크톱) */}
      {showSubNav && subNavItems.length > 0 && (
        <nav className="sub-nav desktop-only">
          <div className="sub-nav-center">
            {subNavItems.map((item) => (
              <div
                key={item.id}
                className={`sub-nav-item ${activeSubNav === item.id ? 'active' : ''}`}
                onClick={() => onSubNavChange?.(item.id)}
              >
                {item.label}
              </div>
            ))}
          </div>
        </nav>
      )}

      {/* 서브 네비게이션 (모바일) */}
      {showSubNav && subNavItems.length > 0 && (
        <nav className="sub-nav mobile-only">
          <div className="sub-nav-mobile">
            <button
              className="sub-nav-mobile-btn"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            >
              <span>{subNavItems.find(item => item.id === activeSubNav)?.label || t('header.menuSelect')}</span>
              <svg
                className={`sub-nav-mobile-arrow ${mobileMenuOpen ? 'open' : ''}`}
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path d="M6 9L12 15L18 9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            {mobileMenuOpen && (
              <div className="sub-nav-mobile-dropdown">
                {subNavItems.map((item) => (
                  <div
                    key={item.id}
                    className={`sub-nav-mobile-item ${activeSubNav === item.id ? 'active' : ''}`}
                    onClick={() => {
                      onSubNavChange?.(item.id);
                      setMobileMenuOpen(false);
                    }}
                  >
                    {item.label}
                  </div>
                ))}
              </div>
            )}
          </div>
        </nav>
      )}

      {/* 플로팅 문의 버튼 */}
      {showSubNav && (
        <button className="floating-contact-btn" onClick={() => setContactModalOpen(true)}>
          <svg className="floating-contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M21 11.5C21.0034 12.8199 20.6951 14.1219 20.1 15.3C19.3944 16.7118 18.3098 17.8992 16.9674 18.7293C15.6251 19.5594 14.0782 19.9994 12.5 20C11.1801 20.0035 9.87812 19.6951 8.7 19.1L3 21L4.9 15.3C4.30493 14.1219 3.99656 12.8199 4 11.5C4.00061 9.92179 4.44061 8.37488 5.27072 7.03258C6.10083 5.69028 7.28825 4.6056 8.7 3.90003C9.87812 3.30496 11.1801 2.99659 12.5 3.00003H13C15.0843 3.11502 17.053 3.99479 18.5291 5.47089C20.0052 6.94699 20.885 8.91568 21 11V11.5Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <span className="floating-contact-text">{contactLabel}</span>
        </button>
      )}

      <LoginModal
        isOpen={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        onSwitchToSignup={handleSwitchToSignup}
        onSuccess={handleLoginSuccess}
      />
      <SignupModal
        isOpen={signupModalOpen}
        onClose={() => setSignupModalOpen(false)}
        onSwitchToLogin={handleSwitchToLogin}
      />
      <ContactModal
        isOpen={contactModalOpen}
        onClose={() => setContactModalOpen(false)}
        category={logoText}
      />
      <AlertModal
        isOpen={alertModal.isOpen}
        onClose={closeAlert}
        title={alertModal.title}
        message={alertModal.message}
        type={alertModal.type}
        autoClose={3000}
      />
    </>
  );
};

export default Header;
