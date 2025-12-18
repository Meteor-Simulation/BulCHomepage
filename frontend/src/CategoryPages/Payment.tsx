import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Header from '../components/Header';
import './Payment.css';

// 상품 플랜 타입
interface PricePlan {
  id: string;
  name: string;
  duration: string;
  price: number;
  originalPrice?: number;
  discount?: number;
  features: string[];
  popular?: boolean;
}

// 결제 정보 타입
interface PaymentInfo {
  name: string;
  email: string;
  phone: string;
  company?: string;
}

// 회사 정보 타입
interface CompanyInfo {
  contact: {
    tel: string;
    email: string;
  };
}

const PaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [companyInfo, setCompanyInfo] = useState<CompanyInfo | null>(null);
  const hasAlerted = useRef(false);

  // 로그인 체크 - 비로그인시 BulC Download 탭으로 이동
  useEffect(() => {
    if (!isLoggedIn && !hasAlerted.current) {
      hasAlerted.current = true;
      alert('로그인이 필요한 페이지입니다.');
      navigate('/bulc', { state: { activeTab: 'download' } });
    }
  }, [isLoggedIn, navigate]);

  // 회사 정보 로드
  useEffect(() => {
    fetch('/config/company.json')
      .then(res => res.json())
      .then(data => setCompanyInfo(data))
      .catch(err => console.error('회사 정보 로드 실패:', err));
  }, []);

  // 상품 플랜 데이터
  const pricePlans: PricePlan[] = [
    {
      id: 'monthly',
      name: '월간 플랜',
      duration: '1개월',
      price: 99000,
      features: ['기본 기능 제공', '이메일 지원', '1대 기기 활성화'],
    },
    {
      id: 'annual',
      name: '연간 플랜',
      duration: '12개월',
      price: 828000,
      originalPrice: 1188000,
      discount: 30,
      features: ['전체 기능 제공', '전담 기술 지원', '5대 기기 활성화', '무료 업데이트'],
      popular: true,
    },
  ];

  // 상태 관리
  const [selectedPlan, setSelectedPlan] = useState<PricePlan | null>(null);
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo>({
    name: '',
    email: '',
    phone: '',
    company: '',
  });
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<string>('card');

  // 입력 핸들러
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setPaymentInfo(prev => ({ ...prev, [name]: value }));
  };

  // 결제 처리
  const handlePayment = () => {
    if (!selectedPlan) {
      alert('플랜을 선택해주세요.');
      return;
    }
    if (!paymentInfo.name || !paymentInfo.email || !paymentInfo.phone) {
      alert('필수 정보를 입력해주세요.');
      return;
    }
    if (!agreeTerms) {
      alert('이용약관에 동의해주세요.');
      return;
    }

    // TODO: 실제 PG 연동
    console.log('결제 정보:', {
      plan: selectedPlan,
      paymentInfo,
      paymentMethod,
    });

    alert('결제 기능은 준비 중입니다.\n\n선택하신 플랜: ' + selectedPlan.name + '\n금액: ' + selectedPlan.price.toLocaleString() + '원');
  };

  // 가격 포맷
  const formatPrice = (price: number) => {
    return price.toLocaleString() + '원';
  };

  // 비로그인시 렌더링 하지 않음
  if (!isLoggedIn) {
    return null;
  }

  return (
    <div className="payment-page">
      <Header hideUserMenu={true} />

      <div className="payment-container">
        <div className="payment-content">
          {/* 왼쪽: 플랜 선택 */}
          <div className="payment-left">
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">1</span>
                플랜 선택
              </h2>
              <div className="plans-grid">
                {pricePlans.map((plan) => (
                  <div
                    key={plan.id}
                    className={`plan-card ${selectedPlan?.id === plan.id ? 'selected' : ''} ${plan.popular ? 'popular' : ''}`}
                    onClick={() => setSelectedPlan(plan)}
                  >
                    {plan.popular && <span className="popular-badge">인기</span>}
                    {plan.discount && <span className="discount-badge">{plan.discount}% 할인</span>}

                    <div className="plan-header">
                      <h3 className="plan-name">{plan.name}</h3>
                      <p className="plan-duration">{plan.duration}</p>
                    </div>

                    <div className="plan-price">
                      {plan.originalPrice && (
                        <span className="original-price">{formatPrice(plan.originalPrice)}</span>
                      )}
                      <span className="current-price">{formatPrice(plan.price)}</span>
                    </div>

                    <ul className="plan-features">
                      {plan.features.map((feature, index) => (
                        <li key={index}>
                          <svg viewBox="0 0 24 24" fill="none" className="check-icon">
                            <path d="M20 6L9 17l-5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                          {feature}
                        </li>
                      ))}
                    </ul>

                    <div className="plan-select-indicator">
                      {selectedPlan?.id === plan.id ? (
                        <svg viewBox="0 0 24 24" fill="currentColor">
                          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                        </svg>
                      ) : (
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <circle cx="12" cy="12" r="10"/>
                        </svg>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* 결제 수단 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">2</span>
                결제 수단
              </h2>
              <div className="payment-methods">
                <label className={`method-option ${paymentMethod === 'card' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    value="card"
                    checked={paymentMethod === 'card'}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                  />
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/>
                      <line x1="1" y1="10" x2="23" y2="10"/>
                    </svg>
                  </div>
                  <span>신용/체크카드</span>
                </label>

                <label className={`method-option ${paymentMethod === 'bank' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    value="bank"
                    checked={paymentMethod === 'bank'}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                  />
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M3 21h18M3 10h18M5 6l7-3 7 3M4 10v11M20 10v11M8 14v3M12 14v3M16 14v3"/>
                    </svg>
                  </div>
                  <span>계좌이체</span>
                </label>

                <label className={`method-option ${paymentMethod === 'vbank' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    value="vbank"
                    checked={paymentMethod === 'vbank'}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                  />
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                      <line x1="8" y1="21" x2="16" y2="21"/>
                      <line x1="12" y1="17" x2="12" y2="21"/>
                    </svg>
                  </div>
                  <span>가상계좌</span>
                </label>
              </div>
            </section>

            {/* 구매자 정보 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">3</span>
                구매자 정보
              </h2>
              <div className="buyer-form">
                <div className="form-row">
                  <div className="form-group">
                    <label>이름 <span className="required">*</span></label>
                    <input
                      type="text"
                      name="name"
                      value={paymentInfo.name}
                      onChange={handleInputChange}
                      placeholder="홍길동"
                    />
                  </div>
                  <div className="form-group">
                    <label>회사명</label>
                    <input
                      type="text"
                      name="company"
                      value={paymentInfo.company}
                      onChange={handleInputChange}
                      placeholder="(주)회사명"
                    />
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>이메일 <span className="required">*</span></label>
                    <input
                      type="email"
                      name="email"
                      value={paymentInfo.email}
                      onChange={handleInputChange}
                      placeholder="example@email.com"
                    />
                  </div>
                  <div className="form-group">
                    <label>연락처 <span className="required">*</span></label>
                    <input
                      type="tel"
                      name="phone"
                      value={paymentInfo.phone}
                      onChange={handleInputChange}
                      placeholder="010-1234-5678"
                    />
                  </div>
                </div>
              </div>
            </section>
          </div>

          {/* 오른쪽: 결제 요약 */}
          <div className="payment-right">
            <div className="order-summary">
              <h3 className="summary-title">주문 요약</h3>

              {selectedPlan ? (
                <>
                  <div className="summary-product">
                    <div className="product-info">
                      <span className="product-name">BulC {selectedPlan.name}</span>
                      <span className="product-duration">{selectedPlan.duration}</span>
                    </div>
                    <span className="product-price">{formatPrice(selectedPlan.price)}</span>
                  </div>

                  {selectedPlan.originalPrice && (
                    <div className="summary-row discount">
                      <span>할인</span>
                      <span>-{formatPrice(selectedPlan.originalPrice - selectedPlan.price)}</span>
                    </div>
                  )}

                  <div className="summary-divider"></div>

                  <div className="summary-row total">
                    <span>총 결제금액</span>
                    <span className="total-price">{formatPrice(selectedPlan.price)}</span>
                  </div>

                  <div className="summary-vat">
                    VAT 포함
                  </div>
                </>
              ) : (
                <div className="no-plan-selected">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <path d="M12 8v4M12 16h.01"/>
                  </svg>
                  <p>플랜을 선택해주세요</p>
                </div>
              )}

              <div className="terms-agreement">
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={agreeTerms}
                    onChange={(e) => setAgreeTerms(e.target.checked)}
                  />
                  <span className="checkmark"></span>
                  <span>
                    <a href="/terms" target="_blank">이용약관</a> 및{' '}
                    <a href="/privacy" target="_blank">개인정보처리방침</a>에 동의합니다
                  </span>
                </label>
              </div>

              <button
                className={`payment-button ${selectedPlan && agreeTerms ? 'active' : ''}`}
                onClick={handlePayment}
                disabled={!selectedPlan || !agreeTerms}
              >
                {selectedPlan ? formatPrice(selectedPlan.price) + ' 결제하기' : '플랜을 선택해주세요'}
              </button>

              <div className="payment-security">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
                <span>안전한 결제 시스템</span>
              </div>
            </div>

            {/* 고객 지원 */}
            <div className="support-info">
              <h4>도움이 필요하신가요?</h4>
              <p>결제 관련 문의사항은 고객센터로 연락해주세요.</p>
              <div className="support-contact">
                <span>{companyInfo?.contact.email || 'simul@msimul.com'}</span>
                <span>{companyInfo?.contact.tel || '010-2747-2056'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PaymentPage;
