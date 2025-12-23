import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '../../components/Header';
import './PaymentResult.css';

const PaymentFail: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const errorCode = searchParams.get('code');
  const errorMessage = searchParams.get('message');

  const getErrorDescription = (code: string | null) => {
    switch (code) {
      case 'PAY_PROCESS_CANCELED':
        return '결제가 취소되었습니다.';
      case 'PAY_PROCESS_ABORTED':
        return '결제 진행 중 문제가 발생했습니다.';
      case 'REJECT_CARD_COMPANY':
        return '카드사에서 결제를 거부했습니다.';
      default:
        return errorMessage || '결제 처리 중 오류가 발생했습니다.';
    }
  };

  return (
    <div className="payment-result-page">
      <Header hideUserMenu={true} />
      <div className="payment-result-container">
        <div className="payment-result-card error">
          <div className="result-icon error">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </div>
          <h2>결제에 실패했습니다</h2>
          <p>{getErrorDescription(errorCode)}</p>

          {errorCode && (
            <div className="error-code">
              오류 코드: {errorCode}
            </div>
          )}

          <div className="result-actions">
            <button className="btn-secondary" onClick={() => navigate('/payment')}>
              다시 시도
            </button>
            <button className="btn-primary" onClick={() => navigate('/')}>
              홈으로
            </button>
          </div>

          <p className="result-notice">
            문제가 계속되면 고객센터로 문의해주세요.<br/>
            simul@msimul.com
          </p>
        </div>
      </div>
    </div>
  );
};

export default PaymentFail;
