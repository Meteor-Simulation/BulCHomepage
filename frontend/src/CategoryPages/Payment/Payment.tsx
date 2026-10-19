import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { loadTossPayments, ANONYMOUS, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import { useAlert } from '../../components/AlertProvider';
import { POLICY_SECTIONS, PolicyLang } from '../../components/policyContent';
import Seo from '../../components/Seo';
import { usePreventRefresh } from '../../hooks/useNavigationGuard';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import PaymentConfirmModal, { ConfirmRow } from './PaymentConfirmModal';
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
//
// ## ck 가 아니라 gck 다
//
// 토스는 연동 방식마다 키 세트를 따로 발급한다. 결제위젯(주문서형·결제창형)은 gck/gsk 고,
// 구 결제창 직접 연동은 ck/sk 다. **계좌이체·가상계좌·간편결제는 위젯 세트에만 열린다** —
// 상점관리자의 "결제 UI 설정" 이 그 세트에 적용되기 때문이다. ck 로 requestPayment('계좌이체')
// 를 부르면 계약이 끝나 있어도 "Toss Payments와 계약된 결제수단이 아닙니다" 로 거부된다.
// 2026-10 까지 그 상태였고, 카드만 되던 이유가 이것이다(카드는 서버 빌링키 경로라 무관).
const TOSS_WIDGET_CLIENT_KEY =
  import.meta.env.VITE_TOSS_WIDGET_CLIENT_KEY || 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';

/** 테스트 키로 동작 중인지. 실결제가 일어나지 않으므로 화면에 표시한다. */
const IS_TOSS_TEST_KEY = TOSS_WIDGET_CLIENT_KEY.startsWith('test_');

if (IS_TOSS_TEST_KEY) {
  // 빌드 산출물에서도 확인할 수 있게 남긴다 — 운영에서 F12 로 바로 보인다
  console.warn(
    '[결제] 토스페이먼츠 테스트 키로 동작합니다. 실제 결제·정산이 일어나지 않습니다. ' +
      (import.meta.env.VITE_TOSS_WIDGET_CLIENT_KEY
        ? 'VITE_TOSS_WIDGET_CLIENT_KEY 가 테스트 키로 설정되어 있습니다.'
        : 'VITE_TOSS_WIDGET_CLIENT_KEY 가 설정되지 않아 코드 기본값(테스트 키)을 사용했습니다.')
  );
}

/** 결제위젯이 그려질 자리. 위젯은 셀렉터로 DOM 을 찾으므로 id 를 한 곳에서 관리한다. */
const WIDGET_METHODS_SELECTOR = 'toss-payment-methods';
const WIDGET_AGREEMENT_SELECTOR = 'toss-payment-agreement';

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
/**
 * 결제 갈래.
 *
 * - `card`   등록해 둔 카드(빌링키)로 서버가 즉시 승인한다. **자동갱신 구독에 쓸 수 있는 유일한 경로다.**
 * - `widget` 토스 결제위젯. 계좌이체·가상계좌·카드·토스페이·페이코가 여기에 들어 있고,
 *            노출 목록은 코드가 아니라 상점관리자의 "결제 UI 설정" 이 정한다.
 *
 * 위젯 안에도 카드가 있어 카드가 두 군데로 보이지만, 그쪽은 일회성이라 자동갱신이 안 된다.
 * 그래서 둘을 합치지 않고 갈래로 나눠 둔다.
 */
type PaymentType = 'card' | 'widget' | null;

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

  // 결제 전송 중 여부. 상태만으로는 중복 클릭을 막을 수 없어서(아래 submittingRef 참고)
  // 표시 전용으로 쓴다 — 실제 차단은 ref 와 서버 멱등키가 한다.
  const [isSubmittingPayment, setIsSubmittingPayment] = useState(false);

  // 중복 진입 차단은 ref 로 한다. 상태는 리렌더 뒤에야 반영되므로, 같은 렌더 안에서 두 번
  // 눌리면 두 핸들러가 모두 옛 값(false)을 보고 통과한다. ref 는 즉시 바뀐다.
  const submittingRef = useRef(false);

  // 결제 확인 창 — 무엇을 결제하는지 보여 주고, 전송이 시작되면 진행 상태를 그 안에서 잇는다.
  // 종전에는 버튼을 누르면 화면이 그대로 있다가 몇 초 뒤 완료 페이지로 넘어가, 그 침묵 구간에
  // 사용자가 다시 눌러 결제가 두 번 됐다.
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);

  // 결제 시도마다 새로 만드는 멱등키. 버튼이 두 번 눌려 요청이 두 번 가도 같은 키가 실려
  // 가므로 서버가 두 번째를 거부한다. 성공·실패로 끝나면 비워 다음 시도에 새 키를 받는다.
  const idempotencyKeyRef = useRef<string | null>(null);

  // 결제 갈래 — 등록 카드(빌링키) vs 토스 결제위젯
  const [selectedPaymentType, setSelectedPaymentType] = useState<PaymentType>(null);

  // 결제위젯 인스턴스. 렌더가 끝나야 requestPayment 를 부를 수 있어서 ref 로 들고 있는다.
  const widgetsRef = useRef<TossPaymentsWidgets | null>(null);
  // 그려 놓은 결제수단·약관 UI. 페이지를 떠날 때 destroy 해야 한다 — 남겨 두면 다시
  // 들어왔을 때 토스가 PAYMENT_METHODS_WIDGET_ALREADY_RENDERED 로 거부한다.
  const renderedWidgetsRef = useRef<Array<{ destroy: () => Promise<void> }>>([]);
  const [isWidgetReady, setIsWidgetReady] = useState(false);
  // 위젯을 띄우지 못한 사유. 계약·키 문제가 여기로 들어오므로 화면에 그대로 보여 준다 —
  // 삼키면 "결제수단이 안 보인다" 는 말밖에 남지 않는다.
  const [widgetError, setWidgetError] = useState<string | null>(null);

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
      // 결제 갈래는 복원하지 않는다 — 요금제가 정하므로 플랜만 복원되면 따라온다.
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

  /**
   * 결제 갈래는 **요금제가 정한다.** 사용자가 고르지 않는다.
   *
   * - 자동갱신 구독 → 등록 카드(빌링키). 갱신은 저장된 카드로만 청구할 수 있다.
   *   위젯으로 결제하면 그 건은 통과해도 다음 해에 청구할 수단이 없다.
   * - 영구 라이선스·단건 → 위젯. 갱신이 없으므로 계좌이체·가상계좌·간편결제를
   *   포함해 전부 열어 준다.
   * - USD → 등록 카드. 위젯에 해외결제(`international`)가 구성돼 있지 않다.
   *
   * 판정에 {@link isSubscriptionPlan} 을 쓰는 것이 핵심이다 — 자동갱신 여부를 정하는
   * 함수와 **같은 것**을 써야 "갱신은 켜졌는데 결제수단이 빌링키가 아닌" 조합이
   * 구조적으로 생기지 않는다.
   */
  useEffect(() => {
    if (!selectedPlan) {
      setSelectedPaymentType(null);
      return;
    }
    const mustUseBillingCard = currency === 'USD' || isSubscriptionPlan(selectedPlan);
    setSelectedPaymentType(mustUseBillingCard ? 'card' : 'widget');
    // isSubscriptionPlan 은 순수 함수라 의존성에 넣지 않는다 (매 렌더 새로 만들어진다).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPlan, currency]);

  // 주문 ID 생성 (pricePlanId 포함)
  const generateOrderId = (planId: number) => {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `BULC_${planId}_${timestamp}_${random}`;
  };

  /**
   * 확인 창에 보여 줄 결제 갈래 이름.
   *
   * 위젯은 구체적인 수단(계좌이체인지 가상계좌인지)을 위젯 안에서 고르고, 그 선택은
   * 토스 쪽에 있다. 우리가 단정해서 적으면 실제와 어긋날 수 있으므로 갈래까지만 적는다.
   */
  const getPaymentMethodLabel = (): string =>
    selectedPaymentType === 'card'
      ? t('payment.methods.savedCard')
      : t('payment.methods.widget');

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

  /**
   * 결제위젯을 띄운다.
   *
   * <p>노출되는 결제수단은 코드가 정하지 않는다 — 상점관리자의 "결제 UI 설정" 에 등록된
   * 것이 그대로 내려온다. 그래서 수단을 늘리거나 줄일 때 배포가 필요 없다.
   *
   * <p>`customerKey` 는 ANONYMOUS 를 쓴다. 회원 식별이 필요한 건 브랜드페이뿐이고,
   * 토스가 customerKey 에 이메일 같은 개인정보를 넣지 말라고 안내한다. 우리 자동갱신은
   * 위젯이 아니라 빌링키로 처리하므로 여기서 회원을 묶을 이유가 없다.
   */
  useEffect(() => {
    if (selectedPaymentType !== 'widget') return;

    // 이미 그려져 있으면 다시 그리지 않는다. 토스는 결제수단 위젯을 페이지당 하나만
    // 허용하고, 지우지 않은 채 다시 그리면 이렇게 거부한다:
    //   code=PAYMENT_METHODS_WIDGET_ALREADY_RENDERED
    //   "하나의 결제수단 위젯만을 사용할 수 있어요."
    // 갈래를 "등록한 카드" 로 바꿨다가 돌아오면 이 effect 가 다시 도는데, 그때 또 그리면
    // 결제가 통째로 막힌다. 감추는 것은 display 가 하므로 지웠다 다시 그릴 이유도 없다.
    if (widgetsRef.current) return;

    let active = true;
    setWidgetError(null);

    (async () => {
      try {
        const toss = await loadTossPayments(TOSS_WIDGET_CLIENT_KEY);
        if (!active) return;

        const widgets = toss.widgets({ customerKey: ANONYMOUS });
        // 금액을 먼저 올린 뒤에 그려야 한다 — 위젯이 금액에 따라 쓸 수 있는 수단을 가린다
        // (계좌이체 최소 200원 등). 순서가 뒤집히면 수단이 비어 보인다.
        await widgets.setAmount({ value: getFinalPrice(), currency: 'KRW' });
        if (!active) return;

        const [methodWidget, agreementWidget] = await Promise.all([
          widgets.renderPaymentMethods({ selector: `#${WIDGET_METHODS_SELECTOR}` }),
          widgets.renderAgreement({ selector: `#${WIDGET_AGREEMENT_SELECTOR}` }),
        ]);

        // 그리는 사이에 페이지를 떠났다면 붙은 것을 치운다. 안 치우면 다음에 들어왔을 때
        // 위의 ALREADY_RENDERED 에 걸린다.
        if (!active) {
          void methodWidget.destroy().catch(() => {});
          void agreementWidget.destroy().catch(() => {});
          return;
        }

        renderedWidgetsRef.current = [methodWidget, agreementWidget];
        widgetsRef.current = widgets;
        setIsWidgetReady(true);
      } catch (error) {
        if (!active) return;
        // 계약·키 문제가 여기로 들어온다. 삼키면 "결제수단이 안 보인다" 는 말만 남는다.
        const reason = (error as { message?: string } | null)?.message?.trim();
        console.error('[결제] 결제위젯 로드 실패', error);
        setWidgetError(reason || t('payment.methods.widgetFailed'));
        widgetsRef.current = null;
      }
    })();

    return () => {
      active = false;
    };
    // 금액은 아래 별도 effect 가 setAmount 로 갱신한다 — 여기서 의존하면 쿠폰을 바꿀 때마다
    // 위젯을 통째로 다시 그려 깜빡이고, 사용자가 고른 수단도 풀린다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPaymentType]);

  /** 페이지를 떠날 때 위젯을 치운다. 남겨 두면 되돌아왔을 때 ALREADY_RENDERED 가 난다. */
  useEffect(() => () => {
    renderedWidgetsRef.current.forEach((w) => { void w.destroy().catch(() => {}); });
    renderedWidgetsRef.current = [];
    widgetsRef.current = null;
  }, []);

  /** 쿠폰 적용·해제로 금액이 바뀌면 위젯에도 알린다. 다시 그리지 않고 금액만 올린다. */
  useEffect(() => {
    if (!isWidgetReady || !widgetsRef.current) return;
    widgetsRef.current
      .setAmount({ value: getFinalPrice(), currency: 'KRW' })
      .catch((e) => console.error('[결제] 위젯 금액 갱신 실패', e));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isWidgetReady, selectedPlan, appliedCoupon]);

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

  /**
   * 확인 창에 보여 줄 항목. 고객이 "내가 맞게 고른 건지" 를 한눈에 볼 수 있어야 한다.
   *
   * 쿠폰은 적용했을 때만 줄을 넣는다 — 비어 있는 줄은 확인을 방해한다.
   * 카드 결제는 어느 카드로 나가는지가 가장 중요하므로 마스킹 번호까지 보여 준다.
   */
  const buildConfirmRows = (): ConfirmRow[] => {
    const rows: ConfirmRow[] = [];
    if (selectedProduct) rows.push({ label: t('payment.confirmModal.product'), value: selectedProduct.name });
    if (selectedPlan) {
      rows.push({
        label: t('payment.confirmModal.plan'),
        value: selectedPlan.description
          ? `${selectedPlan.name} (${selectedPlan.description})`
          : selectedPlan.name,
      });
      rows.push({ label: t('payment.confirmModal.listPrice'), value: formatPrice(selectedPlan.price) });
    }
    rows.push({ label: t('payment.confirmModal.method'), value: getPaymentMethodLabel() });

    if (selectedPaymentType === 'card') {
      const card = billingCards.find((c) => c.id === selectedBillingKeyId);
      if (card) {
        rows.push({
          label: t('payment.confirmModal.card'),
          value: `${card.cardCompany || t('payment.confirmModal.cardFallback')} ${card.cardNumber || ''}`.trim(),
        });
      }
    }
    if (appliedCoupon) {
      rows.push({
        label: t('payment.confirmModal.coupon'),
        value: `${appliedCoupon.code} (−${formatPrice(selectedPlan ? selectedPlan.price - getFinalPrice() : 0)})`,
      });
    }
    rows.push({ label: t('payment.confirmModal.buyer'), value: paymentInfo.name });
    rows.push({ label: t('payment.confirmModal.email'), value: paymentInfo.email });
    rows.push({ label: t('payment.confirmModal.phone'), value: formatPhoneNumber(paymentInfo.phone) });
    return rows;
  };

  /**
   * 결제하기 버튼 — 바로 결제하지 않고 확인 창을 띄운다.
   *
   * 입력 검증을 여기서 먼저 하는 이유: 확인 창에 "무엇을 결제하는지" 를 보여 주려면 상품·요금제·
   * 결제수단·구매자 정보가 모두 채워져 있어야 한다. 빈 값으로 창을 띄우면 확인의 의미가 없다.
   */
  const handlePaymentButtonClick = () => {
    if (submittingRef.current) return;
    if (!validatePaymentInput()) return;
    setIsConfirmModalOpen(true);
  };

  /**
   * 확인 창에서 '결제하기' 를 누른 시점 — 실제 결제를 시작한다.
   *
   * 중복 진입을 ref 로 막는다. 상태(isSubmittingPayment)는 리렌더 뒤에야 반영되므로 같은 렌더
   * 안에서 두 번 눌리면 두 핸들러가 모두 옛 값을 보고 통과한다. 2026-10-07·10-12 두 번 모두
   * 상태 기반 방어가 배포돼 있었는데도 100원이 2건 청구됐다 — 최종 방어선은 서버 멱등키다.
   */
  const handleConfirmPayment = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setIsSubmittingPayment(true);

    // 이 시도에 쓸 멱등키를 만든다. 재시도 때는 새 키를 받아야 하므로 끝에서 비운다.
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = (typeof crypto !== 'undefined' && 'randomUUID' in crypto)
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }

    try {
      await runPayment();
    } finally {
      // 결제창 경로는 리다이렉트로 페이지를 떠나므로 이 복원이 쓰이지 않지만,
      // 실패·취소로 돌아왔을 때는 다시 시도할 수 있어야 한다.
      submittingRef.current = false;
      setIsSubmittingPayment(false);
      idempotencyKeyRef.current = null;
      setIsConfirmModalOpen(false);
    }
  };

  /**
   * 결제에 필요한 입력이 다 채워졌는지 본다. 확인 창을 띄우기 전에 한 번, 실제 전송 직전에
   * 한 번 더 호출한다 — 창이 열린 동안 값이 바뀌었을 수 있다.
   *
   * @returns 통과하면 true. 실패하면 사용자에게 무엇이 비었는지 알리고 false.
   */
  const validatePaymentInput = (): boolean => {
    if (!selectedProduct) {
      showAlert({ message: t('alerts.selectProduct'), type: 'warning' });
      return false;
    }
    // 이미 보유한 상품은 결제 시도 자체를 차단 (뒤로가기 등으로 도달했을 때 방어).
    // 단 내부 전용 요금제는 예외 — 라이선스를 발급하지 않아 중복 구매가 되지 않고,
    // 결제 점검을 라이선스 보유 계정으로 해야 하기 때문이다. 서버도 같은 예외를 둔다.
    if (isProductOwned(selectedProduct) && !selectedPlan?.isInternal) {
      showAlert({ message: t('payment.alreadyOwnedError'), type: 'warning' });
      return false;
    }
    if (!selectedPlan) {
      showAlert({ message: t('alerts.selectPlan'), type: 'warning' });
      return false;
    }
    if (!selectedPaymentType) {
      showAlert({ message: t('alerts.selectPaymentMethod'), type: 'warning' });
      return false;
    }
    if (!paymentInfo.name || !paymentInfo.email || !paymentInfo.phone) {
      showAlert({ message: t('alerts.missingRequiredInfo'), type: 'warning' });
      return false;
    }
    if (!agreeTermsOfService || !agreePrivacy) {
      showAlert({ message: t('alerts.agreeTermsRequired'), type: 'warning' });
      return false;
    }
    // 등록 카드 결제는 쓸 카드가 지정돼야 한다
    if (selectedPaymentType === 'card' && !selectedBillingKeyId) {
      showAlert({ message: t('payment.cards.selectRequired'), type: 'warning' });
      return false;
    }
    // 위젯은 다 그려진 뒤에만 결제를 요청할 수 있다. 못 띄운 사유가 있으면 그걸 보여 준다.
    if (selectedPaymentType === 'widget' && !isWidgetReady) {
      showAlert({
        message: widgetError || t('payment.methods.widgetLoading'),
        type: 'warning',
      });
      return false;
    }
    return true;
  };

  const runPayment = async () => {
    if (!validatePaymentInput() || !selectedProduct || !selectedPlan) return;

    // 구매자 정보 저장
    await saveUserInfo();

    // 카드 결제 → 등록 카드(빌링키)로 즉시 결제
    if (selectedPaymentType === 'card') {
      try {
        const res = await fetch(`${API_URL}/api/payments/billing`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include' as RequestCredentials,
          body: JSON.stringify({
            pricePlanId: selectedPlan.id,
            billingKeyId: selectedBillingKeyId,
            enableAutoRenew: isSubscriptionPlan(selectedPlan),
            // 같은 시도에서 요청이 두 번 가도 같은 키가 실린다 → 서버가 두 번째를 거부한다.
            // 이게 이중 청구를 실제로 막는 지점이다 (클라이언트 상태 방어는 두 번 뚫렸다).
            idempotencyKey: idempotencyKeyRef.current,
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

    // 결제위젯 — 구체적인 수단(계좌이체·가상계좌·카드·간편결제)은 위젯 안에서 고른 것을 쓴다.
    const widgets = widgetsRef.current;
    if (!widgets) {
      showAlert({ message: widgetError || t('payment.methods.widgetLoading'), type: 'error' });
      return;
    }

    try {
      const orderId = generateOrderId(selectedPlan.id);

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

      // 금액을 인자로 넘기지 않는다 — 위젯은 setAmount 로 올려둔 값을 쓴다. 두 값이 어긋나면
      // 승인에서 거부되므로, 금액의 출처를 한 곳(setAmount)으로 둔다.
      await widgets.requestPayment({
        orderId,
        orderName: `${selectedProduct.name} - ${selectedPlan.name}${appliedCoupon ? ` (${t('payment.coupon.couponLabel')}: ${appliedCoupon.code})` : ''}`,
        customerName: paymentInfo.name,
        customerEmail: paymentInfo.email,
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
      });
    } catch (error) {
      // 토스 SDK 는 Error 가 아니라 { code, message } 객체로 거부한다. code 로 판별하지
      // 않으면 사용자가 결제창을 그냥 닫은 것까지 "결제 요청 실패" 로 표시된다.
      const tossError = error as { code?: string; message?: string } | null;
      const code = tossError?.code;
      const reason = tossError?.message?.trim();

      if (code === 'USER_CANCEL') {
        return;
      }

      // 사유를 그대로 보여준다. 계약되지 않은 결제수단·최소 결제금액 미달처럼
      // "왜 안 되는지" 가 여기에만 담겨 오는데, 종전에는 통째로 삼키고 일반 문구만
      // 띄워서 카드 외 수단이 왜 실패하는지 알 수 없었다. 코드는 F12 로 확인한다.
      console.error('[결제] 결제창 호출 실패 -', code ?? '(코드 없음)', reason ?? error);
      showAlert({ message: reason || t('alerts.paymentRequestFailed'), type: 'error' });
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
              {/* 결제 방법은 고르는 것이 아니라 요금제가 정한다.
                  자동갱신 구독은 빌링키가 있어야 갱신이 되므로 등록 카드만 쓸 수 있고,
                  영구 라이선스·단건은 갱신이 없으므로 위젯의 모든 수단을 쓸 수 있다.
                  둘을 고르게 두면 "구독을 샀는데 갱신이 안 되는" 상태가 만들어진다. */}
              {!selectedPlan ? (
                <div className="no-selection-message">{t('payment.selectPlanFirst')}</div>
              ) : (
                <div className="method-fixed-notice">
                  <div className="method-fixed-name">
                    {selectedPaymentType === 'card'
                      ? t('payment.methods.savedCard')
                      : t('payment.methods.widget')}
                  </div>
                  <div className="method-fixed-reason">
                    {selectedPaymentType === 'card'
                      ? t('payment.methods.fixedBySubscription')
                      : t('payment.methods.fixedByOneTime')}
                  </div>
                </div>
              )}

              {/* 결제위젯이 그려지는 자리.
                  셀렉터로 DOM 을 찾으므로 선택 여부와 상관없이 항상 마운트해 둔다 —
                  조건부로 넣으면 렌더 시점에 요소가 없어 위젯이 붙지 못한다. 대신 선택되지
                  않았을 때 감춘다. */}
              <div
                className="toss-widget-area"
                style={{ display: selectedPaymentType === 'widget' ? 'block' : 'none' }}
              >
                {selectedPaymentType === 'widget' && !isWidgetReady && !widgetError && (
                  <div className="loading-placeholder">{t('payment.methods.widgetLoading')}</div>
                )}
                {widgetError && (
                  <div className="no-selection-message" role="alert">
                    {widgetError}
                  </div>
                )}
                <div id={WIDGET_METHODS_SELECTOR} />
                <div id={WIDGET_AGREEMENT_SELECTOR} />
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
                // 보유 중이어도 내부 전용 요금제는 결제할 수 있어야 한다 — 점검은 라이선스를
                // 이미 들고 있는 계정으로 하게 되고, 이 요금제는 license_plan_id 가 NULL 이라
                // 라이선스를 발급하지 않으므로 중복 구매가 되지 않는다. 서버도 같은 예외를 둔다.
                const ownedSelected = isProductOwned(selectedProduct) && !selectedPlan?.isInternal;
                const canPay = !!selectedProduct && !!selectedPlan && agreeTermsOfService && agreePrivacy
                  && !ownedSelected && !isSubmittingPayment;
                // 전송 중에는 진행 문구를 보여 준다. 토스 승인이 수 초 걸리는 동안 아무 변화가
                // 없으면 사용자가 "안 눌렸다" 고 판단해 다시 누른다 — 그게 이중 결제의 원인이었다.
                const label = isSubmittingPayment
                  ? t('payment.paymentButtonProcessing')
                  : ownedSelected
                    ? t('payment.paymentButtonAlreadyOwned')
                    : selectedPlan
                      ? t('payment.paymentButton', { price: formatPrice(getFinalPrice()) })
                      : t('payment.paymentButtonDisabled');
                return (
                  <button
                    className={`payment-button ${canPay ? 'active' : ''}${isSubmittingPayment ? ' payment-button--processing' : ''}`}
                    onClick={handlePaymentButtonClick}
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

      {/* 결제 확인 창 — 무엇을 결제하는지 보여 주고, 전송이 시작되면 진행 상태를 그 안에서 잇는다 */}
      <PaymentConfirmModal
        isOpen={isConfirmModalOpen}
        rows={buildConfirmRows()}
        totalLabel={formatPrice(getFinalPrice())}
        isSubmitting={isSubmittingPayment}
        onConfirm={handleConfirmPayment}
        onClose={() => setIsConfirmModalOpen(false)}
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
