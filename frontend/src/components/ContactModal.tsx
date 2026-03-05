import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { API_URL } from '../utils/api';
import './ContactModal.css';

interface ContactModalProps {
  isOpen: boolean;
  onClose: () => void;
  category?: string;
}

const ContactModal: React.FC<ContactModalProps> = ({ isOpen, onClose, category = 'METEOR' }) => {
  const { t } = useTranslation();
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
      setError(t('contact.errorEmail'));
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError(t('contact.errorEmailInvalid'));
      return;
    }
    if (!subject.trim()) {
      setError(t('contact.errorSubject'));
      return;
    }
    if (!message.trim()) {
      setError(t('contact.errorMessage'));
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
        setError(result.message || t('contact.errorSendFailed'));
      }
    } catch (err) {
      setError(t('contact.errorSendError'));
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

        <h2 className="modal-title">{t('contact.title')}</h2>

        {success ? (
          <div className="contact-success">
            <svg className="success-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p className="success-message">{t('contact.success')}</p>
            <p className="success-sub">{t('contact.successSub')}</p>
            <button className="modal-submit-btn" onClick={onClose}>{t('contact.close')}</button>
          </div>
        ) : (
          <form className="modal-form contact-form" onSubmit={handleSubmit}>
            <div className="contact-field">
              <label className="contact-label">{t('contact.email')} <span className="required">*</span></label>
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
              <label className="contact-label">{t('contact.subject')} <span className="required">*</span></label>
              <input
                type="text"
                className="modal-input"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder={t('contact.subjectPlaceholder')}
                disabled={isLoading}
              />
            </div>

            <div className="contact-field">
              <label className="contact-label">{t('contact.message')} <span className="required">*</span></label>
              <textarea
                className="modal-input contact-textarea"
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder={t('contact.messagePlaceholder')}
                disabled={isLoading}
                rows={5}
              />
            </div>

            {error && <p className="modal-error">{error}</p>}

            <button type="submit" className="modal-submit-btn" disabled={isLoading}>
              {isLoading ? t('contact.submitting') : t('contact.submit')}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default ContactModal;
