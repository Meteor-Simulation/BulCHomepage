import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { loadTossPayments, TossPaymentsInstance } from '@tosspayments/payment-sdk';
import { useAuth } from '../../context/AuthContext';
import Header from '../../components/Header';
import './Payment.css';

// 토스페이먼츠 클라이언트 키
const TOSS_CLIENT_KEY = process.env.REACT_APP_TOSS_CLIENT_KEY || 'test_ck_Z1aOwX7K8mjmkLb4W0B03yQxzvNP';

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

// 카드사 목록
const CARD_COMPANIES = [
  { id: 'shinhan', name: '신한카드', icon: '💳' },
  { id: 'samsung', name: '삼성카드', icon: '💳' },
  { id: 'kb', name: 'KB국민카드', icon: '💳' },
  { id: 'hyundai', name: '현대카드', icon: '💳' },
  { id: 'lotte', name: '롯데카드', icon: '💳' },
  { id: 'bc', name: 'BC카드', icon: '💳' },
  { id: 'hana', name: '하나카드', icon: '💳' },
  { id: 'woori', name: '우리카드', icon: '💳' },
];

// 간편결제 목록
const EASY_PAYMENT_OPTIONS = [
  { id: 'toss', name: '토스', icon: '🔵', description: '토스로 간편하게 결제' },
  { id: 'bank', name: '계좌이체', icon: '🏦', description: '실시간 계좌이체' },
  { id: 'vbank', name: '가상계좌', icon: '📋', description: '가상계좌 발급 후 입금' },
];

// 카드 결제 모달 컴포넌트
interface CardPaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (cardId: string) => void;
  selectedCard: string | null;
}

const CardPaymentModal: React.FC<CardPaymentModalProps> = ({
  isOpen,
  onClose,
  onSelect,
  selectedCard,
}) => {
  if (!isOpen) return null;

  return (
    <div className="payment-modal-overlay" onClick={onClose}>
      <div className="payment-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>카드 선택</h3>
          <button className="modal-close" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="modal-body">
          <p className="modal-description">결제하실 카드사를 선택해주세요.</p>
          <div className="card-grid">
            {CARD_COMPANIES.map((card) => (
              <button
                key={card.id}
                className={`card-option ${selectedCard === card.id ? 'selected' : ''}`}
                onClick={() => onSelect(card.id)}
              >
                <span className="card-icon">{card.icon}</span>
                <span className="card-name">{card.name}</span>
                {selectedCard === card.id && (
                  <span className="check-mark">✓</span>
                )}
              </button>
            ))}
          </div>
        </div>
        <div className="modal-footer">
          <button className="modal-btn cancel" onClick={onClose}>취소</button>
          <button
            className="modal-btn confirm"
            onClick={onClose}
            disabled={!selectedCard}
          >
            선택 완료
          </button>
        </div>
      </div>
    </div>
  );
};

// 간편결제 모달 컴포넌트
interface EasyPaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (optionId: string) => void;
  selectedOption: string | null;
}

const EasyPaymentModal: React.FC<EasyPaymentModalProps> = ({
  isOpen,
  onClose,
  onSelect,
  selectedOption,
}) => {
  if (!isOpen) return null;

  return (
    <div className="payment-modal-overlay" onClick={onClose}>
      <div className="payment-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>간편결제 선택</h3>
          <button className="modal-close" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="modal-body">
          <p className="modal-description">결제 방법을 선택해주세요.</p>
          <div className="easy-payment-list">
            {EASY_PAYMENT_OPTIONS.map((option) => (
              <button
                key={option.id}
                className={`easy-payment-option ${selectedOption === option.id ? 'selected' : ''}`}
                onClick={() => onSelect(option.id)}
              >
                <span className="option-icon">{option.icon}</span>
                <div className="option-info">
                  <span className="option-name">{option.name}</span>
                  <span className="option-desc">{option.description}</span>
                </div>
                {selectedOption === option.id && (
                  <span className="check-mark">✓</span>
                )}
              </button>
            ))}
          </div>
        </div>
        <div className="modal-footer">
          <button className="modal-btn cancel" onClick={onClose}>취소</button>
          <button
            className="modal-btn confirm"
            onClick={onClose}
            disabled={!selectedOption}
          >
            선택 완료
          </button>
        </div>
      </div>
    </div>
  );
};

const PaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, isAuthReady } = useAuth();
  const [companyInfo, setCompanyInfo] = useState<CompanyInfo | null>(null);
  const hasAlerted = useRef(false);

  // 로그인 체크 - 비로그인시 BulC Download 탭으로 이동
  useEffect(() => {
    // 인증 상태 초기화가 완료된 후에만 체크
    if (!isAuthReady) return;

    if (!isLoggedIn && !hasAlerted.current) {
      hasAlerted.current = true;
      alert('로그인이 필요한 페이지입니다.');
      navigate('/bulc', { state: { activeTab: 'download' } });
    }
  }, [isLoggedIn, isAuthReady, navigate]);

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

  // 모달 상태
  const [isCardModalOpen, setIsCardModalOpen] = useState(false);
  const [isEasyPaymentModalOpen, setIsEasyPaymentModalOpen] = useState(false);
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [selectedEasyPayment, setSelectedEasyPayment] = useState<string | null>(null);

  // 입력 핸들러
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setPaymentInfo(prev => ({ ...prev, [name]: value }));
  };

  // 결제 수단 선택 핸들러
  const handlePaymentMethodClick = (method: 'card' | 'easy') => {
    setPaymentMethod(method);
    if (method === 'card') {
      setIsCardModalOpen(true);
    } else {
      setIsEasyPaymentModalOpen(true);
    }
  };

  // 카드 선택 핸들러
  const handleCardSelect = (cardId: string) => {
    setSelectedCard(cardId);
    setSelectedEasyPayment(null); // 간편결제 선택 초기화
  };

  // 간편결제 선택 핸들러
  const handleEasyPaymentSelect = (optionId: string) => {
    setSelectedEasyPayment(optionId);
    setSelectedCard(null); // 카드 선택 초기화
  };

  // 선택된 결제 수단 표시 텍스트
  const getSelectedPaymentText = () => {
    if (selectedCard) {
      const card = CARD_COMPANIES.find(c => c.id === selectedCard);
      return card ? card.name : '';
    }
    if (selectedEasyPayment) {
      const option = EASY_PAYMENT_OPTIONS.find(o => o.id === selectedEasyPayment);
      return option ? option.name : '';
    }
    return '';
  };

  // 주문 ID 생성
  const generateOrderId = () => {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `BULC_${timestamp}_${random}`;
  };

  // 결제 수단 타입 매핑
  type PaymentMethodType = '카드' | '토스페이' | '계좌이체' | '가상계좌';

  const getPaymentMethodType = (): PaymentMethodType => {
    if (selectedCard) {
      return '카드';
    }
    if (selectedEasyPayment) {
      switch (selectedEasyPayment) {
        case 'toss':
          return '토스페이';
        case 'bank':
          return '계좌이체';
        case 'vbank':
          return '가상계좌';
        default:
          return '카드';
      }
    }
    return '카드';
  };

  // 결제 처리
  const handlePayment = async () => {
    if (!selectedPlan) {
      alert('플랜을 선택해주세요.');
      return;
    }
    if (!selectedCard && !selectedEasyPayment) {
      alert('결제 수단을 선택해주세요.');
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

    try {
      // 토스페이먼츠 SDK 로드
      const tossPayments: TossPaymentsInstance = await loadTossPayments(TOSS_CLIENT_KEY);

      const orderId = generateOrderId();
      const paymentMethodType = getPaymentMethodType();

      // 결제 요청
      await tossPayments.requestPayment(paymentMethodType, {
        amount: selectedPlan.price,
        orderId: orderId,
        orderName: `BulC ${selectedPlan.name}`,
        customerName: paymentInfo.name,
        customerEmail: paymentInfo.email,
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
        // 카드 결제 시 카드사 지정
        ...(selectedCard && {
          cardCompany: selectedCard.toUpperCase(),
        }),
        // 가상계좌 옵션
        ...(selectedEasyPayment === 'vbank' && {
          validHours: 24, // 입금 유효 시간 (24시간)
        }),
      });
    } catch (error) {
      // 사용자가 결제창을 닫은 경우
      if (error instanceof Error && error.message.includes('USER_CANCEL')) {
        console.log('사용자가 결제를 취소했습니다.');
        return;
      }
      console.error('결제 요청 오류:', error);
      alert('결제 요청 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  };

  // 가격 포맷
  const formatPrice = (price: number) => {
    return price.toLocaleString() + '원';
  };

  // 인증 상태 초기화 중이거나 비로그인시 렌더링 하지 않음
  if (!isAuthReady || !isLoggedIn) {
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
              <div className="payment-methods two-options">
                <button
                  className={`method-option-btn ${paymentMethod === 'card' && selectedCard ? 'selected' : ''}`}
                  onClick={() => handlePaymentMethodClick('card')}
                >
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/>
                      <line x1="1" y1="10" x2="23" y2="10"/>
                    </svg>
                  </div>
                  <div className="method-text">
                    <span className="method-name">신용/체크카드</span>
                    {selectedCard && (
                      <span className="method-selected">
                        {CARD_COMPANIES.find(c => c.id === selectedCard)?.name}
                      </span>
                    )}
                  </div>
                  <svg className="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M9 18l6-6-6-6"/>
                  </svg>
                </button>

                <button
                  className={`method-option-btn ${paymentMethod === 'easy' && selectedEasyPayment ? 'selected' : ''}`}
                  onClick={() => handlePaymentMethodClick('easy')}
                >
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10"/>
                      <path d="M8 12l2 2 4-4"/>
                    </svg>
                  </div>
                  <div className="method-text">
                    <span className="method-name">간편결제</span>
                    {selectedEasyPayment && (
                      <span className="method-selected">
                        {EASY_PAYMENT_OPTIONS.find(o => o.id === selectedEasyPayment)?.name}
                      </span>
                    )}
                  </div>
                  <svg className="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M9 18l6-6-6-6"/>
                  </svg>
                </button>
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

      {/* 카드 결제 모달 */}
      <CardPaymentModal
        isOpen={isCardModalOpen}
        onClose={() => setIsCardModalOpen(false)}
        onSelect={handleCardSelect}
        selectedCard={selectedCard}
      />

      {/* 간편결제 모달 */}
      <EasyPaymentModal
        isOpen={isEasyPaymentModalOpen}
        onClose={() => setIsEasyPaymentModalOpen(false)}
        onSelect={handleEasyPaymentSelect}
        selectedOption={selectedEasyPayment}
      />
    </div>
  );
};

export default PaymentPage;
