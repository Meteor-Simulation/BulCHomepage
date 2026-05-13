import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { API_URL } from '../utils/api';
import './LoginModal.css';

// 로그인 성공 시 히스토리 정리 (뒤로가기 방지)
const clearAuthHistory = () => {
  // 현재 URL로 히스토리 대체 - 뒤로가기 시 로그인 전 상태로 가지 않도록
  window.history.replaceState({ loggedIn: true }, '', window.location.href);
};

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToSignup?: () => void;
  onSuccess?: () => void;
}

type PasswordResetStep = 'email' | 'code' | 'newPassword' | 'success';

const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onSwitchToSignup, onSuccess }) => {
  const { t } = useTranslation();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // 비밀번호 찾기 상태
  const [isPasswordReset, setIsPasswordReset] = useState(false);
  const [resetStep, setResetStep] = useState<PasswordResetStep>('email');
  const [resetEmail, setResetEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // 모달 열릴 때 상태 초기화
  useEffect(() => {
    if (isOpen) {
      setEmail('');
      setPassword('');
      setShowPassword(false);
      setError('');
      setIsLoading(false);
      setIsPasswordReset(false);
      setResetStep('email');
      setResetEmail('');
      setResetCode('');
      setNewPassword('');
      setConfirmPassword('');
    }
  }, [isOpen]);

  // 소셜 로그인 핸들러
  const handleSocialLogin = (provider: string) => {
    window.location.href = `${API_URL}/api/auth/oauth2/authorize/${provider}`;
  };

  // ESC 키로 모달 닫기
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden'; // 스크롤 방지
    }

    return () => {
      document.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  // 오버레이 마우스다운시 모달 닫기 (클릭 시작 시점에만 체크)
  const handleOverlayMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // 로그인 핸들러
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 프론트엔드 유효성 검사
    if (!email.trim()) {
      setError(t('login.errors.emailRequired'));
      return;
    }
    // 이메일 형식 검사
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError(t('login.errors.emailInvalid'));
      return;
    }
    if (!password) {
      setError(t('login.errors.passwordRequired'));
      return;
    }

    setIsLoading(true);

    try {
      const result = await login(email, password);
      if (result.success) {
        setEmail('');
        setPassword('');
        clearAuthHistory(); // 로그인 성공 시 히스토리 정리
        onSuccess?.(); // 성공 콜백 호출
      } else {
        setError(result.message || t('login.errors.invalidCredentials'));
      }
    } catch {
      setError(t('login.errors.general'));
    } finally {
      setIsLoading(false);
    }
  };

  // 비밀번호 찾기 - 이메일 입력 후 코드 발송
  const handleRequestResetCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!resetEmail.trim()) {
      setError(t('passwordReset.errors.emailRequired'));
      return;
    }
    if (!emailRegex.test(resetEmail)) {
      setError(t('passwordReset.errors.emailInvalid'));
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/auth/password/reset-request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: resetEmail }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setResetStep('code');
        setError('');
      } else {
        setError(data.message || t('passwordReset.errors.sendFailed'));
      }
    } catch {
      setError(t('passwordReset.errors.sendError'));
    } finally {
      setIsLoading(false);
    }
  };

  // 비밀번호 찾기 - 코드 확인
  const handleVerifyResetCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!resetCode.trim()) {
      setError(t('passwordReset.errors.codeRequired'));
      return;
    }
    if (resetCode.length !== 6) {
      setError(t('passwordReset.errors.codeLength'));
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/auth/password/verify-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: resetEmail, code: resetCode }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setResetStep('newPassword');
        setError('');
      } else {
        setError(data.message || t('passwordReset.errors.codeInvalid'));
      }
    } catch {
      setError(t('passwordReset.errors.verifyError'));
    } finally {
      setIsLoading(false);
    }
  };

  // 비밀번호 찾기 - 새 비밀번호 설정
  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!newPassword) {
      setError(t('passwordReset.errors.newPasswordRequired'));
      return;
    }
    if (newPassword.length < 8) {
      setError(t('passwordReset.errors.passwordLength'));
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t('passwordReset.errors.passwordMismatch'));
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/auth/password/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: resetEmail,
          code: resetCode,
          newPassword: newPassword,
        }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setResetStep('success');
        setError('');
      } else {
        setError(data.message || t('passwordReset.errors.resetFailed'));
      }
    } catch {
      setError(t('passwordReset.errors.resetError'));
    } finally {
      setIsLoading(false);
    }
  };

  // 비밀번호 찾기 모드에서 로그인으로 돌아가기
  const handleBackToLogin = () => {
    setIsPasswordReset(false);
    setResetStep('email');
    setResetEmail('');
    setResetCode('');
    setNewPassword('');
    setConfirmPassword('');
    setError('');
  };

  if (!isOpen) return null;

  // 비밀번호 찾기 화면 렌더링
  if (isPasswordReset) {
    return (
      <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
        <div className="modal-content">
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>

          <h2 className="modal-title">{t('passwordReset.title')}</h2>

          {resetStep === 'email' && (
            <form className="modal-form" onSubmit={handleRequestResetCode}>
              <p className="modal-description">
                {t('passwordReset.emailStepDesc1')}<br />
                {t('passwordReset.emailStepDesc2')}
              </p>
              <input
                type="email"
                placeholder={t('passwordReset.emailPlaceholder')}
                className="modal-input"
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                disabled={isLoading}
                autoFocus
              />
              {error && <p className="modal-error">{error}</p>}
              <button type="submit" className="modal-submit-btn" disabled={isLoading}>
                {isLoading ? t('passwordReset.sending') : t('passwordReset.sendCode')}
              </button>
            </form>
          )}

          {resetStep === 'code' && (
            <form className="modal-form" onSubmit={handleVerifyResetCode}>
              <p className="modal-description"
                dangerouslySetInnerHTML={{ __html: t('passwordReset.codeStepDesc', { email: resetEmail }) }}
              />
              <input
                type="text"
                placeholder={t('passwordReset.codePlaceholder')}
                className="modal-input"
                value={resetCode}
                onChange={(e) => setResetCode(e.target.value)}
                disabled={isLoading}
                maxLength={6}
                autoFocus
              />
              {error && <p className="modal-error">{error}</p>}
              <button type="submit" className="modal-submit-btn" disabled={isLoading}>
                {isLoading ? t('passwordReset.verifying') : t('passwordReset.verifyCode')}
              </button>
              <button
                type="button"
                className="modal-secondary-btn"
                onClick={() => setResetStep('email')}
                disabled={isLoading}
              >
                {t('passwordReset.reenterEmail')}
              </button>
            </form>
          )}

          {resetStep === 'newPassword' && (
            <form className="modal-form" onSubmit={handleResetPassword}>
              <p className="modal-description">
                {t('passwordReset.newPasswordDesc')}
              </p>
              <div className="password-input-wrapper">
                <input
                  type={showNewPassword ? 'text' : 'password'}
                  placeholder={t('passwordReset.newPasswordPlaceholder')}
                  className="modal-input"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  disabled={isLoading}
                  autoFocus
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowNewPassword(!showNewPassword)}
                  tabIndex={-1}
                >
                  {showNewPassword ? (
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
              <div className="password-input-wrapper">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder={t('passwordReset.confirmPasswordPlaceholder')}
                  className="modal-input"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={isLoading}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? (
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
              {error && <p className="modal-error">{error}</p>}
              <button type="submit" className="modal-submit-btn" disabled={isLoading}>
                {isLoading ? t('passwordReset.changing') : t('passwordReset.changePassword')}
              </button>
            </form>
          )}

          {resetStep === 'success' && (
            <div className="modal-form">
              <div className="modal-success">
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="success-icon">
                  <path d="M22 11.08V12a10 10 0 11-5.93-9.14" stroke="#22c55e" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M22 4L12 14.01l-3-3" stroke="#22c55e" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <p className="modal-description">
                  {t('passwordReset.successDesc1')}<br />
                  {t('passwordReset.successDesc2')}
                </p>
              </div>
              <button
                type="button"
                className="modal-submit-btn"
                onClick={handleBackToLogin}
              >
                {t('passwordReset.goToLogin')}
              </button>
            </div>
          )}

          {resetStep !== 'success' && (
            <div className="modal-back-to-login">
              <button type="button" className="modal-back-link" onClick={handleBackToLogin}>
                {t('passwordReset.backToLogin')}
              </button>
            </div>
          )}
        </div>
      </div>
    );
  }

  // 기본 로그인 화면 렌더링
  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">{t('login.title')}</h2>

        <form className="modal-form" onSubmit={handleSubmit}>
          <input
            type="email"
            placeholder={t('login.emailPlaceholder')}
            className="modal-input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={isLoading}
          />
          <div className="password-input-wrapper">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder={t('login.passwordPlaceholder')}
              className="modal-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
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
          {error && <p className="modal-error">{error}</p>}
          <button type="submit" className="modal-submit-btn" disabled={isLoading}>
            {isLoading ? t('login.submitting') : t('login.submit')}
          </button>
        </form>

        <div className="modal-forgot-password">
          <button
            type="button"
            className="modal-forgot-link"
            onClick={() => setIsPasswordReset(true)}
          >
            {t('login.forgotPassword')}
          </button>
        </div>

        <div className="modal-divider">
          <span>{t('login.or')}</span>
        </div>

        <div className="modal-social">
          <p className="modal-social-title">{t('login.socialLogin')}</p>
          <div className="modal-social-btns">
            <button type="button" className="social-btn naver" onClick={() => handleSocialLogin('naver')}>
              <svg viewBox="0 0 24 24" className="social-icon">
                <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727v12.845z" fill="currentColor"/>
              </svg>
            </button>
            <button type="button" className="social-btn kakao" onClick={() => handleSocialLogin('kakao')}>
              <svg viewBox="0 0 24 24" className="social-icon">
                <path d="M12 3C6.477 3 2 6.463 2 10.691c0 2.722 1.8 5.108 4.5 6.454-.18.67-.65 2.428-.745 2.805-.118.47.172.463.362.337.15-.1 2.378-1.612 3.34-2.265.51.071 1.03.108 1.543.108 5.523 0 10-3.463 10-7.691S17.523 3 12 3z" fill="currentColor"/>
              </svg>
            </button>
            <button type="button" className="social-btn google" onClick={() => handleSocialLogin('google')}>
              <svg viewBox="0 0 24 24" className="social-icon">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
            </button>
          </div>
        </div>

        <div className="modal-signup">
          <span>{t('login.noAccount')}</span>
          <button type="button" className="modal-signup-link" onClick={onSwitchToSignup}>{t('login.signupLink')}</button>
        </div>
      </div>
    </div>
  );
};

export default LoginModal;
