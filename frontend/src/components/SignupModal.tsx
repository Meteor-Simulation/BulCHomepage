import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { usePreventRefresh } from '../hooks/useNavigationGuard';
import { useAlert } from './AlertProvider';
import { getApiBaseUrl } from '../utils/api';
import {
  fetchBoothGiftConfig,
  isBoothGiftActive,
  markPendingEventRedirect,
} from '../utils/eventConfig';
import './SignupModal.css';

interface SignupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToLogin: () => void;
}

const SignupModal: React.FC<SignupModalProps> = ({ isOpen, onClose, onSwitchToLogin }) => {
  const { t } = useTranslation();
  const { showAlert } = useAlert();
  const passwordInputRef = useRef<HTMLInputElement>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // 이메일 중복 체크 상태
  const [emailCheckStatus, setEmailCheckStatus] = useState<'idle' | 'checking' | 'available' | 'exists' | 'error'>('idle');
  const [emailCheckMessage, setEmailCheckMessage] = useState('');

  // 이메일 인증 상태
  const [verificationCode, setVerificationCode] = useState('');
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [signupTicket, setSignupTicket] = useState<string | null>(null);
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [isVerifyingCode, setIsVerifyingCode] = useState(false);
  const [verificationMessage, setVerificationMessage] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [timerSeconds, setTimerSeconds] = useState(0);
  const [isTimerExpired, setIsTimerExpired] = useState(false);
  const [timerKey, setTimerKey] = useState(0);
  const [codeError, setCodeError] = useState(false);


  // 새로고침 방지 - 모달이 열려있고 입력값이 있을 때만 활성화
  const hasUserInput = email.length > 0 || password.length > 0 || passwordConfirm.length > 0;
  usePreventRefresh(isOpen && hasUserInput);

  // 모달 열릴 때 상태 초기화
  useEffect(() => {
    if (isOpen) {
      setEmail('');
      setPassword('');
      setPasswordConfirm('');
      setShowPassword(false);
      setShowPasswordConfirm(false);
      setError('');
      setIsLoading(false);
      setEmailCheckStatus('idle');
      setEmailCheckMessage('');
      setVerificationCode('');
      setIsEmailVerified(false);
      setSignupTicket(null);
      setIsSendingCode(false);
      setIsVerifyingCode(false);
      setVerificationMessage('');
      setCodeSent(false);
      setTimerSeconds(0);
      setIsTimerExpired(false);
      setTimerKey(0);
      setCodeError(false);
    }
  }, [isOpen]);

  // ESC 키로 모달 닫기
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  // 인증 코드 타이머 (5분) — timerKey가 변경되면 interval 재생성
  useEffect(() => {
    if (!codeSent || isEmailVerified) return;

    const interval = setInterval(() => {
      setTimerSeconds((prev) => {
        if (prev <= 1) {
          setIsTimerExpired(true);
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [codeSent, isEmailVerified, timerKey]);

  // 타이머 포맷 (mm:ss)
  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  // 오버레이 마우스다운시 모달 닫기
  const handleOverlayMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // 이메일 변경 핸들러
  const handleEmailChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newEmail = e.target.value;
    setEmail(newEmail);
    // 이메일이 변경되면 인증 상태 초기화
    setIsEmailVerified(false);
    setSignupTicket(null);
    setCodeSent(false);
    setVerificationCode('');
    setVerificationMessage('');
    setEmailCheckStatus('idle');
    setEmailCheckMessage('');
  };

  // 인증 코드 발송 (이메일 중복 체크 포함)
  const sendVerificationCode = async () => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email.trim() || !emailRegex.test(email)) {
      setError(t('signup.errors.emailInvalid'));
      return;
    }

    setIsSendingCode(true);
    setError('');
    setVerificationMessage('');

    // 이메일 중복 체크 (재발송 시에는 스킵)
    if (emailCheckStatus !== 'available') {
      try {
        const checkResponse = await fetch(`${getApiBaseUrl()}/api/auth/check-email?email=${encodeURIComponent(email)}`);
        const checkResult = await checkResponse.json();

        if (checkResult.success) {
          if (checkResult.data.exists) {
            setEmailCheckStatus('exists');
            setEmailCheckMessage(t('signup.errors.emailExists'));
            setIsSendingCode(false);
            return;
          } else {
            setEmailCheckStatus('available');
          }
        } else {
          setError(t('signup.errors.emailCheckFailed'));
          setIsSendingCode(false);
          return;
        }
      } catch (err) {
        setError(t('signup.errors.emailCheckError'));
        setIsSendingCode(false);
        return;
      }
    }

    // 타이머 즉시 초기화
    setCodeSent(true);
    setTimerSeconds(300);
    setIsTimerExpired(false);
    setTimerKey((prev) => prev + 1);
    setVerificationCode('');
    setCodeError(false);

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/send-verification`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email }),
      });

      const result = await response.json();

      if (!result.success) {
        setVerificationMessage(result.message || t('signup.errors.sendCodeFailed'));
      }
    } catch (err) {
      // verification error
      setVerificationMessage(t('signup.errors.sendCodeFailed'));
    } finally {
      setIsSendingCode(false);
    }
  };

  // 인증 코드 확인
  const verifyCode = async () => {
    if (!verificationCode || verificationCode.length !== 6) {
      setCodeError(true);
      return;
    }

    setIsVerifyingCode(true);
    setCodeError(false);

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/verify-code`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, code: verificationCode }),
      });

      const result = await response.json();

      if (result.success && result.data.verified) {
        setIsEmailVerified(true);
        setSignupTicket(result.data.signupTicket);
        setCodeError(false);
      } else {
        setCodeError(true);
      }
    } catch (err) {
      setCodeError(true);
    } finally {
      setIsVerifyingCode(false);
    }
  };

  // 복사/붙여넣기 방지 핸들러
  const preventCopyPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
  };


  // 회원가입 핸들러
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 유효성 검사
    if (!email.trim()) {
      setError(t('signup.errors.emailRequired'));
      return;
    }
    // 이메일 형식 검사
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError(t('signup.errors.emailInvalid'));
      return;
    }
    if (email.length > 255) {
      setError(t('signup.errors.emailTooLong'));
      return;
    }
    if (emailCheckStatus === 'exists') {
      setError(t('signup.errors.emailExistsLong'));
      return;
    }
    if (!isEmailVerified) {
      setError(t('signup.errors.emailVerifyRequired'));
      return;
    }
    if (!password) {
      setError(t('signup.errors.passwordRequired'));
      return;
    }
    if (password.length < 8) {
      setError(t('signup.errors.passwordTooShort'));
      return;
    }
    // 비밀번호 복잡성 검사
    const hasLetter = /[a-zA-Z]/.test(password);
    const hasDigit = /[0-9]/.test(password);
    // eslint-disable-next-line no-useless-escape
    const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(password);
    if (!hasLetter) {
      setError(t('signup.errors.passwordMissingLetter'));
      return;
    }
    if (!hasDigit) {
      setError(t('signup.errors.passwordMissingDigit'));
      return;
    }
    if (!hasSpecialChar) {
      setError(t('signup.errors.passwordMissingSpecial'));
      return;
    }
    if (password !== passwordConfirm) {
      setError(t('signup.errors.passwordMismatch'));
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/signup`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          signupTicket,
          password,
        }),
      });

      const result = await response.json();

      if (result.success) {
        // 이벤트 진행 중이면 로그인 후 사은품 페이지로 자동 이동하도록 플래그만 설정
        // (실제 리다이렉트는 로그인 시점에 사용자 국가가 한국인지 추가로 검증한 뒤 수행)
        const eventConfig = await fetchBoothGiftConfig({ force: true });
        if (isBoothGiftActive(eventConfig)) {
          markPendingEventRedirect();
        }
        showAlert({ message: t('alerts.signupCompleted'), type: 'success' });
        // 입력 필드 초기화
        setEmail('');
        setPassword('');
        setPasswordConfirm('');
        setEmailCheckStatus('idle');
        setEmailCheckMessage('');
        setVerificationCode('');
        setIsEmailVerified(false);
        setSignupTicket(null);
        setCodeSent(false);
        setVerificationMessage('');
        onClose();
        onSwitchToLogin();
      } else {
        setError(result.message || t('signup.errors.signupFailed'));
      }
    } catch (err) {
      // signup error
      setError(t('signup.errors.signupError'));
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content signup-modal">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">{t('signup.title')}</h2>

        <form className="modal-form" onSubmit={handleSubmit}>
          <div className="input-group">
            <div className="email-input-row">
              <div className="input-wrapper" style={{flex: 1}}>
                <input
                  type="email"
                  className={`modal-input ${emailCheckStatus === 'exists' ? 'input-error' : isEmailVerified ? 'input-success' : ''}`}
                  placeholder={t('signup.emailPlaceholder')}
                  value={email}
                  onChange={handleEmailChange}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      if (!isEmailVerified && !codeSent) {
                        sendVerificationCode();
                      } else if (codeSent && !isEmailVerified) {
                        verifyCode();
                      } else {
                        passwordInputRef.current?.focus();
                      }
                    }
                  }}
                  disabled={isLoading || isEmailVerified}
                  maxLength={255}
                />
              </div>
              {!isEmailVerified && !codeSent && (
                <button
                  type="button"
                  className="verification-btn email-verify-btn"
                  onClick={sendVerificationCode}
                  disabled={isSendingCode || isLoading || !email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)}
                >
                  {isSendingCode ? t('signup.sending') : t('signup.emailVerify')}
                </button>
              )}
              {!isEmailVerified && codeSent && (
                <button
                  type="button"
                  className="verification-btn email-verify-btn secondary"
                  onClick={sendVerificationCode}
                  disabled={isSendingCode || isLoading}
                >
                  {isSendingCode ? '...' : t('signup.resend')}
                </button>
              )}
            </div>

            {/* 에러 메시지 (이메일 중복, 오류 등) */}
            {emailCheckMessage && (emailCheckStatus === 'exists' || emailCheckStatus === 'error') && (
              <p className="input-message error">{emailCheckMessage}</p>
            )}

            {/* 인증 코드 입력 영역 */}
            {codeSent && !isEmailVerified && (
              <div className="verification-code-row">
                <input
                  type="text"
                  placeholder={t('signup.codePlaceholder')}
                  className={`verification-code-input ${codeError ? 'input-error' : ''}`}
                  value={verificationCode}
                  onChange={(e) => {
                    setVerificationCode(e.target.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 6));
                    setCodeError(false);
                  }}
                  disabled={isVerifyingCode || isLoading || isTimerExpired}
                  maxLength={6}
                />
                <span className={`verification-timer ${isTimerExpired ? 'expired' : ''}`}>
                  {isTimerExpired ? t('signup.expired') : formatTimer(timerSeconds)}
                </span>
                {codeError && <span className="code-error-msg">{t('signup.codeMismatch')}</span>}
                <button
                  type="button"
                  className="verification-btn small"
                  onClick={verifyCode}
                  disabled={isVerifyingCode || isLoading || verificationCode.length !== 6 || isTimerExpired}
                >
                  {isVerifyingCode ? '...' : t('signup.verify')}
                </button>
              </div>
            )}

            {isEmailVerified && (
              <p className="input-message success verified">{t('signup.emailVerified')}</p>
            )}
            {verificationMessage && !isEmailVerified && (
              <p className="input-message error">{verificationMessage}</p>
            )}
          </div>

          <div className="input-group">
            <div className="input-wrapper">
              <input
                ref={passwordInputRef}
                type={showPassword ? 'text' : 'password'}
                className="modal-input"
                placeholder={t('signup.passwordPlaceholder')}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onCopy={preventCopyPaste}
                onPaste={preventCopyPaste}
                onCut={preventCopyPaste}
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
            {/* 비밀번호 유효성 검사 메시지 */}
            {password.length > 0 && (
              <>
                {!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password) || !/[!@#$%^&*()_+\-=[\]{}|;':",./<>?]/.test(password) ? (
                  <p className="input-validation-error">{t('signup.validation.needsAllChars')}</p>
                ) : password.length < 8 ? (
                  <p className="input-validation-error">{t('signup.validation.minLength')}</p>
                ) : null}
              </>
            )}
          </div>

          <div className="input-group">
            <div className="input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                className="modal-input"
                placeholder={t('signup.passwordConfirmPlaceholder')}
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                onCopy={preventCopyPaste}
                onPaste={preventCopyPaste}
                onCut={preventCopyPaste}
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
            {/* 비밀번호 확인 불일치 메시지 */}
            {passwordConfirm.length > 0 && password !== passwordConfirm && (
              <p className="input-validation-error">{t('signup.validation.passwordMismatch')}</p>
            )}
          </div>

          {error && <p className="modal-error">{error}</p>}

          <button type="submit" className="modal-submit-btn" disabled={isLoading}>
            {isLoading ? t('signup.submitting') : t('signup.submit')}
          </button>
        </form>

        <div className="modal-login">
          <span>{t('signup.haveAccount')}</span>
          <button type="button" className="modal-login-link" onClick={onSwitchToLogin}>
            {t('signup.loginLink')}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SignupModal;
