import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import Header from '../../components/Header';
import LoginModal from '../../components/LoginModal';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import './MyPage.css';

// 메뉴 타입 정의
type MenuSection = 'profile' | 'account' | 'subscription' | 'payment' | 'admin-users' | 'admin-payments' | 'admin-products' | 'admin-licenses' | 'admin-promotions';

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
  const { isLoggedIn, isAuthReady, logout, user } = useAuth();
  const { changeLanguage: changeGlobalLanguage } = useLanguage();

  // 관리자 여부 (rolesCode '000'만)
  const isSystemAdmin = user?.rolesCode === '000';

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

  // 사이드바 메뉴 상태
  const [activeMenu, setActiveMenu] = useState<MenuSection>('profile');

  // 관리자 데이터 상태
  const [adminUsers, setAdminUsers] = useState<AdminUser[]>([]);
  const [adminLicenses, setAdminLicenses] = useState<AdminLicense[]>([]);
  const [adminPayments, setAdminPayments] = useState<AdminPayment[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [isAdminLoading, setIsAdminLoading] = useState(false);

  // 관리자 검색/페이징
  const [adminSearchQuery, setAdminSearchQuery] = useState('');
  const [adminAppliedSearch, setAdminAppliedSearch] = useState('');
  const [adminCurrentPage, setAdminCurrentPage] = useState(1);
  const ADMIN_ITEMS_PER_PAGE = 10;

  // 로그인 체크 - 미로그인 시 로그인 모달 표시
  useEffect(() => {
    if (isAuthReady && !isLoggedIn) {
      setLoginModalOpen(true);
    }
  }, [isAuthReady, isLoggedIn]);

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

  // 관리자 메뉴 변경 시 데이터 로드
  useEffect(() => {
    if (!isSystemAdmin || !activeMenu.startsWith('admin-')) return;

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
        }
      } catch (error) {
        console.error('관리자 데이터 로드 실패:', error);
      } finally {
        setIsAdminLoading(false);
      }
    };

    loadAdminData();
  }, [activeMenu, isSystemAdmin]);

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

  // 페이징
  const getPaginatedData = <T,>(data: T[]): T[] => {
    const startIndex = (adminCurrentPage - 1) * ADMIN_ITEMS_PER_PAGE;
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
      <Header logoText="BULC" hideUserMenu={true} />
      <div className="mypage-container">
        <div className="mypage-layout">
          {/* 왼쪽 사이드바 */}
          <aside className="mypage-sidebar">
            <div className="sidebar-header">
              <button className="back-btn" onClick={() => navigate(-1)}>
                <svg className="back-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M19 12H5M5 12L12 19M5 12L12 5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                {t('myPage.back')}
              </button>
              <h1 className="mypage-title">{t('myPage.title')}</h1>
            </div>

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
                    onClick={() => setActiveMenu('profile')}
                  >
                    {t('myPage.menu.profile')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'account' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('account')}
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
                    onClick={() => setActiveMenu('subscription')}
                  >
                    {t('myPage.menu.subscription')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'payment' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('payment')}
                  >
                    {t('myPage.menu.payment')}
                  </button>
                </div>
              </div>

              {/* 관리자 정보 대메뉴 (관리자만 표시) */}
              {isSystemAdmin && (
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
                    onClick={() => setActiveMenu('admin-users')}
                  >
                    {t('myPage.menu.adminUsers')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-payments' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('admin-payments')}
                  >
                    {t('myPage.menu.adminPayments')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-products' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('admin-products')}
                  >
                    {t('myPage.menu.adminProducts')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-licenses' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('admin-licenses')}
                  >
                    {t('myPage.menu.adminLicenses')}
                  </button>
                  <button
                    className={`menu-child ${activeMenu === 'admin-promotions' ? 'active' : ''}`}
                    onClick={() => setActiveMenu('admin-promotions')}
                  >
                    {t('myPage.menu.adminPromotions')}
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
                      {isSystemAdmin && isTestMode && (
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

            {/* 구독 테스트 패널 (관리자 전용) */}
            {isSystemAdmin && (
              <div className="subscription-test-panel">
                <div className="test-panel-header">
                  <button
                    className={`test-mode-toggle ${isTestMode ? 'active' : ''}`}
                    onClick={() => setIsTestMode(!isTestMode)}
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                    </svg>
                    테스트 모드 {isTestMode ? 'ON' : 'OFF'}
                  </button>
                </div>
                {isTestMode && (
                  <div className="test-panel-body">
                    <div className="test-info">
                      개발 환경 전용 테스트 기능입니다. 구독 갱신 시스템을 수동으로 실행할 수 있습니다.
                    </div>
                    <div className="global-test-buttons">
                      <button
                        className="test-btn global"
                        onClick={handleProcessRenewals}
                        disabled={testLoading !== null}
                      >
                        {testLoading === 'process-renewals' ? '실행중...' : '갱신 처리 실행'}
                      </button>
                      <button
                        className="test-btn global"
                        onClick={handleRetryFailed}
                        disabled={testLoading !== null}
                      >
                        {testLoading === 'retry-failed' ? '실행중...' : '실패 결제 재시도'}
                      </button>
                      <button
                        className="test-btn global"
                        onClick={handleProcessExpired}
                        disabled={testLoading !== null}
                      >
                        {testLoading === 'process-expired' ? '실행중...' : '만료 처리 실행'}
                      </button>
                    </div>
                  </div>
                )}
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

          {/* 개발자 미리보기 (관리자 전용) */}
          {isSystemAdmin && (
            <div className="info-card dev-preview-card">
              <div className="card-header">
                <h2 className="card-title">
                  <svg className="dev-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M16 18L22 12L16 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M8 6L2 12L8 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  페이지 미리보기
                </h2>
                <button
                  className={`preview-toggle-btn ${isPreviewOpen ? 'open' : ''}`}
                  onClick={() => setIsPreviewOpen(!isPreviewOpen)}
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </button>
              </div>

              {isPreviewOpen && (
                <div className="preview-menu">
                  <div className="preview-section">
                    <h4 className="preview-section-title">오류 페이지 미리보기</h4>
                    <div className="preview-buttons">
                      <button
                        className="preview-btn error-btn-400"
                        onClick={() => navigate('/error', { state: { errorCode: 400 } })}
                      >
                        400 잘못된 요청
                      </button>
                      <button
                        className="preview-btn error-btn-401"
                        onClick={() => navigate('/error', { state: { errorCode: 401 } })}
                      >
                        401 인증 필요
                      </button>
                      <button
                        className="preview-btn error-btn-403"
                        onClick={() => navigate('/error', { state: { errorCode: 403 } })}
                      >
                        403 접근 거부
                      </button>
                      <button
                        className="preview-btn error-btn-404"
                        onClick={() => navigate('/error', { state: { errorCode: 404 } })}
                      >
                        404 페이지 없음
                      </button>
                      <button
                        className="preview-btn error-btn-500"
                        onClick={() => navigate('/error', { state: { errorCode: 500 } })}
                      >
                        500 서버 오류
                      </button>
                      <button
                        className="preview-btn error-btn-502"
                        onClick={() => navigate('/error', { state: { errorCode: 502 } })}
                      >
                        502 연결 실패
                      </button>
                      <button
                        className="preview-btn error-btn-503"
                        onClick={() => navigate('/error', { state: { errorCode: 503 } })}
                      >
                        503 서비스 불가
                      </button>
                    </div>
                  </div>
                  <div className="preview-note">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="16" x2="12" y2="12"></line>
                      <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                    <span>이 메뉴는 관리자에게만 표시됩니다.</span>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 관리자 섹션 - 사용자 관리 */}
          {activeMenu === 'admin-users' && isSystemAdmin && (
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
          {activeMenu === 'admin-payments' && isSystemAdmin && (
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
          {activeMenu === 'admin-products' && isSystemAdmin && (
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
          {activeMenu === 'admin-licenses' && isSystemAdmin && (
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
          )}

          {/* 관리자 섹션 - 프로모션 관리 */}
          {activeMenu === 'admin-promotions' && isSystemAdmin && (
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
