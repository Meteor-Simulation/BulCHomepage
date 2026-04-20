import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import Header from '../../components/Header';
import LoginModal from '../../components/LoginModal';
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import {
  MenuSection,
  UserInfo, License, Activation, Subscription, BillingKey,
  AdminUser, AdminLicense, AdminPayment, Product, PricePlan,
  Promotion, LicensePlan, RedeemCampaign, RedeemCodeItem,
} from './types';
import { VALID_MENU_SECTIONS } from './constants';
import {
  ProfilePanel, AccountPanel, SubscriptionPanel, PaymentPanel, RedeemPanel,
  AdminUsersPanel, AdminPaymentsPanel, AdminProductsPanel,
  AdminLicensesPanel, AdminPromotionsPanel, AdminRedeemPanel,
} from './panels';
import './MyPage.css';

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
  const [selectedLanguage, setSelectedLanguage] = useState(() => localStorage.getItem('language') || 'ko');
  const [tempCountry, setTempCountry] = useState('KR');
  const [tempLanguage, setTempLanguage] = useState(() => localStorage.getItem('language') || 'ko');

  // 메시지
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

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
    success: boolean; licenseId?: string; licenseKey?: string;
    productName?: string; planName?: string; validUntil?: string; errorMessage?: string;
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
    customCode: '', count: '10', maxRedemptions: '1', expiresAt: '', allowedEmailDomain: '',
  });
  const [generatedCodes, setGeneratedCodes] = useState<string[]>([]);
  const [isGeneratedCodesModalOpen, setIsGeneratedCodesModalOpen] = useState(false);

  // 라이선스 플랜 모달 상태
  const [isLicensePlanModalOpen, setIsLicensePlanModalOpen] = useState(false);
  const [editingLicensePlan, setEditingLicensePlan] = useState<LicensePlan | null>(null);
  const [licensePlanForm, setLicensePlanForm] = useState({
    productId: '', code: '', name: '', description: '',
    licenseType: 'SUBSCRIPTION' as 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL',
    durationDays: 365, graceDays: 7, maxActivations: 1,
    maxConcurrentSessions: 1, allowOfflineDays: 0, entitlements: '',
  });

  // 라이선스 플랜 검색 (기존 admin 검색과 독립)
  const [licensePlanSearchQuery, setLicensePlanSearchQuery] = useState('');
  const [licensePlanAppliedSearch, setLicensePlanAppliedSearch] = useState('');
  const [licensePlanCurrentPage, setLicensePlanCurrentPage] = useState(1);

  // 관리자 검색/페이징
  const [adminSearchQuery, setAdminSearchQuery] = useState('');
  const [adminAppliedSearch, setAdminAppliedSearch] = useState('');
  const [adminCurrentPage, setAdminCurrentPage] = useState(1);

  // ========== Effects ==========

  // 로그인 체크 - 미인증 시 홈으로 이동 + 알림
  useEffect(() => {
    if (isAuthReady && !isLoggedIn) {
      sessionStorage.setItem('sessionExpiredAlert', 'true');
      navigate('/', { replace: true });
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
      try {
        const response = await fetch(`${API_URL}/api/users/me`, {
          credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          const data = await response.json();
          setUserInfo(data);
          setEditName(data.name || '');
          setEditPhone(formatPhoneNumber(data.phone) || '');
          setSelectedCountry(data.country || 'KR');
          if (data.language) {
            setSelectedLanguage(data.language);
            setTempLanguage(data.language);
            changeGlobalLanguage(data.language);
          }
        }
      } catch (error) {
        // 사용자 정보 로드 실패
      } finally {
        setIsLoading(false);
      }
    };
    if (isLoggedIn) fetchUserInfo();
  }, [isLoggedIn]);

  // 라이선스 정보 로드
  useEffect(() => {
    const fetchLicenses = async () => {
      setIsLoadingLicenses(true);
      try {
        const response = await fetch(`${API_URL}/api/v1/me/licenses`, {
          credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          const data = await response.json();
          setLicenses(data.licenses || []);
        }
      } catch (error) {
        // 라이선스 정보 로드 실패
      } finally {
        setIsLoadingLicenses(false);
      }
    };
    if (isLoggedIn) fetchLicenses();
  }, [isLoggedIn]);

  // 구독 정보 로드
  useEffect(() => {
    const fetchSubscriptions = async () => {
      setIsLoadingSubscriptions(true);
      try {
        const response = await fetch(`${API_URL}/api/subscriptions`, {
          credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          const data = await response.json();
          setSubscriptions(data.data || []);
        }
      } catch (error) {
        // 구독 정보 로드 실패
      } finally {
        setIsLoadingSubscriptions(false);
      }
    };
    if (isLoggedIn) fetchSubscriptions();
  }, [isLoggedIn]);

  // 등록된 카드 로드
  useEffect(() => {
    const fetchBillingKeys = async () => {
      setIsLoadingBillingKeys(true);
      try {
        const response = await fetch(`${API_URL}/api/subscriptions/billing-keys`, {
          credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          const data = await response.json();
          setBillingKeys(data.data || []);
        }
      } catch (error) {
        // 등록된 카드 로드 실패
      } finally {
        setIsLoadingBillingKeys(false);
      }
    };
    if (isLoggedIn) fetchBillingKeys();
  }, [isLoggedIn]);

  // 관리자 메뉴 변경 시 데이터 로드
  useEffect(() => {
    if (!isAdmin || !activeMenu.startsWith('admin-')) return;

    const loadAdminData = async () => {
      setIsAdminLoading(true);
      setAdminSearchQuery('');
      setAdminAppliedSearch('');
      setAdminCurrentPage(1);
      try {
        switch (activeMenu) {
          case 'admin-users': await fetchAdminUsers(); break;
          case 'admin-licenses':
            await fetchAdminLicenses();
            await fetchLicensePlans();
            await fetchProducts();
            break;
          case 'admin-payments': await fetchAdminPayments(); break;
          case 'admin-products':
            await fetchProducts();
            await fetchPricePlans();
            break;
          case 'admin-promotions':
            await fetchPromotions();
            await fetchProducts();
            break;
          case 'admin-redeem':
            await fetchRedeemCampaigns();
            await fetchProducts();
            await fetchLicensePlans();
            break;
        }
      } catch (error) {
        // 관리자 데이터 로드 실패
      } finally {
        setIsAdminLoading(false);
      }
    };
    loadAdminData();
  }, [activeMenu, isAdmin]);

  // ========== 유틸리티 함수 ==========

  const showSuccess = (message: string) => {
    setSuccessMessage(message);
    setTimeout(() => setSuccessMessage(''), 3000);
  };

  const showError = (message: string) => {
    setErrorMessage(message);
    setTimeout(() => setErrorMessage(''), 3000);
  };

  const validatePassword = (password: string): string | null => {
    if (password.length < 8) return '비밀번호는 8자 이상이어야 합니다.';
    if (!/[a-zA-Z]/.test(password)) return '비밀번호는 영문을 포함해야 합니다.';
    if (!/[0-9]/.test(password)) return '비밀번호는 숫자를 포함해야 합니다.';
    if (!/[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(password)) return '비밀번호는 특수문자를 포함해야 합니다.';
    return null;
  };

  const formatPrice = (price: number, currency: string) => {
    if (currency === 'KRW') return new Intl.NumberFormat('ko-KR').format(price) + '원';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(price);
  };

  const filteredLicensePlansForRedeem = useMemo(() => {
    if (!redeemCampaignForm.productId) return [];
    return licensePlans.filter(p => p.productId === redeemCampaignForm.productId && !p.deleted);
  }, [licensePlans, redeemCampaignForm.productId]);

  // ========== 관리자 데이터 조회 함수 ==========

  const fetchAdminUsers = async () => {
    const response = await fetch(`${API_URL}/api/admin/users`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setAdminUsers(await response.json());
  };

  const fetchAdminLicenses = async () => {
    const response = await fetch(`${API_URL}/api/admin/license-list`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setAdminLicenses(await response.json());
  };

  const fetchAdminPayments = async () => {
    const response = await fetch(`${API_URL}/api/admin/payments`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setAdminPayments(await response.json());
  };

  const fetchProducts = async () => {
    const response = await fetch(`${API_URL}/api/admin/products`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setProducts(await response.json());
  };

  const fetchPricePlans = async () => {
    const response = await fetch(`${API_URL}/api/admin/price-plans`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setPricePlans(await response.json());
  };

  const fetchPromotions = async () => {
    const response = await fetch(`${API_URL}/api/promotions`, { credentials: 'include' as RequestCredentials });
    if (response.ok) setPromotions(await response.json());
  };

  const fetchLicensePlans = async () => {
    const response = await fetch(`${API_URL}/api/v1/admin/license-plans?size=100`, { credentials: 'include' as RequestCredentials });
    if (response.ok) { const data = await response.json(); setLicensePlans(data.content || []); }
  };

  const fetchRedeemCampaigns = async () => {
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns?size=100`, { credentials: 'include' as RequestCredentials });
    if (response.ok) { const data = await response.json(); setRedeemCampaigns(data.content || []); }
  };

  const fetchRedeemCodes = async (campaignId: string) => {
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/${campaignId}/codes?size=100`, { credentials: 'include' as RequestCredentials });
    if (response.ok) { const data = await response.json(); setRedeemCodes(data.content || []); }
  };

  // ========== 프로필 핸들러 ==========

  const handleSaveProfile = async () => {
    try {
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
        body: JSON.stringify({ name: editName, phone: cleanPhoneNumber(editPhone), country: userInfo.country, language: tempLanguage }),
      });
      if (response.ok) {
        const data = await response.json();
        setUserInfo(data);
        setSelectedLanguage(tempLanguage);
        changeGlobalLanguage(tempLanguage);
        setIsEditingProfile(false);
        showSuccess(t('myPage.profileSaved'));
      } else { showError('프로필 저장에 실패했습니다.'); }
    } catch { showError('프로필 저장 중 오류가 발생했습니다.'); }
  };

  const handleCancelProfile = () => {
    setEditName(userInfo.name || '');
    setEditPhone(formatPhoneNumber(userInfo.phone) || '');
    setTempLanguage(selectedLanguage);
    setIsEditingProfile(false);
  };

  // ========== 설정 핸들러 ==========

  const handleStartEditSettings = () => { setTempCountry(selectedCountry); setIsEditingSettings(true); };

  const handleSaveSettings = async () => {
    try {
      const response = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
        body: JSON.stringify({ name: userInfo.name, phone: userInfo.phone, country: tempCountry }),
      });
      if (response.ok) {
        const data = await response.json();
        setUserInfo(data); setSelectedCountry(tempCountry); setIsEditingSettings(false);
        showSuccess('결제 통화가 저장되었습니다.');
      } else { showError('설정 저장에 실패했습니다.'); }
    } catch { showError('설정 저장 중 오류가 발생했습니다.'); }
  };

  const handleCancelSettings = () => { setTempCountry(selectedCountry); setIsEditingSettings(false); };

  // ========== 비밀번호 핸들러 ==========

  const handleChangePassword = async () => {
    const validationError = validatePassword(newPassword);
    if (validationError) { setPasswordError(validationError); return; }
    if (newPassword !== confirmPassword) { setPasswordError('새 비밀번호가 일치하지 않습니다.'); return; }
    try {
      const response = await fetch(`${API_URL}/api/users/me/password`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      const data = await response.json();
      if (response.ok && data.success) {
        setIsEditingPassword(false); setCurrentPassword(''); setNewPassword('');
        setConfirmPassword(''); setPasswordError('');
        alert('비밀번호가 변경되었습니다. 보안을 위해 다시 로그인해주세요.');
        logout(); navigate('/');
      } else { setPasswordError(data.message || '비밀번호 변경에 실패했습니다.'); }
    } catch { setPasswordError('비밀번호 변경 중 오류가 발생했습니다.'); }
  };

  const handleCancelPassword = () => {
    setCurrentPassword(''); setNewPassword(''); setConfirmPassword(''); setPasswordError('');
    setShowCurrentPassword(false); setShowNewPassword(false); setShowConfirmPassword(false);
    setIsEditingPassword(false);
  };

  // ========== 기기 관리 핸들러 ==========

  const handleToggleDeviceList = async (licenseId: string) => {
    if (expandedLicenseId === licenseId) { setExpandedLicenseId(null); return; }
    setExpandedLicenseId(licenseId);
    if (activations[licenseId]) return;
    setIsLoadingActivations(licenseId);
    try {
      const response = await fetch(`${API_URL}/api/v1/licenses/${licenseId}`, { credentials: 'include' as RequestCredentials });
      if (response.ok) { const data = await response.json(); setActivations(prev => ({ ...prev, [licenseId]: data.activations || [] })); }
    } catch (error) { /* 기기 목록 로드 실패 */ }
    finally { setIsLoadingActivations(null); }
  };

  const refreshActivations = async (licenseId: string) => {
    try {
      const response = await fetch(`${API_URL}/api/v1/licenses/${licenseId}`, { credentials: 'include' as RequestCredentials });
      if (response.ok) { const data = await response.json(); setActivations(prev => ({ ...prev, [licenseId]: data.activations || [] })); }
    } catch (error) { /* 기기 목록 재조회 실패 */ }
  };

  const handleDeactivateDevice = async (licenseId: string, deviceFingerprint: string) => {
    if (!window.confirm('이 기기를 해제하시겠습니까?')) return;
    try {
      const response = await fetch(`${API_URL}/api/v1/licenses/${licenseId}/activations/${deviceFingerprint}`, {
        method: 'DELETE', credentials: 'include' as RequestCredentials,
      });
      if (response.ok) {
        showSuccess('기기가 해제되었습니다.');
        await refreshActivations(licenseId);
        const licResponse = await fetch(`${API_URL}/api/v1/me/licenses`, { credentials: 'include' as RequestCredentials });
        if (licResponse.ok) { const licData = await licResponse.json(); setLicenses(licData.licenses || []); }
      } else { showError('기기 해제에 실패했습니다.'); }
    } catch { showError('기기 해제 중 오류가 발생했습니다.'); }
  };

  // ========== 계정 삭제 핸들러 ==========

  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== '계정삭제') { setDeleteModalError('확인 문구를 정확히 입력해주세요.'); return; }
    setIsDeleting(true); setDeleteModalError('');
    try {
      const response = await fetch(`${API_URL}/api/users/me/deactivate`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
      });
      if (response.ok) {
        setIsDeleteModalOpen(false); setDeleteConfirmText('');
        logout(); navigate('/', { state: { message: '계정이 삭제되었습니다.' } });
      } else {
        const errorData = await response.json();
        setDeleteModalError(errorData.message || '계정 삭제에 실패했습니다.');
      }
    } catch { setDeleteModalError('계정 삭제 중 오류가 발생했습니다.'); }
    finally { setIsDeleting(false); }
  };

  // ========== 구독 핸들러 ==========

  const handleToggleAutoRenew = async (subscriptionId: number, currentState: boolean) => {
    try {
      if (currentState) {
        const response = await fetch(`${API_URL}/api/subscriptions/${subscriptionId}/auto-renew`, {
          method: 'DELETE', credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          setSubscriptions(prev => prev.map(sub => sub.id === subscriptionId ? { ...sub, autoRenew: false, nextBillingDate: null } : sub));
          showSuccess('자동 갱신이 비활성화되었습니다.');
        } else { showError('자동 갱신 비활성화에 실패했습니다.'); }
      } else {
        const defaultCard = billingKeys.find(b => b.isDefault);
        if (!defaultCard) { showError('기본 결제 수단을 먼저 등록해주세요.'); return; }
        const response = await fetch(`${API_URL}/api/subscriptions/${subscriptionId}/auto-renew?billingKeyId=${defaultCard.id}&billingCycle=YEARLY`, {
          method: 'POST', credentials: 'include' as RequestCredentials,
        });
        if (response.ok) {
          const data = await response.json();
          setSubscriptions(prev => prev.map(sub => sub.id === subscriptionId ? data.data : sub));
          showSuccess('자동 갱신이 활성화되었습니다.');
        } else { showError('자동 갱신 활성화에 실패했습니다.'); }
      }
    } catch { showError('자동 갱신 설정 중 오류가 발생했습니다.'); }
  };

  const handleSetDefaultCard = async (billingKeyId: number) => {
    try {
      const response = await fetch(`${API_URL}/api/subscriptions/billing-keys/${billingKeyId}/default`, {
        method: 'PATCH', credentials: 'include' as RequestCredentials,
      });
      if (response.ok) {
        setBillingKeys(prev => prev.map(b => ({ ...b, isDefault: b.id === billingKeyId })));
        showSuccess('기본 결제 수단이 변경되었습니다.');
      } else { showError('기본 결제 수단 변경에 실패했습니다.'); }
    } catch { showError('기본 결제 수단 변경 중 오류가 발생했습니다.'); }
  };

  const handleDeleteCard = async (billingKeyId: number) => {
    if (!window.confirm('이 카드를 삭제하시겠습니까?')) return;
    try {
      const response = await fetch(`${API_URL}/api/subscriptions/billing-keys/${billingKeyId}`, {
        method: 'DELETE', credentials: 'include' as RequestCredentials,
      });
      if (response.ok) { setBillingKeys(prev => prev.filter(b => b.id !== billingKeyId)); showSuccess('카드가 삭제되었습니다.'); }
      else { showError('카드 삭제에 실패했습니다.'); }
    } catch { showError('카드 삭제 중 오류가 발생했습니다.'); }
  };

  // ========== 구독 테스트 함수 (개발 환경 전용) ==========

  const handleSimulateNearExpiry = async (subscriptionId: number, days: number = 3) => {
    setTestLoading(`simulate-${subscriptionId}`);
    try {
      const response = await fetch(`${API_URL}/api/test/subscriptions/${subscriptionId}/simulate-near-expiry?daysUntilExpiry=${days}`, {
        method: 'POST', credentials: 'include' as RequestCredentials,
      });
      if (response.ok) {
        showSuccess(`구독 종료일이 ${days}일 후로 설정되었습니다.`);
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, { credentials: 'include' as RequestCredentials });
        if (subsResponse.ok) { const subsData = await subsResponse.json(); setSubscriptions(subsData.data || []); }
      } else { const err = await response.json(); showError(err.error || '테스트 실행 실패'); }
    } catch { showError('테스트 실행 중 오류 발생'); }
    finally { setTestLoading(null); }
  };

  const handleMakeDueNow = async (subscriptionId: number) => {
    setTestLoading(`due-${subscriptionId}`);
    try {
      const response = await fetch(`${API_URL}/api/test/subscriptions/${subscriptionId}/make-due-now`, {
        method: 'POST', credentials: 'include' as RequestCredentials,
      });
      if (response.ok) {
        showSuccess('구독이 즉시 갱신 대상으로 설정되었습니다.');
        const subsResponse = await fetch(`${API_URL}/api/subscriptions`, { credentials: 'include' as RequestCredentials });
        if (subsResponse.ok) { const subsData = await subsResponse.json(); setSubscriptions(subsData.data || []); }
      } else { const err = await response.json(); showError(err.error || '테스트 실행 실패'); }
    } catch { showError('테스트 실행 중 오류 발생'); }
    finally { setTestLoading(null); }
  };

  // ========== 리딤 코드 핸들러 ==========

  const handleRedeemSubmit = async () => {
    if (!redeemCode.trim()) return;
    setIsRedeeming(true); setRedeemResult(null);
    try {
      const response = await fetch(`${API_URL}/api/v1/redeem`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
        body: JSON.stringify({ code: redeemCode }),
      });
      if (response.ok) {
        const data = await response.json();
        setRedeemResult({ success: true, licenseId: data.licenseId, licenseKey: data.licenseKey, productName: data.productName, planName: data.planName, validUntil: data.validUntil });
        setRedeemCode('');
      } else {
        const error = await response.json();
        const errorMessages: Record<string, string> = {
          REDEEM_CODE_INVALID: '유효하지 않은 쿠폰 코드 형식입니다.', REDEEM_CODE_NOT_FOUND: '쿠폰 코드를 찾을 수 없습니다.',
          REDEEM_CODE_EXPIRED: '만료된 쿠폰 코드입니다.', REDEEM_CODE_DISABLED: '비활성화된 쿠폰 코드입니다.',
          REDEEM_CODE_DEPLETED: '사용 횟수가 소진된 코드입니다.', REDEEM_CAMPAIGN_FULL: '캠페인 발급 한도에 도달했습니다.',
          REDEEM_USER_LIMIT_EXCEEDED: '사용자별 사용 한도를 초과했습니다.', REDEEM_CAMPAIGN_NOT_ACTIVE: '현재 이용할 수 없는 캠페인입니다.',
          REDEEM_RATE_LIMITED: '요청이 너무 빈번합니다. 잠시 후 다시 시도해주세요.',
        };
        setRedeemResult({ success: false, errorMessage: errorMessages[error.error] || error.message || '코드 등록에 실패했습니다.' });
      }
    } catch { setRedeemResult({ success: false, errorMessage: '네트워크 오류가 발생했습니다.' }); }
    finally { setIsRedeeming(false); }
  };

  // ========== 리딤 캠페인 관리자 핸들러 ==========

  const openRedeemCampaignModal = (campaign?: RedeemCampaign) => {
    if (campaign) {
      setEditingCampaign(campaign);
      setRedeemCampaignForm({
        name: campaign.name, description: campaign.description || '', productId: campaign.productId,
        licensePlanId: campaign.licensePlanId, usageCategory: campaign.usageCategory,
        seatLimit: campaign.seatLimit?.toString() || '', perUserLimit: campaign.perUserLimit.toString(),
        validFrom: campaign.validFrom ? new Date(campaign.validFrom).toISOString().slice(0, 16) : '',
        validUntil: campaign.validUntil ? new Date(campaign.validUntil).toISOString().slice(0, 16) : '',
      });
    } else {
      setEditingCampaign(null);
      setRedeemCampaignForm({ name: '', description: '', productId: '', licensePlanId: '', usageCategory: 'COMMERCIAL', seatLimit: '', perUserLimit: '1', validFrom: '', validUntil: '' });
    }
    setIsRedeemCampaignModalOpen(true);
  };

  const closeRedeemCampaignModal = () => { setIsRedeemCampaignModalOpen(false); setEditingCampaign(null); };

  const handleRedeemCampaignSubmit = async () => {
    const body = {
      name: redeemCampaignForm.name, description: redeemCampaignForm.description || null,
      productId: redeemCampaignForm.productId, licensePlanId: redeemCampaignForm.licensePlanId,
      usageCategory: redeemCampaignForm.usageCategory,
      seatLimit: redeemCampaignForm.seatLimit ? parseInt(redeemCampaignForm.seatLimit) : null,
      perUserLimit: parseInt(redeemCampaignForm.perUserLimit) || 1,
      validFrom: redeemCampaignForm.validFrom ? new Date(redeemCampaignForm.validFrom).toISOString() : null,
      validUntil: redeemCampaignForm.validUntil ? new Date(redeemCampaignForm.validUntil).toISOString() : null,
    };
    const url = editingCampaign ? `${API_URL}/api/v1/admin/redeem-campaigns/${editingCampaign.id}` : `${API_URL}/api/v1/admin/redeem-campaigns`;
    const response = await fetch(url, { method: editingCampaign ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials, body: JSON.stringify(body) });
    if (response.ok) { closeRedeemCampaignModal(); fetchRedeemCampaigns(); }
    else { const error = await response.json(); alert(error.message || '캠페인 저장에 실패했습니다.'); }
  };

  const handleCampaignStatusChange = async (campaignId: string, action: 'pause' | 'end' | 'resume') => {
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/${campaignId}/${action}`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
    if (response.ok) { fetchRedeemCampaigns(); } else { alert('상태 변경에 실패했습니다.'); }
  };

  const openCodeGenerateModal = (campaign: RedeemCampaign) => {
    setSelectedCampaignForCodes(campaign);
    setCodeGenerateForm({ codeType: 'RANDOM', customCode: '', count: '10', maxRedemptions: '1', expiresAt: '', allowedEmailDomain: '' });
    setIsCodeGenerateModalOpen(true);
  };

  const handleGenerateCodes = async () => {
    if (!selectedCampaignForCodes) return;
    const body = {
      campaignId: selectedCampaignForCodes.id, codeType: codeGenerateForm.codeType,
      customCode: codeGenerateForm.codeType === 'CUSTOM' ? codeGenerateForm.customCode : null,
      count: codeGenerateForm.codeType === 'RANDOM' ? parseInt(codeGenerateForm.count) || 1 : 1,
      maxRedemptions: parseInt(codeGenerateForm.maxRedemptions) || 1,
      expiresAt: codeGenerateForm.expiresAt ? new Date(codeGenerateForm.expiresAt).toISOString() : null,
      allowedEmailDomain: codeGenerateForm.allowedEmailDomain.trim() || null,
    };
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/codes`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials, body: JSON.stringify(body),
    });
    if (response.ok) {
      const data = await response.json();
      setGeneratedCodes(data.codes || []); setIsCodeGenerateModalOpen(false); setIsGeneratedCodesModalOpen(true);
      fetchRedeemCampaigns();
    } else { const error = await response.json(); alert(error.message || '코드 생성에 실패했습니다.'); }
  };

  const handleDeactivateCode = async (codeId: string) => {
    if (!window.confirm('이 코드를 비활성화하시겠습니까?')) return;
    const response = await fetch(`${API_URL}/api/v1/admin/redeem-campaigns/codes/${codeId}`, { method: 'DELETE', credentials: 'include' as RequestCredentials });
    if (response.ok && selectedCampaignForCodes) { fetchRedeemCodes(selectedCampaignForCodes.id); }
  };

  const copyGeneratedCodes = () => { navigator.clipboard.writeText(generatedCodes.join('\n')); alert('클립보드에 복사되었습니다.'); };

  const downloadCodesAsCsv = () => {
    const csv = 'code\n' + generatedCodes.join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `redeem-codes-${selectedCampaignForCodes?.name || 'export'}.csv`; a.click();
    URL.revokeObjectURL(url);
  };

  // ========== 관리자 검색/토글 핸들러 ==========

  const handleAdminSearch = () => { setAdminAppliedSearch(adminSearchQuery); setAdminCurrentPage(1); };
  const handleAdminSearchKeyPress = (e: React.KeyboardEvent) => { if (e.key === 'Enter') handleAdminSearch(); };
  const handleAdminClearSearch = () => { setAdminSearchQuery(''); setAdminAppliedSearch(''); setAdminCurrentPage(1); };

  const handleLicensePlanSearch = () => { setLicensePlanAppliedSearch(licensePlanSearchQuery); setLicensePlanCurrentPage(1); };
  const handleLicensePlanSearchKeyPress = (e: React.KeyboardEvent) => { if (e.key === 'Enter') handleLicensePlanSearch(); };
  const handleLicensePlanClearSearch = () => { setLicensePlanSearchQuery(''); setLicensePlanAppliedSearch(''); setLicensePlanCurrentPage(1); };

  const handleToggleProduct = async (code: string) => {
    try {
      const response = await fetch(`${API_URL}/api/admin/products/${code}/toggle`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) await fetchProducts();
    } catch (error) { /* 상품 토글 실패 */ }
  };

  const handleTogglePricePlan = async (id: number) => {
    try {
      const response = await fetch(`${API_URL}/api/admin/price-plans/${id}/toggle`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) await fetchPricePlans();
    } catch (error) { /* 요금제 토글 실패 */ }
  };

  const handleTogglePromotion = async (id: number) => {
    try {
      const response = await fetch(`${API_URL}/api/promotions/${id}/toggle`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) await fetchPromotions();
    } catch (error) { /* 프로모션 토글 실패 */ }
  };

  const handleActivateLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 활성화하시겠습니까?')) return;
    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/activate`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) { await fetchAdminLicenses(); showSuccess('라이선스가 활성화되었습니다.'); }
      else { showError('활성화에 실패했습니다.'); }
    } catch { showError('활성화 중 오류가 발생했습니다.'); }
  };

  const handleSuspendLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 비활성화하시겠습니까?')) return;
    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/suspend`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) { await fetchAdminLicenses(); showSuccess('라이선스가 비활성화되었습니다.'); }
      else { showError('비활성화에 실패했습니다.'); }
    } catch { showError('비활성화 중 오류가 발생했습니다.'); }
  };

  // ========== 라이선스 플랜 CRUD ==========

  const openLicensePlanModal = (plan?: LicensePlan) => {
    if (plan) {
      setEditingLicensePlan(plan);
      setLicensePlanForm({
        productId: plan.productId, code: plan.code, name: plan.name, description: plan.description || '',
        licenseType: plan.licenseType, durationDays: plan.durationDays, graceDays: plan.graceDays,
        maxActivations: plan.maxActivations, maxConcurrentSessions: plan.maxConcurrentSessions,
        allowOfflineDays: plan.allowOfflineDays, entitlements: plan.entitlements.join(', '),
      });
    } else {
      setEditingLicensePlan(null);
      setLicensePlanForm({
        productId: products.length > 0 ? products[0].id : '', code: '', name: '', description: '',
        licenseType: 'SUBSCRIPTION', durationDays: 365, graceDays: 7, maxActivations: 1,
        maxConcurrentSessions: 1, allowOfflineDays: 0, entitlements: 'core-simulation',
      });
    }
    setIsLicensePlanModalOpen(true);
  };

  const closeLicensePlanModal = () => { setIsLicensePlanModalOpen(false); setEditingLicensePlan(null); };

  const handleLicensePlanSubmit = async () => {
    const entitlementList = licensePlanForm.entitlements.split(',').map(e => e.trim()).filter(e => e.length > 0);
    const payload = {
      productId: licensePlanForm.productId, code: licensePlanForm.code, name: licensePlanForm.name,
      description: licensePlanForm.description || null, licenseType: licensePlanForm.licenseType,
      durationDays: licensePlanForm.durationDays, graceDays: licensePlanForm.graceDays,
      maxActivations: licensePlanForm.maxActivations, maxConcurrentSessions: licensePlanForm.maxConcurrentSessions,
      allowOfflineDays: licensePlanForm.allowOfflineDays, entitlements: entitlementList,
    };
    try {
      const url = editingLicensePlan ? `${API_URL}/api/v1/admin/license-plans/${editingLicensePlan.id}` : `${API_URL}/api/v1/admin/license-plans`;
      const response = await fetch(url, {
        method: editingLicensePlan ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' }, credentials: 'include' as RequestCredentials,
        body: JSON.stringify(payload),
      });
      if (response.ok) {
        closeLicensePlanModal(); await fetchLicensePlans();
        showSuccess(editingLicensePlan ? '라이선스 플랜이 수정되었습니다.' : '라이선스 플랜이 등록되었습니다.');
      } else { const error = await response.json(); showError(error.message || '저장에 실패했습니다.'); }
    } catch { showError('저장 중 오류가 발생했습니다.'); }
  };

  const handleToggleLicensePlan = async (id: string, currentActive: boolean) => {
    try {
      const action = currentActive ? 'deactivate' : 'activate';
      const response = await fetch(`${API_URL}/api/v1/admin/license-plans/${id}/${action}`, { method: 'PATCH', credentials: 'include' as RequestCredentials });
      if (response.ok) await fetchLicensePlans();
    } catch (error) { /* 라이선스 플랜 토글 실패 */ }
  };

  const handleDeleteLicensePlan = async (id: string) => {
    if (!window.confirm('이 플랜을 삭제하시겠습니까? (기존 라이선스에는 영향 없음)')) return;
    try {
      const response = await fetch(`${API_URL}/api/v1/admin/license-plans/${id}`, { method: 'DELETE', credentials: 'include' as RequestCredentials });
      if (response.ok || response.status === 204) { await fetchLicensePlans(); showSuccess('라이선스 플랜이 삭제되었습니다.'); }
      else { const error = await response.json(); showError(error.message || '삭제에 실패했습니다.'); }
    } catch { showError('삭제 중 오류가 발생했습니다.'); }
  };

  // ========== 로그아웃 ==========
  const handleLogout = () => { logout(); navigate('/'); };

  // ========== 관리자 검색 공통 props ==========
  const adminSearchProps = {
    searchQuery: adminSearchQuery,
    appliedSearch: adminAppliedSearch,
    currentPage: adminCurrentPage,
    onSearchQueryChange: setAdminSearchQuery,
    onSearch: handleAdminSearch,
    onSearchKeyPress: handleAdminSearchKeyPress,
    onClearSearch: handleAdminClearSearch,
    onPageChange: setAdminCurrentPage,
    isLoading: isAdminLoading,
  };

  // ========== 렌더링 ==========

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
                  <button className={`menu-child ${activeMenu === 'profile' ? 'active' : ''}`} onClick={() => handleMenuChange('profile')}>
                    {t('myPage.menu.profile')}
                  </button>
                  <button className={`menu-child ${activeMenu === 'account' ? 'active' : ''}`} onClick={() => handleMenuChange('account')}>
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
                  <button className={`menu-child ${activeMenu === 'subscription' ? 'active' : ''}`} onClick={() => handleMenuChange('subscription')}>
                    {t('myPage.menu.subscription')}
                  </button>
                  <button className={`menu-child ${activeMenu === 'payment' ? 'active' : ''}`} onClick={() => handleMenuChange('payment')}>
                    {t('myPage.menu.payment')}
                  </button>
                  <button className={`menu-child ${activeMenu === 'redeem' ? 'active' : ''}`} onClick={() => handleMenuChange('redeem')}>
                    {t('myPage.menu.redeem')}
                  </button>
                </div>
              </div>

              {/* 관리자 정보 대메뉴 */}
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
                  <button className={`menu-child ${activeMenu === 'admin-users' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-users')}>{t('myPage.menu.adminUsers')}</button>
                  <button className={`menu-child ${activeMenu === 'admin-payments' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-payments')}>{t('myPage.menu.adminPayments')}</button>
                  <button className={`menu-child ${activeMenu === 'admin-products' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-products')}>{t('myPage.menu.adminProducts')}</button>
                  <button className={`menu-child ${activeMenu === 'admin-licenses' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-licenses')}>{t('myPage.menu.adminLicenses')}</button>
                  <button className={`menu-child ${activeMenu === 'admin-promotions' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-promotions')}>{t('myPage.menu.adminPromotions')}</button>
                  <button className={`menu-child ${activeMenu === 'admin-redeem' ? 'active' : ''}`} onClick={() => handleMenuChange('admin-redeem')}>{t('myPage.menu.adminRedeem')}</button>
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
            {successMessage && <div className="message success">{successMessage}</div>}
            {errorMessage && <div className="message error">{errorMessage}</div>}

            <div className="mypage-grid">
              {activeMenu === 'profile' && (
                <ProfilePanel
                  userInfo={userInfo}
                  isEditingProfile={isEditingProfile}
                  editName={editName}
                  editPhone={editPhone}
                  tempLanguage={tempLanguage}
                  selectedLanguage={selectedLanguage}
                  onStartEdit={() => setIsEditingProfile(true)}
                  onEditNameChange={setEditName}
                  onEditPhoneChange={setEditPhone}
                  onTempLanguageChange={setTempLanguage}
                  onSave={handleSaveProfile}
                  onCancel={handleCancelProfile}
                />
              )}

              {activeMenu === 'account' && (
                <AccountPanel
                  userInfo={userInfo}
                  isEditingPassword={isEditingPassword}
                  currentPassword={currentPassword}
                  newPassword={newPassword}
                  confirmPassword={confirmPassword}
                  passwordError={passwordError}
                  showCurrentPassword={showCurrentPassword}
                  showNewPassword={showNewPassword}
                  showConfirmPassword={showConfirmPassword}
                  onStartEditPassword={() => setIsEditingPassword(true)}
                  onCurrentPasswordChange={setCurrentPassword}
                  onNewPasswordChange={(v) => { setNewPassword(v); setPasswordError(''); }}
                  onConfirmPasswordChange={(v) => { setConfirmPassword(v); setPasswordError(''); }}
                  onToggleShowCurrentPassword={() => setShowCurrentPassword(v => !v)}
                  onToggleShowNewPassword={() => setShowNewPassword(v => !v)}
                  onToggleShowConfirmPassword={() => setShowConfirmPassword(v => !v)}
                  onChangePassword={handleChangePassword}
                  onCancelPassword={handleCancelPassword}
                  isDeleteModalOpen={isDeleteModalOpen}
                  deleteConfirmText={deleteConfirmText}
                  deleteModalError={deleteModalError}
                  isDeleting={isDeleting}
                  onOpenDeleteModal={() => setIsDeleteModalOpen(true)}
                  onCloseDeleteModal={() => { setIsDeleteModalOpen(false); setDeleteConfirmText(''); }}
                  onDeleteConfirmTextChange={setDeleteConfirmText}
                  onDeleteModalErrorClear={() => setDeleteModalError('')}
                  onDeleteAccount={handleDeleteAccount}
                />
              )}

              {activeMenu === 'subscription' && (
                <SubscriptionPanel
                  licenses={licenses}
                  isLoadingLicenses={isLoadingLicenses}
                  expandedLicenseId={expandedLicenseId}
                  activations={activations}
                  isLoadingActivations={isLoadingActivations}
                  onToggleDeviceList={handleToggleDeviceList}
                  onDeactivateDevice={handleDeactivateDevice}
                  subscriptions={subscriptions}
                  isLoadingSubscriptions={isLoadingSubscriptions}
                  billingKeys={billingKeys}
                  onToggleAutoRenew={handleToggleAutoRenew}
                  formatPrice={formatPrice}
                  isAdmin={isAdmin}
                  isTestMode={isTestMode}
                  testLoading={testLoading}
                  onSimulateNearExpiry={handleSimulateNearExpiry}
                  onMakeDueNow={handleMakeDueNow}
                />
              )}

              {activeMenu === 'payment' && (
                <PaymentPanel
                  isEditingSettings={isEditingSettings}
                  tempCountry={tempCountry}
                  selectedCountry={selectedCountry}
                  isLoadingBillingKeys={isLoadingBillingKeys}
                  billingKeys={billingKeys}
                  onStartEditSettings={handleStartEditSettings}
                  onSaveSettings={handleSaveSettings}
                  onCancelSettings={handleCancelSettings}
                  onTempCountryChange={setTempCountry}
                  onSetDefaultCard={handleSetDefaultCard}
                  onDeleteCard={handleDeleteCard}
                />
              )}

              {activeMenu === 'redeem' && (
                <RedeemPanel
                  redeemCode={redeemCode}
                  redeemResult={redeemResult}
                  isRedeeming={isRedeeming}
                  onRedeemCodeChange={setRedeemCode}
                  onRedeemSubmit={handleRedeemSubmit}
                />
              )}

              {isAdmin && activeMenu === 'admin-users' && (
                <AdminUsersPanel {...adminSearchProps} adminUsers={adminUsers} />
              )}

              {isAdmin && activeMenu === 'admin-payments' && (
                <AdminPaymentsPanel {...adminSearchProps} adminPayments={adminPayments} />
              )}

              {isAdmin && activeMenu === 'admin-products' && (
                <AdminProductsPanel
                  {...adminSearchProps}
                  products={products}
                  pricePlans={pricePlans}
                  onToggleProduct={handleToggleProduct}
                  onTogglePricePlan={handleTogglePricePlan}
                />
              )}

              {isAdmin && activeMenu === 'admin-licenses' && (
                <AdminLicensesPanel
                  {...adminSearchProps}
                  adminLicenses={adminLicenses}
                  onActivateLicense={handleActivateLicense}
                  onSuspendLicense={handleSuspendLicense}
                  licensePlans={licensePlans}
                  products={products}
                  licensePlanSearchQuery={licensePlanSearchQuery}
                  licensePlanAppliedSearch={licensePlanAppliedSearch}
                  licensePlanCurrentPage={licensePlanCurrentPage}
                  onLicensePlanSearchQueryChange={setLicensePlanSearchQuery}
                  onLicensePlanSearch={handleLicensePlanSearch}
                  onLicensePlanSearchKeyPress={handleLicensePlanSearchKeyPress}
                  onLicensePlanClearSearch={handleLicensePlanClearSearch}
                  onLicensePlanPageChange={setLicensePlanCurrentPage}
                  onOpenLicensePlanModal={openLicensePlanModal}
                  onToggleLicensePlan={handleToggleLicensePlan}
                  onDeleteLicensePlan={handleDeleteLicensePlan}
                  isLicensePlanModalOpen={isLicensePlanModalOpen}
                  editingLicensePlan={editingLicensePlan}
                  licensePlanForm={licensePlanForm}
                  onLicensePlanFormChange={setLicensePlanForm}
                  onCloseLicensePlanModal={closeLicensePlanModal}
                  onLicensePlanSubmit={handleLicensePlanSubmit}
                />
              )}

              {isAdmin && activeMenu === 'admin-promotions' && (
                <AdminPromotionsPanel
                  {...adminSearchProps}
                  promotions={promotions}
                  onTogglePromotion={handleTogglePromotion}
                />
              )}

              {isAdmin && activeMenu === 'admin-redeem' && (
                <AdminRedeemPanel
                  isLoading={isAdminLoading}
                  redeemCampaigns={redeemCampaigns}
                  products={products}
                  filteredLicensePlansForRedeem={filteredLicensePlansForRedeem}
                  isRedeemCampaignModalOpen={isRedeemCampaignModalOpen}
                  editingCampaign={editingCampaign}
                  redeemCampaignForm={redeemCampaignForm}
                  onOpenCampaignModal={openRedeemCampaignModal}
                  onCloseCampaignModal={closeRedeemCampaignModal}
                  onCampaignFormChange={setRedeemCampaignForm}
                  onCampaignSubmit={handleRedeemCampaignSubmit}
                  onCampaignStatusChange={handleCampaignStatusChange}
                  selectedCampaignForCodes={selectedCampaignForCodes}
                  redeemCodes={redeemCodes}
                  onSelectCampaignForCodes={setSelectedCampaignForCodes}
                  onFetchRedeemCodes={fetchRedeemCodes}
                  onDeactivateCode={handleDeactivateCode}
                  isCodeGenerateModalOpen={isCodeGenerateModalOpen}
                  codeGenerateForm={codeGenerateForm}
                  onOpenCodeGenerateModal={openCodeGenerateModal}
                  onCloseCodeGenerateModal={() => setIsCodeGenerateModalOpen(false)}
                  onCodeGenerateFormChange={setCodeGenerateForm}
                  onGenerateCodes={handleGenerateCodes}
                  isGeneratedCodesModalOpen={isGeneratedCodesModalOpen}
                  generatedCodes={generatedCodes}
                  onCloseGeneratedCodesModal={() => setIsGeneratedCodesModalOpen(false)}
                  onCopyGeneratedCodes={copyGeneratedCodes}
                  onDownloadCodesAsCsv={downloadCodesAsCsv}
                />
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

      {/* 로그인 모달 - 전역 */}
      <LoginModal
        isOpen={loginModalOpen}
        onClose={() => { setLoginModalOpen(false); navigate('/'); }}
        onSuccess={() => { setLoginModalOpen(false); window.location.reload(); }}
      />
    </>
  );
};

export default MyPage;
