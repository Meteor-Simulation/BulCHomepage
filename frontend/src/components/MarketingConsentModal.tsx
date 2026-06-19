import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { getApiBaseUrl } from '../utils/api';
import './MarketingConsentModal.css';

/**
 * 로그인 후 마케팅(광고성 메일) 수신 동의 팝업 (MDP-609).
 *
 * 기존 가입자(특히 소셜 가입자는 marketingAgreed 가 항상 false) 중 아직 동의/미동의를
 * 결정하지 않은 사용자에게 로그인 직후 노출한다.
 * - 동의/미동의: /api/auth/me/marketing-consent 로 저장 후 다시 노출되지 않음
 * - 나중에: 일정 기간 노출을 보류
 */
const DISMISS_KEY = 'mkt_consent_dismissed_until';
const REMIND_LATER_DAYS = 7;     // "나중에" 시 보류 기간
const DECLINE_DAYS = 90;         // "미동의" 시 재요청 보류 기간
const DAY_MS = 24 * 60 * 60 * 1000;

const MarketingConsentModal: React.FC = () => {
  const { user, isLoggedIn, isAuthReady, applyMarketingConsent } = useAuth();
  const [visible, setVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isAuthReady || !isLoggedIn || !user) {
      setVisible(false);
      return;
    }
    // 이미 동의한 사용자는 노출하지 않음
    if (user.marketingAgreed === true) {
      setVisible(false);
      return;
    }
    // marketingAgreed 값을 아직 못 받았으면(undefined) 판단 보류
    if (user.marketingAgreed === undefined) {
      setVisible(false);
      return;
    }
    // 보류 기간 내면 노출하지 않음
    const until = Number(localStorage.getItem(DISMISS_KEY) || 0);
    if (until && Date.now() < until) {
      setVisible(false);
      return;
    }
    setVisible(true);
  }, [isAuthReady, isLoggedIn, user]);

  if (!visible) return null;

  const choose = async (agreed: boolean) => {
    setSubmitting(true);
    try {
      const res = await fetch(`${getApiBaseUrl()}/api/auth/me/marketing-consent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ agreed }),
      });
      if (res.ok) {
        applyMarketingConsent(agreed);
        if (!agreed) {
          // 미동의 — 한동안 다시 묻지 않음
          localStorage.setItem(DISMISS_KEY, String(Date.now() + DECLINE_DAYS * DAY_MS));
        } else {
          localStorage.removeItem(DISMISS_KEY);
        }
        setVisible(false);
      } else {
        // 실패 시 잠시 보류 후 닫기
        localStorage.setItem(DISMISS_KEY, String(Date.now() + REMIND_LATER_DAYS * DAY_MS));
        setVisible(false);
      }
    } catch {
      localStorage.setItem(DISMISS_KEY, String(Date.now() + REMIND_LATER_DAYS * DAY_MS));
      setVisible(false);
    } finally {
      setSubmitting(false);
    }
  };

  const remindLater = () => {
    localStorage.setItem(DISMISS_KEY, String(Date.now() + REMIND_LATER_DAYS * DAY_MS));
    setVisible(false);
  };

  return (
    <div className="mkt-consent-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) remindLater(); }}>
      <div className="mkt-consent-modal" role="dialog" aria-modal="true">
        <div className="mkt-consent-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 4h16v16H4z" />
            <path d="M4 7l8 6 8-6" />
          </svg>
        </div>
        <h3 className="mkt-consent-title">광고성 정보 수신 동의</h3>
        <p className="mkt-consent-desc">
          BUL:C의 신규 기능, 이벤트, 할인 등 <strong>광고성 정보</strong>를 이메일로 받아보시겠어요?
        </p>
        <p className="mkt-consent-note">
          동의하지 않아도 서비스 이용에는 제한이 없으며, 계정·결제·보안 등 필수 안내는 동의와 무관하게 발송됩니다.
          동의 후에도 언제든 메일 하단의 수신거부 링크로 철회할 수 있습니다.
        </p>
        <div className="mkt-consent-actions">
          <button className="mkt-consent-btn decline" disabled={submitting} onClick={() => choose(false)}>
            받지 않기
          </button>
          <button className="mkt-consent-btn agree" disabled={submitting} onClick={() => choose(true)}>
            {submitting ? '처리 중...' : '동의하고 받기'}
          </button>
        </div>
        <button className="mkt-consent-later" disabled={submitting} onClick={remindLater}>
          나중에 선택할게요
        </button>
      </div>
    </div>
  );
};

export default MarketingConsentModal;
