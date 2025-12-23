import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '../../components/Header';
import './PaymentResult.css';

interface PaymentResult {
  orderId: string;
  amount: number;
  paymentKey: string;
  orderName?: string;
}

const PaymentSuccess: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isProcessing, setIsProcessing] = useState(true);
  const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isConfirming = useRef(false); // 중복 요청 방지

  useEffect(() => {
    const confirmPayment = async () => {
      // 이미 처리 중이면 무시
      if (isConfirming.current) return;
      isConfirming.current = true;

      const paymentKey = searchParams.get('paymentKey');
      const orderId = searchParams.get('orderId');
      const amount = searchParams.get('amount');

      if (!paymentKey || !orderId || !amount) {
        setError('결제 정보가 올바르지 않습니다.');
        setIsProcessing(false);
        return;
      }

      // 이미 처리된 결제인지 로컬스토리지 확인
      const processedPayments = JSON.parse(localStorage.getItem('processedPayments') || '[]');
      if (processedPayments.includes(orderId)) {
        setPaymentResult({
          orderId,
          amount: parseInt(amount),
          paymentKey,
        });
        setIsProcessing(false);
        return;
      }

      try {
        // 백엔드에 결제 승인 요청
        const response = await fetch('/api/payments/confirm', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            paymentKey,
            orderId,
            amount: parseInt(amount),
          }),
        });

        if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.message || '결제 승인에 실패했습니다.');
        }

        const result = await response.json();

        // 처리 완료된 결제 저장
        processedPayments.push(orderId);
        localStorage.setItem('processedPayments', JSON.stringify(processedPayments));

        setPaymentResult({
          orderId,
          amount: parseInt(amount),
          paymentKey,
          orderName: result.orderName,
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : '결제 처리 중 오류가 발생했습니다.');
      } finally {
        setIsProcessing(false);
      }
    };

    confirmPayment();
  }, [searchParams]);

  if (isProcessing) {
    return (
      <div className="payment-result-page">
        <Header hideUserMenu={true} />
        <div className="payment-result-container">
          <div className="payment-result-card processing">
            <div className="spinner"></div>
            <h2>결제 처리 중...</h2>
            <p>잠시만 기다려주세요.</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
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
            <h2>결제 승인 실패</h2>
            <p>{error}</p>
            <div className="result-actions">
              <button className="btn-secondary" onClick={() => navigate('/payment')}>
                다시 시도
              </button>
              <button className="btn-primary" onClick={() => navigate('/')}>
                홈으로
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="payment-result-page">
      <Header hideUserMenu={true} />
      <div className="payment-result-container">
        <div className="payment-result-card success">
          <div className="result-icon success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M8 12l2 2 4-4"/>
            </svg>
          </div>
          <h2>결제가 완료되었습니다</h2>
          <p>BulC 라이선스 구매가 완료되었습니다.</p>

          {paymentResult && (
            <div className="payment-details">
              <div className="detail-row">
                <span className="label">주문번호</span>
                <span className="value">{paymentResult.orderId}</span>
              </div>
              <div className="detail-row">
                <span className="label">결제금액</span>
                <span className="value">{paymentResult.amount.toLocaleString()}원</span>
              </div>
            </div>
          )}

          <div className="result-actions">
            <button className="btn-primary" onClick={() => navigate('/bulc', { state: { activeTab: 'download' } })}>
              다운로드 페이지로 이동
            </button>
          </div>

          <p className="result-notice">
            라이선스 키가 이메일로 발송됩니다.<br/>
            문의사항은 고객센터로 연락해주세요.
          </p>
        </div>
      </div>
    </div>
  );
};

export default PaymentSuccess;
