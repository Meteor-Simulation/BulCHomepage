import React, { useState, useEffect, useRef } from 'react';
import { usePreventRefresh } from '../hooks/useNavigationGuard';
import { getApiBaseUrl } from '../utils/api';
import './SignupModal.css';

interface SignupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToLogin: () => void;
}

const SignupModal: React.FC<SignupModalProps> = ({ isOpen, onClose, onSwitchToLogin }) => {
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
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [isVerifyingCode, setIsVerifyingCode] = useState(false);
  const [verificationMessage, setVerificationMessage] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [timerSeconds, setTimerSeconds] = useState(0);
  const [isTimerExpired, setIsTimerExpired] = useState(false);
  const [codeError, setCodeError] = useState(false);

  // 계정 재활성화 상태
  const [showReactivation, setShowReactivation] = useState(false);
  const [reactivationEmail, setReactivationEmail] = useState('');
  const [reactivationCode, setReactivationCode] = useState('');
  const [reactivationCodeSent, setReactivationCodeSent] = useState(false);
  const [reactivationMessage, setReactivationMessage] = useState('');

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
      setIsSendingCode(false);
      setIsVerifyingCode(false);
      setVerificationMessage('');
      setCodeSent(false);
      setTimerSeconds(0);
      setIsTimerExpired(false);
      setCodeError(false);
      setShowReactivation(false);
      setReactivationEmail('');
      setReactivationCode('');
      setReactivationCodeSent(false);
      setReactivationMessage('');
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

  // 인증 코드 타이머 (5분)
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (codeSent && timerSeconds > 0 && !isEmailVerified) {
      interval = setInterval(() => {
        setTimerSeconds((prev) => {
          if (prev <= 1) {
            setIsTimerExpired(true);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [codeSent, timerSeconds, isEmailVerified]);

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

  // 이메일 중복 체크
  const checkEmailDuplicate = async (emailToCheck: string) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(emailToCheck)) {
      setEmailCheckStatus('idle');
      setEmailCheckMessage('');
      return;
    }

    setEmailCheckStatus('checking');
    setEmailCheckMessage('확인 중...');

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/check-email?email=${encodeURIComponent(emailToCheck)}`);
      const result = await response.json();

      if (result.success) {
        if (result.data.deactivated) {
          // 비활성화된 계정 - 재가입 가능 (사용 가능으로 처리)
          setEmailCheckStatus('available');
          setEmailCheckMessage('사용 가능한 이메일입니다');
        } else if (result.data.exists) {
          setEmailCheckStatus('exists');
          setEmailCheckMessage('이미 가입된 이메일입니다');
          // 이메일이 중복이면 인증 상태 초기화
          setIsEmailVerified(false);
          setCodeSent(false);
          setVerificationCode('');
          setVerificationMessage('');
        } else {
          setEmailCheckStatus('available');
          setEmailCheckMessage('사용 가능한 이메일입니다');
        }
      } else {
        setEmailCheckStatus('error');
        setEmailCheckMessage('이메일 확인에 실패했습니다');
      }
    } catch (err) {
      setEmailCheckStatus('error');
      setEmailCheckMessage('이메일 확인 중 오류가 발생했습니다');
    }
  };

  // 이메일 변경 핸들러
  const handleEmailChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newEmail = e.target.value;
    setEmail(newEmail);
    // 이메일이 변경되면 인증 상태 초기화
    setIsEmailVerified(false);
    setCodeSent(false);
    setVerificationCode('');
    setVerificationMessage('');
    setEmailCheckStatus('idle');
    setEmailCheckMessage('');
  };

  // 이메일 입력 완료 시 중복 체크 (blur)
  const handleEmailBlur = () => {
    if (email.trim()) {
      checkEmailDuplicate(email);
    }
  };

  // 인증 코드 발송
  const sendVerificationCode = async () => {
    if (emailCheckStatus !== 'available') {
      setError('먼저 이메일 중복 확인을 해주세요.');
      return;
    }

    setIsSendingCode(true);
    setVerificationMessage('');

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/send-verification`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email }),
      });

      const result = await response.json();

      if (result.success) {
        setCodeSent(true);
        setTimerSeconds(300); // 5분 타이머 시작
        setIsTimerExpired(false);
        setVerificationCode(''); // 재발송 시 입력값 초기화
      } else {
        setVerificationMessage(result.message || '인증 코드 발송에 실패했습니다.');
      }
    } catch (err) {
      console.error('Verification error:', err);
      setVerificationMessage('인증 코드 발송에 실패했습니다.');
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

  // 계정 재활성화 - 인증 코드 발송
  const sendReactivationCode = async () => {
    setIsSendingCode(true);
    setReactivationMessage('');

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/reactivate/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: reactivationEmail }),
      });

      const result = await response.json();

      if (result.success) {
        setReactivationCodeSent(true);
        setReactivationMessage('인증 코드가 발송되었습니다. 이메일을 확인해주세요.');
      } else {
        setReactivationMessage(result.message || '인증 코드 발송에 실패했습니다.');
      }
    } catch (err) {
      console.error('Reactivation error:', err);
      setReactivationMessage('인증 코드 발송 중 오류가 발생했습니다.');
    } finally {
      setIsSendingCode(false);
    }
  };

  // 계정 재활성화 - 인증 확인 및 활성화
  const confirmReactivation = async () => {
    if (!reactivationCode || reactivationCode.length !== 6) {
      setReactivationMessage('6자리 인증 코드를 입력해주세요.');
      return;
    }

    setIsVerifyingCode(true);

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/reactivate/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: reactivationEmail, code: reactivationCode }),
      });

      const result = await response.json();

      if (result.success) {
        alert('계정이 재활성화되었습니다. 로그인해주세요.');
        // 상태 초기화
        setShowReactivation(false);
        setReactivationEmail('');
        setReactivationCode('');
        setReactivationCodeSent(false);
        setReactivationMessage('');
        setEmail('');
        setPassword('');
        setPasswordConfirm('');
        onClose();
        onSwitchToLogin();
      } else {
        setReactivationMessage(result.message || '계정 재활성화에 실패했습니다.');
      }
    } catch (err) {
      setReactivationMessage('계정 재활성화 중 오류가 발생했습니다.');
    } finally {
      setIsVerifyingCode(false);
    }
  };

  // 재활성화 취소
  const cancelReactivation = () => {
    setShowReactivation(false);
    setReactivationEmail('');
    setReactivationCode('');
    setReactivationCodeSent(false);
    setReactivationMessage('');
  };

  // 회원가입 핸들러
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 유효성 검사
    if (!email.trim()) {
      setError('이메일을 입력해주세요.');
      return;
    }
    // 이메일 형식 검사
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('올바른 이메일 형식을 입력해주세요.');
      return;
    }
    if (email.length > 255) {
      setError('이메일은 255자 이하여야 합니다.');
      return;
    }
    if (emailCheckStatus === 'exists') {
      setError('이미 가입된 이메일입니다. 다른 이메일을 사용해주세요.');
      return;
    }
    if (!isEmailVerified) {
      setError('이메일 인증을 완료해주세요.');
      return;
    }
    if (!password) {
      setError('비밀번호를 입력해주세요.');
      return;
    }
    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    // 비밀번호 복잡성 검사
    const hasLetter = /[a-zA-Z]/.test(password);
    const hasDigit = /[0-9]/.test(password);
    // eslint-disable-next-line no-useless-escape
    const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(password);
    if (!hasLetter) {
      setError('비밀번호에 영문자를 포함해야 합니다.');
      return;
    }
    if (!hasDigit) {
      setError('비밀번호에 숫자를 포함해야 합니다.');
      return;
    }
    if (!hasSpecialChar) {
      setError('비밀번호에 특수문자를 포함해야 합니다.');
      return;
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
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
          email,
          password,
        }),
      });

      const result = await response.json();

      if (result.success) {
        alert('회원가입이 완료되었습니다. 로그인해주세요.');
        // 입력 필드 초기화
        setEmail('');
        setPassword('');
        setPasswordConfirm('');
        setEmailCheckStatus('idle');
        setEmailCheckMessage('');
        setVerificationCode('');
        setIsEmailVerified(false);
        setCodeSent(false);
        setVerificationMessage('');
        onClose();
        onSwitchToLogin();
      } else if (result.errorCode === 'ACCOUNT_DEACTIVATED') {
        // 비활성화된 계정 - 재활성화 옵션 표시
        setReactivationEmail(email);
        setShowReactivation(true);
        setError('');
      } else {
        setError(result.message || '회원가입에 실패했습니다.');
      }
    } catch (err) {
      console.error('Signup error:', err);
      setError('회원가입 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  // 재활성화 화면
  if (showReactivation) {
    return (
      <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
        <div className="modal-content signup-modal">
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>

          <h2 className="modal-title">계정 재활성화</h2>
          <p className="reactivation-description">
            비활성화된 계정이 존재합니다.<br/>
            이메일 인증을 통해 계정을 재활성화할 수 있습니다.
          </p>

          <div className="reactivation-form">
            <div className="input-group">
              <input
                type="email"
                className="modal-input"
                value={reactivationEmail}
                disabled
              />
            </div>

            {!reactivationCodeSent ? (
              <button
                type="button"
                className="modal-submit-btn"
                onClick={sendReactivationCode}
                disabled={isSendingCode}
              >
                {isSendingCode ? '발송 중...' : '인증 코드 발송'}
              </button>
            ) : (
              <div className="reactivation-verify-section">
                <div className="verification-code-wrapper">
                  <input
                    type="text"
                    placeholder="6자리 인증 코드"
                    className="verification-code-input"
                    value={reactivationCode}
                    onChange={(e) => setReactivationCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                    disabled={isVerifyingCode}
                    maxLength={6}
                  />
                  <button
                    type="button"
                    className="verification-btn small"
                    onClick={confirmReactivation}
                    disabled={isVerifyingCode || reactivationCode.length !== 6}
                  >
                    {isVerifyingCode ? '확인 중...' : '확인'}
                  </button>
                  <button
                    type="button"
                    className="resend-btn"
                    onClick={sendReactivationCode}
                    disabled={isSendingCode}
                  >
                    재발송
                  </button>
                </div>
              </div>
            )}

            {reactivationMessage && (
              <p className="input-message info">{reactivationMessage}</p>
            )}

            <button
              type="button"
              className="cancel-reactivation-btn"
              onClick={cancelReactivation}
            >
              취소하고 돌아가기
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content signup-modal">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">회원가입</h2>

        <form className="modal-form" onSubmit={handleSubmit}>
          <div className="input-group">
            <div className="input-wrapper">
              <input
                type="email"
                className={`modal-input ${emailCheckStatus === 'exists' ? 'input-error' : emailCheckStatus === 'available' ? 'input-success' : ''}`}
                placeholder="아이디(이메일)"
                value={email}
                onChange={handleEmailChange}
                onBlur={handleEmailBlur}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    passwordInputRef.current?.focus();
                  }
                }}
                disabled={isLoading || isEmailVerified}
                maxLength={255}
              />
              {emailCheckStatus !== 'idle' && (
                <span className={`email-check-status ${emailCheckStatus}`}>
                  {emailCheckStatus === 'checking' && '...'}
                  {emailCheckStatus === 'available' && '✓'}
                  {emailCheckStatus === 'exists' && '✗'}
                  {emailCheckStatus === 'error' && '!'}
                </span>
              )}
            </div>
            {/* 에러 메시지 (이메일 중복, 오류 등) */}
            {emailCheckMessage && (emailCheckStatus === 'exists' || emailCheckStatus === 'error') && (
              <p className={`input-message error`}>
                {emailCheckMessage}
              </p>
            )}

            {/* 이메일 인증 영역 */}
            {emailCheckStatus === 'available' && !isEmailVerified && (
              <div className="email-verification-section">
                {!codeSent ? (
                  <div className="email-available-row">
                    <span className="input-message success inline">사용 가능한 이메일입니다</span>
                    <button
                      type="button"
                      className="verification-btn inline"
                      onClick={sendVerificationCode}
                      disabled={isSendingCode || isLoading}
                    >
                      {isSendingCode ? '발송 중...' : '인증 메일 발송'}
                    </button>
                  </div>
                ) : (
                  <div className="verification-code-row">
                    <input
                      type="text"
                      placeholder="인증코드"
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
                      {isTimerExpired ? '만료' : formatTimer(timerSeconds)}
                    </span>
                    {codeError && <span className="code-error-msg">코드 불일치</span>}
                    <button
                      type="button"
                      className="verification-btn small"
                      onClick={verifyCode}
                      disabled={isVerifyingCode || isLoading || verificationCode.length !== 6 || isTimerExpired}
                    >
                      {isVerifyingCode ? '...' : '확인'}
                    </button>
                    <button
                      type="button"
                      className="verification-btn small secondary"
                      onClick={sendVerificationCode}
                      disabled={isSendingCode || isLoading}
                    >
                      {isSendingCode ? '...' : '재발송'}
                    </button>
                  </div>
                )}
              </div>
            )}
            {isEmailVerified && (
              <p className="input-message success verified">이메일 인증이 완료되었습니다 ✓</p>
            )}
            {verificationMessage && !isEmailVerified && !codeSent && (
              <p className="input-message error">{verificationMessage}</p>
            )}
          </div>

          <div className="input-group">
            <div className="input-wrapper">
              <input
                ref={passwordInputRef}
                type={showPassword ? 'text' : 'password'}
                className="modal-input"
                placeholder="비밀번호"
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
                  <p className="input-validation-error">영문, 숫자, 특수문자를 포함해주세요.</p>
                ) : password.length < 8 ? (
                  <p className="input-validation-error">비밀번호를 8자 이상으로 만들어주세요.</p>
                ) : null}
              </>
            )}
          </div>

          <div className="input-group">
            <div className="input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                className="modal-input"
                placeholder="비밀번호 확인"
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
              <p className="input-validation-error">위 비밀번호와 동일하게 입력해주세요.</p>
            )}
          </div>

          {error && <p className="modal-error">{error}</p>}

          <button type="submit" className="modal-submit-btn" disabled={isLoading}>
            {isLoading ? '가입 중...' : '회원가입'}
          </button>
        </form>

        <div className="modal-login">
          <span>이미 계정이 있으신가요?</span>
          <button type="button" className="modal-login-link" onClick={onSwitchToLogin}>
            로그인
          </button>
        </div>
      </div>
    </div>
  );
};

export default SignupModal;
