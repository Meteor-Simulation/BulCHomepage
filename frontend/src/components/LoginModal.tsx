import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import './LoginModal.css';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose }) => {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

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
    setIsLoading(true);

    try {
      const success = await login(username, password);
      if (success) {
        setUsername('');
        setPassword('');
        onClose();
      } else {
        setError('아이디 또는 비밀번호가 올바르지 않습니다.');
      }
    } catch {
      setError('로그인 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">로그인</h2>

        <form className="modal-form" onSubmit={handleSubmit}>
          <input
            type="text"
            placeholder="아이디"
            className="modal-input"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={isLoading}
          />
          <input
            type="password"
            placeholder="비밀번호"
            className="modal-input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
          />
          {error && <p className="modal-error">{error}</p>}
          <button type="submit" className="modal-submit-btn" disabled={isLoading}>
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="modal-divider">
          <span>또는</span>
        </div>

        <div className="modal-social">
          <p className="modal-social-title">간편 로그인</p>
          <div className="modal-social-btns">
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

        <div className="modal-signup">
          <span>계정이 없으신가요?</span>
          <button type="button" className="modal-signup-link">회원가입</button>
        </div>
      </div>
    </div>
  );
};

export default LoginModal;
