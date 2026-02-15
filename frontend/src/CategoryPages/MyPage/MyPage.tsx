import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import Header from '../../components/Header';
import LoginModal from '../../components/LoginModal';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import './MyPage.css';

// 메뉴 타입 정의
type MenuSection = 'profile' | 'account' | 'subscription' | 'payment' | 'redeem' | 'admin-users' | 'admin-payments' | 'admin-products' | 'admin-licenses' | 'admin-promotions' | 'admin-redeem';

const VALID_MENU_SECTIONS: MenuSection[] = ['profile', 'account', 'subscription', 'payment', 'redeem', 'admin-users', 'admin-payments', 'admin-products', 'admin-licenses', 'admin-promotions', 'admin-redeem'];

// 관리자용 인터페이스
interface AdminUser {
  id: string;
  email: string;
  name: string;
  phone: string;
  rolesCode: string;
  countryCode: string;
  createdAt: string;
}

interface Product {
  id: string;
  code: string;
  name: string;
  description: string;
  isActive: boolean;
  createdAt: string;
}

interface PricePlan {
  id: number;
  productCode: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  isActive: boolean;
  licensePlanId: string | null;
  createdAt: string;
}

interface AdminLicense {
  id: string;
  licenseKey: string;
  ownerType: string;
  ownerId: string;
  status: string;
  validUntil: string;
  createdAt: string;
}

interface AdminPayment {
  id: number;
  userEmail: string;
  userName: string | null;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  paymentMethod: string | null;
  cardCompany: string | null;
  cardNumber: string | null;
  installmentMonths: number | null;
  approveNo: string | null;
  easyPayProvider: string | null;
  bankName: string | null;
  accountNumber: string | null;
  dueDate: string | null;
  depositorName: string | null;
  settlementStatus: string | null;
  createdAt: string;
}

interface Promotion {
  id: number;
  code: string;
  name: string;
  discountType: number;
  discountValue: number;
  productCode: string | null;
  usageLimit: number | null;
  usageCount: number;
  validFrom: string | null;
  validUntil: string | null;
  isActive: boolean;
  createdAt: string;
}

interface LicensePlan {
  id: string;
  productId: string;
  code: string;
  name: string;
  description: string | null;
  licenseType: 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL';
  durationDays: number;
  graceDays: number;
  maxActivations: number;
  maxConcurrentSessions: number;
  allowOfflineDays: number;
  active: boolean;
  deleted: boolean;
  entitlements: string[];
  createdAt: string;
  updatedAt: string;
}

interface RedeemCampaign {
  id: string;
  name: string;
  description: string | null;
  productId: string;
  productName: string;
  licensePlanId: string;
  planName: string;
  usageCategory: string;
  seatLimit: number | null;
  seatsUsed: number;
  perUserLimit: number;
  status: 'ACTIVE' | 'PAUSED' | 'ENDED';
  validFrom: string | null;
  validUntil: string | null;
  codeCount: number;
  createdAt: string;
}

interface RedeemCodeItem {
  id: string;
  campaignId: string;
  codeType: 'RANDOM' | 'CUSTOM';
  maxRedemptions: number;
  currentRedemptions: number;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
}

interface UserInfo {
  email: string;
  name: string;
  phone: string;
  country: string;
  language: string | null;
}

interface License {
  id: string;
  productId: string;
  productName: string | null;
  planName: string | null;
  licenseType: string;
  status: string;
  validFrom: string;
  validUntil: string;
  entitlements: string[];
  usedActivations: number;
  maxActivations: number;
}

interface Activation {
  id: string;
  deviceFingerprint: string;
  status: string;
  activatedAt: string;
  lastSeenAt: string;
  clientVersion: string;
  clientOs: string;
  lastIp: string;
  deviceDisplayName: string | null;
}

interface Subscription {
  id: number;
  productCode: string;
  productName: string | null;
  pricePlanId: number;
  pricePlanName: string | null;
  price: number;
  currency: string;
  status: string;
  startDate: string;
  endDate: string;
  autoRenew: boolean;
  billingCycle: string | null;
  nextBillingDate: string | null;
  createdAt: string;
}

interface BillingKey {
  id: number;
  cardCompany: string;
  cardNumber: string;
  cardType: string;
  ownerType: string;
  isDefault: boolean;
  createdAt: string;
}

const COUNTRIES = [
  { code: 'KR', name: '대한민국', currency: 'KRW' },
  { code: 'CN', name: '중국', currency: 'USD' },
  { code: 'JP', name: '일본', currency: 'USD' },
  { code: 'US', name: '미국', currency: 'USD' },
  { code: 'EU', name: '유럽', currency: 'USD' },
  { code: 'RU', name: '러시아', currency: 'USD' },
];

const LANGUAGES = [
  { code: 'ko', name: '한국어' },
  { code: 'en', name: 'English' },
];

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { isLoggedIn, isAuthReady, logout, user, isAdmin } = useAuth();
  const { changeLanguage: changeGlobalLanguage } = useLanguage();


  // 사용자 정보
  const [userInfo, setUserInfo] = useState<UserInfo>({ email: '', name: '', phone: '', country: 'KR', language: null });
  const [isLoading, setIsLoading] = useState(true);

  // 라이선스 정보
  const [licenses, setLicenses] = useState<License[]>([]);
  const [isLoadingLicenses, setIsLoadingLicenses] = useState(false);

  // 기기 목록 (라이선스별)
  const [expandedLicenseId, setExpandedLicenseId] = useState<string | null>(null);
  const [activations, setActivations] = useState<Record<string, Activation[]>>({});
  const [isLoadingActivations, setIsLoadingActivations] = useState<string | null>(null);

  // 구독 정보
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [isLoadingSubscriptions, setIsLoadingSubscriptions] = useState(false);

  // 등록된 카드 (빌링키)
  const [billingKeys, setBillingKeys] = useState<BillingKey[]>([]);
  const [isLoadingBillingKeys, setIsLoadingBillingKeys] = useState(false);

  // 프로필 수정 모드
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [editName, setEditName] = useState('');
  const [editPhone, setEditPhone] = useState('');

  // 비밀번호 변경
  const [isEditingPassword, setIsEditingPassword] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // 설정 (국가/언어)
  const [isEditingSettings, setIsEditingSettings] = useState(false);
  const [selectedCountry, setSelectedCountry] = useState('KR');
  const [selectedLanguage, setSelectedLanguage] = useState(() => {
    return localStorage.getItem('language') || 'ko';
  });
  // 임시 설정값 (저장 전)
  const [tempCountry, setTempCountry] = useState('KR');
  const [tempLanguage, setTempLanguage] = useState(() => {
    return localStorage.getItem('language') || 'ko';
  });

  // 메시지
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // 개발자 미리보기 (관리자 전용)
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  // 구독 테스트 (개발 환경용)
  const [isTestMode, setIsTestMode] = useState(false);
  const [testLoading, setTestLoading] = useState<string | null>(null);

  // 계정 삭제
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteModalError, setDeleteModalError] = useState('');

  // 로그인 모달
  const [loginModalOpen, setLoginModalOpen] = useState(false);

  // URL query parameter로 초기 탭 설정
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  const [activeMenu, setActiveMenu] = useState<MenuSection>(
    initialTab && VALID_MENU_SECTIONS.includes(initialTab as MenuSection)
      ? (initialTab as MenuSection)
      : 'profile'
  );

  // 관리자 데이터 상태
  const [adminUsers, setAdminUsers] = useState<AdminUser[]>([]);
  const [adminLicenses, setAdminLicenses] = useState<AdminLicense[]>([]);
  const [adminPayments, setAdminPayments] = useState<AdminPayment[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [licensePlans, setLicensePlans] = useState<LicensePlan[]>([]);
  const [isAdminLoading, setIsAdminLoading] = useState(false);

  // 리딤 코드 상태
  const [redeemCode, setRedeemCode] = useState('');
  const [redeemResult, setRedeemResult] = useState<{
    success: boolean;
    licenseId?: string;
    licenseKey?: string;
    productName?: string;
    planName?: string;
    validUntil?: string;
    errorMessage?: string;
  } | null>(null);
  const [isRedeeming, setIsRedeeming] = useState(false);

  // 리딤 캠페인 관리자 상태
  const [redeemCampaigns, setRedeemCampaigns] = useState<RedeemCampaign[]>([]);
  const [isRedeemCampaignModalOpen, setIsRedeemCampaignModalOpen] = useState(false);
  const [editingCampaign, setEditingCampaign] = useState<RedeemCampaign | null>(null);
  const [redeemCampaignForm, setRedeemCampaignForm] = useState({
    name: '', description: '', productId: '', licensePlanId: '',
    usageCategory: 'COMMERCIAL', seatLimit: '', perUserLimit: '1',
    validFrom: '', validUntil: '',
  });
  const [selectedCampaignForCodes, setSelectedCampaignForCodes] = useState<RedeemCampaign | null>(null);
  const [redeemCodes, setRedeemCodes] = useState<RedeemCodeItem[]>([]);
  const [isCodeGenerateModalOpen, setIsCodeGenerateModalOpen] = useState(false);
  const [codeGenerateForm, setCodeGenerateForm] = useState({
    codeType: 'RANDOM' as 'RANDOM' | 'CUSTOM',
    customCode: '', count: '10', maxRedemptions: '1', expiresAt: '',
  });
  const [generatedCodes, setGeneratedCodes] = useState<string[]>([]);
  const [isGeneratedCodesModalOpen, setIsGeneratedCodesModalOpen] = useState(false);

  // 라이선스 플랜 모달 상태
  const [isLicensePlanModalOpen, setIsLicensePlanModalOpen] = useState(false);
  const [editingLicensePlan, setEditingLicensePlan] = useState<LicensePlan | null>(null);
  const [licensePlanForm, setLicensePlanForm] = useState({
    productId: '',
    code: '',
    name: '',
    description: '',
    licenseType: 'SUBSCRIPTION' as 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL',
    durationDays: 365,
    graceDays: 7,
    maxActivations: 1,
    maxConcurrentSessions: 1,
    allowOfflineDays: 0,
    entitlements: '',
  });

  // 라이선스 플랜 검색 (기존 admin 검색과 독립)
  const [licensePlanSearchQuery, setLicensePlanSearchQuery] = useState('');
  const [licensePlanAppliedSearch, setLicensePlanAppliedSearch] = useState('');
  const [licensePlanCurrentPage, setLicensePlanCurrentPage] = useState(1);

  // 관리자 검색/페이징
  const [adminSearchQuery, setAdminSearchQuery] = useState('');
  const [adminAppliedSearch, setAdminAppliedSearch] = useState('');
  const [adminCurrentPage, setAdminCurrentPage] = useState(1);
  const ADMIN_ITEMS_PER_PAGE = 10;

  // 로그인 체크 - 미로그인 시 에러 페이지로 리다이렉트
  useEffect(() => {
    if (isAuthReady && !isLoggedIn) {
      navigate('/error', { state: { errorCode: 401 } });
    }
  }, [isAuthReady, isLoggedIn, navigate]);

  // 탭 변경 시 URL query parameter 동기화
  const handleMenuChange = (menu: MenuSection) => {
    setActiveMenu(menu);
    setSearchParams(menu === 'profile' ? {} : { tab: menu }, { replace: true });
  };

  // 사용자 정보 로드
  useEffect(() => {
    const fetchUserInfo = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      try {
        const response = await fetch(`${API_URL}/api/users/me`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setUserInfo(data);
          setEditName(data.name || '');
          // 전화번호를 포맷팅하여 표시
          setEditPhone(formatPhoneNumber(data.phone) || '');
          setSelectedCountry(data.country || 'KR');
          // DB에 언어 설정이 있으면 적용 (우선순위: DB > localStorage > IP)
          if (data.language) {
            setSelectedLanguage(data.language);
            setTempLanguage(data.language);
            changeGlobalLanguage(data.language);
          }
        }
      } catch (error) {
        console.error('사용자 정보 로드 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    if (isLoggedIn) {
      fetchUserInfo();
    }
  }, [isLoggedIn]);

  // 라이선스 정보 로드
  useEffect(() => {
    const fetchLicenses = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      setIsLoadingLicenses(true);
      try {
        const response = await fetch(`${API_URL}/api/v1/me/licenses`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setLicenses(data.licenses || []);
        }
      } catch (error) {
        console.error('라이선스 정보 로드 실패:', error);
      } finally {
        setIsLoadingLicenses(false);
      }
    };

    if (isLoggedIn) {
      fetchLicenses();
    }
  }, [isLoggedIn]);

  // 구독 정보 로드
  useEffect(() => {
    const fetchSubscriptions = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      setIsLoadingSubscriptions(true);
      try {
        const response = await fetch(`${API_URL}/api/subscriptions`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setSubscriptions(data.data || []);
        }
      } catch (error) {
        console.error('구독 정보 로드 실패:', error);
      } finally {
        setIsLoadingSubscriptions(false);
      }
    };

    if (isLoggedIn) {
      fetchSubscriptions();
    }
  }, [isLoggedIn]);

  // 등록된 카드 로드
  useEffect(() => {
    const fetchBillingKeys = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) return;

      setIsLoadingBillingKeys(true);
      try {
        const response = await fetch(`${API_URL}/api/subscriptions/billing-keys`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          const data = await response.json();
          setBillingKeys(data.data || []);
        }
      } catch (error) {
        console.error('등록된 카드 로드 실패:', error);
      } finally {
        setIsLoadingBillingKeys(false);
      }
    };

    if (isLoggedIn) {
      fetchBillingKeys();
    }
  }, [isLoggedIn]);

  // 비밀번호 유효성 검사
  const validatePassword = (password: string): string | null => {
    if (password.length < 8) {
      return '비밀번호는 8자 이상이어야 합니다.';
    }
    if (!/[a-zA-Z]/.test(password)) {
      return '비밀번호는 영문을 포함해야 합니다.';
    }
    if (!/[0-9]/.test(password)) {
      return '비밀번호는 숫자를 포함해야 합니다.';
    }
    if (!/[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(password)) {
      return '비밀번호는 특수문자를 포함해야 합니다.';
    }
    return null;
  };

  // 프로필 저장
  const handleSaveProfile = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: editName,
          // 저장할 때는 숫자만 추출하여 저장
          phone: cleanPhoneNumber(editPhone),
          country: userInfo.country,
          language: tempLanguage,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setUserInfo(data);
        // 언어 저장 (로컬 + i18n + context)
        setSelectedLanguage(tempLanguage);
        changeGlobalLanguage(tempLanguage);
        setIsEditingProfile(false);
        showSuccess(t('myPage.profileSaved'));
      } else {
        showError('프로필 저장에 실패했습니다.');
      }
    } catch (error) {
      showError('프로필 저장 중 오류가 발생했습니다.');
    }
  };

  // 설정 편집 시작 (결제 통화)
  const handleStartEditSettings = () => {
    setTempCountry(selectedCountry);
    setIsEditingSettings(true);
  };

  // 설정 저장 (결제 통화)
  const handleSaveSettings = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      // 국가/통화 저장 (서버)
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: userInfo.name,
          phone: userInfo.phone,
          country: tempCountry,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setUserInfo(data);
        setSelectedCountry(tempCountry);
        setIsEditingSettings(false);
        showSuccess('결제 통화가 저장되었습니다.');
      } else {
        showError('설정 저장에 실패했습니다.');
      }
    } catch (error) {
      showError('설정 저장 중 오류가 발생했습니다.');
    }
  };

  // 설정 취소 (결제 통화)
  const handleCancelSettings = () => {
    setTempCountry(selectedCountry);
    setIsEditingSettings(false);
  };

  // 비밀번호 변경
  const handleChangePassword = async () => {
    const validationError = validatePassword(newPassword);
    if (validationError) {
      setPasswordError(validationError);
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/users/me/password`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setIsEditingPassword(false);
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
        setPasswordError('');
        // 비밀번호 변경 후 로그아웃
        alert('비밀번호가 변경되었습니다. 보안을 위해 다시 로그인해주세요.');
        logout();
        navigate('/');
        return;
      } else {
        setPasswordError(data.message || '비밀번호 변경에 실패했습니다.');
      }
    } catch (error) {
      setPasswordError('비밀번호 변경 중 오류가 발생했습니다.');
    }
  };

  const handleCancelProfile = () => {
    setEditName(userInfo.name || '');
    setEditPhone(formatPhoneNumber(userInfo.phone) || '');
    setTempLanguage(selectedLanguage);
    setIsEditingProfile(false);
  };

  const handleCancelPassword = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordError('');
    setShowCurrentPassword(false);
    setShowNewPassword(false);
    setShowConfirmPassword(false);
    setIsEditingPassword(false);
  };

  // 기기 목록 토글
  const handleToggleDeviceList = async (licenseId: string) => {
    if (expandedLicenseId === licenseId) {
      setExpandedLicenseId(null);
      return;
    }

    setExpandedLicenseId(licenseId);

    // 이미 fetch된 데이터가 있으면 재요청하지 않음
    if (activations[licenseId]) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setIsLoadingActivations(licenseId);
    try {
      const response = await fetch(`${API_URL}/api/v1/licenses/${licenseId}`, {
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        const data = await response.json();
        setActivations(prev => ({ ...prev, [licenseId]: data.activations || [] }));
      }
    } catch (error) {
      console.error('기기 목록 로드 실패:', error);
    } finally {
      setIsLoadingActivations(null);
    }
  };

  // 기기 목록 재조회
  const refreshActivations = async (licenseId: string) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/v1/licenses/${licenseId}`, {
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        const data = await response.json();
        setActivations(prev => ({ ...prev, [licenseId]: data.activations || [] }));
      }
    } catch (error) {
      console.error('기기 목록 재조회 실패:', error);
    }
  };

  // 기기 비활성화
  const handleDeactivateDevice = async (licenseId: string, deviceFingerprint: string) => {
    if (!window.confirm('이 기기를 해제하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(
        `${API_URL}/api/v1/licenses/${licenseId}/activations/${deviceFingerprint}`,
        {
          method: 'DELETE',
          headers: { 'Authorization': `Bearer ${token}` },
        }
      );
      if (response.ok) {
        showSuccess('기기가 해제되었습니다.');
        // 기기 목록 재조회
        await refreshActivations(licenseId);
        // 라이선스 목록 재조회 (usedActivations 갱신)
        const licResponse = await fetch(`${API_URL}/api/v1/me/licenses`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        if (licResponse.ok) {
          const licData = await licResponse.json();
          setLicenses(licData.licenses || []);
        }
      } else {
        showError('기기 해제에 실패했습니다.');
      }
    } catch (error) {
      console.error('기기 비활성화 오류:', error);
      showError('기기 해제 중 오류가 발생했습니다.');
    }
  };

  // 로그아웃
  const handleLogout = () => {
    logout();
    navigate('/');
  };

  // 계정 삭제
  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== '계정삭제') {
      setDeleteModalError('확인 문구를 정확히 입력해주세요.');
      return;
    }

    setIsDeleting(true);
    setDeleteModalError('');
    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch(`${API_URL}/api/users/me/deactivate`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        // 성공 시에만 모달 닫기
        setIsDeleteModalOpen(false);
        setDeleteConfirmText('');
        logout();
        navigate('/', { state: { message: '계정이 삭제되었습니다.' } });
      } else {
        const errorData = await response.json();
        // 에러 시 모달 안에 에러 표시 (모달 닫지 않음)
        setDeleteModalError(errorData.message || '계정 삭제에 실패했습니다.');
      }
    } catch (error) {
      setDeleteModalError('계정 삭제 중 오류가 발생했습니다.');
    } finally {
      setIsDeleting(false);
    }
  };

  // 자동 갱신 토글
  const handleToggleAutoRenew = async (subscriptionId: number, currentState: boolean) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      if (currentState) {
        // 자동 갱신 비활성화
        const response = await fetch(`${API_URL}/api/subscriptions/${subscriptionId}/auto-renew`, {
          method: 'DELETE',
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });
        if (response.ok) {
          setSubscriptions(prev => prev.map(sub =>
            sub.id === subscriptionId ? { ...sub, autoRenew: false, nextBillingDate: null } : sub
          ));
          showSuccess('자동 갱신이 비활성화되었습니다.');
        } else {
          showError('자동 갱신 비활성화에 실패했습니다.');
        }
      } else {
        // 자동 갱신 활성화 - 기본 결제 수단 사용
        const defaultCard = billingKeys.find(b => b.isDefault);
        if (!defaultCard) {
          showError('기본 결제 수단을 먼저 등록해주세요.');
          return;
        }
        const response = await fetch(
          `${API_URL}/api/subscriptions/${subscriptionId}/auto-renew?billingKeyId=${defaultCard.id}&billingCycle=YEARLY`,
          {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`,
            },
          }
        );
        if (response.ok) {
          const data = await response.json();
          setSubscriptions(prev => prev.map(sub =>
            sub.id === subscriptionId ? data.data : sub
          ));
          showSuccess('자동 갱신이 활성화되었습니다.');
        } else {
          showError('자동 갱신 활성화에 실패했습니다.');
        }
      }
    } catch (error) {
      console.error('자동 갱신 토글 오류:', error);
      showError('자동 갱신 설정 중 오류가 발생했습니다.');
    }
  };

  // 기본 결제 수단 변경
  const handleSetDefaultCard = async (billingKeyId: number) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/subscriptions/billing-keys/${billingKeyId}/default`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });
      if (response.ok) {
        setBillingKeys(prev => prev.map(b => ({
          ...b,
          isDefault: b.id === billingKeyId
        })));
        showSuccess('기본 결제 수단이 변경되었습니다.');
      } else {
        showError('기본 결제 수단 변경에 실패했습니다.');
      }
    } catch (error) {
      console.error('기본 결제 수단 변경 오류:', error);
      showError('기본 결제 수단 변경 중 오류가 발생했습니다.');
    }
  };

  // 카드 삭제
  const handleDeleteCard = async (billingKeyId: number) => {
    if (!window.confirm('이 카드를 삭제하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/subscriptions/billing-keys/${billingKeyId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });
      if (response.ok) {
        setBillingKeys(prev => prev.filter(b => b.id !== billingKeyId));
        showSuccess('카드가 삭제되었습니다.');
      } else {
        showError('카드 삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('카드 삭제 오류:', error);
      showError('카드 삭제 중 오류가 발생했습니다.');
    }
  };

  // === 구독 테스트 함수들 (개발 환경 전용) ===

  // 구독 만료일을 N일 후로 설정
  const handleSimulateNearExpiry = async (subscriptionId: number, days: number = 3) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setTestLoading(`simulate-${subscriptionId}`);
    try {
      const response = await fetch(
        `${API_URL}/api/test/subscriptions/${subscriptionId}/simulate-near-expiry?daysUntilExpiry=${days}`,
        {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` },
        }
      );
      if (response.ok) {
        const data = await response.json();
        showSuccess(`구독 종료일이 ${days}일 후로 설정되었습니다.`);
        // 구독 목록 새로고침
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        if (subsResponse.ok) {
          const subsData = await subsResponse.json();
          setSubscriptions(subsData.data || []);
        }
      } else {
        const err = await response.json();
        showError(err.error || '테스트 실행 실패');
      }
    } catch (error) {
      showError('테스트 실행 중 오류 발생');
    } finally {
      setTestLoading(null);
    }
  };

  // 구독을 즉시 갱신 대상으로 설정
  const handleMakeDueNow = async (subscriptionId: number) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setTestLoading(`due-${subscriptionId}`);
    try {
      const response = await fetch(
        `${API_URL}/api/test/subscriptions/${subscriptionId}/make-due-now`,
        {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` },
        }
      );
      if (response.ok) {
        showSuccess('구독이 즉시 갱신 대상으로 설정되었습니다.');
        // 구독 목록 새로고침
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        if (subsResponse.ok) {
          const subsData = await subsResponse.json();
          setSubscriptions(subsData.data || []);
        }
      } else {
        const err = await response.json();
        showError(err.error || '테스트 실행 실패');
      }
    } catch (error) {
      showError('테스트 실행 중 오류 발생');
    } finally {
      setTestLoading(null);
    }
  };

  // 갱신 프로세스 수동 실행
  const handleProcessRenewals = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setTestLoading('process-renewals');
    try {
      const response = await fetch(`${API_URL}/api/test/subscriptions/process-renewals`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        showSuccess('갱신 프로세스가 실행되었습니다.');
        // 구독 목록 새로고침
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        if (subsResponse.ok) {
          const subsData = await subsResponse.json();
          setSubscriptions(subsData.data || []);
        }
      } else {
        showError('갱신 프로세스 실행 실패');
      }
    } catch (error) {
      showError('갱신 프로세스 실행 중 오류 발생');
    } finally {
      setTestLoading(null);
    }
  };

  // 실패한 결제 재시도
  const handleRetryFailed = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setTestLoading('retry-failed');
    try {
      const response = await fetch(`${API_URL}/api/test/subscriptions/retry-failed`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        showSuccess('결제 재시도가 실행되었습니다.');
      } else {
        showError('결제 재시도 실행 실패');
      }
    } catch (error) {
      showError('결제 재시도 실행 중 오류 발생');
    } finally {
      setTestLoading(null);
    }
  };

  // 만료 구독 처리
  const handleProcessExpired = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setTestLoading('process-expired');
    try {
      const response = await fetch(`${API_URL}/api/test/subscriptions/process-expired`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        showSuccess('만료 처리가 실행되었습니다.');
        // 구독 목록 새로고침
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        if (subsResponse.ok) {
          const subsData = await subsResponse.json();
          setSubscriptions(subsData.data || []);
        }
      } else {
        showError('만료 처리 실행 실패');
      }
    } catch (error) {
      showError('만료 처리 실행 중 오류 발생');
    } finally {
      setTestLoading(null);
    }
  };

  // 금액 포맷팅
  const formatPrice = (price: number, currency: string) => {
    if (currency === 'KRW') {
      return new Intl.NumberFormat('ko-KR').format(price) + '원';
    }
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(price);
  };

  const showSuccess = (message: string) => {
    setSuccessMessage(message);
    setTimeout(() => setSuccessMessage(''), 3000);
  };

  const showError = (message: string) => {
    setErrorMessage(message);
    setTimeout(() => setErrorMessage(''), 3000);
  };

  // ========== 리딤 코드 기능 ==========
  const handleRedeemSubmit = async () => {
    if (!redeemCode.trim()) return;
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setIsRedeeming(true);
    setRedeemResult(null);

    try {
      const response = await fetch(`${API_URL}/api/v1/redeem`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: redeemCode }),
      });

      if (response.ok) {
        const data = await response.json();
        setRedeemResult({
          success: true,
          licenseId: data.licenseId,
          licenseKey: data.licenseKey,
          productName: data.productName,
          planName: data.planName,
          validUntil: data.validUntil,
        });
        setRedeemCode('');
      } else {
        const error = await response.json();
        const errorMessages: Record<string, string> = {
          REDEEM_CODE_INVALID: '유효하지 않은 리딤 코드 형식입니다.',
          REDEEM_CODE_NOT_FOUND: '리딤 코드를 찾을 수 없습니다.',
          REDEEM_CODE_EXPIRED: '만료된 리딤 코드입니다.',
          REDEEM_CODE_DISABLED: '비활성화된 리딤 코드입니다.',
          REDEEM_CODE_DEPLETED: '사용 횟수가 소진된 코드입니다.',
          REDEEM_CAMPAIGN_FULL: '캠페인 발급 한도에 도달했습니다.',
          REDEEM_USER_LIMIT_EXCEEDED: '사용자별 사용 한도를 초과했습니다.',
          REDEEM_CAMPAIGN_NOT_ACTIVE: '현재 이용할 수 없는 캠페인입니다.',
          REDEEM_RATE_LIMITED: '요청이 너무 빈번합니다. 잠시 후 다시 시도해주세요.',
        };
        setRedeemResult({
          success: false,
          errorMessage: errorMessages[error.error] || error.message || '코드 등록에 실패했습니다.',
        });
      }
    } catch {
      setRedeemResult({ success: false, errorMessage: '네트워크 오류가 발생했습니다.' });
    } finally {
      setIsRedeeming(false);
    }
  };

  // ========== 리딤 캠페인 관리자 기능 ==========

  const fetchRedeemCampaigns = async (token: string) => {
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns?size=100`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setRedeemCampaigns(data.content || []);
    }
  };

  const fetchRedeemCodes = async (campaignId: string) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/${campaignId}/codes?size=100`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setRedeemCodes(data.content || []);
    }
  };

  const openRedeemCampaignModal = (campaign?: RedeemCampaign) => {
    if (campaign) {
      setEditingCampaign(campaign);
      setRedeemCampaignForm({
        name: campaign.name,
        description: campaign.description || '',
        productId: campaign.productId,
        licensePlanId: campaign.licensePlanId,
        usageCategory: campaign.usageCategory,
        seatLimit: campaign.seatLimit?.toString() || '',
        perUserLimit: campaign.perUserLimit.toString(),
        validFrom: campaign.validFrom ? new Date(campaign.validFrom).toISOString().slice(0, 16) : '',
        validUntil: campaign.validUntil ? new Date(campaign.validUntil).toISOString().slice(0, 16) : '',
      });
    } else {
      setEditingCampaign(null);
      setRedeemCampaignForm({
        name: '', description: '', productId: '', licensePlanId: '',
        usageCategory: 'COMMERCIAL', seatLimit: '', perUserLimit: '1',
        validFrom: '', validUntil: '',
      });
    }
    setIsRedeemCampaignModalOpen(true);
  };

  const closeRedeemCampaignModal = () => {
    setIsRedeemCampaignModalOpen(false);
    setEditingCampaign(null);
  };

  const handleRedeemCampaignSubmit = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const body = {
      name: redeemCampaignForm.name,
      description: redeemCampaignForm.description || null,
      productId: redeemCampaignForm.productId,
      licensePlanId: redeemCampaignForm.licensePlanId,
      usageCategory: redeemCampaignForm.usageCategory,
      seatLimit: redeemCampaignForm.seatLimit ? parseInt(redeemCampaignForm.seatLimit) : null,
      perUserLimit: parseInt(redeemCampaignForm.perUserLimit) || 1,
      validFrom: redeemCampaignForm.validFrom ? new Date(redeemCampaignForm.validFrom).toISOString() : null,
      validUntil: redeemCampaignForm.validUntil ? new Date(redeemCampaignForm.validUntil).toISOString() : null,
    };

    const url = editingCampaign
      ? `${API_URL}/api/v1/admin/redeem-campaigns/${editingCampaign.id}`
      : `${API_URL}/api/v1/admin/redeem-campaigns`;

    const response = await fetch(url, {
      method: editingCampaign ? 'PUT' : 'POST',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (response.ok) {
      closeRedeemCampaignModal();
      fetchRedeemCampaigns(token);
    } else {
      const error = await response.json();
      alert(error.message || '캠페인 저장에 실패했습니다.');
    }
  };

  const handleCampaignStatusChange = async (campaignId: string, action: 'pause' | 'end' | 'resume') => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/${campaignId}/${action}`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}` },
    });

    if (response.ok) {
      fetchRedeemCampaigns(token);
    } else {
      alert('상태 변경에 실패했습니다.');
    }
  };

  const openCodeGenerateModal = (campaign: RedeemCampaign) => {
    setSelectedCampaignForCodes(campaign);
    setCodeGenerateForm({ codeType: 'RANDOM', customCode: '', count: '10', maxRedemptions: '1', expiresAt: '' });
    setIsCodeGenerateModalOpen(true);
  };

  const handleGenerateCodes = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token || !selectedCampaignForCodes) return;

    const body = {
      campaignId: selectedCampaignForCodes.id,
      codeType: codeGenerateForm.codeType,
      customCode: codeGenerateForm.codeType === 'CUSTOM' ? codeGenerateForm.customCode : null,
      count: codeGenerateForm.codeType === 'RANDOM' ? parseInt(codeGenerateForm.count) || 1 : 1,
      maxRedemptions: parseInt(codeGenerateForm.maxRedemptions) || 1,
      expiresAt: codeGenerateForm.expiresAt ? new Date(codeGenerateForm.expiresAt).toISOString() : null,
    };

    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/codes`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (response.ok) {
      const data = await response.json();
      setGeneratedCodes(data.codes || []);
      setIsCodeGenerateModalOpen(false);
      setIsGeneratedCodesModalOpen(true);
      fetchRedeemCampaigns(token);
    } else {
      const error = await response.json();
      alert(error.message || '코드 생성에 실패했습니다.');
    }
  };

  const handleDeactivateCode = async (codeId: string) => {
    if (!window.confirm('이 코드를 비활성화하시겠습니까?')) return;
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/codes/${codeId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` },
    });

    if (response.ok && selectedCampaignForCodes) {
      fetchRedeemCodes(selectedCampaignForCodes.id);
    }
  };

  const copyGeneratedCodes = () => {
    navigator.clipboard.writeText(generatedCodes.join('\n'));
    alert('클립보드에 복사되었습니다.');
  };

  const downloadCodesAsCsv = () => {
    const csv = 'code\n' + generatedCodes.join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `redeem-codes-${selectedCampaignForCodes?.name || 'export'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const filteredLicensePlansForRedeem = useMemo(() => {
    if (!redeemCampaignForm.productId) return [];
    return licensePlans.filter(p => p.productId === redeemCampaignForm.productId && !p.deleted);
  }, [licensePlans, redeemCampaignForm.productId]);

  // ========== 관리자 기능 ==========

  // 관리자 데이터 조회 함수들
  const fetchAdminUsers = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/users`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setAdminUsers(data);
    }
  };

  const fetchAdminLicenses = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/license-list`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setAdminLicenses(data);
    }
  };

  const fetchAdminPayments = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/payments`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setAdminPayments(data);
    }
  };

  const fetchProducts = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/products`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setProducts(data);
    }
  };

  const fetchPricePlans = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/price-plans`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setPricePlans(data);
    }
  };

  const fetchPromotions = async (token: string) => {
    const response = await fetch(`${API_URL}/api/promotions`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setPromotions(data);
    }
  };

  const fetchLicensePlans = async (token: string) => {
    const response = await fetch(`${API_URL}/api/v1/admin/license-plans?size=100`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setLicensePlans(data.content || []);
    }
  };

  // 관리자 메뉴 변경 시 데이터 로드
  useEffect(() => {
    if (!isAdmin || !activeMenu.startsWith('admin-')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const loadAdminData = async () => {
      setIsAdminLoading(true);
      setAdminSearchQuery('');
      setAdminAppliedSearch('');
      setAdminCurrentPage(1);

      try {
        switch (activeMenu) {
          case 'admin-users':
            await fetchAdminUsers(token);
            break;
          case 'admin-licenses':
            await fetchAdminLicenses(token);
            await fetchLicensePlans(token);
            await fetchProducts(token);
            break;
          case 'admin-payments':
            await fetchAdminPayments(token);
            break;
          case 'admin-products':
            await fetchProducts(token);
            await fetchPricePlans(token);
            break;
          case 'admin-promotions':
            await fetchPromotions(token);
            await fetchProducts(token);
            break;
          case 'admin-redeem':
            await fetchRedeemCampaigns(token);
            await fetchProducts(token);
            await fetchLicensePlans(token);
            break;
        }
      } catch (error) {
        console.error('관리자 데이터 로드 실패:', error);
      } finally {
        setIsAdminLoading(false);
      }
    };

    loadAdminData();
  }, [activeMenu, isAdmin]);

  // 관리자 검색 핸들러
  const handleAdminSearch = () => {
    setAdminAppliedSearch(adminSearchQuery);
    setAdminCurrentPage(1);
  };

  const handleAdminSearchKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleAdminSearch();
  };

  const handleAdminClearSearch = () => {
    setAdminSearchQuery('');
    setAdminAppliedSearch('');
    setAdminCurrentPage(1);
  };

  // 관리자 유틸리티 함수들
  const getRoleName = (code: string) => {
    switch (code) {
      case '000': return '관리자';
      case '001': return '매니저';
      case '002': return '일반';
      default: return code;
    }
  };

  const formatAdminDate = (dateStr: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatAdminPrice = (price: number, currency: string) => {
    if (currency === 'KRW') {
      return price.toLocaleString('ko-KR') + '원';
    }
    return '$' + price.toLocaleString('en-US');
  };

  const formatPaymentMethod = (method: string | null) => {
    if (!method) return '-';
    const methodMap: { [key: string]: string } = {
      'CARD': '카드',
      'VIRTUAL_ACCOUNT': '가상계좌',
      'EASY_PAY': '간편결제',
      'TRANSFER': '계좌이체',
      'MOBILE': '휴대폰',
    };
    if (method.startsWith('EASY_PAY_')) {
      return `간편결제(${method.replace('EASY_PAY_', '')})`;
    }
    return methodMap[method] || method;
  };

  // 라이선스 활성화/비활성화
  const handleActivateLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 활성화하시겠습니까?')) return;
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/activate`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        await fetchAdminLicenses(token);
        showSuccess('라이선스가 활성화되었습니다.');
      } else {
        showError('활성화에 실패했습니다.');
      }
    } catch (error) {
      showError('활성화 중 오류가 발생했습니다.');
    }
  };

  const handleSuspendLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 비활성화하시겠습니까?')) return;
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/suspend`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) {
        await fetchAdminLicenses(token);
        showSuccess('라이선스가 비활성화되었습니다.');
      } else {
        showError('비활성화에 실패했습니다.');
      }
    } catch (error) {
      showError('비활성화 중 오류가 발생했습니다.');
    }
  };

  // 라이선스 플랜 CRUD 함수들
  const openLicensePlanModal = (plan?: LicensePlan) => {
    if (plan) {
      setEditingLicensePlan(plan);
      setLicensePlanForm({
        productId: plan.productId,
        code: plan.code,
        name: plan.name,
        description: plan.description || '',
        licenseType: plan.licenseType,
        durationDays: plan.durationDays,
        graceDays: plan.graceDays,
        maxActivations: plan.maxActivations,
        maxConcurrentSessions: plan.maxConcurrentSessions,
        allowOfflineDays: plan.allowOfflineDays,
        entitlements: plan.entitlements.join(', '),
      });
    } else {
      setEditingLicensePlan(null);
      setLicensePlanForm({
        productId: products.length > 0 ? products[0].id : '',
        code: '',
        name: '',
        description: '',
        licenseType: 'SUBSCRIPTION',
        durationDays: 365,
        graceDays: 7,
        maxActivations: 1,
        maxConcurrentSessions: 1,
        allowOfflineDays: 0,
        entitlements: 'core-simulation',
      });
    }
    setIsLicensePlanModalOpen(true);
  };

  const closeLicensePlanModal = () => {
    setIsLicensePlanModalOpen(false);
    setEditingLicensePlan(null);
  };

  const handleLicensePlanSubmit = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const entitlementList = licensePlanForm.entitlements
      .split(',')
      .map(e => e.trim())
      .filter(e => e.length > 0);

    const payload = {
      productId: licensePlanForm.productId,
      code: licensePlanForm.code,
      name: licensePlanForm.name,
      description: licensePlanForm.description || null,
      licenseType: licensePlanForm.licenseType,
      durationDays: licensePlanForm.durationDays,
      graceDays: licensePlanForm.graceDays,
      maxActivations: licensePlanForm.maxActivations,
      maxConcurrentSessions: licensePlanForm.maxConcurrentSessions,
      allowOfflineDays: licensePlanForm.allowOfflineDays,
      entitlements: entitlementList,
    };

    try {
      const url = editingLicensePlan
        ? `${API_URL}/api/v1/admin/license-plans/${editingLicensePlan.id}`
        : `${API_URL}/api/v1/admin/license-plans`;
      const method = editingLicensePlan ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) {
        closeLicensePlanModal();
        await fetchLicensePlans(token);
        showSuccess(editingLicensePlan ? '라이선스 플랜이 수정되었습니다.' : '라이선스 플랜이 등록되었습니다.');
      } else {
        const error = await response.json();
        showError(error.message || '저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('라이선스 플랜 저장 실패:', error);
      showError('저장 중 오류가 발생했습니다.');
    }
  };

  const handleToggleLicensePlan = async (id: string, currentActive: boolean) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const action = currentActive ? 'deactivate' : 'activate';
      const response = await fetch(`${API_URL}/api/v1/admin/license-plans/${id}/${action}`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok) {
        await fetchLicensePlans(token);
      }
    } catch (error) {
      console.error('라이선스 플랜 토글 실패:', error);
    }
  };

  const handleDeleteLicensePlan = async (id: string) => {
    if (!window.confirm('이 플랜을 삭제하시겠습니까? (기존 라이선스에는 영향 없음)')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/v1/admin/license-plans/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok || response.status === 204) {
        await fetchLicensePlans(token);
        showSuccess('라이선스 플랜이 삭제되었습니다.');
      } else {
        const error = await response.json();
        showError(error.message || '삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('라이선스 플랜 삭제 실패:', error);
      showError('삭제 중 오류가 발생했습니다.');
    }
  };

  const getProductNameById = (productId: string) => {
    const product = products.find(p => p.id === productId);
    return product ? `${product.code} - ${product.name}` : productId;
  };

  // 라이선스 플랜 검색 핸들러
  const handleLicensePlanSearch = () => {
    setLicensePlanAppliedSearch(licensePlanSearchQuery);
    setLicensePlanCurrentPage(1);
  };

  const handleLicensePlanSearchKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleLicensePlanSearch();
  };

  const handleLicensePlanClearSearch = () => {
    setLicensePlanSearchQuery('');
    setLicensePlanAppliedSearch('');
    setLicensePlanCurrentPage(1);
  };

  // 상품/요금제/프로모션 토글
  const handleToggleProduct = async (code: string) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    try {
      const response = await fetch(`${API_URL}/api/admin/products/${code}/toggle`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) await fetchProducts(token);
    } catch (error) {
      console.error('상품 토글 실패:', error);
    }
  };

  const handleTogglePricePlan = async (id: number) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    try {
      const response = await fetch(`${API_URL}/api/admin/price-plans/${id}/toggle`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) await fetchPricePlans(token);
    } catch (error) {
      console.error('요금제 토글 실패:', error);
    }
  };

  const handleTogglePromotion = async (id: number) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    try {
      const response = await fetch(`${API_URL}/api/promotions/${id}/toggle`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });
      if (response.ok) await fetchPromotions(token);
    } catch (error) {
      console.error('프로모션 토글 실패:', error);
    }
  };

  // 필터링 함수들
  const getFilteredAdminUsers = () => {
    if (!adminAppliedSearch) return adminUsers;
    const query = adminAppliedSearch.toLowerCase();
    return adminUsers.filter(u =>
      u.email.toLowerCase().includes(query) ||
      (u.name && u.name.toLowerCase().includes(query)) ||
      (u.phone && u.phone.includes(query))
    );
  };

  const getFilteredAdminLicenses = () => {
    if (!adminAppliedSearch) return adminLicenses;
    const query = adminAppliedSearch.toLowerCase();
    return adminLicenses.filter(l =>
      l.licenseKey.toLowerCase().includes(query) ||
      l.ownerId.toLowerCase().includes(query) ||
      l.status.toLowerCase().includes(query)
    );
  };

  const getFilteredAdminPayments = () => {
    if (!adminAppliedSearch) return adminPayments;
    const query = adminAppliedSearch.toLowerCase();
    return adminPayments.filter(p =>
      p.userEmail.toLowerCase().includes(query) ||
      (p.userName && p.userName.toLowerCase().includes(query)) ||
      p.orderId.toLowerCase().includes(query)
    );
  };

  const getFilteredProducts = () => {
    if (!adminAppliedSearch) return products;
    const query = adminAppliedSearch.toLowerCase();
    return products.filter(p =>
      p.code.toLowerCase().includes(query) ||
      p.name.toLowerCase().includes(query)
    );
  };

  const getFilteredPricePlans = () => {
    if (!adminAppliedSearch) return pricePlans;
    const query = adminAppliedSearch.toLowerCase();
    return pricePlans.filter(p =>
      p.name.toLowerCase().includes(query) ||
      p.productCode.toLowerCase().includes(query)
    );
  };

  const getFilteredPromotions = () => {
    if (!adminAppliedSearch) return promotions;
    const query = adminAppliedSearch.toLowerCase();
    return promotions.filter(p =>
      p.code.toLowerCase().includes(query) ||
      p.name.toLowerCase().includes(query)
    );
  };

  const getFilteredLicensePlans = () => {
    if (!licensePlanAppliedSearch) return licensePlans;
    const query = licensePlanAppliedSearch.toLowerCase();
    return licensePlans.filter(plan =>
      plan.code.toLowerCase().includes(query) ||
      plan.name.toLowerCase().includes(query) ||
      plan.licenseType.toLowerCase().includes(query)
    );
  };

  // 페이징
  const getPaginatedData = <T,>(data: T[], page?: number): T[] => {
    const currentPage = page ?? adminCurrentPage;
    const startIndex = (currentPage - 1) * ADMIN_ITEMS_PER_PAGE;
    return data.slice(startIndex, startIndex + ADMIN_ITEMS_PER_PAGE);
  };

  const getTotalPages = (totalItems: number): number => {
    return Math.ceil(totalItems / ADMIN_ITEMS_PER_PAGE);
  };


  if (!isAuthReady || isLoading) {
    return (
      <div className="mypage-container">
        <div className="mypage-loading">로딩 중...</div>
      </div>
    );
  }

  return (
    <>
      <Header logoText="BUL:C" hideUserMenu={true} />
      <div className="mypage-container">
        <div className="mypage-layout">
          {/* 상단 영역 */}
          <div className="mypage-layout-top">
            {/* 추후 상단 컨텐츠 추가 가능 */}
          </div>

          {/* 왼쪽 사이드바 */}
          <aside className="mypage-sidebar">
            <nav className="sidebar-nav">
              {/* 개인정보 대메뉴 */}
              <div className="menu-group">
                <div className="menu-parent">
                  <svg className="menu-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M20 21V19C20 17.9391 19.5786 16.9217 18.8284 16.1716C18.0783 15.4214 17.0609 15 16 15H8C6.93913 15 5.92172 15.4214 5.17157 16.1716C4.42143 16.9217 4 17.9391 4 19V21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span>{t('myPage.menu.personalInfo')}</span>
                </div>
                <div className="menu-children">
                  <button
                    className={`menu-child ${activeMenu === 'profile' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('profile')}
                  >
                    {t('myPage.menu.profile')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'account' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('account')}
                  >
                    {t('myPage.menu.account')}
                  </button>
                </div>
              </div>

              {/* 라이선스 정보 대메뉴 */}
              <div className="menu-group">
                <div className="menu-parent">
                  <svg className="menu-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M9 12L11 14L15 10M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span>{t('myPage.menu.licenseInfo')}</span>
                </div>
                <div className="menu-children">
                  <button
                    className={`menu-child ${activeMenu === 'subscription' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('subscription')}
                  >
                    {t('myPage.menu.subscription')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'payment' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('payment')}
                  >
                    {t('myPage.menu.payment')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'redeem' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('redeem')}
                  >
                    리딤 코드 등록
                  </button>
                </div>
              </div>

              {/* 관리자 정보 대메뉴 (관리자/매니저만 표시) */}
              {isAdmin && (
              <div className="menu-group admin-menu-group">
                <div className="menu-parent admin">
                  <svg className="menu-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 15C15.866 15 19 11.866 19 8V6C19 4.89543 18.1046 4 17 4H7C5.89543 4 5 4.89543 5 6V8C5 11.866 8.13401 15 12 15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M8.21 13.89L7 23L12 20L17 23L15.79 13.88" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span>{t('myPage.menu.admin')}</span>
                </div>
                <div className="menu-children">
                  <button
                    className={`menu-child ${activeMenu === 'admin-users' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-users')}
                  >
                    {t('myPage.menu.adminUsers')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-payments' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-payments')}
                  >
                    {t('myPage.menu.adminPayments')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-products' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-products')}
                  >
                    {t('myPage.menu.adminProducts')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-licenses' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-licenses')}
                  >
                    {t('myPage.menu.adminLicenses')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-promotions' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-promotions')}
                  >
                    {t('myPage.menu.adminPromotions')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-redeem' ? 'active' : ''}`}
                    onClick={() => handleMenuChange('admin-redeem')}
                  >
                    {t('myPage.menu.adminRedeem')}
                  </button>
                </div>
              </div>
              )}

            </nav>

            {/* 로그아웃 버튼 */}
            <div className="sidebar-footer">
              <button className="logout-btn-sidebar" onClick={handleLogout}>
                <svg className="logout-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M9 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M16 17L21 12L16 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M21 12H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                {t('myPage.logout')}
              </button>
            </div>
          </aside>

          {/* 오른쪽 콘텐츠 영역 */}
          <main className="mypage-content">
            {successMessage && (
              <div className="message success">{successMessage}</div>
            )}
            {errorMessage && (
              <div className="message error">{errorMessage}</div>
            )}

            <div className="mypage-grid">
          {/* 프로필 정보 */}
          {activeMenu === 'profile' && (
          <div className="info-card">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.profile')}</h2>
                {!isEditingProfile && (
                  <button className="edit-btn" onClick={() => setIsEditingProfile(true)}>
                    {t('myPage.edit')}
                  </button>
                )}
              </div>

              {isEditingProfile ? (
                <div className="edit-form">
                  <div className="form-group">
                    <label>{t('myPage.email')}</label>
                    <div className="input-wrapper">
                      <input
                        type="email"
                        value={userInfo.email}
                        disabled
                        className="disabled"
                      />
                      <span className="helper-text">{t('myPage.emailNotEditable')}</span>
                    </div>
                  </div>
                  <div className="form-group">
                    <label>{t('myPage.name')}</label>
                    <input
                      type="text"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      placeholder={t('myPage.namePlaceholder')}
                    />
                  </div>
                  <div className="form-group">
                    <label>{t('myPage.phone')}</label>
                    <input
                      type="tel"
                      value={editPhone}
                      onChange={(e) => setEditPhone(formatPhoneNumberOnInput(e.target.value))}
                      placeholder="010-0000-0000"
                      maxLength={13}
                    />
                  </div>
                  <div className="form-group">
                    <label>{t('myPage.language')}</label>
                    <div className="language-options">
                      {LANGUAGES.map((lang) => (
                        <button
                          key={lang.code}
                          type="button"
                          className={`language-btn ${tempLanguage === lang.code ? 'active' : ''}`}
                          onClick={() => setTempLanguage(lang.code)}
                        >
                          {lang.name}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="form-actions">
                    <button className="save-btn" onClick={handleSaveProfile}>{t('myPage.save')}</button>
                    <button className="cancel-btn" onClick={handleCancelProfile}>{t('myPage.cancel')}</button>
                  </div>
                </div>
              ) : (
                <div className="info-list">
                  <div className="info-row">
                    <span className="info-label">{t('myPage.email')}</span>
                    <span className="info-value">{userInfo.email}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">{t('myPage.name')}</span>
                    <span className="info-value">{userInfo.name || '-'}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">{t('myPage.phone')}</span>
                    <span className="info-value">{formatPhoneNumber(userInfo.phone) || '-'}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">{t('myPage.language')}</span>
                    <span className="info-value">
                      {LANGUAGES.find(l => l.code === selectedLanguage)?.name || selectedLanguage}
                    </span>
                  </div>
                </div>
              )}
          </div>
          )}

          {/* 계정정보 (아이디/비밀번호/계정삭제) */}
          {activeMenu === 'account' && (
          <>
            {/* 이메일 정보 */}
            <div className="info-card">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.account')}</h2>
              </div>
              <div className="info-list">
                <div className="info-row">
                  <span className="info-label">{t('myPage.emailId')}</span>
                  <span className="info-value">{userInfo.email}</span>
                </div>
              </div>

              {/* 비밀번호 변경 섹션 */}
              <div className="password-section">
                <div className="info-row">
                  <span className="info-label">{t('myPage.password')}</span>
                  {!isEditingPassword ? (
                    <button className="password-change-btn" onClick={() => setIsEditingPassword(true)}>
                      {t('myPage.changePassword')}
                    </button>
                  ) : (
                    <span className="info-value">{t('myPage.loading')}</span>
                  )}
                </div>
                {isEditingPassword && (
                  <div className="password-edit-form">
                    <div className="edit-form">
                      <div className="form-group">
                        <label>{t('myPage.currentPassword')}</label>
                        <div className="password-input-wrapper">
                          <input
                            type={showCurrentPassword ? 'text' : 'password'}
                            value={currentPassword}
                            onChange={(e) => setCurrentPassword(e.target.value)}
                            placeholder={t('myPage.currentPasswordPlaceholder')}
                          />
                          <button
                            type="button"
                            className="password-toggle-btn"
                            onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                          >
                            {showCurrentPassword ? (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                                <line x1="1" y1="1" x2="23" y2="23"/>
                              </svg>
                            ) : (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                <circle cx="12" cy="12" r="3"/>
                              </svg>
                            )}
                          </button>
                        </div>
                      </div>
                      <div className="form-group">
                        <label>{t('myPage.newPassword')}</label>
                        <div className="password-input-wrapper">
                          <input
                            type={showNewPassword ? 'text' : 'password'}
                            value={newPassword}
                            onChange={(e) => {
                              setNewPassword(e.target.value);
                              setPasswordError('');
                            }}
                            placeholder={t('myPage.newPasswordPlaceholder')}
                          />
                          <button
                            type="button"
                            className="password-toggle-btn"
                            onClick={() => setShowNewPassword(!showNewPassword)}
                          >
                            {showNewPassword ? (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                                <line x1="1" y1="1" x2="23" y2="23"/>
                              </svg>
                            ) : (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                <circle cx="12" cy="12" r="3"/>
                              </svg>
                            )}
                          </button>
                        </div>
                      </div>
                      <div className="form-group">
                        <label>{t('myPage.confirmPassword')}</label>
                        <div className="password-input-wrapper">
                          <input
                            type={showConfirmPassword ? 'text' : 'password'}
                            value={confirmPassword}
                            onChange={(e) => {
                              setConfirmPassword(e.target.value);
                              setPasswordError('');
                            }}
                            placeholder={t('myPage.confirmPasswordPlaceholder')}
                          />
                          <button
                            type="button"
                            className="password-toggle-btn"
                            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                          >
                            {showConfirmPassword ? (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                                <line x1="1" y1="1" x2="23" y2="23"/>
                              </svg>
                            ) : (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                <circle cx="12" cy="12" r="3"/>
                              </svg>
                            )}
                          </button>
                        </div>
                      </div>
                      {passwordError && (
                        <div className="form-error">{passwordError}</div>
                      )}
                      <div className="form-actions">
                        <button className="save-btn" onClick={handleChangePassword}>{t('myPage.changePasswordBtn')}</button>
                        <button className="cancel-btn" onClick={handleCancelPassword}>{t('myPage.cancel')}</button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* 계정 삭제 */}
            <div className="info-card delete-account-card">
              <div className="delete-account-section">
                <div className="delete-account-info">
                  <span className="delete-label">{t('myPage.deleteAccount')}</span>
                  <span className="delete-description">{t('myPage.deleteAccountDesc')}</span>
                </div>
                <button
                  className="delete-account-btn"
                  onClick={() => setIsDeleteModalOpen(true)}
                >
                  {t('myPage.deleteAccount')}
                </button>
              </div>
            </div>
          </>
          )}

          {/* 구독 정보 (라이선스 + 구독) */}
          {activeMenu === 'subscription' && (
          <>
          {/* 라이선스 정보 */}
          <div className="info-card license-card">
            <div className="card-header">
              <h2 className="card-title">{t('myPage.license')}</h2>
            </div>
            {isLoadingLicenses ? (
              <div className="loading-text">{t('myPage.loading')}</div>
            ) : licenses.length === 0 ? (
              <div className="empty-licenses">
                <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M9 12L11 14L15 10M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <p>{t('myPage.noLicense')}</p>
                <button className="purchase-btn" onClick={() => navigate('/payment')}>
                  {t('myPage.purchaseLicense')}
                </button>
              </div>
            ) : (
              <div className="license-list">
                {licenses.map((license) => (
                  <div key={license.id} className={`license-item ${license.status.toLowerCase()}`}>
                    <div className="license-header">
                      <span className="license-product">
                        {license.productName || license.planName || 'BULC'}
                      </span>
                      <span className={`license-status status-${license.status.toLowerCase()}`}>
                        {license.status === 'ACTIVE' ? '활성' :
                         license.status === 'PENDING' ? '대기' :
                         license.status === 'EXPIRED_GRACE' ? '만료 유예' :
                         license.status === 'EXPIRED_HARD' ? '만료됨' :
                         license.status === 'SUSPENDED' ? '정지됨' :
                         license.status === 'REVOKED' ? '취소됨' : license.status}
                      </span>
                    </div>
                    <div className="license-details">
                      <div className="license-detail-row">
                        <span className="detail-label">라이선스 유형</span>
                        <span className="detail-value">
                          {license.licenseType === 'SUBSCRIPTION' ? '구독형' :
                           license.licenseType === 'PERPETUAL' ? '영구' :
                           license.licenseType === 'TRIAL' ? '체험판' : license.licenseType}
                        </span>
                      </div>
                      <div className="license-detail-row">
                        <span className="detail-label">유효 기간</span>
                        <span className="detail-value">
                          {license.validFrom ? new Date(license.validFrom).toLocaleDateString('ko-KR') : '-'}
                          {' ~ '}
                          {license.validUntil ? new Date(license.validUntil).toLocaleDateString('ko-KR') : '무제한'}
                        </span>
                      </div>
                      <div
                        className="license-detail-row device-toggle-row"
                        onClick={() => handleToggleDeviceList(license.id)}
                      >
                        <span className="detail-label">기기 등록</span>
                        <span className="detail-value device-toggle-value">
                          {license.usedActivations} / {license.maxActivations}대
                          <span className={`device-toggle-icon ${expandedLicenseId === license.id ? 'expanded' : ''}`}>
                            ▼
                          </span>
                        </span>
                      </div>
                      {expandedLicenseId === license.id && (
                        <div className="device-list">
                          {isLoadingActivations === license.id ? (
                            <div className="device-loading">기기 목록을 불러오는 중...</div>
                          ) : !activations[license.id] || activations[license.id].length === 0 ? (
                            <div className="device-empty">등록된 기기가 없습니다.</div>
                          ) : (
                            activations[license.id].map((activation) => (
                              <div key={activation.id} className="device-item">
                                <div className="device-info">
                                  <span className="device-name">
                                    {activation.deviceDisplayName || activation.clientOs || '알 수 없는 기기'}
                                  </span>
                                  <span className="device-meta">
                                    {activation.clientOs && <span className="device-os">{activation.clientOs}</span>}
                                    {activation.clientVersion && <span className="device-version"> v{activation.clientVersion}</span>}
                                  </span>
                                  <span className="device-meta">
                                    마지막 접속: {activation.lastSeenAt
                                      ? new Date(activation.lastSeenAt).toLocaleString('ko-KR')
                                      : '-'}
                                  </span>
                                </div>
                                <div className="device-actions">
                                  <span className={`device-status-badge status-${activation.status.toLowerCase()}`}>
                                    {activation.status === 'ACTIVE' ? '활성' :
                                     activation.status === 'STALE' ? '비활성' :
                                     activation.status === 'DEACTIVATED' ? '해제됨' :
                                     activation.status === 'EXPIRED' ? '만료' : activation.status}
                                  </span>
                                  {(activation.status === 'ACTIVE' || activation.status === 'STALE') && (
                                    <button
                                      className="device-deactivate-btn"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleDeactivateDevice(license.id, activation.deviceFingerprint);
                                      }}
                                    >
                                      해제
                                    </button>
                                  )}
                                </div>
                              </div>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 구독 관리 */}
          <div className="info-card subscription-card">
            <div className="card-header">
              <h2 className="card-title">{t('myPage.subscription')}</h2>
            </div>
            {isLoadingSubscriptions ? (
              <div className="loading-text">{t('myPage.loading')}</div>
            ) : subscriptions.length === 0 ? (
              <div className="empty-subscriptions">
                <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M4 4H20C21.1 4 22 4.9 22 6V18C22 19.1 21.1 20 20 20H4C2.9 20 2 19.1 2 18V6C2 4.9 2.9 4 4 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M22 6L12 13L2 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <p>{t('myPage.noSubscription')}</p>
              </div>
            ) : (
              <div className="subscription-list">
                {subscriptions.map((sub) => (
                  <div key={sub.id} className={`subscription-item status-${sub.status.toLowerCase()}`}>
                    <div className="subscription-header">
                      <span className="subscription-product">
                        {sub.productName || sub.pricePlanName || '구독'}
                      </span>
                      <span className={`subscription-status ${sub.status === 'A' ? 'active' : sub.status === 'E' ? 'expired' : 'canceled'}`}>
                        {sub.status === 'A' ? '활성' : sub.status === 'E' ? '만료됨' : '취소됨'}
                      </span>
                    </div>
                    <div className="subscription-details">
                      <div className="subscription-detail-row">
                        <span className="detail-label">요금</span>
                        <span className="detail-value">{formatPrice(sub.price, sub.currency)}</span>
                      </div>
                      <div className="subscription-detail-row">
                        <span className="detail-label">구독 기간</span>
                        <span className="detail-value">
                          {new Date(sub.startDate).toLocaleDateString('ko-KR')} ~ {new Date(sub.endDate).toLocaleDateString('ko-KR')}
                        </span>
                      </div>
                      {sub.status === 'A' && (
                        <div className="subscription-detail-row auto-renew-row">
                          <span className="detail-label">자동 갱신</span>
                          <div className="auto-renew-toggle">
                            <label className="toggle-switch">
                              <input
                                type="checkbox"
                                checked={sub.autoRenew}
                                onChange={() => handleToggleAutoRenew(sub.id, sub.autoRenew)}
                                disabled={billingKeys.length === 0}
                              />
                              <span className="toggle-slider"></span>
                            </label>
                            <span className="toggle-label">{sub.autoRenew ? 'ON' : 'OFF'}</span>
                          </div>
                        </div>
                      )}
                      {sub.autoRenew && sub.nextBillingDate && (
                        <div className="subscription-detail-row">
                          <span className="detail-label">다음 결제일</span>
                          <span className="detail-value next-billing">
                            {new Date(sub.nextBillingDate).toLocaleDateString('ko-KR')}
                          </span>
                        </div>
                      )}
                      {/* 개발 테스트 버튼 (관리자 전용) */}
                      {isAdmin && isTestMode && (
                        <div className="subscription-test-actions">
                          <div className="test-label">테스트 액션</div>
                          <div className="test-buttons">
                            <button
                              className="test-btn"
                              onClick={() => handleSimulateNearExpiry(sub.id, 3)}
                              disabled={testLoading !== null}
                            >
                              {testLoading === `simulate-${sub.id}` ? '처리중...' : '3일 후 만료'}
                            </button>
                            <button
                              className="test-btn"
                              onClick={() => handleMakeDueNow(sub.id)}
                              disabled={testLoading !== null || !sub.autoRenew}
                              title={!sub.autoRenew ? '자동 갱신이 활성화되어야 합니다' : ''}
                            >
                              {testLoading === `due-${sub.id}` ? '처리중...' : '즉시 갱신 대상'}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

          </div>
          </>
          )}

          {/* 결제 정보 */}
          {activeMenu === 'payment' && (
          <div className="info-card payment-methods-card">
            <div className="card-header">
              <h2 className="card-title">{t('myPage.paymentMethod')}</h2>
              {!isEditingSettings && (
                <button className="edit-btn" onClick={handleStartEditSettings}>
                  {t('myPage.edit')}
                </button>
              )}
            </div>

            {/* 결제 통화 설정 */}
            {isEditingSettings ? (
              <div className="edit-form currency-edit-form">
                <div className="form-group">
                  <label>{t('myPage.paymentCurrency')}</label>
                  <div className="input-wrapper">
                    <select
                      value={tempCountry}
                      onChange={(e) => setTempCountry(e.target.value)}
                      className="country-dropdown"
                    >
                      {COUNTRIES.map((country) => (
                        <option key={country.code} value={country.code}>
                          {country.name} ({country.currency})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="form-actions">
                  <button className="save-btn" onClick={handleSaveSettings}>{t('myPage.save')}</button>
                  <button className="cancel-btn" onClick={handleCancelSettings}>{t('myPage.cancel')}</button>
                </div>
              </div>
            ) : (
              <div className="currency-info-section">
                <div className="info-row">
                  <span className="info-label">{t('myPage.paymentCurrency')}</span>
                  <span className="info-value">
                    {COUNTRIES.find(c => c.code === selectedCountry)?.name || selectedCountry}
                    ({COUNTRIES.find(c => c.code === selectedCountry)?.currency || 'KRW'})
                  </span>
                </div>
              </div>
            )}

            {isLoadingBillingKeys ? (
              <div className="loading-text">{t('myPage.loading')}</div>
            ) : billingKeys.length === 0 ? (
              <div className="empty-payment-methods">
                <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M21 4H3C1.89 4 1 4.89 1 6V18C1 19.11 1.89 20 3 20H21C22.11 20 23 19.11 23 18V6C23 4.89 22.11 4 21 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M1 10H23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <p>{t('myPage.noPaymentMethod')}</p>
                <span className="helper-text">{t('myPage.paymentMethodHelper')}</span>
              </div>
            ) : (
              <div className="payment-methods-list">
                {billingKeys.map((card) => (
                  <div key={card.id} className={`payment-method-item ${card.isDefault ? 'default' : ''}`}>
                    <div className="card-info">
                      <div className="card-icon">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M21 4H3C1.89 4 1 4.89 1 6V18C1 19.11 1.89 20 3 20H21C22.11 20 23 19.11 23 18V6C23 4.89 22.11 4 21 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                          <path d="M1 10H23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      </div>
                      <div className="card-details">
                        <span className="card-company">{card.cardCompany || '카드'}</span>
                        <span className="card-number">{card.cardNumber}</span>
                      </div>
                      {card.isDefault && <span className="default-badge">기본</span>}
                    </div>
                    <div className="card-actions">
                      {!card.isDefault && (
                        <button
                          className="set-default-btn"
                          onClick={() => handleSetDefaultCard(card.id)}
                        >
                          기본으로 설정
                        </button>
                      )}
                      <button
                        className="delete-card-btn"
                        onClick={() => handleDeleteCard(card.id)}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
          )}


          {/* 리딤 코드 등록 */}
          {activeMenu === 'redeem' && (
          <div className="info-card">
            <div className="card-header">
              <h2 className="card-title">리딤 코드 등록</h2>
            </div>
            <p style={{ color: '#666', marginBottom: '1.5rem', fontSize: '14px' }}>
              리딤 코드를 입력하여 라이선스를 등록하세요.
            </p>
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
              <input
                type="text"
                value={redeemCode}
                onChange={(e) => setRedeemCode(e.target.value.toUpperCase())}
                onKeyDown={(e) => e.key === 'Enter' && handleRedeemSubmit()}
                placeholder="XXXX-XXXX-XXXX-XXXX"
                style={{ flex: 1, padding: '0.75rem', borderRadius: '8px', border: '1px solid #d1d5db', fontSize: '16px', fontFamily: 'monospace', letterSpacing: '1px' }}
                disabled={isRedeeming}
              />
              <button
                onClick={handleRedeemSubmit}
                disabled={isRedeeming || !redeemCode.trim()}
                style={{ padding: '0.75rem 1.5rem', borderRadius: '8px', background: '#C4320A', color: 'white', border: 'none', cursor: 'pointer', fontWeight: 600, opacity: isRedeeming || !redeemCode.trim() ? 0.5 : 1 }}
              >
                {isRedeeming ? '등록 중...' : '등록'}
              </button>
            </div>

            {redeemResult && (
              <div style={{ padding: '1rem', borderRadius: '8px', background: redeemResult.success ? '#f0fdf4' : '#fef2f2', border: `1px solid ${redeemResult.success ? '#86efac' : '#fecaca'}`, marginTop: '1rem' }}>
                {redeemResult.success ? (
                  <div>
                    <p style={{ color: '#166534', fontWeight: 'bold', marginBottom: '0.5rem' }}>라이선스가 성공적으로 등록되었습니다!</p>
                    <div style={{ fontSize: '14px', color: '#333' }}>
                      <p>제품: {redeemResult.productName}</p>
                      <p>플랜: {redeemResult.planName}</p>
                      <p>라이선스 키: <code style={{ background: '#e5e7eb', padding: '2px 6px', borderRadius: '4px' }}>{redeemResult.licenseKey}</code></p>
                      {redeemResult.validUntil && (
                        <p>만료일: {new Date(redeemResult.validUntil).toLocaleDateString()}</p>
                      )}
                    </div>
                  </div>
                ) : (
                  <p style={{ color: '#991b1b' }}>{redeemResult.errorMessage}</p>
                )}
              </div>
            )}

            <div style={{ marginTop: '1.5rem', padding: '1rem', background: '#f9fafb', borderRadius: '8px', fontSize: '13px', color: '#6b7280' }}>
              <p style={{ fontWeight: 600, marginBottom: '0.5rem' }}>안내</p>
              <ul style={{ paddingLeft: '1.2rem', margin: 0 }}>
                <li>코드 입력 시 하이픈(-)과 공백은 자동으로 무시됩니다.</li>
                <li>코드는 대소문자를 구분하지 않습니다.</li>
                <li>분당 최대 5회까지 시도할 수 있습니다.</li>
              </ul>
            </div>
          </div>
          )}

          {/* 관리자 섹션 - 사용자 관리 */}
          {activeMenu === 'admin-users' && isAdmin && (
            <div className="info-card admin-section-card wide">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.adminUsers')}</h2>
                <span className="admin-count">{getFilteredAdminUsers().length}명</span>
              </div>
              <div className="admin-search-bar">
                <input
                  type="text"
                  placeholder="이메일, 이름, 전화번호로 검색"
                  value={adminSearchQuery}
                  onChange={(e) => setAdminSearchQuery(e.target.value)}
                  onKeyPress={handleAdminSearchKeyPress}
                />
                <button onClick={handleAdminSearch}>조회</button>
                {adminAppliedSearch && <button className="clear" onClick={handleAdminClearSearch}>초기화</button>}
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>이메일</th>
                          <th>이름</th>
                          <th>전화번호</th>
                          <th>권한</th>
                          <th>가입일</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedData(getFilteredAdminUsers()).length > 0 ? (
                          getPaginatedData(getFilteredAdminUsers()).map((u) => (
                            <tr key={u.id}>
                              <td>{u.email}</td>
                              <td>{u.name || '-'}</td>
                              <td>{formatPhoneNumber(u.phone) || '-'}</td>
                              <td><span className={`role-badge role-${u.rolesCode}`}>{getRoleName(u.rolesCode)}</span></td>
                              <td>{formatAdminDate(u.createdAt)}</td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={5} className="empty-row">데이터가 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {getTotalPages(getFilteredAdminUsers().length) > 1 && (
                    <div className="admin-pagination">
                      <button disabled={adminCurrentPage === 1} onClick={() => setAdminCurrentPage(p => p - 1)}>&lsaquo;</button>
                      <span>{adminCurrentPage} / {getTotalPages(getFilteredAdminUsers().length)}</span>
                      <button disabled={adminCurrentPage === getTotalPages(getFilteredAdminUsers().length)} onClick={() => setAdminCurrentPage(p => p + 1)}>&rsaquo;</button>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* 관리자 섹션 - 결제 관리 */}
          {activeMenu === 'admin-payments' && isAdmin && (
            <div className="info-card admin-section-card wide">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.adminPayments')}</h2>
                <span className="admin-count">{getFilteredAdminPayments().length}건</span>
              </div>
              <div className="admin-search-bar">
                <input
                  type="text"
                  placeholder="이메일, 이름, 주문번호로 검색"
                  value={adminSearchQuery}
                  onChange={(e) => setAdminSearchQuery(e.target.value)}
                  onKeyPress={handleAdminSearchKeyPress}
                />
                <button onClick={handleAdminSearch}>조회</button>
                {adminAppliedSearch && <button className="clear" onClick={handleAdminClearSearch}>초기화</button>}
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>주문번호</th>
                          <th>이름</th>
                          <th>이메일</th>
                          <th>결제수단</th>
                          <th>금액</th>
                          <th>상태</th>
                          <th>결제일</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedData(getFilteredAdminPayments()).length > 0 ? (
                          getPaginatedData(getFilteredAdminPayments()).map((p) => (
                            <tr key={p.id}>
                              <td className="order-id">{p.orderId}</td>
                              <td>{p.userName || '-'}</td>
                              <td>{p.userEmail}</td>
                              <td>{formatPaymentMethod(p.paymentMethod)}</td>
                              <td>{formatAdminPrice(p.amount, p.currency)}</td>
                              <td><span className={`status-badge status-${p.status?.toLowerCase()}`}>{p.status}</span></td>
                              <td>{formatAdminDate(p.createdAt)}</td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={7} className="empty-row">데이터가 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {getTotalPages(getFilteredAdminPayments().length) > 1 && (
                    <div className="admin-pagination">
                      <button disabled={adminCurrentPage === 1} onClick={() => setAdminCurrentPage(p => p - 1)}>&lsaquo;</button>
                      <span>{adminCurrentPage} / {getTotalPages(getFilteredAdminPayments().length)}</span>
                      <button disabled={adminCurrentPage === getTotalPages(getFilteredAdminPayments().length)} onClick={() => setAdminCurrentPage(p => p + 1)}>&rsaquo;</button>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* 관리자 섹션 - 상품 관리 */}
          {activeMenu === 'admin-products' && isAdmin && (
            <>
              <div className="info-card admin-section-card wide">
                <div className="card-header">
                  <h2 className="card-title">{t('myPage.menu.adminProducts')}</h2>
                  <span className="admin-count">{getFilteredProducts().length}개</span>
                </div>
                <div className="admin-search-bar">
                  <input
                    type="text"
                    placeholder="상품 코드, 상품명으로 검색"
                    value={adminSearchQuery}
                    onChange={(e) => setAdminSearchQuery(e.target.value)}
                    onKeyPress={handleAdminSearchKeyPress}
                  />
                  <button onClick={handleAdminSearch}>조회</button>
                  {adminAppliedSearch && <button className="clear" onClick={handleAdminClearSearch}>초기화</button>}
                </div>
                {isAdminLoading ? (
                  <div className="admin-loading">데이터 로딩 중...</div>
                ) : (
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>코드</th>
                          <th>상품명</th>
                          <th>설명</th>
                          <th>상태</th>
                          <th>생성일</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getFilteredProducts().length > 0 ? (
                          getFilteredProducts().map((p) => (
                            <tr key={p.code}>
                              <td>{p.code}</td>
                              <td>{p.name}</td>
                              <td>{p.description || '-'}</td>
                              <td>
                                <button
                                  className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                                  onClick={() => handleToggleProduct(p.code)}
                                >
                                  {p.isActive ? '활성' : '비활성'}
                                </button>
                              </td>
                              <td>{formatAdminDate(p.createdAt)}</td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={5} className="empty-row">데이터가 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="info-card admin-section-card wide">
                <div className="card-header">
                  <h2 className="card-title">요금제 목록</h2>
                  <span className="admin-count">{getFilteredPricePlans().length}개</span>
                </div>
                {isAdminLoading ? (
                  <div className="admin-loading">데이터 로딩 중...</div>
                ) : (
                  <>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>상품코드</th>
                            <th>요금제명</th>
                            <th>가격</th>
                            <th>상태</th>
                            <th>생성일</th>
                          </tr>
                        </thead>
                        <tbody>
                          {getPaginatedData(getFilteredPricePlans()).length > 0 ? (
                            getPaginatedData(getFilteredPricePlans()).map((p) => (
                              <tr key={p.id}>
                                <td>{p.id}</td>
                                <td>{p.productCode}</td>
                                <td>{p.name}</td>
                                <td>{formatAdminPrice(p.price, p.currency)}</td>
                                <td>
                                  <button
                                    className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                                    onClick={() => handleTogglePricePlan(p.id)}
                                  >
                                    {p.isActive ? '활성' : '비활성'}
                                  </button>
                                </td>
                                <td>{formatAdminDate(p.createdAt)}</td>
                              </tr>
                            ))
                          ) : (
                            <tr><td colSpan={6} className="empty-row">데이터가 없습니다.</td></tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    {getTotalPages(getFilteredPricePlans().length) > 1 && (
                      <div className="admin-pagination">
                        <button disabled={adminCurrentPage === 1} onClick={() => setAdminCurrentPage(p => p - 1)}>&lsaquo;</button>
                        <span>{adminCurrentPage} / {getTotalPages(getFilteredPricePlans().length)}</span>
                        <button disabled={adminCurrentPage === getTotalPages(getFilteredPricePlans().length)} onClick={() => setAdminCurrentPage(p => p + 1)}>&rsaquo;</button>
                      </div>
                    )}
                  </>
                )}
              </div>
            </>
          )}

          {/* 관리자 섹션 - 라이선스 관리 */}
          {activeMenu === 'admin-licenses' && isAdmin && (
            <>
            <div className="info-card admin-section-card wide">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.adminLicenses')}</h2>
                <span className="admin-count">{getFilteredAdminLicenses().length}개</span>
              </div>
              <div className="admin-search-bar">
                <input
                  type="text"
                  placeholder="라이선스 키, 소유자 ID, 상태로 검색"
                  value={adminSearchQuery}
                  onChange={(e) => setAdminSearchQuery(e.target.value)}
                  onKeyPress={handleAdminSearchKeyPress}
                />
                <button onClick={handleAdminSearch}>조회</button>
                {adminAppliedSearch && <button className="clear" onClick={handleAdminClearSearch}>초기화</button>}
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>라이선스 키</th>
                          <th>소유자 이메일</th>
                          <th>상태</th>
                          <th>만료일</th>
                          <th>생성일</th>
                          <th>관리</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedData(getFilteredAdminLicenses()).length > 0 ? (
                          getPaginatedData(getFilteredAdminLicenses()).map((l) => (
                            <tr key={l.id}>
                              <td className="license-key">{l.licenseKey}</td>
                              <td>{l.ownerId}</td>
                              <td><span className={`status-badge status-${l.status?.toLowerCase()}`}>{l.status}</span></td>
                              <td>{formatAdminDate(l.validUntil)}</td>
                              <td>{formatAdminDate(l.createdAt)}</td>
                              <td>
                                {l.status === 'ACTIVE' ? (
                                  <button className="action-btn delete" onClick={() => handleSuspendLicense(l.id)}>비활성화</button>
                                ) : l.status === 'SUSPENDED' ? (
                                  <button className="action-btn edit" onClick={() => handleActivateLicense(l.id)}>활성화</button>
                                ) : (
                                  <span>-</span>
                                )}
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={6} className="empty-row">데이터가 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {getTotalPages(getFilteredAdminLicenses().length) > 1 && (
                    <div className="admin-pagination">
                      <button disabled={adminCurrentPage === 1} onClick={() => setAdminCurrentPage(p => p - 1)}>&lsaquo;</button>
                      <span>{adminCurrentPage} / {getTotalPages(getFilteredAdminLicenses().length)}</span>
                      <button disabled={adminCurrentPage === getTotalPages(getFilteredAdminLicenses().length)} onClick={() => setAdminCurrentPage(p => p + 1)}>&rsaquo;</button>
                    </div>
                  )}
                </>
              )}
            </div>

            {/* 라이선스 플랜 관리 */}
            <div className="info-card admin-section-card wide" style={{ marginTop: '24px' }}>
              <div className="card-header">
                <h2 className="card-title">라이선스 플랜 관리</h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <span className="admin-count">
                    {licensePlanAppliedSearch
                      ? `${getFilteredLicensePlans().length}개 / 전체 ${licensePlans.length}개`
                      : `${licensePlans.length}개`
                    }
                  </span>
                  <button className="edit-btn" onClick={() => openLicensePlanModal()}>+ 플랜 추가</button>
                </div>
              </div>
              <div className="admin-search-bar">
                <input
                  type="text"
                  placeholder="플랜 코드, 플랜명, 라이선스 유형으로 검색"
                  value={licensePlanSearchQuery}
                  onChange={(e) => setLicensePlanSearchQuery(e.target.value)}
                  onKeyPress={handleLicensePlanSearchKeyPress}
                />
                <button onClick={handleLicensePlanSearch}>조회</button>
                {licensePlanAppliedSearch && <button className="clear" onClick={handleLicensePlanClearSearch}>초기화</button>}
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>코드</th>
                          <th>플랜명</th>
                          <th>상품</th>
                          <th>라이선스 유형</th>
                          <th>유효기간</th>
                          <th>최대 기기</th>
                          <th>동시 세션</th>
                          <th>상태</th>
                          <th>관리</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedData(getFilteredLicensePlans(), licensePlanCurrentPage).length > 0 ? (
                          getPaginatedData(getFilteredLicensePlans(), licensePlanCurrentPage).map((plan) => (
                            <tr key={plan.id} className={plan.deleted ? 'deleted-row' : ''}>
                              <td><span className="coupon-code">{plan.code}</span></td>
                              <td>{plan.name}</td>
                              <td>{getProductNameById(plan.productId)}</td>
                              <td>
                                <span className={`status-badge status-${plan.licenseType.toLowerCase()}`}>
                                  {plan.licenseType}
                                </span>
                              </td>
                              <td>{plan.durationDays}일</td>
                              <td>{plan.maxActivations}</td>
                              <td>{plan.maxConcurrentSessions}</td>
                              <td>
                                {plan.deleted ? (
                                  <span className="status-badge status-deleted">삭제됨</span>
                                ) : (
                                  <button
                                    className={`status-toggle-btn ${plan.active ? 'active' : 'inactive'}`}
                                    onClick={() => handleToggleLicensePlan(plan.id, plan.active)}
                                  >
                                    {plan.active ? '활성' : '비활성'}
                                  </button>
                                )}
                              </td>
                              <td>
                                {!plan.deleted && (
                                  <div style={{ display: 'flex', gap: '4px' }}>
                                    <button
                                      className="action-btn edit"
                                      onClick={() => openLicensePlanModal(plan)}
                                    >
                                      수정
                                    </button>
                                    <button
                                      className="action-btn delete"
                                      onClick={() => handleDeleteLicensePlan(plan.id)}
                                    >
                                      삭제
                                    </button>
                                  </div>
                                )}
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={9} className="empty-row">
                              {licensePlanAppliedSearch ? '검색 결과가 없습니다.' : '등록된 라이선스 플랜이 없습니다.'}
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {getTotalPages(getFilteredLicensePlans().length) > 1 && (
                    <div className="admin-pagination">
                      <button disabled={licensePlanCurrentPage === 1} onClick={() => setLicensePlanCurrentPage(p => p - 1)}>&lsaquo;</button>
                      <span>{licensePlanCurrentPage} / {getTotalPages(getFilteredLicensePlans().length)}</span>
                      <button disabled={licensePlanCurrentPage === getTotalPages(getFilteredLicensePlans().length)} onClick={() => setLicensePlanCurrentPage(p => p + 1)}>&rsaquo;</button>
                    </div>
                  )}
                </>
              )}
            </div>
            </>
          )}

          {/* 관리자 섹션 - 프로모션 관리 */}
          {activeMenu === 'admin-promotions' && isAdmin && (
            <div className="info-card admin-section-card wide">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.adminPromotions')}</h2>
                <span className="admin-count">{getFilteredPromotions().length}개</span>
              </div>
              <div className="admin-search-bar">
                <input
                  type="text"
                  placeholder="쿠폰 코드, 프로모션명으로 검색"
                  value={adminSearchQuery}
                  onChange={(e) => setAdminSearchQuery(e.target.value)}
                  onKeyPress={handleAdminSearchKeyPress}
                />
                <button onClick={handleAdminSearch}>조회</button>
                {adminAppliedSearch && <button className="clear" onClick={handleAdminClearSearch}>초기화</button>}
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>쿠폰 코드</th>
                          <th>프로모션명</th>
                          <th>할인율</th>
                          <th>할인금액</th>
                          <th>사용 현황</th>
                          <th>유효 기간</th>
                          <th>상태</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getPaginatedData(getFilteredPromotions()).length > 0 ? (
                          getPaginatedData(getFilteredPromotions()).map((p) => (
                            <tr key={p.id}>
                              <td className="coupon-code">{p.code}</td>
                              <td>{p.name}</td>
                              <td>{p.discountType}%</td>
                              <td>{p.discountValue.toLocaleString()}원</td>
                              <td>{p.usageCount} / {p.usageLimit || '∞'}</td>
                              <td>
                                {p.validFrom && p.validUntil ? (
                                  <>{formatAdminDate(p.validFrom).split(' ')[0]} ~ {formatAdminDate(p.validUntil).split(' ')[0]}</>
                                ) : '제한 없음'}
                              </td>
                              <td>
                                <button
                                  className={`status-toggle-btn ${p.isActive ? 'active' : 'inactive'}`}
                                  onClick={() => handleTogglePromotion(p.id)}
                                >
                                  {p.isActive ? '활성' : '비활성'}
                                </button>
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={7} className="empty-row">데이터가 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {getTotalPages(getFilteredPromotions().length) > 1 && (
                    <div className="admin-pagination">
                      <button disabled={adminCurrentPage === 1} onClick={() => setAdminCurrentPage(p => p - 1)}>&lsaquo;</button>
                      <span>{adminCurrentPage} / {getTotalPages(getFilteredPromotions().length)}</span>
                      <button disabled={adminCurrentPage === getTotalPages(getFilteredPromotions().length)} onClick={() => setAdminCurrentPage(p => p + 1)}>&rsaquo;</button>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* 리딤 캠페인 관리 (관리자) */}
          {activeMenu === 'admin-redeem' && isAdmin && (
            <div className="info-card admin-section-card wide">
              <div className="card-header">
                <h2 className="card-title">{t('myPage.menu.adminRedeem')}</h2>
                <button className="btn-action btn-edit" onClick={() => openRedeemCampaignModal()} style={{ marginLeft: 'auto' }}>+ 캠페인 추가</button>
              </div>
              {isAdminLoading ? (
                <div className="admin-loading">데이터 로딩 중...</div>
              ) : (
                <>
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>캠페인명</th>
                          <th>상품</th>
                          <th>플랜</th>
                          <th>좌석</th>
                          <th>코드</th>
                          <th>상태</th>
                          <th>유효기간</th>
                          <th>관리</th>
                        </tr>
                      </thead>
                      <tbody>
                        {redeemCampaigns.length > 0 ? (
                          redeemCampaigns.map((campaign) => (
                            <tr key={campaign.id}>
                              <td>{campaign.name}</td>
                              <td>{campaign.productName}</td>
                              <td>{campaign.planName}</td>
                              <td>{campaign.seatsUsed}{campaign.seatLimit ? `/${campaign.seatLimit}` : '/∞'}</td>
                              <td>{campaign.codeCount}개</td>
                              <td>
                                <span className={`status-badge status-${campaign.status.toLowerCase()}`}>
                                  {campaign.status === 'ACTIVE' ? '활성' : campaign.status === 'PAUSED' ? '일시정지' : '종료'}
                                </span>
                              </td>
                              <td>
                                {campaign.validFrom ? new Date(campaign.validFrom).toLocaleDateString() : '-'}
                                {' ~ '}
                                {campaign.validUntil ? new Date(campaign.validUntil).toLocaleDateString() : '무제한'}
                              </td>
                              <td>
                                <div className="action-buttons">
                                  <button className="btn-action btn-edit" onClick={() => openRedeemCampaignModal(campaign)}>수정</button>
                                  <button className="btn-action btn-info" onClick={() => openCodeGenerateModal(campaign)}>코드 생성</button>
                                  <button className="btn-action btn-info" onClick={() => { setSelectedCampaignForCodes(campaign); fetchRedeemCodes(campaign.id); }}>코드 목록</button>
                                  {campaign.status === 'ACTIVE' && (
                                    <>
                                      <button className="btn-action btn-warning" onClick={() => handleCampaignStatusChange(campaign.id, 'pause')}>일시정지</button>
                                      <button className="btn-action btn-danger" onClick={() => handleCampaignStatusChange(campaign.id, 'end')}>종료</button>
                                    </>
                                  )}
                                  {campaign.status === 'PAUSED' && (
                                    <>
                                      <button className="btn-action btn-edit" onClick={() => handleCampaignStatusChange(campaign.id, 'resume')}>재개</button>
                                      <button className="btn-action btn-danger" onClick={() => handleCampaignStatusChange(campaign.id, 'end')}>종료</button>
                                    </>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr><td colSpan={8} className="empty-row">등록된 캠페인이 없습니다.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* 코드 목록 (캠페인 선택 시) */}
                  {selectedCampaignForCodes && redeemCodes.length > 0 && (
                    <div style={{ marginTop: '2rem' }}>
                      <div className="card-header">
                        <h2 className="card-title">{selectedCampaignForCodes.name} - 코드 목록</h2>
                      </div>
                      <div className="admin-table-wrapper">
                        <table className="admin-table">
                          <thead>
                            <tr>
                              <th>유형</th>
                              <th>사용횟수</th>
                              <th>활성</th>
                              <th>만료일</th>
                              <th>생성일</th>
                              <th>관리</th>
                            </tr>
                          </thead>
                          <tbody>
                            {redeemCodes.map((code) => (
                              <tr key={code.id}>
                                <td>{code.codeType}</td>
                                <td>{code.currentRedemptions}/{code.maxRedemptions}</td>
                                <td>{code.active ? '활성' : '비활성'}</td>
                                <td>{code.expiresAt ? new Date(code.expiresAt).toLocaleString() : '-'}</td>
                                <td>{new Date(code.createdAt).toLocaleString()}</td>
                                <td>
                                  {code.active && (
                                    <button className="btn-action btn-danger" onClick={() => handleDeactivateCode(code.id)}>비활성화</button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* 모바일용 로그아웃 버튼 */}
          <div className="mobile-logout-card">
            <button className="logout-btn" onClick={handleLogout}>
              <svg className="logout-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M9 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M16 17L21 12L16 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M21 12H9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              로그아웃
            </button>
          </div>
        </div>
          </main>
        </div>
      </div>

    {/* 로그인 모달 - 미로그인 시 표시 */}
    <LoginModal
      isOpen={loginModalOpen}
      onClose={() => {
        setLoginModalOpen(false);
        navigate('/');
      }}
      onSuccess={() => {
        setLoginModalOpen(false);
        window.location.reload();
      }}
    />

    {/* 라이선스 플랜 모달 */}
    {isLicensePlanModalOpen && (
      <div className="delete-modal-overlay" onClick={closeLicensePlanModal}>
        <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <h3>{editingLicensePlan ? '라이선스 플랜 수정' : '라이선스 플랜 추가'}</h3>
          </div>
          <div className="delete-modal-body">
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>상품 <span style={{ color: '#dc3545' }}>*</span></label>
                <select
                  value={licensePlanForm.productId}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, productId: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}
                >
                  <option value="">상품 선택</option>
                  {products.map(p => (
                    <option key={p.id} value={p.id}>{p.code} - {p.name}</option>
                  ))}
                </select>
              </div>
              <div className="form-group vertical">
                <label>플랜 코드 <span style={{ color: '#dc3545' }}>*</span></label>
                <input
                  type="text"
                  value={licensePlanForm.code}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, code: e.target.value })}
                  placeholder="예: BULC-PRO-1Y"
                  maxLength={64}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>플랜명 <span style={{ color: '#dc3545' }}>*</span></label>
                <input
                  type="text"
                  value={licensePlanForm.name}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, name: e.target.value })}
                  placeholder="예: BUL:C PRO 1년"
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
              <div className="form-group vertical">
                <label>라이선스 유형 <span style={{ color: '#dc3545' }}>*</span></label>
                <select
                  value={licensePlanForm.licenseType}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, licenseType: e.target.value as 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL' })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}
                >
                  <option value="TRIAL">TRIAL (체험판)</option>
                  <option value="SUBSCRIPTION">SUBSCRIPTION (구독)</option>
                  <option value="PERPETUAL">PERPETUAL (영구)</option>
                </select>
              </div>
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>유효기간 (일) <span style={{ color: '#dc3545' }}>*</span></label>
                <input
                  type="number"
                  value={licensePlanForm.durationDays}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, durationDays: parseInt(e.target.value) || 0 })}
                  min={0}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
              <div className="form-group vertical">
                <label>유예기간 (일)</label>
                <input
                  type="number"
                  value={licensePlanForm.graceDays}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, graceDays: parseInt(e.target.value) || 0 })}
                  min={0}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>최대 기기 수 <span style={{ color: '#dc3545' }}>*</span></label>
                <input
                  type="number"
                  value={licensePlanForm.maxActivations}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, maxActivations: parseInt(e.target.value) || 1 })}
                  min={1}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
              <div className="form-group vertical">
                <label>최대 동시 세션 <span style={{ color: '#dc3545' }}>*</span></label>
                <input
                  type="number"
                  value={licensePlanForm.maxConcurrentSessions}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, maxConcurrentSessions: parseInt(e.target.value) || 1 })}
                  min={1}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
                />
              </div>
            </div>
            <div className="form-group vertical" style={{ marginTop: '12px' }}>
              <label>오프라인 허용 일수</label>
              <input
                type="number"
                value={licensePlanForm.allowOfflineDays}
                onChange={(e) => setLicensePlanForm({ ...licensePlanForm, allowOfflineDays: parseInt(e.target.value) || 0 })}
                min={0}
                style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }}
              />
            </div>
            <div className="form-group vertical" style={{ marginTop: '12px' }}>
              <label>설명</label>
              <textarea
                value={licensePlanForm.description}
                onChange={(e) => setLicensePlanForm({ ...licensePlanForm, description: e.target.value })}
                placeholder="플랜 설명을 입력하세요"
                rows={2}
                className="license-plan-textarea"
              />
            </div>
            <div className="form-group vertical" style={{ marginTop: '12px' }}>
              <label>Entitlements</label>
              <textarea
                value={licensePlanForm.entitlements}
                onChange={(e) => setLicensePlanForm({ ...licensePlanForm, entitlements: e.target.value })}
                placeholder="쉼표로 구분 (예: core-simulation, advanced-visualization)"
                rows={2}
                className="license-plan-textarea"
              />
              <small style={{ display: 'block', marginTop: '4px', fontSize: '12px', color: '#888' }}>기능 식별자를 쉼표(,)로 구분하여 입력하세요</small>
            </div>
          </div>
          <div className="delete-modal-footer">
            <button className="cancel-btn" onClick={closeLicensePlanModal}>취소</button>
            <button className="save-btn" onClick={handleLicensePlanSubmit}>
              {editingLicensePlan ? '수정' : '등록'}
            </button>
          </div>
        </div>
      </div>
    )}

    {/* 리딤 캠페인 모달 */}
    {isRedeemCampaignModalOpen && (
      <div className="delete-modal-overlay" onClick={closeRedeemCampaignModal}>
        <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <h3>{editingCampaign ? '캠페인 수정' : '캠페인 추가'}</h3>
          </div>
          <div className="delete-modal-body">
            <div className="form-group vertical">
              <label>캠페인명 <span style={{ color: '#dc3545' }}>*</span></label>
              <input type="text" value={redeemCampaignForm.name}
                onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, name: e.target.value })}
                placeholder="예: 2025 신년 프로모션"
                style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
            </div>
            <div className="form-group vertical" style={{ marginTop: '12px' }}>
              <label>설명</label>
              <textarea value={redeemCampaignForm.description}
                onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, description: e.target.value })}
                placeholder="캠페인 설명" rows={2}
                className="license-plan-textarea" />
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>상품 <span style={{ color: '#dc3545' }}>*</span></label>
                <select value={redeemCampaignForm.productId}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, productId: e.target.value, licensePlanId: '' })}
                  disabled={!!editingCampaign}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                  <option value="">상품 선택</option>
                  {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>
              <div className="form-group vertical">
                <label>라이선스 플랜 <span style={{ color: '#dc3545' }}>*</span></label>
                <select value={redeemCampaignForm.licensePlanId}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, licensePlanId: e.target.value })}
                  disabled={!!editingCampaign || !redeemCampaignForm.productId}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                  <option value="">플랜 선택</option>
                  {filteredLicensePlansForRedeem.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.licenseType})</option>)}
                </select>
              </div>
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>좌석 한도</label>
                <input type="number" value={redeemCampaignForm.seatLimit}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, seatLimit: e.target.value })}
                  placeholder="무제한" min={0}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
              <div className="form-group vertical">
                <label>사용자별 한도</label>
                <input type="number" value={redeemCampaignForm.perUserLimit}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, perUserLimit: e.target.value })}
                  min={1}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
            </div>
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>시작일</label>
                <input type="datetime-local" value={redeemCampaignForm.validFrom}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, validFrom: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
              <div className="form-group vertical">
                <label>종료일</label>
                <input type="datetime-local" value={redeemCampaignForm.validUntil}
                  onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, validUntil: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
            </div>
            <div className="form-group vertical" style={{ marginTop: '12px' }}>
              <label>사용 용도</label>
              <select value={redeemCampaignForm.usageCategory}
                onChange={(e) => setRedeemCampaignForm({ ...redeemCampaignForm, usageCategory: e.target.value })}
                style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                <option value="COMMERCIAL">상업용</option>
                <option value="RESEARCH_NON_COMMERCIAL">비상업 연구용</option>
                <option value="EDUCATION">교육용</option>
                <option value="INTERNAL_EVAL">내부 평가용</option>
              </select>
            </div>
          </div>
          <div className="delete-modal-footer">
            <button className="cancel-btn" onClick={closeRedeemCampaignModal}>취소</button>
            <button className="save-btn" onClick={handleRedeemCampaignSubmit}>
              {editingCampaign ? '수정' : '등록'}
            </button>
          </div>
        </div>
      </div>
    )}

    {/* 코드 생성 모달 */}
    {isCodeGenerateModalOpen && selectedCampaignForCodes && (
      <div className="delete-modal-overlay" onClick={() => setIsCodeGenerateModalOpen(false)}>
        <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <h3>리딤 코드 생성 - {selectedCampaignForCodes.name}</h3>
          </div>
          <div className="delete-modal-body">
            <div className="form-group vertical">
              <label>코드 타입</label>
              <div style={{ display: 'flex', gap: '1rem', marginTop: '4px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                  <input type="radio" value="RANDOM" checked={codeGenerateForm.codeType === 'RANDOM'}
                    onChange={() => setCodeGenerateForm({ ...codeGenerateForm, codeType: 'RANDOM' })} /> 랜덤 코드
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                  <input type="radio" value="CUSTOM" checked={codeGenerateForm.codeType === 'CUSTOM'}
                    onChange={() => setCodeGenerateForm({ ...codeGenerateForm, codeType: 'CUSTOM' })} /> 커스텀 코드
                </label>
              </div>
            </div>
            {codeGenerateForm.codeType === 'RANDOM' ? (
              <div className="form-group vertical" style={{ marginTop: '12px' }}>
                <label>생성 수량</label>
                <input type="number" value={codeGenerateForm.count}
                  onChange={(e) => setCodeGenerateForm({ ...codeGenerateForm, count: e.target.value })}
                  min={1} max={1000}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
            ) : (
              <div className="form-group vertical" style={{ marginTop: '12px' }}>
                <label>커스텀 코드</label>
                <input type="text" value={codeGenerateForm.customCode}
                  onChange={(e) => setCodeGenerateForm({ ...codeGenerateForm, customCode: e.target.value.toUpperCase() })}
                  placeholder="영문 대문자, 숫자 8~64자"
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
            )}
            <div className="license-plan-form-row">
              <div className="form-group vertical">
                <label>코드당 최대 사용횟수</label>
                <input type="number" value={codeGenerateForm.maxRedemptions}
                  onChange={(e) => setCodeGenerateForm({ ...codeGenerateForm, maxRedemptions: e.target.value })}
                  min={1}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
              <div className="form-group vertical">
                <label>코드 만료일</label>
                <input type="datetime-local" value={codeGenerateForm.expiresAt}
                  onChange={(e) => setCodeGenerateForm({ ...codeGenerateForm, expiresAt: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
            </div>
          </div>
          <div className="delete-modal-footer">
            <button className="cancel-btn" onClick={() => setIsCodeGenerateModalOpen(false)}>취소</button>
            <button className="save-btn" onClick={handleGenerateCodes}>생성</button>
          </div>
        </div>
      </div>
    )}

    {/* 생성된 코드 결과 모달 */}
    {isGeneratedCodesModalOpen && (
      <div className="delete-modal-overlay" onClick={() => setIsGeneratedCodesModalOpen(false)}>
        <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <h3>생성된 리딤 코드</h3>
          </div>
          <div className="delete-modal-body">
            <p style={{ color: '#e53e3e', fontWeight: 'bold', marginBottom: '1rem' }}>
              이 코드는 이 화면에서만 확인할 수 있습니다. 반드시 복사하거나 다운로드하세요.
            </p>
            <div style={{ maxHeight: '300px', overflow: 'auto', background: '#f5f5f5', padding: '1rem', borderRadius: '8px', fontFamily: 'monospace', fontSize: '14px' }}>
              {generatedCodes.map((code, i) => <div key={i}>{code}</div>)}
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
              <button className="save-btn" onClick={copyGeneratedCodes}>전체 복사</button>
              <button className="save-btn" onClick={downloadCodesAsCsv}>CSV 다운로드</button>
            </div>
          </div>
          <div className="delete-modal-footer">
            <button className="cancel-btn" onClick={() => setIsGeneratedCodesModalOpen(false)}>닫기</button>
          </div>
        </div>
      </div>
    )}

    {/* 계정 삭제 확인 모달 */}
    {isDeleteModalOpen && (
      <div className="delete-modal-overlay" onClick={() => {
        setIsDeleteModalOpen(false);
        setDeleteConfirmText('');
        setDeleteModalError('');
      }}>
        <div className="delete-modal" onClick={(e) => e.stopPropagation()}>
          <div className="delete-modal-header">
            <svg className="warning-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 9V13M12 17H12.01M5.07183 19H18.9282C20.4678 19 21.4301 17.3333 20.6603 16L13.7321 4C12.9623 2.66667 11.0377 2.66667 10.2679 4L3.33975 16C2.56995 17.3333 3.53223 19 5.07183 19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <h3>계정 삭제</h3>
          </div>
          <div className="delete-modal-body">
            <p className="warning-text">
              정말로 계정을 삭제하시겠습니까?<br />
              이 작업은 되돌릴 수 없습니다.
            </p>
            <ul className="delete-warning-list">
              <li>모든 개인 정보가 삭제됩니다</li>
              <li>보유한 라이선스가 모두 비활성화됩니다</li>
              <li>결제 내역은 법적 보관 기간 동안 유지됩니다</li>
            </ul>
            <div className="confirm-input-group">
              <label>확인을 위해 <strong>'계정삭제'</strong>를 입력해주세요</label>
              <input
                type="text"
                value={deleteConfirmText}
                onChange={(e) => {
                  setDeleteConfirmText(e.target.value);
                  setDeleteModalError('');
                }}
                placeholder="계정삭제"
                className="confirm-input"
              />
            </div>
            {deleteModalError && (
              <p className="delete-modal-error">{deleteModalError}</p>
            )}
          </div>
          <div className="delete-modal-footer">
            <button
              className="cancel-btn"
              onClick={() => {
                setIsDeleteModalOpen(false);
                setDeleteConfirmText('');
                setDeleteModalError('');
              }}
            >
              취소
            </button>
            <button
              className="confirm-delete-btn"
              onClick={handleDeleteAccount}
              disabled={deleteConfirmText !== '계정삭제' || isDeleting}
            >
              {isDeleting ? '삭제 중...' : '계정 삭제'}
            </button>
          </div>
        </div>
      </div>
    )}
    </>
  );
};

export default MyPage;
