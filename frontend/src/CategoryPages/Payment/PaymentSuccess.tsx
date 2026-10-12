import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import Seo from '../../components/Seo';
import './PaymentResult.css';

interface PaymentResult {
  orderId: string;
  amount: number;
  currency: string;
  paymentKey: string;
  orderName?: string;
  licenseKey?: string;
  licenseValidUntil?: string;
  // 결제는 완료됐으나 라이선스 발급이 지연된 경우 (MDP-832). 서버가 구체 사유를 담아 보낸다
  licenseError?: string;
  // 이 요금제는 라이선스를 발급하지 않는다(license_plan_id 미연결 — 점검용 요금제 등).
  // '지연' 과 반드시 구분해야 한다. 지연은 곧 오지만 이건 영원히 오지 않는다.
  licenseNotApplicable?: boolean;
  // 가상계좌 정보
  isVirtualAccount?: boolean;
  bankName?: string;
  accountNumber?: string;
  dueDate?: string;
}

const formatAmount = (amount: number, currency: string) => {
  if (currency === 'USD') {
    return '$' + amount.toLocaleString();
  }
  return amount.toLocaleString() + '원';
};

const PaymentSuccess: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const [isProcessing, setIsProcessing] = useState(true);
  const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isConfirming = useRef(false); // 중복 요청 방지

  useEffect(() => {
    // 빌링키 즉시 결제 결과(navigation state) → 확인 API 없이 바로 표시
    const billingResult = (location.state as any)?.billingResult;
    if (billingResult) {
      setPaymentResult(billingResult as PaymentResult);
      setIsProcessing(false);
      return;
    }
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
          currency: 'KRW',
          paymentKey,
        });
        setIsProcessing(false);
        return;
      }

      try {
        // orderId에서 pricePlanId 추출 (형식: BULC_{planId}_{timestamp}_{random})
        const orderIdParts = orderId.split('_');
        const pricePlanId = orderIdParts.length >= 2 ? parseInt(orderIdParts[1]) : null;

        if (!pricePlanId) {
          throw new Error('잘못된 주문 ID 형식입니다.');
        }

        // 백엔드에 결제 승인 요청
        const response = await fetch(`${API_URL}/api/payments/confirm`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            paymentKey,
            orderId,
            amount: parseInt(amount),
            pricePlanId,
            // 결제 페이지에서 보관한 쿠폰 코드 (MDP-748). 없으면 쿠폰 미적용 결제다.
            couponCode: localStorage.getItem(`couponCode_${orderId}`) || undefined,
          }),
        });

        if (!response.ok) {
          let errorMessage = '결제 승인에 실패했습니다.';
          try {
            const errorData = await response.json();
            errorMessage = errorData.message || errorMessage;
          } catch {
            // 응답 본문이 비어있거나 JSON이 아닌 경우
          }
          throw new Error(errorMessage);
        }

        const result = await response.json();

        // 처리 완료된 결제 저장
        processedPayments.push(orderId);
        localStorage.setItem('processedPayments', JSON.stringify(processedPayments));
        // 쿠폰 코드는 승인에만 쓰이므로 즉시 정리한다 (주문별로 쌓이면 지저분하다)
        localStorage.removeItem(`couponCode_${orderId}`);

        setPaymentResult({
          orderId,
          amount: parseInt(amount),
          currency: result.currency || 'KRW',
          paymentKey,
          orderName: result.orderName,
          licenseKey: result.licenseKey,
          licenseValidUntil: result.licenseValidUntil,
          licenseError: result.licenseError,
          licenseNotApplicable: result.licenseNotApplicable,
          // 가상계좌 정보
          isVirtualAccount: result.isVirtualAccount,
          bankName: result.bankName,
          accountNumber: result.accountNumber,
          dueDate: result.dueDate,
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : '결제 처리 중 오류가 발생했습니다.');
      } finally {
        setIsProcessing(false);
      }
    };

    confirmPayment();
  }, [searchParams, location.state]);

  if (isProcessing) {
    return (
      <div className="payment-result-page">
        <Seo title="결제 결과 | BUL:C" noindex />
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
        <Seo title="결제 결과 | BUL:C" noindex />
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

  // 가상계좌 입금 대기 UI
  if (paymentResult?.isVirtualAccount) {
    const formatDueDate = (dateStr: string) => {
      try {
        const date = new Date(dateStr);
        return date.toLocaleString('ko-KR', {
          year: 'numeric',
          month: 'long',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        });
      } catch {
        return dateStr;
      }
    };

    return (
      <div className="payment-result-page">
        <Seo title="결제 결과 | BUL:C" noindex />
        <Header hideUserMenu={true} />
        <div className="payment-result-container">
          <div className="payment-result-card waiting">
            <div className="result-icon waiting">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </div>
            <h2>가상계좌가 발급되었습니다</h2>
            {/* 라이선스를 주지 않는 요금제에는 "라이선스가 발급됩니다" 라고 말하지 않는다 */}
            <p>
              {paymentResult.licenseNotApplicable
                ? '아래 계좌로 입금하시면 결제가 완료됩니다.'
                : '아래 계좌로 입금하시면 라이선스가 발급됩니다.'}
            </p>

            <div className="payment-details virtual-account">
              <div className="detail-row bank-info">
                <span className="label">입금 계좌</span>
                <span className="value account">
                  {paymentResult.bankName} {paymentResult.accountNumber}
                </span>
              </div>
              <div className="detail-row">
                <span className="label">입금 금액</span>
                <span className="value highlight">{formatAmount(paymentResult.amount, paymentResult.currency)}</span>
              </div>
              {paymentResult.dueDate && (
                <div className="detail-row">
                  <span className="label">입금 기한</span>
                  <span className="value">{formatDueDate(paymentResult.dueDate)}</span>
                </div>
              )}
              <div className="detail-row">
                <span className="label">주문번호</span>
                <span className="value">{paymentResult.orderId}</span>
              </div>
            </div>

            <div className="virtual-account-notice">
              <p>입금 기한 내에 입금하지 않으면 주문이 자동 취소됩니다.</p>
              {!paymentResult.licenseNotApplicable && (
                <p>입금 완료 후 라이선스가 자동으로 발급되며, 이메일로 안내됩니다.</p>
              )}
            </div>

            <div className="result-actions">
              <button className="btn-primary" onClick={() => navigate('/')}>
                홈으로 이동
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // 일반 결제 완료 UI (카드, 계좌이체 등)
  return (
    <div className="payment-result-page">
      <Seo title="결제 결과 | BUL:C" noindex />
      <Header hideUserMenu={true} />
      <div className="payment-result-container">
        <div className="payment-result-card success">
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
                <span className="value">{formatAmount(paymentResult.amount, paymentResult.currency)}</span>
              </div>
              {/*
                라이선스 — 상태가 셋이고 서로 다른 뜻이다. 틀은 하나로 두고 내용만 바꾼다.
                종전에는 "발급 처리 중" 안내와 하단의 "이메일로 발송됩니다" 가 동시에 떠서
                키가 지금 있는 건지 기다려야 하는 건지 알 수 없었다. 하단 문구는 지웠다.

                  ① licenseKey 있음        → 바로 쓸 수 있다 (메일도 자동 발송된다)
                  ② licenseNotApplicable  → 이 상품은 라이선스를 주지 않는다 (영원히 안 옴)
                  ③ 그 외                  → 발급이 지연됐다, 자동 재시도 중 (MDP-832)

                ②와 ③을 구분하는 것이 핵심이다. 종전에는 둘을 같은 분기로 묶어, 라이선스가
                없는 요금제에도 "발급 처리 중" 이라고 알려 오지 않을 키를 기다리게 했다.
                가상계좌는 입금 대기 화면에서 따로 안내하므로 여기 오지 않는다.
              */}
              {!paymentResult.isVirtualAccount && (
                <div className="detail-row license-block">
                  <span className="label">라이선스</span>
                  {paymentResult.licenseKey ? (
                    <div className="license-body">
                      <code className="license-key-value">{paymentResult.licenseKey}</code>
                      {paymentResult.licenseValidUntil && (
                        <p className="license-meta">
                          유효기간 {new Date(paymentResult.licenseValidUntil).toLocaleDateString('ko-KR')}
                        </p>
                      )}
                      {/* 문장마다 줄을 나눈다. 개행은 CSS white-space: pre-line 이 살린다 —
                          서버가 보내는 문구(licenseError)도 평문이라 같은 방식이어야 한다. */}
                      <p className="license-meta">
                        {'이 키로 바로 이용하실 수 있습니다.\n같은 내용을 이메일로도 보냈습니다.'}
                      </p>
                    </div>
                  ) : paymentResult.licenseNotApplicable ? (
                    <div className="license-body">
                      <p className="license-note">이 상품은 라이선스가 발급되지 않습니다.</p>
                    </div>
                  ) : (
                    <div className="license-body">
                      {/* 실제로 뜨는 것은 서버가 보낸 licenseError 다. 아래 문구는 응답이
                          유실된 비정상 경우의 보루이므로 서버(PaymentService)와 같게 유지한다. */}
                      <p className="license-note license-note--pending">
                        {paymentResult.licenseError
                          || '발급 후 이메일로 전송됩니다.\n발급에 시간이 소요될 수 있습니다.'}
                      </p>
                      <p className="license-meta">
                        {'결제가 완료되었습니다.\n홈페이지 우측 상단 내 정보에서 확인할 수 있습니다.'}
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* .result-actions 는 flex + button{flex:1} 이라 두 버튼이 균등하게 나뉜다
              (좁은 화면에서는 세로로 쌓인다 — 아래 반응형 분기) */}
          <div className="result-actions">
            <button className="btn-primary" onClick={() => navigate('/bulc', { state: { activeTab: 'download' } })}>
              다운로드
            </button>
            <button className="btn-secondary" onClick={() => navigate('/')}>
              홈페이지 이동
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PaymentSuccess;
