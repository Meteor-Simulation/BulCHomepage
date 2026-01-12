import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import './ContactModal.css';

interface ContactModalProps {
  isOpen: boolean;
  onClose: () => void;
  category?: string;
}

const API_URL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
  ? 'http://localhost:8080'
  : `http://${window.location.hostname}:8080`;

const ContactModal: React.FC<ContactModalProps> = ({ isOpen, onClose, category = 'METEOR' }) => {
  const { user } = useAuth();
  const [email, setEmail] = useState('');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // 모달 열릴 때 상태 초기화 및 로그인 사용자 이메일 자동 입력
  useEffect(() => {
    if (isOpen) {
      setEmail(user?.email || '');
      setSubject('');
      setMessage('');
      setError('');
      setSuccess(false);
      setIsLoading(false);
    }
  }, [isOpen, user]);

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

  const handleOverlayMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 유효성 검사
    if (!email.trim()) {
      setError('이메일을 입력해주세요.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('올바른 이메일 형식을 입력해주세요.');
      return;
    }
    if (!subject.trim()) {
      setError('문의 제목을 입력해주세요.');
      return;
    }
    if (!message.trim()) {
      setError('문의 내용을 입력해주세요.');
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/contact`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email,
          subject,
          message,
          category
        }),
      });

      const result = await response.json();

      if (result.success) {
        setSuccess(true);
      } else {
        setError(result.message || '문의 전송에 실패했습니다.');
      }
    } catch (err) {
      setError('문의 전송 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content contact-modal">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">문의하기</h2>

        {success ? (
          <div className="contact-success">
            <svg className="success-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p className="success-message">문의가 성공적으로 전송되었습니다.</p>
            <p className="success-sub">빠른 시일 내에 답변 드리겠습니다.</p>
            <button className="modal-submit-btn" onClick={onClose}>닫기</button>
          </div>
        ) : (
          <form className="modal-form contact-form" onSubmit={handleSubmit}>
            <div className="contact-field">
              <label className="contact-label">이메일 <span className="required">*</span></label>
              <input
                type="email"
                className={`modal-input ${user ? 'input-readonly' : ''}`}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="example@email.com"
                disabled={isLoading}
                readOnly={!!user}
              />
            </div>

            <div className="contact-field">
              <label className="contact-label">문의 제목 <span className="required">*</span></label>
              <input
                type="text"
                className="modal-input"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="문의 제목을 입력해주세요"
                disabled={isLoading}
              />
            </div>

            <div className="contact-field">
              <label className="contact-label">문의 내용 <span className="required">*</span></label>
              <textarea
                className="modal-input contact-textarea"
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder="문의 내용을 입력해주세요"
                disabled={isLoading}
                rows={5}
              />
            </div>

            {error && <p className="modal-error">{error}</p>}

            <button type="submit" className="modal-submit-btn" disabled={isLoading}>
              {isLoading ? '전송 중...' : '문의 보내기'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default ContactModal;
