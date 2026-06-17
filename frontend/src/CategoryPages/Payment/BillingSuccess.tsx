import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import Seo from '../../components/Seo';
import './PaymentResult.css';

/**
 * 토스 빌링 인증(카드 등록) 성공 콜백.
 *
 * 토스가 successUrl로 ?customerKey=...&authKey=... 를 붙여 리다이렉트한다.
 * authKey를 백엔드에 넘겨 빌링키를 발급/저장한 뒤 마이페이지 결제 탭으로 복귀한다.
 * (결제가 아니라 카드 등록이므로 금액 처리는 없다)
 */
const BillingSuccess: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isProcessing, setIsProcessing] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const isRegistering = useRef(false); // 중복 요청 방지

  // 결제 페이지에서 카드 등록을 시작했으면 등록 후 결제 페이지로 복귀한다.
  const returnTo = searchParams.get('returnTo');
  const successPath = returnTo === 'payment' ? '/payment' : '/mypage?tab=payment';

  useEffect(() => {
    const registerCard = async () => {
      if (isRegistering.current) return;
      isRegistering.current = true;

      const authKey = searchParams.get('authKey');
      if (!authKey) {
        setError('인증 정보가 올바르지 않습니다. 다시 시도해주세요.');
        setIsProcessing(false);
        return;
      }

      try {
        const response = await fetch(`${API_URL}/api/subscriptions/billing-keys`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include' as RequestCredentials,
          body: JSON.stringify({ authKey }),
        });

        if (response.ok) {
          // 카드 등록 성공 → 시작 지점(결제 페이지 또는 마이페이지 결제 탭)으로 복귀
          navigate(successPath, { replace: true });
        } else {
          const data = await response.json().catch(() => ({}));
          setError(data.error || '카드 등록에 실패했습니다. 다시 시도해주세요.');
          setIsProcessing(false);
        }
      } catch {
        setError('카드 등록 중 오류가 발생했습니다.');
        setIsProcessing(false);
      }
    };
    registerCard();
  }, [searchParams, navigate, successPath]);

  if (isProcessing) {
    return (
      <div className="payment-result-page">
        <Seo title="카드 등록 중 | BUL:C" noindex />
        <Header hideUserMenu={true} />
        <div className="payment-result-container">
          <div className="payment-result-card processing">
            <div className="spinner"></div>
            <h2>카드를 등록하는 중입니다...</h2>
            <p>잠시만 기다려주세요.</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="payment-result-page">
      <Seo title="카드 등록 실패 | BUL:C" noindex />
      <Header hideUserMenu={true} />
      <div className="payment-result-container">
        <div className="payment-result-card error">
          <div className="result-icon error">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M15 9l-6 6M9 9l6 6" />
            </svg>
          </div>
          <h2>카드 등록에 실패했습니다</h2>
          <p>{error}</p>
          <div className="result-actions">
            <button className="btn-primary" onClick={() => navigate(successPath, { replace: true })}>
              {returnTo === 'payment' ? '결제 페이지로 이동' : '결제 수단 관리로 이동'}
            </button>
          </div>
          <p className="result-notice">
            문제가 계속되면 고객센터로 문의해주세요.<br />
            simul@msimul.com
          </p>
        </div>
      </div>
    </div>
  );
};

export default BillingSuccess;
