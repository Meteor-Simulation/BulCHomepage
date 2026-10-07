import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { loadTossPayments, TossPaymentsInstance } from '@tosspayments/payment-sdk';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import { useAlert } from '../../components/AlertProvider';
import { POLICY_SECTIONS, PolicyLang } from '../../components/policyContent';
import Seo from '../../components/Seo';
import { usePreventRefresh } from '../../hooks/useNavigationGuard';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import Header from '../../components/Header';
import CardRegisterModal from '../../components/CardRegisterModal';
import './Payment.css';

// 토스페이먼츠 클라이언트 키 (MDP-937).
//
// 폴백을 남겨 둔다 — Vite 는 빌드 시점에 값을 박으므로, 키가 없으면 결제 페이지가 통째로
// 죽는다. Cloudflare Pages 환경변수 설정 여부를 빌드 전에 확인할 수 없어서 폴백 제거는
// "설정을 깜빡하면 결제 불가" 로 바뀐다. 그 대신 테스트 키로 동작 중임을 화면에 드러낸다.
//
// 문제는 폴백 자체가 아니라 그것이 '조용했다'는 점이었다. 테스트 키로는 결제창이 열리고
// 승인도 성공하지만 대금이 들어오지 않는다. 눈에 보이면 사고가 아니다.
const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY || 'test_ck_Z1aOwX7K8mjmkLb4W0B03yQxzvNP';

/** 테스트 키로 동작 중인지. 실결제가 일어나지 않으므로 화면에 표시한다. */
const IS_TOSS_TEST_KEY = TOSS_CLIENT_KEY.startsWith('test_');

if (IS_TOSS_TEST_KEY) {
  // 빌드 산출물에서도 확인할 수 있게 남긴다 — 운영에서 F12 로 바로 보인다
  console.warn(
    '[결제] 토스페이먼츠 테스트 키로 동작합니다. 실제 결제·정산이 일어나지 않습니다. ' +
      (import.meta.env.VITE_TOSS_CLIENT_KEY
        ? 'VITE_TOSS_CLIENT_KEY 가 테스트 키로 설정되어 있습니다.'
        : 'VITE_TOSS_CLIENT_KEY 가 설정되지 않아 코드 기본값(테스트 키)을 사용했습니다.')
  );
}

// 카드 등록(빌링 인증) 모달은 성공 시 토스가 결제 페이지를 떠나 successUrl로 리다이렉트한다.
// 등록 후 결제 페이지로 복귀했을 때 직전 결제 진행 상태를 복원하기 위한 임시 저장 키.
const CHECKOUT_STATE_KEY = 'bulc_checkout_state';

// 상품 타입
interface Product {
  id: string;
  code: string;
  name: string;
  description: string;
}

// 내 라이선스 응답 항목 (필요한 필드만)
interface MyLicense {
  productId: string;
  licenseType: 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL';
  status: 'PENDING' | 'ACTIVE' | 'EXPIRED_GRACE' | 'EXPIRED_HARD' | 'SUSPENDED' | 'REVOKED';
}

// 요금제 타입
interface PricePlan {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
  // 내부 전용(소액 결제 점검용) 요금제. 서버가 매니저 이상에게만 true 를 섞어 내려준다 —
  // 일반 고객 응답에는 내부 요금제가 아예 포함되지 않으므로 이 값으로 화면을 분기해도 안전하다.
  isInternal?: boolean;
}

// 결제 정보 타입
interface PaymentInfo {
  name: string;
  email: string;
  phone: string;
}

// 결제 수단 타입 (토스페이먼츠 결제창에서 세부 선택)
type PaymentType = 'card' | 'bank' | 'vbank' | null;

const PaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { showAlert } = useAlert();
  const { isLoggedIn, isAuthReady } = useAuth();
  const { language } = useLanguage();
  const currency = language === 'ko' ? 'KRW' : 'USD';
  const hasAlerted = useRef(false);

  // 상품 및 요금제 데이터
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<PricePlan | null>(null);
  const [isLoadingProducts, setIsLoadingProducts] = useState(true);
  const [isLoadingPlans, setIsLoadingPlans] = useState(false);

  // 현재 보유 중인(중복 구매 차단 대상) productId 집합.
  // ACTIVE/PENDING + 비-TRIAL 라이선스만 포함.
  const [ownedProductIds, setOwnedProductIds] = useState<Set<string>>(new Set());

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

  // 등록 카드(빌링키) 선택 — 카드 결제 시
  const [billingCards, setBillingCards] = useState<Array<{ id: number; cardCompany: string | null; cardNumber: string; isDefault: boolean }>>([]);
  const [selectedBillingKeyId, setSelectedBillingKeyId] = useState<number | null>(null);
  const [isLoadingCards, setIsLoadingCards] = useState(false);

  // 자체 카드 등록 모달 (MDP-758)
  const [isCardModalOpen, setIsCardModalOpen] = useState(false);

  // 새로고침 방지 - 상품이 선택되었거나 결제 정보가 입력되었을 때
  const hasPaymentProgress = selectedProduct !== null ||
    paymentInfo.name.length > 0 ||
    paymentInfo.phone.length > 0 ||
    selectedPaymentType !== null;
  usePreventRefresh(hasPaymentProgress);

  // 카드 등록(빌링 인증) 모달 성공 후 결제 페이지로 복귀 시, 직전 결제 진행 상태 복원.
  // (토스 빌링 인증은 성공 시 successUrl로 리다이렉트되어 React 상태가 초기화되므로 sessionStorage로 이어준다)
  useEffect(() => {
    const raw = sessionStorage.getItem(CHECKOUT_STATE_KEY);
    if (!raw) return;
    sessionStorage.removeItem(CHECKOUT_STATE_KEY);
    try {
      const s = JSON.parse(raw);
      if (s.product) setSelectedProduct(s.product);
      if (s.plan) setSelectedPlan(s.plan);
      if (s.paymentType) setSelectedPaymentType(s.paymentType);
      if (s.coupon) setAppliedCoupon(s.coupon);
      if (s.paymentInfo) {
        setPaymentInfo(s.paymentInfo);
        setInitialUserInfo(s.paymentInfo);
        setUserInfoLoaded(true);
      }
      if (s.agreeTermsOfService) setAgreeTermsOfService(true);
      if (s.agreePrivacy) setAgreePrivacy(true);
    } catch {
      // 복원 실패 시 무시 — 사용자가 처음부터 다시 선택
    }
  }, []);

  // 로그인 체크 - 비로그인시 BulC Download 탭으로 이동
  useEffect(() => {
    if (!isAuthReady) return;

    if (!isLoggedIn && !hasAlerted.current) {
      hasAlerted.current = true;
      showAlert({ message: t('alerts.loginRequired'), type: 'warning' });
      navigate('/bulc', { state: { activeTab: 'download' } });
    }
  }, [isLoggedIn, isAuthReady, navigate]);

  // 상품 목록 로드
  useEffect(() => {
    const fetchProducts = async () => {
      try {
        const response = await fetch(`${API_URL}/api/products`);
        if (response.ok) {
          const data = await response.json();
          setProducts(data);
        }
      } catch (error) {
        // 상품 목록 로드 실패
      } finally {
        setIsLoadingProducts(false);
      }
    };

    fetchProducts();
  }, []);

  // 내 라이선스 로드 (중복 구매 차단을 위한 보유 productId 집합 구성)
  useEffect(() => {
    if (!isLoggedIn) return;

    const fetchMyLicenses = async () => {
      try {
        const response = await fetch(`${API_URL}/api/v1/me/licenses`, {
          credentials: 'include',
        });
        if (!response.ok) return;
        const data = await response.json();
        const licenses: MyLicense[] = data.licenses || [];
        const owned = new Set<string>();
        licenses.forEach((lic) => {
          const isPaid = lic.licenseType !== 'TRIAL';
          const isBlocking = lic.status === 'ACTIVE' || lic.status === 'PENDING';
          if (isPaid && isBlocking && lic.productId) {
            owned.add(lic.productId);
          }
        });
        setOwnedProductIds(owned);
      } catch (error) {
        // 라이선스 조회 실패 — 차단 기능은 백엔드에서도 다시 검증되므로 무시
      }
    };

    fetchMyLicenses();
  }, [isLoggedIn]);

  // 상품이 1개면 자동 선택. 보유 중이어도 선택은 해 둔다 — 선택이 곧 결제 가능을 뜻하지 않고,
  // 요금제 목록을 받아 봐야 내부 전용 요금제가 있는지 알 수 있다.
  useEffect(() => {
    if (products.length === 1 && !selectedProduct) {
      setSelectedProduct(products[0]);
    }
  }, [products, selectedProduct]);

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
        // credentials 를 보내야 서버가 역할을 보고 내부 전용 요금제를 섞어 준다.
        // 비로그인/일반 회원에게는 공개 요금제만 내려오므로 응답은 종전과 같다.
        const response = await fetch(
          `${API_URL}/api/products/${selectedProduct.code}/plans?currency=${currency}`,
          { credentials: 'include' as RequestCredentials }
        );
        if (response.ok) {
          const data = await response.json();
          setPricePlans(data);
        }
      } catch (error) {
        // 요금제 로드 실패
      } finally {
        setIsLoadingPlans(false);
      }
    };

    fetchPlans();
  }, [selectedProduct, currency]);

  // 사용자 정보 로드
  useEffect(() => {
    if (!isLoggedIn || userInfoLoaded) return;

    const fetchUserInfo = async () => {
      try {
        const response = await fetch(`${API_URL}/api/users/me`, {
          credentials: 'include',
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
        // 사용자 정보 로드 실패
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

  // 이미 보유 중인 상품인지 판단 (USER + 동일 productId의 ACTIVE/PENDING 유상 라이선스)
  const isProductOwned = (product: Product | null) => {
    if (!product) return false;
    return ownedProductIds.has(product.id);
  };

  // 내부 전용 요금제 목록. 서버가 매니저 이상에게만 내려주므로 일반 고객에게는 항상 빈 배열이다.
  const internalPlans = pricePlans.filter((p) => p.isInternal);

  // 보유 중인 상품이어도 내부 전용 요금제는 고를 수 있어야 한다.
  // 결제 점검은 라이선스를 이미 들고 있는 계정으로 하게 되는데(매니저들이 대부분 보유 중),
  // 중복 보유로 화면을 막아 버리면 점검 자체가 불가능해진다. 내부 요금제는
  // license_plan_id 가 NULL 이라 라이선스를 발급하지 않으므로 중복 구매가 되지도 않는다.
  const canSelectDespiteOwned = internalPlans.length > 0;

  // 보유 중일 때는 내부 전용만 보여 준다 — 중복 구매 차단은 그대로 유지하면서 점검 경로만 연다.
  const visiblePlans = isProductOwned(selectedProduct) ? internalPlans : pricePlans;

  // 상품 선택 핸들러
  const handleProductSelect = (product: Product) => {
    // 보유 중이어도 상품 자체는 선택할 수 있다(정보 노출). 결제 가능 여부는 요금제 단위로
    // 판단한다 — 내부 전용 요금제만 보유 중에도 결제가 열린다.
    setSelectedProduct(product);
    setSelectedPlan(null); // 플랜 선택 초기화
  };

  // USD일 때 카드만 허용 → 자동 선택
  useEffect(() => {
    if (currency === 'USD') {
      setSelectedPaymentType('card');
    }
  }, [currency]);

  // 결제 수단 선택 핸들러 (간소화: 모달 없이 바로 선택)
  const handlePaymentMethodSelect = (type: PaymentType) => {
    // USD일 때 카드 외 결제 수단 선택 차단
    if (currency === 'USD' && type !== 'card') return;
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
    try {
      await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          name: paymentInfo.name,
          // 전화번호는 숫자만 추출하여 저장
          phone: cleanPhoneNumber(paymentInfo.phone),
        }),
      });
    } catch (error) {
      // 사용자 정보 저장 실패
    }
  };

  // 결제 처리
  // 카드 결제 선택 시 등록 카드(빌링키) 목록 로드
  useEffect(() => {
    if (selectedPaymentType !== 'card' || !isLoggedIn) return;
    let active = true;
    setIsLoadingCards(true);
    fetch(`${API_URL}/api/subscriptions/billing-keys`, { credentials: 'include' as RequestCredentials })
      .then((r) => (r.ok ? r.json() : { data: [] }))
      .then((d) => {
        if (!active) return;
        const cards = d.data || [];
        setBillingCards(cards);
        const def = cards.find((c: any) => c.isDefault) || cards[0];
        setSelectedBillingKeyId(def ? def.id : null);
      })
      .catch(() => { if (active) setBillingCards([]); })
      .finally(() => { if (active) setIsLoadingCards(false); });
    return () => { active = false; };
  }, [selectedPaymentType, isLoggedIn]);

  // 카드 등록 — 자체 카드 입력 모달을 띄운다 (MDP-758).
  // 토스 인증창으로 넘기지 않으므로 페이지 이탈도, 상태 저장/복원도 필요 없다.
  const handleAddCard = () => setIsCardModalOpen(true);

  // 카드 등록 성공 → 목록을 다시 불러와 방금 등록한 카드를 선택 상태로 만든다
  const handleCardRegistered = async () => {
    setIsCardModalOpen(false);
    try {
      const res = await fetch(`${API_URL}/api/subscriptions/billing-keys`, {
        credentials: 'include' as RequestCredentials,
      });
      const d = res.ok ? await res.json() : { data: [] };
      const cards = d.data || [];
      setBillingCards(cards);
      const def = cards.find((c: any) => c.isDefault) || cards[cards.length - 1];
      if (def) setSelectedBillingKeyId(def.id);
    } catch {
      // 목록 갱신 실패 — 등록 자체는 성공했으므로 새로고침하면 보인다
    }
  };

  // 구독형 플랜 여부 (자동갱신 활성화 판단)
  const isSubscriptionPlan = (plan: PricePlan | null): boolean => {
    if (!plan) return false;
    const isPremium = plan.name === 'BUL:C 3D Premium';
    const isPermanent = isPremium && (plan.description?.includes('영구') ?? false);
    return isPremium && !isPermanent;
  };

  const handlePayment = async () => {
    if (!selectedProduct) {
      showAlert({ message: t('alerts.selectProduct'), type: 'warning' });
      return;
    }
    // 이미 보유한 상품은 결제 시도 자체를 차단 (뒤로가기 등으로 도달했을 때 방어).
    // 단 내부 전용 요금제는 예외 — 라이선스를 발급하지 않아 중복 구매가 되지 않고,
    // 결제 점검을 라이선스 보유 계정으로 해야 하기 때문이다. 서버도 같은 예외를 둔다.
    if (isProductOwned(selectedProduct) && !selectedPlan?.isInternal) {
      showAlert({ message: t('payment.alreadyOwnedError'), type: 'warning' });
      return;
    }
    if (!selectedPlan) {
      showAlert({ message: t('alerts.selectPlan'), type: 'warning' });
      return;
    }
    if (!selectedPaymentType) {
      showAlert({ message: t('alerts.selectPaymentMethod'), type: 'warning' });
      return;
    }
    if (!paymentInfo.name || !paymentInfo.email || !paymentInfo.phone) {
      showAlert({ message: t('alerts.missingRequiredInfo'), type: 'warning' });
      return;
    }
    if (!agreeTermsOfService || !agreePrivacy) {
      showAlert({ message: t('alerts.agreeTermsRequired'), type: 'warning' });
      return;
    }

    // 구매자 정보 저장
    await saveUserInfo();

    // 카드 결제 → 등록 카드(빌링키)로 즉시 결제
    if (selectedPaymentType === 'card') {
      if (!selectedBillingKeyId) {
        showAlert({ message: t('payment.cards.selectRequired'), type: 'warning' });
        return;
      }
      try {
        const res = await fetch(`${API_URL}/api/payments/billing`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include' as RequestCredentials,
          body: JSON.stringify({
            pricePlanId: selectedPlan.id,
            billingKeyId: selectedBillingKeyId,
            enableAutoRenew: isSubscriptionPlan(selectedPlan),
          }),
        });
        const data = await res.json();
        if (res.ok && data.success) {
          navigate('/payment/success', { state: { billingResult: data } });
        } else {
          showAlert({ message: data.message || t('alerts.paymentRequestFailed'), type: 'error' });
        }
      } catch {
        showAlert({ message: t('alerts.paymentRequestFailed'), type: 'error' });
      }
      return;
    }

    try {
      const tossPayments: TossPaymentsInstance = await loadTossPayments(TOSS_CLIENT_KEY);

      const orderId = generateOrderId(selectedPlan.id);
      const paymentMethodType = getPaymentMethodType();
      const finalAmount = getFinalPrice();

      // 적용한 쿠폰 코드를 주문번호로 보관한다 (MDP-748).
      // 결제창을 거쳐 /payment/success 로 리다이렉트되면 이 컴포넌트의 상태는 사라지는데,
      // 승인 요청에는 "어떤 쿠폰을 썼는지" 가 필요하다. 토스가 successUrl 에 붙이는
      // 쿼리(paymentKey·orderId·amount) 에 끼워 넣는 방식은 토스 동작에 의존하므로 쓰지 않는다.
      //
      // 값이 조작돼도 안전하다 — 서버가 쿠폰을 다시 검증하고 할인액을 직접 계산한다.
      // 임의 코드를 넣으면 기대 금액이 달라져 승인이 거부된다.
      if (appliedCoupon) {
        localStorage.setItem(`couponCode_${orderId}`, appliedCoupon.code);
      }

      await tossPayments.requestPayment(paymentMethodType, {
        amount: finalAmount,
        orderId: orderId,
        orderName: `${selectedProduct.name} - ${selectedPlan.name}${appliedCoupon ? ` (${t('payment.coupon.couponLabel')}: ${appliedCoupon.code})` : ''}`,
        customerName: paymentInfo.name,
        customerEmail: paymentInfo.email,
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
        ...(selectedPaymentType === 'vbank' && {
          validHours: 24,
        }),
        ...(currency === 'USD' && {
          currency: 'USD',
          useInternationalCardOnly: 'true',
        }),
      });
    } catch (error) {
      if (error instanceof Error && error.message.includes('USER_CANCEL')) {
        return;
      }
      // 결제 요청 오류
      showAlert({ message: t('alerts.paymentRequestFailed'), type: 'error' });
    }
  };

  // 가격 포맷 (통화별)
  const formatPrice = (price: number) => {
    if (currency === 'USD') {
      return '$' + price.toLocaleString();
    }
    return price.toLocaleString() + '원';
  };

  // 쿠폰 적용 핸들러
  const handleApplyCoupon = async () => {
    if (!couponCode.trim()) {
      setCouponError(t('payment.coupon.errors.empty'));
      return;
    }

    if (!selectedPlan) {
      setCouponError(t('payment.coupon.errors.noPlan'));
      return;
    }

    setIsCheckingCoupon(true);
    setCouponError('');

    try {
      const response = await fetch(`${API_URL}/api/promotions/validate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
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
        setCouponError(error.message || t('payment.coupon.errors.invalid'));
      }
    } catch (error) {
      // 쿠폰 확인 오류
      setCouponError(t('payment.coupon.errors.checkError'));
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
      <Seo title="구매하기 | BUL:C" noindex />
      <Header hideUserMenu={true} />

      <div className="payment-container">
        {/*
          테스트 키로 동작 중임을 알린다 (MDP-937). 실키로 전환하면 자동으로 사라진다.
          테스트 키에서는 결제창이 열리고 승인도 성공하지만 대금이 들어오지 않는다 —
          고객에게도 알려야 하고, 운영자가 전환을 깜빡한 것을 바로 알아채야 한다.
        */}
        {IS_TOSS_TEST_KEY && (
          <div className="payment-testmode-banner" role="status">
            <strong>테스트 모드</strong>
            <span>
              실제 결제가 이루어지지 않습니다. 카드가 청구되지 않으며 정산도 발생하지 않습니다.
            </span>
          </div>
        )}

        <div className="payment-content">
          {/* 왼쪽: 선택 영역 */}
          <div className="payment-left">
            {/* Step 1: 상품 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">1</span>
                {t('payment.step1Title')}
              </h2>
              {isLoadingProducts ? (
                <div className="loading-placeholder">{t('payment.loadingProducts')}</div>
              ) : (
                <div className="products-grid">
                  {products.map((product) => {
                    const owned = ownedProductIds.has(product.id);
                    return (
                      <div
                        key={product.code}
                        className={`product-card ${selectedProduct?.code === product.code ? 'selected' : ''}${owned ? ' product-card--owned' : ''}`}
                        onClick={() => handleProductSelect(product)}
                      >
                        {owned && (
                          <span className="product-card__owned-badge">
                            {t('payment.alreadyOwnedBadge')}
                          </span>
                        )}
                        <div className="product-header">
                          <h3 className="product-name">{product.name}</h3>
                        </div>
                        <p className="product-description">{product.description}</p>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            {/* Step 2: 요금제 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">2</span>
                {t('payment.step2Title')}
              </h2>
              {!selectedProduct ? (
                <div className="no-selection-message">{t('payment.selectProductFirst')}</div>
              ) : isProductOwned(selectedProduct) && !canSelectDespiteOwned ? (
                <div className="no-selection-message">{t('payment.alreadyOwnedNotice')}</div>
              ) : isLoadingPlans ? (
                <div className="loading-placeholder">{t('payment.loadingPlans')}</div>
              ) : visiblePlans.length === 0 ? (
                <div className="no-selection-message">{t('payment.noPlans')}</div>
              ) : (
                <div className="plans-grid">
                  {visiblePlans.map((plan) => {
                    const isPremium = plan.name === 'BUL:C 3D Premium';
                    const isPermanent = isPremium && (plan.description?.includes('영구') ?? false);
                    const isSubscription = isPremium && !isPermanent;
                    const isInternal = plan.isInternal === true;
                    const showBadges = isSubscription || isPermanent || isInternal;
                    const isComingSoon = false;
                    return (
                      <div
                        key={plan.id}
                        className={`plan-card ${selectedPlan?.id === plan.id ? 'selected' : ''}${isComingSoon ? ' plan-card--coming-soon' : ''}`}
                        onClick={isComingSoon ? undefined : () => setSelectedPlan(plan)}
                      >
                        {isComingSoon && (
                          <div className="plan-card__overlay">
                            <span>{t('payment.comingSoon')}</span>
                          </div>
                        )}
                        <div className="plan-header">
                          <h3 className="plan-name">{plan.name}</h3>
                          {showBadges && (
                            <div className="plan-badges">
                              {isSubscription && (
                                <span className="plan-badge plan-badge--subscription">
                                  {t('payment.subscription')}
                                </span>
                              )}
                              {isPermanent && (
                                <span className="plan-badge plan-badge--permanent">
                                  {t('payment.permanent')}
                                </span>
                              )}
                              {/* 매니저 이상에게만 내려오는 점검용 요금제. 실수로 고객 응대에
                                  쓰지 않도록 눈에 띄게 표시한다. */}
                              {isInternal && (
                                <span className="plan-badge plan-badge--internal">
                                  {t('payment.internalOnly')}
                                </span>
                              )}
                            </div>
                          )}
                        </div>
                        <div className="plan-price-row">
                          <span className="current-price">{formatPrice(plan.price)}</span>
                          {plan.description && (
                            <span className="plan-duration">{plan.description}</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            {/* Step 3: 결제 수단 선택 */}
            <section className="payment-section">
              <h2 className="section-title">
                <span className="step-number">3</span>
                {t('payment.step3Title')}
              </h2>
              <div className={`payment-methods ${currency === 'USD' ? 'one-option' : 'three-options'}`}>
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
                    <span className="method-name">{t('payment.methods.creditCard')}</span>
                    <span className="method-description">{t('payment.methods.creditCardDesc')}</span>
                  </div>
                </button>

                {currency !== 'USD' && (
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
                      <span className="method-name">{t('payment.methods.bank')}</span>
                      <span className="method-description">{t('payment.methods.bankDesc')}</span>
                    </div>
                  </button>
                )}

                {currency !== 'USD' && (
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
                      <span className="method-name">{t('payment.methods.vbank')}</span>
                      <span className="method-description">{t('payment.methods.vbankDesc')}</span>
                    </div>
                  </button>
                )}
              </div>

              {/* 등록 카드 선택 (카드 결제 시) */}
              {selectedPaymentType === 'card' && isLoggedIn && (
                <div className="registered-cards">
                  {isLoadingCards ? (
                    <p className="cards-loading">{t('payment.cards.loading')}</p>
                  ) : billingCards.length === 0 ? (
                    <div className="cards-empty">
                      <p>{t('payment.cards.empty')}</p>
                      <button type="button" className="register-card-btn" onClick={handleAddCard}>
                        {t('payment.cards.register')}
                      </button>
                    </div>
                  ) : (
                    <>
                      <p className="cards-label">{t('payment.cards.selectLabel')}</p>
                      <div className="cards-list">
                        {billingCards.map((card) => (
                          <button
                            key={card.id}
                            type="button"
                            className={`card-option ${selectedBillingKeyId === card.id ? 'selected' : ''}`}
                            onClick={() => setSelectedBillingKeyId(card.id)}
                          >
                            <span className="card-option-info">
                              <span className="card-option-company">{card.cardCompany || t('payment.cards.fallback')}</span>
                              <span className="card-option-number">{card.cardNumber}</span>
                            </span>
                            {card.isDefault && <span className="card-option-default">{t('payment.cards.default')}</span>}
                          </button>
                        ))}
                      </div>
                      <button type="button" className="add-card-link" onClick={handleAddCard}>
                        + {t('payment.cards.addAnother')}
                      </button>
                    </>
                  )}
                </div>
              )}
            </section>

            {/* Step 4: 쿠폰 입력 */}
            <section className="payment-section coupon-section">
              <h2 className="section-title">
                <span className="step-number">4</span>
                {t('payment.step4Title')}
              </h2>
              {appliedCoupon ? (
                <div className="applied-coupon">
                  <div className="coupon-info">
                    <span className="coupon-tag">🎫</span>
                    <div className="coupon-details">
                      <span className="coupon-name">{appliedCoupon.name}</span>
                      <span className="coupon-discount">
                        {t('payment.coupon.discount', { percent: appliedCoupon.discountType, amount: formatPrice(appliedCoupon.discountAmount) })}
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
                      placeholder={t('payment.coupon.placeholder')}
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
                      {isCheckingCoupon ? t('payment.coupon.checking') : t('payment.coupon.apply')}
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
              <h3 className="buyer-info-title">{t('payment.buyer.title')}</h3>
              <div className="buyer-form-compact">
                <div className="form-group">
                  <label>{t('payment.buyer.name')} <span className="required">*</span></label>
                  <input
                    type="text"
                    name="name"
                    value={paymentInfo.name}
                    onChange={handleInputChange}
                    placeholder={t('payment.buyer.namePlaceholder')}
                    readOnly={!!initialUserInfo.name && userInfoLoaded}
                    className={initialUserInfo.name && userInfoLoaded ? 'readonly' : ''}
                  />
                </div>
                <div className="form-group">
                  <label>{t('payment.buyer.email')} <span className="required">*</span></label>
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
                  <label>{t('payment.buyer.phone')} <span className="required">*</span></label>
                  <input
                    type="tel"
                    name="phone"
                    value={paymentInfo.phone}
                    onChange={handleInputChange}
                    placeholder={t('payment.buyer.phonePlaceholder')}
                    maxLength={13}
                    readOnly={!!initialUserInfo.phone && userInfoLoaded}
                    className={initialUserInfo.phone && userInfoLoaded ? 'readonly' : ''}
                  />
                </div>
              </div>
            </div>

            <div className="order-summary">
              <h3 className="summary-title">{t('payment.summary.title')}</h3>

              {selectedProduct && selectedPlan ? (
                <>
                  <div className="summary-product">
                    <div className="product-info">
                      <span className="product-name">{selectedProduct.name}</span>
                      <span className="product-plan">{selectedPlan.name}</span>
                    </div>
                    <span className="product-price">{formatPrice(selectedPlan.price)}</span>
                  </div>

                  <div className="summary-row service-period">
                    <span>{t('payment.summary.period')}</span>
                    <span>{t('payment.summary.periodValue')}</span>
                  </div>

                  {appliedCoupon && (
                    <div className="summary-row discount">
                      <span>{t('payment.summary.couponDiscount')} ({appliedCoupon.code})</span>
                      <span>-{formatPrice(appliedCoupon.discountAmount)}</span>
                    </div>
                  )}

                  <div className="summary-divider"></div>

                  <div className="summary-row total">
                    <span>{t('payment.summary.total')}</span>
                    <span className="total-price">{formatPrice(getFinalPrice())}</span>
                  </div>

                  <div className="summary-vat">
                    {t('payment.summary.vatIncluded')}
                  </div>
                </>
              ) : (
                <div className="no-plan-selected">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <path d="M12 8v4M12 16h.01"/>
                  </svg>
                  <p>{!selectedProduct ? t('payment.summary.selectProduct') : t('payment.summary.selectPlan')}</p>
                </div>
              )}

              <div className="terms-agreement">
                {/* 전체 동의하기 */}
                <label className="checkbox-label checkbox-label--all">
                  <input
                    type="checkbox"
                    checked={agreeTermsOfService && agreePrivacy}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setAgreeTermsOfService(true);
                        setAgreePrivacy(true);
                      } else {
                        setAgreeTermsOfService(false);
                        setAgreePrivacy(false);
                      }
                    }}
                  />
                  <span className="checkmark"></span>
                  <span className="terms-label">{t('payment.agreement.agreeAll')}</span>
                </label>

                <div className="terms-divider"></div>

                {/* 이용약관 동의 */}
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={agreeTermsOfService}
                    onChange={(e) => {
                      if (e.target.checked && !agreeTermsOfService) {
                        setIsTermsModalOpen(true);
                      } else {
                        setAgreeTermsOfService(e.target.checked);
                      }
                    }}
                  />
                  <span className="checkmark"></span>
                  <button
                    type="button"
                    className="terms-link"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      setIsTermsModalOpen(true);
                    }}
                  >
                    {t('payment.agreement.termsOfService')}
                  </button>
                </label>

                {/* 개인정보처리방침 동의 */}
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={agreePrivacy}
                    onChange={(e) => {
                      if (e.target.checked && !agreePrivacy) {
                        setIsPrivacyModalOpen(true);
                      } else {
                        setAgreePrivacy(e.target.checked);
                      }
                    }}
                  />
                  <span className="checkmark"></span>
                  <button
                    type="button"
                    className="terms-link"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      setIsPrivacyModalOpen(true);
                    }}
                  >
                    {t('payment.agreement.privacyPolicy')}
                  </button>
                </label>
              </div>

              {(() => {
                const ownedSelected = isProductOwned(selectedProduct);
                const canPay = !!selectedProduct && !!selectedPlan && agreeTermsOfService && agreePrivacy && !ownedSelected;
                const label = ownedSelected
                  ? t('payment.paymentButtonAlreadyOwned')
                  : selectedPlan
                    ? t('payment.paymentButton', { price: formatPrice(getFinalPrice()) })
                    : t('payment.paymentButtonDisabled');
                return (
                  <button
                    className={`payment-button ${canPay ? 'active' : ''}`}
                    onClick={handlePayment}
                    disabled={!canPay}
                  >
                    {label}
                  </button>
                );
              })()}

              <div className="payment-security">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
                <span>{t('payment.secureSystem')}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 자체 카드 등록 모달 (MDP-758) */}
      {isCardModalOpen && (
        <CardRegisterModal
          onClose={() => setIsCardModalOpen(false)}
          onSuccess={handleCardRegistered}
        />
      )}

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

// 이용약관 모달 컴포넌트 (본문은 policyContent의 단일 진실 데이터 사용)
const TermsModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onAgree: () => void;
}> = ({ isOpen, onClose, onAgree }) => {
  const { t, i18n } = useTranslation();
  if (!isOpen) return null;
  const lang: PolicyLang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const sections = POLICY_SECTIONS.terms[lang];

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="terms-modal" onClick={(e) => e.stopPropagation()}>
        <div className="terms-modal-header">
          <h2>{t('payment.agreement.termsOfService')}</h2>
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="terms-modal-content">
          {sections.map((section, i) => (
            <React.Fragment key={i}>
              <h3>{section.title}</h3>
              {section.bodies.map((body, j) => (
                <p key={j} dangerouslySetInnerHTML={{ __html: body }} />
              ))}
            </React.Fragment>
          ))}
        </div>
        <div className="terms-modal-footer">
          <button className="terms-cancel-btn" onClick={onClose}>{t('payment.modal.cancel')}</button>
          <button className="terms-agree-btn" onClick={onAgree}>{t('payment.modal.agree')}</button>
        </div>
      </div>
    </div>
  );
};

// 개인정보처리방침 모달 컴포넌트 (본문은 policyContent의 단일 진실 데이터 사용)
const PrivacyModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onAgree: () => void;
}> = ({ isOpen, onClose, onAgree }) => {
  const { t, i18n } = useTranslation();
  if (!isOpen) return null;
  const lang: PolicyLang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const sections = POLICY_SECTIONS.privacy[lang];

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="terms-modal" onClick={(e) => e.stopPropagation()}>
        <div className="terms-modal-header">
          <h2>{t('payment.agreement.privacyPolicy')}</h2>
          <button className="modal-close-btn" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="terms-modal-content">
          {sections.map((section, i) => (
            <React.Fragment key={i}>
              <h3>{section.title}</h3>
              {section.bodies.map((body, j) => (
                <p key={j} dangerouslySetInnerHTML={{ __html: body }} />
              ))}
            </React.Fragment>
          ))}
        </div>
        <div className="terms-modal-footer">
          <button className="terms-cancel-btn" onClick={onClose}>{t('payment.modal.cancel')}</button>
          <button className="terms-agree-btn" onClick={onAgree}>{t('payment.modal.agree')}</button>
        </div>
      </div>
    </div>
  );
};

export default PaymentPage;
