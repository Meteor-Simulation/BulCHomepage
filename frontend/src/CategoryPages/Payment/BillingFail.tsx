import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '../../components/Header';
import Seo from '../../components/Seo';
import './PaymentResult.css';

/**
 * 토스 빌링 인증(카드 등록) 실패/취소 콜백.
 * 토스가 failUrl로 ?code=...&message=... 를 붙여 리다이렉트한다.
 */
const BillingFail: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const errorMessage = searchParams.get('message');

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
          <p>{errorMessage || '카드 등록이 취소되었거나 오류가 발생했습니다.'}</p>

          <div className="result-actions">
            <button className="btn-secondary" onClick={() => navigate('/mypage?tab=payment')}>
              결제 수단 관리
            </button>
            <button className="btn-primary" onClick={() => navigate('/')}>
              홈으로
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

export default BillingFail;
