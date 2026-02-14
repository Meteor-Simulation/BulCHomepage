import React from 'react';
import { useNavigate } from 'react-router-dom';
import './Login.css';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="header visible">
        <div className="header-logo" onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
          <img src="/logo_transparent.png" alt="BUL:C" className="header-logo-img" />
          <span className="header-logo-text">BUL:C</span>
        </div>
        <div className="header-right">
          <button className="login-btn" onClick={() => navigate('/login')}>
            <svg className="login-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 12C14.7614 12 17 9.76142 17 7C17 4.23858 14.7614 2 12 2C9.23858 2 7 4.23858 7 7C7 9.76142 9.23858 12 12 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M20.59 22C20.59 18.13 16.74 15 12 15C7.26 15 3.41 18.13 3.41 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        </div>
      </header>

      <main className="main-content">
        <div className="login-container">
          <h2 className="login-title">로그인</h2>
          <form className="login-form">
            <input type="email" placeholder="이메일" className="login-input" />
            <input type="password" placeholder="비밀번호" className="login-input" />
            <div className="login-btn-group">
              <button type="submit" className="login-submit-btn">로그인</button>
              <button type="button" className="login-signup-btn" onClick={() => navigate('/signup')}>회원가입</button>
            </div>
          </form>

          <div className="social-login">
            <p className="social-login-title">간편 로그인</p>
            <div className="social-btn-group">
              <button type="button" className="social-btn naver">
                <svg viewBox="0 0 24 24" className="social-icon">
                  <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727v12.845z" fill="currentColor"/>
                </svg>
              </button>
              <button type="button" className="social-btn kakao">
                <svg viewBox="0 0 24 24" className="social-icon">
                  <path d="M12 3C6.477 3 2 6.463 2 10.691c0 2.722 1.8 5.108 4.5 6.454-.18.67-.65 2.428-.745 2.805-.118.47.172.463.362.337.15-.1 2.378-1.612 3.34-2.265.51.071 1.03.108 1.543.108 5.523 0 10-3.463 10-7.691S17.523 3 12 3z" fill="currentColor"/>
                </svg>
              </button>
              <button type="button" className="social-btn google">
                <svg viewBox="0 0 24 24" className="social-icon">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default LoginPage;
