import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoginModal from './LoginModal';
import SignupModal from './SignupModal';
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
  contactLabel = '체험 문의하기',
  logoLink = '/',
  onLogoClick,
  logoText = 'METEOR',
  hideUserMenu = false
}) => {
  const navigate = useNavigate();
  const { isLoggedIn, logout } = useAuth();
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [signupModalOpen, setSignupModalOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [langMenuOpen, setLangMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const langMenuRef = useRef<HTMLDivElement>(null);
  const [alertModal, setAlertModal] = useState<{
    isOpen: boolean;
    title?: string;
    message: string;
    type: 'success' | 'error' | 'info' | 'warning';
  }>({ isOpen: false, message: '', type: 'info' });

  // 메뉴 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false);
      }
      if (langMenuRef.current && !langMenuRef.current.contains(event.target as Node)) {
        setLangMenuOpen(false);
      }
    };

    if (userMenuOpen || langMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [userMenuOpen, langMenuOpen]);

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
    setUserMenuOpen(false);
    // 로그아웃 후 히스토리 정리 - 뒤로가기 시 로그인 상태 페이지로 가지 않도록
    window.history.replaceState({ loggedOut: true }, '', window.location.href);
    // 로그아웃 알림
    setAlertModal({
      isOpen: true,
      title: '로그아웃',
      message: '로그아웃 되었습니다.',
      type: 'success',
    });
  };

  const handleMyInfo = () => {
    setUserMenuOpen(false);
    navigate('/mypage');
  };

  // 로그인 성공 콜백
  const handleLoginSuccess = () => {
    setLoginModalOpen(false);
    setAlertModal({
      isOpen: true,
      title: '로그인 성공',
      message: '환영합니다! 로그인 되었습니다.',
      type: 'success',
    });
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
      <header className="header visible">
        <div className="header-logo" onClick={handleLogoClick} style={{ cursor: 'pointer' }}>
          <img src="/logo_transparent.png" alt="METEOR" className="header-logo-img" />
          <span className="header-logo-text">{logoText}</span>
        </div>
        <div className="header-right">
          {/* 언어 메뉴 */}
          <div className="user-menu-container" ref={langMenuRef}>
            <button className="header-action-btn" onClick={() => setLangMenuOpen(!langMenuOpen)}>
              <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M2 12H22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M12 2C14.5013 4.73835 15.9228 8.29203 16 12C15.9228 15.708 14.5013 19.2616 12 22C9.49872 19.2616 8.07725 15.708 8 12C8.07725 8.29203 9.49872 4.73835 12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="header-action-text">언어</span>
            </button>
            {langMenuOpen && (
              <div className="user-menu-dropdown lang-menu-dropdown">
                <div className="user-menu-item" onClick={() => setLangMenuOpen(false)}>
                  <span>English</span>
                </div>
                <div className="user-menu-item" onClick={() => setLangMenuOpen(false)}>
                  <span>한국어</span>
                </div>
                <div className="user-menu-item" onClick={() => setLangMenuOpen(false)}>
                  <span>日本語</span>
                </div>
                <div className="user-menu-item" onClick={() => setLangMenuOpen(false)}>
                  <span>中文</span>
                </div>
              </div>
            )}
          </div>

          {/* 내 정보 / 로그인 메뉴 */}
          {!hideUserMenu && (
            isLoggedIn ? (
              <div className="user-menu-container" ref={userMenuRef}>
                <button className="header-action-btn" onClick={() => setUserMenuOpen(!userMenuOpen)}>
                  <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span className="header-action-text">내 정보</span>
                </button>
                {userMenuOpen && (
                  <div className="user-menu-dropdown">
                    <div className="user-menu-item" onClick={handleMyInfo}>
                      <svg className="user-menu-item-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                      <span>내 정보 보기</span>
                    </div>
                    <div className="user-menu-item" onClick={handleLogout}>
                      <svg className="user-menu-item-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M9 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <path d="M16 17L21 12L16 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <path d="M21 12H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                      <span>로그아웃</span>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <button className="header-action-btn" onClick={() => setLoginModalOpen(true)}>
                <svg className="header-action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <span className="header-action-text">로그인</span>
              </button>
            )
          )}
        </div>
      </header>

      {/* 서브 네비게이션 */}
      {showSubNav && subNavItems.length > 0 && (
        <>
          <nav className="sub-nav">
            {/* 데스크톱 메뉴 */}
            <div className="sub-nav-center desktop-only">
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

            {/* 모바일 드롭다운 */}
            <div className="sub-nav-mobile mobile-only">
              <button
                className="sub-nav-mobile-btn"
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              >
                <span>{subNavItems.find(item => item.id === activeSubNav)?.label || '메뉴 선택'}</span>
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

          {/* 플로팅 문의 버튼 */}
          <button className="floating-contact-btn">
            <svg className="floating-contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M21 11.5C21.0034 12.8199 20.6951 14.1219 20.1 15.3C19.3944 16.7118 18.3098 17.8992 16.9674 18.7293C15.6251 19.5594 14.0782 19.9994 12.5 20C11.1801 20.0035 9.87812 19.6951 8.7 19.1L3 21L4.9 15.3C4.30493 14.1219 3.99656 12.8199 4 11.5C4.00061 9.92179 4.44061 8.37488 5.27072 7.03258C6.10083 5.69028 7.28825 4.6056 8.7 3.90003C9.87812 3.30496 11.1801 2.99659 12.5 3.00003H13C15.0843 3.11502 17.053 3.99479 18.5291 5.47089C20.0052 6.94699 20.885 8.91568 21 11V11.5Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <span className="floating-contact-text">{contactLabel}</span>
          </button>
        </>
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
