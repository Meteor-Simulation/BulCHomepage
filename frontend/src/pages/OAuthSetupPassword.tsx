import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './OAuthSetupPassword.css';

const getApiUrl = () => {
  return window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : `http://${window.location.hostname}:8080`;
};

const OAuthSetupPassword: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { loginWithToken } = useAuth();

  const [token] = useState(searchParams.get('token') || '');
  const [email] = useState(decodeURIComponent(searchParams.get('email') || ''));
  const [name] = useState(decodeURIComponent(searchParams.get('name') || ''));
  const [phone] = useState(decodeURIComponent(searchParams.get('mobile') || ''));
  const [provider] = useState(searchParams.get('provider') || '');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!token || !email) {
      setError('잘못된 접근입니다. 다시 로그인해주세요.');
    }
  }, [token, email]);

  const validatePassword = (pwd: string): string | null => {
    if (pwd.length < 8) {
      return '비밀번호는 최소 8자 이상이어야 합니다.';
    }
    if (!/[A-Za-z]/.test(pwd)) {
      return '비밀번호에 영문자가 포함되어야 합니다.';
    }
    if (!/[0-9]/.test(pwd)) {
      return '비밀번호에 숫자가 포함되어야 합니다.';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 유효성 검사
    const passwordError = validatePassword(password);
    if (passwordError) {
      setError(passwordError);
      return;
    }

    if (password !== confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${getApiUrl()}/api/auth/oauth/signup`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          token,
          password,
          name: name.trim(),
          phone: phone.trim() || null,
        }),
      });

      const result = await response.json();

      if (result.success && result.data) {
        // 로그인 처리
        const { accessToken, refreshToken } = result.data;
        const loginResult = await loginWithToken(accessToken, refreshToken);

        if (loginResult.success) {
          navigate('/');
        } else {
          setError(loginResult.message || '로그인 처리 중 오류가 발생했습니다.');
        }
      } else {
        setError(result.message || '회원가입에 실패했습니다.');
      }
    } catch (err) {
      setError('회원가입 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const getProviderName = (provider: string) => {
    switch (provider.toUpperCase()) {
      case 'NAVER': return '네이버';
      case 'KAKAO': return '카카오';
      case 'GOOGLE': return '구글';
      default: return provider;
    }
  };

  return (
    <div className="oauth-setup-container">
      <div className="oauth-setup-card">
        <div className="oauth-setup-header">
          <h2>회원가입 완료</h2>
          <p className="oauth-provider-info">
            {getProviderName(provider)} 계정으로 가입합니다
          </p>
        </div>

        <form className="oauth-setup-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label>이메일</label>
            <input
              type="email"
              value={email}
              disabled
              className="form-input disabled"
            />
          </div>

          <div className="form-group">
            <label>이름</label>
            <input
              type="text"
              value={name}
              disabled
              className="form-input disabled"
            />
          </div>

          <div className="form-group">
            <label>전화번호</label>
            <input
              type="tel"
              value={phone || '-'}
              disabled
              className="form-input disabled"
            />
          </div>

          <div className="form-group">
            <label>비밀번호</label>
            <div className="password-input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호 (8자 이상, 영문+숫자)"
                className="form-input"
                disabled={isLoading}
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </button>
            </div>
          </div>

          <div className="form-group">
            <label>비밀번호 확인</label>
            <div className="password-input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="비밀번호를 다시 입력해주세요"
                className="form-input"
                disabled={isLoading}
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowPasswordConfirm(!showPasswordConfirm)}
                tabIndex={-1}
              >
                {showPasswordConfirm ? (
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <line x1="1" y1="1" x2="23" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </button>
            </div>
          </div>

          {error && <p className="error-message">{error}</p>}

          <button
            type="submit"
            className="submit-btn"
            disabled={isLoading || !token || !email}
          >
            {isLoading ? '처리 중...' : '회원가입 완료'}
          </button>
        </form>

        <p className="oauth-setup-notice">
          비밀번호를 설정하면 이메일/비밀번호로도 로그인할 수 있습니다.
        </p>
      </div>
    </div>
  );
};

export default OAuthSetupPassword;
