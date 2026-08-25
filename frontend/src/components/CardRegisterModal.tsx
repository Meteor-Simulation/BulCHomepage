import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { API_URL } from '../utils/api';
import './CardRegisterModal.css';

interface CardRegisterModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * 자체 카드 등록 폼 (MDP-758).
 *
 * 토스 인증창 대신 우리 화면에서 카드 정보를 받는다.
 *
 * 카드 정보 취급 원칙:
 * - 컴포넌트 state 밖으로 내보내지 않는다 (localStorage/sessionStorage 저장 금지)
 * - 서버 전송 후 즉시 state 를 비운다
 * - 자동완성을 끄지 않는다(브라우저 카드 자동완성은 사용자 편의이므로 허용)
 */
const CardRegisterModal: React.FC<CardRegisterModalProps> = ({ onClose, onSuccess }) => {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);

  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [identityNumber, setIdentityNumber] = useState('');
  const [cardPassword, setCardPassword] = useState('');
  const [setAsDefault, setSetAsDefault] = useState(true);

  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    firstFieldRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !isSubmitting) onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose, isSubmitting]);

  /** 카드번호 — 숫자만 남기고 4자리마다 공백 */
  const handleCardNumber = (v: string) => {
    const digits = v.replace(/\D/g, '').slice(0, 16);
    setCardNumber(digits.replace(/(\d{4})(?=\d)/g, '$1 '));
  };

  /** 유효기간 — MM/YY */
  const handleExpiry = (v: string) => {
    const digits = v.replace(/\D/g, '').slice(0, 4);
    setExpiry(digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits);
  };

  const digitsOnly = (v: string) => v.replace(/\D/g, '');

  const validate = (): string | null => {
    const num = digitsOnly(cardNumber);
    if (num.length < 13) return t('payment.cardForm.errors.cardNumber');

    const exp = digitsOnly(expiry);
    if (exp.length !== 4) return t('payment.cardForm.errors.expiry');
    const month = Number(exp.slice(0, 2));
    if (month < 1 || month > 12) return t('payment.cardForm.errors.expiry');

    const id = digitsOnly(identityNumber);
    if (id.length !== 6 && id.length !== 10) return t('payment.cardForm.errors.identity');

    if (digitsOnly(cardPassword).length !== 2) return t('payment.cardForm.errors.password');
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    const invalid = validate();
    if (invalid) { setError(invalid); return; }

    setError('');
    setIsSubmitting(true);

    const exp = digitsOnly(expiry);
    try {
      const res = await fetch(`${API_URL}/api/subscriptions/billing-keys/card`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include' as RequestCredentials,
        body: JSON.stringify({
          cardNumber: digitsOnly(cardNumber),
          expiryMonth: exp.slice(0, 2),
          expiryYear: exp.slice(2),
          identityNumber: digitsOnly(identityNumber),
          cardPassword: digitsOnly(cardPassword),
          setAsDefault,
        }),
      });
      const data = await res.json().catch(() => ({}));

      if (res.ok) {
        onSuccess();
      } else {
        setError(data.message || data.error || t('payment.cardForm.errors.failed'));
      }
    } catch {
      setError(t('payment.cardForm.errors.failed'));
    } finally {
      // 성공/실패와 무관하게 입력값을 메모리에서 지운다
      setCardNumber('');
      setExpiry('');
      setIdentityNumber('');
      setCardPassword('');
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="card-modal-overlay"
      onMouseDown={(e) => { if (e.target === e.currentTarget && !isSubmitting) onClose(); }}
    >
      <div className="card-modal" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="card-modal-title">
        <div className="card-modal__header">
          <h2 className="card-modal__title" id="card-modal-title">{t('payment.cardForm.title')}</h2>
          <button
            type="button"
            className="card-modal__close"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label={t('common.cancel')}
          >
            ×
          </button>
        </div>

        <form className="card-modal__body" onSubmit={handleSubmit}>
          <div className="card-field">
            <label htmlFor="cf-number">{t('payment.cardForm.cardNumber')}</label>
            <input
              id="cf-number"
              ref={firstFieldRef}
              type="text"
              inputMode="numeric"
              autoComplete="cc-number"
              placeholder="0000 0000 0000 0000"
              value={cardNumber}
              onChange={(e) => handleCardNumber(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <div className="card-field-row">
            <div className="card-field">
              <label htmlFor="cf-expiry">{t('payment.cardForm.expiry')}</label>
              <input
                id="cf-expiry"
                type="text"
                inputMode="numeric"
                autoComplete="cc-exp"
                placeholder="MM/YY"
                value={expiry}
                onChange={(e) => handleExpiry(e.target.value)}
                disabled={isSubmitting}
              />
            </div>
            <div className="card-field">
              <label htmlFor="cf-pw">{t('payment.cardForm.password')}</label>
              <input
                id="cf-pw"
                type="password"
                inputMode="numeric"
                autoComplete="off"
                placeholder="••"
                maxLength={2}
                value={cardPassword}
                onChange={(e) => setCardPassword(digitsOnly(e.target.value).slice(0, 2))}
                disabled={isSubmitting}
              />
              <span className="card-field__hint">{t('payment.cardForm.passwordHint')}</span>
            </div>
          </div>

          <div className="card-field">
            <label htmlFor="cf-id">{t('payment.cardForm.identity')}</label>
            <input
              id="cf-id"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              placeholder={t('payment.cardForm.identityPlaceholder')}
              maxLength={10}
              value={identityNumber}
              onChange={(e) => setIdentityNumber(digitsOnly(e.target.value).slice(0, 10))}
              disabled={isSubmitting}
            />
            <span className="card-field__hint">{t('payment.cardForm.identityHint')}</span>
          </div>

          <label className="card-default-check">
            <input
              type="checkbox"
              checked={setAsDefault}
              onChange={(e) => setSetAsDefault(e.target.checked)}
              disabled={isSubmitting}
            />
            <span>{t('payment.cardForm.setDefault')}</span>
          </label>

          {error && <p className="card-modal__error" role="alert">{error}</p>}

          <p className="card-modal__notice">{t('payment.cardForm.notice')}</p>

          <div className="card-modal__actions">
            <button type="button" className="card-modal__btn card-modal__btn--ghost" onClick={onClose} disabled={isSubmitting}>
              {t('common.cancel')}
            </button>
            <button type="submit" className="card-modal__btn card-modal__btn--primary" disabled={isSubmitting}>
              {isSubmitting ? t('payment.cardForm.submitting') : t('payment.cardForm.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CardRegisterModal;
