import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { loadTossPayments, TossPaymentsInstance } from '@tosspayments/payment-sdk';
import { useAuth } from '../../context/AuthContext';
import Header from '../../components/Header';
import './Payment.css';

// 토스페이먼츠 클라이언트 키
const TOSS_CLIENT_KEY = process.env.REACT_APP_TOSS_CLIENT_KEY || 'test_ck_Z1aOwX7K8mjmkLb4W0B03yQxzvNP';

// API URL - 동적으로 현재 호스트에 맞게 설정
const getApiUrl = () => {
  return window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : `http://${window.location.hostname}:8080`;
};
const API_URL = getApiUrl();

// 상품 타입
interface Product {
  code: string;
  name: string;
  description: string;
}

// 요금제 타입
interface PricePlan {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
}

// 결제 정보 타입
interface PaymentInfo {
  name: string;
  email: string;
  phone: string;
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

  // 상품 및 요금제 데이터
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<PricePlan | null>(null);
  const [isLoadingProducts, setIsLoadingProducts] = useState(true);
  const [isLoadingPlans, setIsLoadingPlans] = useState(false);

  // 결제 정보
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo>({
    name: '',
    email: '',
    phone: '',
  });
  const [userInfoLoaded, setUserInfoLoaded] = useState(false);

  const [agreeTerms, setAgreeTerms] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<string>('card');

  // 모달 상태
  const [isCardModalOpen, setIsCardModalOpen] = useState(false);
  const [isEasyPaymentModalOpen, setIsEasyPaymentModalOpen] = useState(false);
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [selectedEasyPayment, setSelectedEasyPayment] = useState<string | null>(null);

  // 로그인 체크 - 비로그인시 BulC Download 탭으로 이동
  useEffect(() => {
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

  // 상품 목록 로드
  useEffect(() => {
    const fetchProducts = async () => {
      try {
        const response = await fetch(`${API_URL}/api/products`);
        if (response.ok) {
          const data = await response.json();
          setProducts(data);
          // 상품이 1개면 자동 선택
          if (data.length === 1) {
            setSelectedProduct(data[0]);
          }
        }
      } catch (error) {
        console.error('상품 목록 로드 실패:', error);
      } finally {
        setIsLoadingProducts(false);
      }
    };

    fetchProducts();
  }, []);

  // 선택된 상품의 요금제 로드
  useEffect(() => {
    if (!selectedProduct) {
      setPricePlans([]);
      setSelectedPlan(null);
      return;
    }

    const fetchPlans = async () => {
      setIsLoadingPlans(true);
      try {
        const response = await fetch(`${API_URL}/api/products/${selectedProduct.code}/plans?currency=KRW`);
        if (response.ok) {
          const data = await response.json();
          setPricePlans(data);
        }
      } catch (error) {
        console.error('요금제 로드 실패:', error);
      } finally {
        setIsLoadingPlans(false);
      }
    };

    fetchPlans();
  }, [selectedProduct]);

  // 사용자 정보 로드
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!isLoggedIn || !token || userInfoLoaded) return;

    const fetchUserInfo = async () => {
      try {
        const response = await fetch(`${API_URL}/api/users/me`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setPaymentInfo(prev => ({
            ...prev,
            email: data.email || '',
            name: data.name || '',
            phone: data.phone || '',
          }));
          setUserInfoLoaded(true);
        }
      } catch (error) {
        console.error('사용자 정보 로드 실패:', error);
      }
    };

    fetchUserInfo();
  }, [isLoggedIn, userInfoLoaded]);

  // 입력 핸들러
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setPaymentInfo(prev => ({ ...prev, [name]: value }));
  };

  // 상품 선택 핸들러
  const handleProductSelect = (product: Product) => {
    setSelectedProduct(product);
    setSelectedPlan(null); // 플랜 선택 초기화
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
    setSelectedEasyPayment(null);
  };

  // 간편결제 선택 핸들러
  const handleEasyPaymentSelect = (optionId: string) => {
    setSelectedEasyPayment(optionId);
    setSelectedCard(null);
  };

  // 주문 ID 생성 (pricePlanId 포함)
  const generateOrderId = (planId: number) => {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `BULC_${planId}_${timestamp}_${random}`;
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

  // 구매자 정보 저장
  const saveUserInfo = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: paymentInfo.name,
          phone: paymentInfo.phone,
        }),
      });
    } catch (error) {
      console.error('사용자 정보 저장 실패:', error);
    }
  };

  // 결제 처리
  const handlePayment = async () => {
    if (!selectedProduct) {
      alert('상품을 선택해주세요.');
      return;
    }
    if (!selectedPlan) {
      alert('요금제를 선택해주세요.');
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

    // 구매자 정보 저장
    await saveUserInfo();

    try {
      const tossPayments: TossPaymentsInstance = await loadTossPayments(TOSS_CLIENT_KEY);

      const orderId = generateOrderId(selectedPlan.id);
      const paymentMethodType = getPaymentMethodType();

      await tossPayments.requestPayment(paymentMethodType, {
        amount: selectedPlan.price,
        orderId: orderId,
        orderName: `${selectedProduct.name} - ${selectedPlan.name}`,
        customerName: paymentInfo.name,
        customerEmail: paymentInfo.email,
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
        ...(selectedCard && {
          cardCompany: selectedCard.toUpperCase(),
        }),
        ...(selectedEasyPayment === 'vbank' && {
          validHours: 24,
        }),
      });
    } catch (error) {
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
          {/* 왼쪽: 선택 영역 */}
          <div className="payment-left">
            {/* Step 1: 상품 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">1</span>
                상품 선택
              </h2>
              {isLoadingProducts ? (
                <div className="loading-placeholder">상품 목록을 불러오는 중...</div>
              ) : (
                <div className="products-grid">
                  {products.map((product) => (
                    <div
                      key={product.code}
                      className={`product-card ${selectedProduct?.code === product.code ? 'selected' : ''}`}
                      onClick={() => handleProductSelect(product)}
                    >
                      <div className="product-header">
                        <h3 className="product-name">{product.name}</h3>
                      </div>
                      <p className="product-description">{product.description}</p>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Step 2: 요금제 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">2</span>
                요금제 선택
              </h2>
              {!selectedProduct ? (
                <div className="no-selection-message">상품을 먼저 선택해주세요.</div>
              ) : isLoadingPlans ? (
                <div className="loading-placeholder">요금제를 불러오는 중...</div>
              ) : pricePlans.length === 0 ? (
                <div className="no-selection-message">등록된 요금제가 없습니다.</div>
              ) : (
                <div className="plans-grid">
                  {pricePlans.map((plan) => (
                    <div
                      key={plan.id}
                      className={`plan-card ${selectedPlan?.id === plan.id ? 'selected' : ''}`}
                      onClick={() => setSelectedPlan(plan)}
                    >
                      <div className="plan-header">
                        <h3 className="plan-name">{plan.name}</h3>
                      </div>
                      <div className="plan-price-row">
                        <span className="current-price">{formatPrice(plan.price)}</span>
                        {plan.description && (
                          <span className="plan-duration">{plan.description}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Step 3: 결제 수단 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">3</span>
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
                </button>
              </div>
            </section>

          </div>

          {/* 오른쪽: 구매자 정보 & 결제 요약 */}
          <div className="payment-right">
            {/* 구매자 정보 */}
            <div className="buyer-info-card">
              <h3 className="buyer-info-title">구매자 정보</h3>
              <div className="buyer-form-compact">
                <div className="form-group">
                  <label>이름 <span className="required">*</span></label>
                  <input
                    type="text"
                    name="name"
                    value={paymentInfo.name}
                    onChange={handleInputChange}
                    placeholder="홍길동"
                    readOnly={!!paymentInfo.name && userInfoLoaded}
                    className={paymentInfo.name && userInfoLoaded ? 'readonly' : ''}
                  />
                </div>
                <div className="form-group">
                  <label>이메일 <span className="required">*</span></label>
                  <input
                    type="email"
                    name="email"
                    value={paymentInfo.email}
                    onChange={handleInputChange}
                    placeholder="example@email.com"
                    readOnly={!!paymentInfo.email && userInfoLoaded}
                    className={paymentInfo.email && userInfoLoaded ? 'readonly' : ''}
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
                    readOnly={!!paymentInfo.phone && userInfoLoaded}
                    className={paymentInfo.phone && userInfoLoaded ? 'readonly' : ''}
                  />
                </div>
              </div>
            </div>

            <div className="order-summary">
              <h3 className="summary-title">주문 요약</h3>

              {selectedProduct && selectedPlan ? (
                <>
                  <div className="summary-product">
                    <div className="product-info">
                      <span className="product-name">{selectedProduct.name}</span>
                      <span className="product-plan">{selectedPlan.name}</span>
                    </div>
                    <span className="product-price">{formatPrice(selectedPlan.price)}</span>
                  </div>

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
                  <p>{!selectedProduct ? '상품을 선택해주세요' : '요금제를 선택해주세요'}</p>
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
                className={`payment-button ${selectedProduct && selectedPlan && agreeTerms ? 'active' : ''}`}
                onClick={handlePayment}
                disabled={!selectedProduct || !selectedPlan || !agreeTerms}
              >
                {selectedPlan ? formatPrice(selectedPlan.price) + ' 결제하기' : '상품과 요금제를 선택해주세요'}
              </button>

              <div className="payment-security">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
                <span>안전한 결제 시스템</span>
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
