import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { loadTossPayments, TossPaymentsInstance } from '@tosspayments/payment-sdk';
import { useAuth } from '../../context/AuthContext';
import { usePreventRefresh } from '../../hooks/useNavigationGuard';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import './Payment.css';

// 토스페이먼츠 클라이언트 키
const TOSS_CLIENT_KEY = process.env.REACT_APP_TOSS_CLIENT_KEY || 'test_ck_Z1aOwX7K8mjmkLb4W0B03yQxzvNP';

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

// 결제 수단 타입 (토스페이먼츠 결제창에서 세부 선택)
type PaymentType = 'card' | 'bank' | 'vbank' | null;

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
  // 초기 로드된 값 저장 (어떤 필드가 원래 비어있었는지 추적)
  const [initialUserInfo, setInitialUserInfo] = useState<PaymentInfo>({
    name: '',
    email: '',
    phone: '',
  });

  const [agreeTermsOfService, setAgreeTermsOfService] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);

  // 결제 수단 선택 상태 (card: 카드, bank: 계좌이체, vbank: 가상계좌)
  const [selectedPaymentType, setSelectedPaymentType] = useState<PaymentType>(null);

  // 약관 모달 상태
  const [isTermsModalOpen, setIsTermsModalOpen] = useState(false);
  const [isPrivacyModalOpen, setIsPrivacyModalOpen] = useState(false);
  const [pendingTermsAgreement, setPendingTermsAgreement] = useState(false); // 체크박스로 시작한 경우

  // 쿠폰 상태
  const [couponCode, setCouponCode] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<{
    code: string;
    name: string;
    discountType: number; // 할인율 (퍼센트)
    discountValue: number; // 할인 금액
    discountAmount: number; // 계산된 할인 금액
  } | null>(null);
  const [couponError, setCouponError] = useState('');
  const [isCheckingCoupon, setIsCheckingCoupon] = useState(false);

  // 새로고침 방지 - 상품이 선택되었거나 결제 정보가 입력되었을 때
  const hasPaymentProgress = selectedProduct !== null ||
    paymentInfo.name.length > 0 ||
    paymentInfo.phone.length > 0 ||
    selectedPaymentType !== null;
  usePreventRefresh(hasPaymentProgress);

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
          const loadedInfo = {
            email: data.email || '',
            name: data.name || '',
            phone: formatPhoneNumber(data.phone) || '',
          };
          setPaymentInfo(loadedInfo);
          // 초기값 저장 (어떤 필드가 원래 비어있었는지 추적)
          setInitialUserInfo(loadedInfo);
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
    // 전화번호는 포맷팅 적용
    if (name === 'phone') {
      setPaymentInfo(prev => ({ ...prev, [name]: formatPhoneNumberOnInput(value) }));
    } else {
      setPaymentInfo(prev => ({ ...prev, [name]: value }));
    }
  };

  // 상품 선택 핸들러
  const handleProductSelect = (product: Product) => {
    setSelectedProduct(product);
    setSelectedPlan(null); // 플랜 선택 초기화
  };

  // 결제 수단 선택 핸들러 (간소화: 모달 없이 바로 선택)
  const handlePaymentMethodSelect = (type: PaymentType) => {
    setSelectedPaymentType(type);
  };

  // 주문 ID 생성 (pricePlanId 포함)
  const generateOrderId = (planId: number) => {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `BULC_${planId}_${timestamp}_${random}`;
  };

  // 결제 수단 타입 매핑 (토스페이먼츠 SDK 지원 타입)
  type PaymentMethodType = '카드' | '계좌이체' | '가상계좌';

  const getPaymentMethodType = (): PaymentMethodType => {
    switch (selectedPaymentType) {
      case 'card':
        return '카드';
      case 'bank':
        return '계좌이체';
      case 'vbank':
        return '가상계좌';
      default:
        return '카드';
    }
  };

  // 구매자 정보 저장 (결제 시에만 호출)
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
          // 전화번호는 숫자만 추출하여 저장
          phone: cleanPhoneNumber(paymentInfo.phone),
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
    if (!selectedPaymentType) {
      alert('결제 수단을 선택해주세요.');
      return;
    }
    if (!paymentInfo.name || !paymentInfo.email || !paymentInfo.phone) {
      alert('필수 정보를 입력해주세요.');
      return;
    }
    if (!agreeTermsOfService || !agreePrivacy) {
      alert('이용약관 및 개인정보처리방침에 동의해주세요.');
      return;
    }

    // 구매자 정보 저장
    await saveUserInfo();

    try {
      const tossPayments: TossPaymentsInstance = await loadTossPayments(TOSS_CLIENT_KEY);

      const orderId = generateOrderId(selectedPlan.id);
      const paymentMethodType = getPaymentMethodType();
      const finalAmount = getFinalPrice();

      await tossPayments.requestPayment(paymentMethodType, {
        amount: finalAmount,
        orderId: orderId,
        orderName: `${selectedProduct.name} - ${selectedPlan.name}${appliedCoupon ? ` (쿠폰: ${appliedCoupon.code})` : ''}`,
        customerName: paymentInfo.name,
        customerEmail: paymentInfo.email,
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
        ...(selectedPaymentType === 'vbank' && {
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

  // 쿠폰 적용 핸들러
  const handleApplyCoupon = async () => {
    if (!couponCode.trim()) {
      setCouponError('쿠폰 코드를 입력해주세요.');
      return;
    }

    if (!selectedPlan) {
      setCouponError('요금제를 먼저 선택해주세요.');
      return;
    }

    setIsCheckingCoupon(true);
    setCouponError('');

    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch(`${API_URL}/api/promotions/validate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
        body: JSON.stringify({
          code: couponCode.trim().toUpperCase(),
          productCode: selectedProduct?.code,
          orderAmount: selectedPlan.price,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setAppliedCoupon({
          code: data.code,
          name: data.name,
          discountType: data.discountType,
          discountValue: data.discountValue,
          discountAmount: data.discountAmount,
        });
        setCouponCode('');
        setCouponError('');
      } else {
        const error = await response.json();
        setCouponError(error.message || '유효하지 않은 쿠폰입니다.');
      }
    } catch (error) {
      console.error('쿠폰 확인 오류:', error);
      setCouponError('쿠폰 확인 중 오류가 발생했습니다.');
    } finally {
      setIsCheckingCoupon(false);
    }
  };

  // 쿠폰 해제 핸들러
  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponCode('');
    setCouponError('');
  };

  // 최종 결제 금액 계산
  const getFinalPrice = () => {
    if (!selectedPlan) return 0;
    const discount = appliedCoupon?.discountAmount || 0;
    return Math.max(0, selectedPlan.price - discount);
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
              <div className="payment-methods three-options">
                <button
                  className={`method-option-btn ${selectedPaymentType === 'card' ? 'selected' : ''}`}
                  onClick={() => handlePaymentMethodSelect('card')}
                >
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/>
                      <line x1="1" y1="10" x2="23" y2="10"/>
                    </svg>
                  </div>
                  <div className="method-text">
                    <span className="method-name">신용/체크카드</span>
                    <span className="method-description">카드사 선택은 결제창에서</span>
                  </div>
                </button>

                <button
                  className={`method-option-btn ${selectedPaymentType === 'bank' ? 'selected' : ''}`}
                  onClick={() => handlePaymentMethodSelect('bank')}
                >
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="3" y="3" width="18" height="18" rx="2"/>
                      <path d="M3 9h18M9 21V9"/>
                    </svg>
                  </div>
                  <div className="method-text">
                    <span className="method-name">계좌이체</span>
                    <span className="method-description">실시간 계좌이체</span>
                  </div>
                </button>

                <button
                  className={`method-option-btn ${selectedPaymentType === 'vbank' ? 'selected' : ''}`}
                  onClick={() => handlePaymentMethodSelect('vbank')}
                >
                  <div className="method-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="2" y="4" width="20" height="16" rx="2"/>
                      <path d="M6 8h.01M6 12h.01M6 16h.01M10 8h8M10 12h8M10 16h8"/>
                    </svg>
                  </div>
                  <div className="method-text">
                    <span className="method-name">무통장입금</span>
                    <span className="method-description">가상계좌 발급</span>
                  </div>
                </button>
              </div>
            </section>

            {/* Step 4: 쿠폰 입력 */}
            <section className="payment-section coupon-section">
              <h2 className="section-title">
                <span className="step-number">4</span>
                쿠폰 적용
              </h2>
              {appliedCoupon ? (
                <div className="applied-coupon">
                  <div className="coupon-info">
                    <span className="coupon-tag">🎫</span>
                    <div className="coupon-details">
                      <span className="coupon-name">{appliedCoupon.name}</span>
                      <span className="coupon-discount">
                        {appliedCoupon.discountType}% 할인 (-{formatPrice(appliedCoupon.discountAmount)})
                      </span>
                    </div>
                  </div>
                  <button
                    className="remove-coupon-btn"
                    onClick={handleRemoveCoupon}
                    type="button"
                  >
                    ✕
                  </button>
                </div>
              ) : (
                <div className="coupon-input-wrapper">
                  <div className="coupon-input-row">
                    <input
                      type="text"
                      className={`coupon-input ${couponError ? 'error' : ''}`}
                      placeholder="쿠폰 코드를 입력하세요"
                      value={couponCode}
                      onChange={(e) => {
                        setCouponCode(e.target.value.toUpperCase());
                        setCouponError('');
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleApplyCoupon();
                        }
                      }}
                      disabled={isCheckingCoupon}
                    />
                    <button
                      className="apply-coupon-btn"
                      onClick={handleApplyCoupon}
                      disabled={isCheckingCoupon || !couponCode.trim()}
                      type="button"
                    >
                      {isCheckingCoupon ? '확인중...' : '적용'}
                    </button>
                  </div>
                  {couponError && (
                    <p className="coupon-error">{couponError}</p>
                  )}
                </div>
              )}
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
                    readOnly={!!initialUserInfo.name && userInfoLoaded}
                    className={initialUserInfo.name && userInfoLoaded ? 'readonly' : ''}
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
                    readOnly={!!initialUserInfo.email && userInfoLoaded}
                    className={initialUserInfo.email && userInfoLoaded ? 'readonly' : ''}
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
                    maxLength={13}
                    readOnly={!!initialUserInfo.phone && userInfoLoaded}
                    className={initialUserInfo.phone && userInfoLoaded ? 'readonly' : ''}
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

                  {appliedCoupon && (
                    <div className="summary-row discount">
                      <span>쿠폰 할인 ({appliedCoupon.code})</span>
                      <span>-{formatPrice(appliedCoupon.discountAmount)}</span>
                    </div>
                  )}

                  <div className="summary-divider"></div>

                  <div className="summary-row total">
                    <span>총 결제금액</span>
                    <span className="total-price">{formatPrice(getFinalPrice())}</span>
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
                    checked={agreeTermsOfService && agreePrivacy}
                    onChange={(e) => {
                      if (e.target.checked && !agreeTermsOfService && !agreePrivacy) {
                        // 체크박스 클릭 시 순차적 모달 표시
                        setPendingTermsAgreement(true);
                        setIsTermsModalOpen(true);
                      } else if (!e.target.checked) {
                        // 체크 해제 시 모든 동의 취소
                        setAgreeTermsOfService(false);
                        setAgreePrivacy(false);
                      }
                    }}
                  />
                  <span className="checkmark"></span>
                  <span className="terms-text">
                    <button
                      type="button"
                      className="terms-link"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        setIsTermsModalOpen(true);
                      }}
                    >
                      이용약관
                      {agreeTermsOfService && <span className="agreed-badge">동의</span>}
                    </button>
                    {' 및 '}
                    <button
                      type="button"
                      className="terms-link"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        setIsPrivacyModalOpen(true);
                      }}
                    >
                      개인정보처리방침
                      {agreePrivacy && <span className="agreed-badge">동의</span>}
                    </button>
                    에 동의합니다
                  </span>
                </label>
              </div>

              <button
                className={`payment-button ${selectedProduct && selectedPlan && agreeTermsOfService && agreePrivacy ? 'active' : ''}`}
                onClick={handlePayment}
                disabled={!selectedProduct || !selectedPlan || !agreeTermsOfService || !agreePrivacy}
              >
                {selectedPlan ? formatPrice(getFinalPrice()) + ' 결제하기' : '상품과 요금제를 선택해주세요'}
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

      {/* 이용약관 모달 */}
      <TermsModal
        isOpen={isTermsModalOpen}
        onClose={() => {
          setIsTermsModalOpen(false);
          setPendingTermsAgreement(false);
        }}
        onAgree={() => {
          setAgreeTermsOfService(true);
          setIsTermsModalOpen(false);
          // 체크박스로 시작한 경우 개인정보처리방침 모달도 표시
          if (pendingTermsAgreement && !agreePrivacy) {
            setIsPrivacyModalOpen(true);
          }
        }}
      />

      {/* 개인정보처리방침 모달 */}
      <PrivacyModal
        isOpen={isPrivacyModalOpen}
        onClose={() => {
          setIsPrivacyModalOpen(false);
          setPendingTermsAgreement(false);
        }}
        onAgree={() => {
          setAgreePrivacy(true);
          setIsPrivacyModalOpen(false);
          setPendingTermsAgreement(false);
        }}
      />
    </div>
  );
};

// 이용약관 모달 컴포넌트
const TermsModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onAgree: () => void;
}> = ({ isOpen, onClose, onAgree }) => {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="terms-modal" onClick={(e) => e.stopPropagation()}>
        <div className="terms-modal-header">
          <h2>이용약관</h2>
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="terms-modal-content">
          <h3>제1조 (목적)</h3>
          <p>
            본 약관은 주식회사 메테오시뮬레이션(이하 "회사")이 제공하는 BUL:C 소프트웨어 및
            관련 서비스(이하 "서비스")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및
            책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.
          </p>

          <h3>제2조 (정의)</h3>
          <p>
            1. "서비스"란 회사가 제공하는 화재 시뮬레이션 소프트웨어 BUL:C 및 이와 관련된
            모든 서비스를 의미합니다.<br/>
            2. "이용자"란 본 약관에 따라 회사가 제공하는 서비스를 이용하는 회원 및 비회원을
            말합니다.<br/>
            3. "회원"이란 회사에 개인정보를 제공하여 회원등록을 한 자로서, 회사의 정보를
            지속적으로 제공받으며 서비스를 계속적으로 이용할 수 있는 자를 말합니다.<br/>
            4. "라이선스"란 서비스를 이용할 수 있는 권한을 의미합니다.
          </p>

          <h3>제3조 (약관의 효력 및 변경)</h3>
          <p>
            1. 본 약관은 서비스를 이용하고자 하는 모든 이용자에게 적용됩니다.<br/>
            2. 회사는 필요하다고 인정되는 경우 본 약관을 변경할 수 있으며, 변경된 약관은
            서비스 내 공지사항에 게시하거나 기타 방법으로 이용자에게 공지함으로써 효력이
            발생합니다.<br/>
            3. 이용자가 변경된 약관에 동의하지 않는 경우 서비스 이용을 중단하고 이용계약을
            해지할 수 있습니다.
          </p>

          <h3>제4조 (서비스의 제공)</h3>
          <p>
            1. 회사는 다음과 같은 서비스를 제공합니다:<br/>
            &nbsp;&nbsp;- BUL:C 화재 시뮬레이션 소프트웨어<br/>
            &nbsp;&nbsp;- 소프트웨어 업데이트 및 기술 지원<br/>
            &nbsp;&nbsp;- 기타 회사가 추가 개발하거나 제휴 계약 등을 통해 이용자에게 제공하는 서비스<br/>
            2. 회사는 서비스의 품질 향상을 위해 서비스의 내용을 변경할 수 있습니다.
          </p>

          <h3>제5조 (이용계약의 성립)</h3>
          <p>
            1. 이용계약은 이용자가 본 약관에 동의하고 회원가입을 완료한 후, 회사가 이를
            승낙함으로써 성립합니다.<br/>
            2. 회사는 다음 각 호에 해당하는 경우 이용신청을 승낙하지 않을 수 있습니다:<br/>
            &nbsp;&nbsp;- 허위 정보를 기재한 경우<br/>
            &nbsp;&nbsp;- 타인의 명의를 도용한 경우<br/>
            &nbsp;&nbsp;- 기타 회사가 정한 이용신청 요건을 충족하지 못한 경우
          </p>

          <h3>제6조 (회원의 의무)</h3>
          <p>
            1. 회원은 관계법령, 본 약관의 규정, 이용안내 및 서비스와 관련하여 공지한 주의사항을
            준수하여야 합니다.<br/>
            2. 회원은 다음 행위를 하여서는 안 됩니다:<br/>
            &nbsp;&nbsp;- 타인의 정보 도용<br/>
            &nbsp;&nbsp;- 회사가 제공하는 서비스의 변경 또는 소프트웨어의 무단 복제, 배포<br/>
            &nbsp;&nbsp;- 회사 및 제3자의 저작권 등 지적재산권에 대한 침해<br/>
            &nbsp;&nbsp;- 회사 및 제3자의 명예를 손상시키거나 업무를 방해하는 행위<br/>
            &nbsp;&nbsp;- 기타 불법적이거나 부당한 행위
          </p>

          <h3>제7조 (저작권의 귀속)</h3>
          <p>
            1. 서비스에 대한 저작권 및 지적재산권은 회사에 귀속됩니다.<br/>
            2. 이용자는 서비스를 이용함으로써 얻은 정보를 회사의 사전 승낙 없이 복제, 송신,
            출판, 배포, 방송 기타 방법에 의하여 영리 목적으로 이용하거나 제3자에게 이용하게
            하여서는 안 됩니다.
          </p>

          <h3>제8조 (면책조항)</h3>
          <p>
            1. 회사는 천재지변 또는 이에 준하는 불가항력으로 인하여 서비스를 제공할 수 없는
            경우에는 서비스 제공에 관한 책임이 면제됩니다.<br/>
            2. 회사는 이용자의 귀책사유로 인한 서비스 이용의 장애에 대하여 책임을 지지 않습니다.<br/>
            3. 회사는 이용자가 서비스를 이용하여 기대하는 수익을 상실한 것에 대하여 책임을
            지지 않습니다.
          </p>

          <h3>제9조 (분쟁해결)</h3>
          <p>
            1. 회사와 이용자 간에 발생한 분쟁에 관한 소송은 대한민국 법을 적용합니다.<br/>
            2. 회사와 이용자 간에 발생한 분쟁에 관한 소송은 회사의 본사 소재지를 관할하는
            법원을 관할법원으로 합니다.
          </p>

          <h3>부칙</h3>
          <p>본 약관은 2024년 1월 1일부터 시행합니다.</p>
        </div>
        <div className="terms-modal-footer">
          <button className="terms-cancel-btn" onClick={onClose}>취소</button>
          <button className="terms-agree-btn" onClick={onAgree}>동의합니다</button>
        </div>
      </div>
    </div>
  );
};

// 개인정보처리방침 모달 컴포넌트
const PrivacyModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onAgree: () => void;
}> = ({ isOpen, onClose, onAgree }) => {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="terms-modal" onClick={(e) => e.stopPropagation()}>
        <div className="terms-modal-header">
          <h2>개인정보처리방침</h2>
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="terms-modal-content">
          <h3>1. 개인정보의 수집 및 이용 목적</h3>
          <p>
            주식회사 메테오시뮬레이션(이하 "회사")은 다음의 목적을 위하여 개인정보를 수집 및
            이용합니다. 수집한 개인정보는 다음의 목적 이외의 용도로는 사용되지 않으며,
            이용 목적이 변경될 시에는 사전동의를 구할 예정입니다.
          </p>
          <p>
            가. 서비스 제공<br/>
            &nbsp;&nbsp;- 콘텐츠 제공, 본인인증, 서비스 이용 및 결제<br/><br/>
            나. 회원 관리<br/>
            &nbsp;&nbsp;- 회원제 서비스 이용에 따른 본인확인, 개인식별, 불량회원의 부정 이용 방지와
            비인가 사용 방지, 가입 의사 확인, 분쟁 조정을 위한 기록보존, 불만처리 등 민원처리,
            고지사항 전달<br/><br/>
            다. 마케팅 및 광고에 활용 (선택 동의 시)<br/>
            &nbsp;&nbsp;- 신규 서비스 개발 및 맞춤 서비스 제공, 이벤트 및 광고성 정보 제공
          </p>

          <h3>2. 수집하는 개인정보의 항목</h3>
          <p>
            회사는 회원가입, 서비스 이용 등을 위해 아래와 같은 개인정보를 수집하고 있습니다.
          </p>
          <p>
            가. 필수항목<br/>
            &nbsp;&nbsp;- 이메일 주소, 비밀번호, 이름<br/><br/>
            나. 선택항목<br/>
            &nbsp;&nbsp;- 전화번호, 회사명, 부서, 직책<br/><br/>
            다. 서비스 이용 과정에서 자동으로 수집되는 정보<br/>
            &nbsp;&nbsp;- IP주소, 쿠키, 방문일시, 서비스 이용기록, 기기정보(OS, 브라우저 등)
          </p>

          <h3>3. 개인정보의 보유 및 이용기간</h3>
          <p>
            회사는 법령에 따른 개인정보 보유·이용기간 또는 정보주체로부터 개인정보 수집 시에
            동의 받은 개인정보 보유·이용기간 내에서 개인정보를 처리·보유합니다.
          </p>
          <p>
            가. 회원 정보: 회원 탈퇴 시까지 (단, 관계 법령에 의해 보존할 필요가 있는 경우
            해당 기간까지 보존)<br/><br/>
            나. 전자상거래에서의 계약 또는 청약철회 등에 관한 기록: 5년<br/><br/>
            다. 대금결제 및 재화 등의 공급에 관한 기록: 5년<br/><br/>
            라. 소비자의 불만 또는 분쟁처리에 관한 기록: 3년<br/><br/>
            마. 접속에 관한 기록: 3개월
          </p>

          <h3>4. 개인정보의 제3자 제공</h3>
          <p>
            회사는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 다만, 아래의
            경우에는 예외로 합니다.
          </p>
          <p>
            가. 이용자들이 사전에 동의한 경우<br/>
            나. 법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라
            수사기관의 요구가 있는 경우
          </p>

          <h3>5. 개인정보처리의 위탁</h3>
          <p>
            회사는 원활한 개인정보 업무처리를 위하여 다음과 같이 개인정보 처리업무를
            위탁하고 있습니다.
          </p>
          <p>
            가. 결제처리<br/>
            &nbsp;&nbsp;- 위탁받는 자: 토스페이먼츠<br/>
            &nbsp;&nbsp;- 위탁하는 업무의 내용: 결제 처리 및 결제 정보 관리<br/><br/>
            나. 이메일 발송<br/>
            &nbsp;&nbsp;- 위탁받는 자: Google (Gmail SMTP)<br/>
            &nbsp;&nbsp;- 위탁하는 업무의 내용: 이메일 인증 및 알림 발송
          </p>

          <h3>6. 정보주체의 권리·의무 및 행사방법</h3>
          <p>
            이용자는 개인정보주체로서 다음과 같은 권리를 행사할 수 있습니다.
          </p>
          <p>
            가. 개인정보 열람 요구<br/>
            나. 오류 등이 있을 경우 정정 요구<br/>
            다. 삭제 요구<br/>
            라. 처리정지 요구
          </p>

          <h3>7. 개인정보의 파기</h3>
          <p>
            회사는 원칙적으로 개인정보 처리목적이 달성된 경우에는 지체없이 해당 개인정보를
            파기합니다. 파기의 절차, 기한 및 방법은 다음과 같습니다.
          </p>
          <p>
            가. 파기절차: 이용자가 입력한 정보는 목적 달성 후 별도의 DB에 옮겨져 내부 방침
            및 기타 관련 법령에 따라 일정기간 저장된 후 혹은 즉시 파기됩니다.<br/><br/>
            나. 파기방법: 전자적 파일 형태의 정보는 기록을 재생할 수 없는 기술적 방법을
            사용합니다.
          </p>

          <h3>8. 개인정보의 안전성 확보 조치</h3>
          <p>
            회사는 개인정보의 안전성 확보를 위해 다음과 같은 조치를 취하고 있습니다.
          </p>
          <p>
            가. 관리적 조치: 내부관리계획 수립·시행, 정기적 직원 교육<br/>
            나. 기술적 조치: 개인정보처리시스템 등의 접근권한 관리, 접근통제시스템 설치,
            고유식별정보 등의 암호화, 보안프로그램 설치<br/>
            다. 물리적 조치: 전산실, 자료보관실 등의 접근통제
          </p>

          <h3>9. 개인정보 보호책임자</h3>
          <p>
            회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한
            정보주체의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호책임자를
            지정하고 있습니다.
          </p>
          <p>
            ▶ 개인정보 보호책임자<br/>
            &nbsp;&nbsp;- 성명: 관리자<br/>
            &nbsp;&nbsp;- 연락처: support@meteor-simulation.com
          </p>

          <h3>10. 개인정보처리방침 변경</h3>
          <p>
            이 개인정보처리방침은 2024년 1월 1일부터 적용되며, 법령 및 방침에 따른 변경내용의
            추가, 삭제 및 정정이 있는 경우에는 변경사항의 시행 7일 전부터 공지사항을 통하여
            고지할 것입니다.
          </p>
        </div>
        <div className="terms-modal-footer">
          <button className="terms-cancel-btn" onClick={onClose}>취소</button>
          <button className="terms-agree-btn" onClick={onAgree}>동의합니다</button>
        </div>
      </div>
    </div>
  );
};

export default PaymentPage;
