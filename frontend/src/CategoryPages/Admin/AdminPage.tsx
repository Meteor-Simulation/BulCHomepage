import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Header from '../../components/Header';
import { formatPhoneNumber } from '../../utils/phoneUtils';
import { API_URL } from '../../utils/api';
import './AdminPage.css';

interface User {
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

interface License {
  id: string;
  licenseKey: string;
  ownerType: string;
  ownerId: string;
  status: string;
  validUntil: string;
  createdAt: string;
}

interface Payment {
  id: number;
  userEmail: string;
  userName: string | null;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  paymentMethod: string | null;
  // 카드 결제 정보
  cardCompany: string | null;
  cardNumber: string | null;
  installmentMonths: number | null;
  approveNo: string | null;
  // 간편결제 정보
  easyPayProvider: string | null;
  // 가상계좌/계좌이체 정보
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

type TabType = 'users' | 'licenses' | 'payments' | 'products' | 'promotions';

const ITEMS_PER_PAGE = 10;

const AdminPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn, isAdmin, isAuthReady, user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>('users');
  const [isLoading, setIsLoading] = useState(false);

  // 시스템 관리자 여부 (000만 사용자 권한 수정, 라이선스 수동 발급 가능)
  const isSystemAdmin = user?.rolesCode === '000';

  // 프로모션/상품 관리 가능 여부 (000, 001 모두 가능)
  const canManagePromotions = user?.rolesCode === '000' || user?.rolesCode === '001';

  // 데이터 상태
  const [users, setUsers] = useState<User[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [pricePlans, setPricePlans] = useState<PricePlan[]>([]);
  const [licenses, setLicenses] = useState<License[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [licensePlans, setLicensePlans] = useState<LicensePlan[]>([]);

  // 프로모션 모달 상태
  const [isPromotionModalOpen, setIsPromotionModalOpen] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState<Promotion | null>(null);
  const [promotionForm, setPromotionForm] = useState({
    code: '',
    name: '',
    discountType: 10,
    discountValue: 0,
    productCode: '',
    usageLimit: '',
    validFrom: '',
    validUntil: '',
    isActive: true,
  });

  // 상품 모달 상태
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [productForm, setProductForm] = useState({
    code: '',
    name: '',
    description: '',
    isActive: true,
  });

  // 요금제 모달 상태
  const [isPricePlanModalOpen, setIsPricePlanModalOpen] = useState(false);
  const [editingPricePlan, setEditingPricePlan] = useState<PricePlan | null>(null);
  const [pricePlanForm, setPricePlanForm] = useState({
    productCode: '',
    name: '',
    description: '',
    price: 0,
    currency: 'KRW',
    licensePlanId: '',
    isActive: true,
  });

  // 사용자 권한 수정 모달 상태
  const [isRoleModalOpen, setIsRoleModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [selectedRole, setSelectedRole] = useState('');
  const [isUpdatingRole, setIsUpdatingRole] = useState(false);

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

  // 검색 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');

  // 페이징 상태
  const [currentPage, setCurrentPage] = useState(1);

  // 권한 체크
  useEffect(() => {
    if (isAuthReady && (!isLoggedIn || !isAdmin)) {
      navigate('/');
    }
  }, [isAuthReady, isLoggedIn, isAdmin, navigate]);

  // 탭 변경 시 검색/페이지 초기화
  useEffect(() => {
    setSearchQuery('');
    setAppliedSearch('');
    setCurrentPage(1);
  }, [activeTab]);

  // 필터링된 데이터
  const filteredUsers = useMemo(() => {
    if (!appliedSearch) return users;
    const query = appliedSearch.toLowerCase();
    return users.filter(user =>
      user.email.toLowerCase().includes(query) ||
      (user.name && user.name.toLowerCase().includes(query)) ||
      (user.phone && user.phone.includes(query))
    );
  }, [users, appliedSearch]);

  const filteredLicenses = useMemo(() => {
    if (!appliedSearch) return licenses;
    const query = appliedSearch.toLowerCase();
    return licenses.filter(license =>
      license.licenseKey.toLowerCase().includes(query) ||
      license.ownerId.toLowerCase().includes(query) ||
      license.status.toLowerCase().includes(query)
    );
  }, [licenses, appliedSearch]);

  const filteredPayments = useMemo(() => {
    if (!appliedSearch) return payments;
    const query = appliedSearch.toLowerCase();
    return payments.filter(payment =>
      payment.userEmail.toLowerCase().includes(query) ||
      (payment.userName && payment.userName.toLowerCase().includes(query)) ||
      payment.orderId.toLowerCase().includes(query) ||
      payment.status.toLowerCase().includes(query) ||
      (payment.paymentMethod && payment.paymentMethod.toLowerCase().includes(query))
    );
  }, [payments, appliedSearch]);

  const filteredProducts = useMemo(() => {
    if (!appliedSearch) return products;
    const query = appliedSearch.toLowerCase();
    return products.filter(product =>
      product.code.toLowerCase().includes(query) ||
      product.name.toLowerCase().includes(query)
    );
  }, [products, appliedSearch]);

  const filteredPricePlans = useMemo(() => {
    if (!appliedSearch) return pricePlans;
    const query = appliedSearch.toLowerCase();
    return pricePlans.filter(plan =>
      plan.name.toLowerCase().includes(query) ||
      plan.productCode.toLowerCase().includes(query)
    );
  }, [pricePlans, appliedSearch]);

  const filteredPromotions = useMemo(() => {
    if (!appliedSearch) return promotions;
    const query = appliedSearch.toLowerCase();
    return promotions.filter(promo =>
      promo.code.toLowerCase().includes(query) ||
      promo.name.toLowerCase().includes(query)
    );
  }, [promotions, appliedSearch]);

  const filteredLicensePlans = useMemo(() => {
    if (!appliedSearch) return licensePlans;
    const query = appliedSearch.toLowerCase();
    return licensePlans.filter(plan =>
      plan.code.toLowerCase().includes(query) ||
      plan.name.toLowerCase().includes(query) ||
      plan.licenseType.toLowerCase().includes(query)
    );
  }, [licensePlans, appliedSearch]);

  // 페이징 계산
  const getPaginatedData = <T,>(data: T[]): T[] => {
    const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
    return data.slice(startIndex, startIndex + ITEMS_PER_PAGE);
  };

  const getTotalPages = (totalItems: number): number => {
    return Math.ceil(totalItems / ITEMS_PER_PAGE);
  };

  // 검색 핸들러
  const handleSearch = () => {
    setAppliedSearch(searchQuery);
    setCurrentPage(1);
  };

  const handleSearchKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    setAppliedSearch('');
    setCurrentPage(1);
  };

  // 탭 변경 시 데이터 로드
  useEffect(() => {
    if (!isAuthReady || !isLoggedIn || !isAdmin) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const fetchData = async () => {
      setIsLoading(true);
      try {
        switch (activeTab) {
          case 'users':
            await fetchUsers(token);
            break;
          case 'licenses':
            await fetchLicenses(token);
            await fetchLicensePlans(token);
            await fetchProducts(token);
            break;
          case 'payments':
            await fetchPayments(token);
            break;
          case 'products':
            await fetchProducts(token);
            await fetchPricePlans(token);
            break;
          case 'promotions':
            await fetchPromotions(token);
            await fetchProducts(token);
            break;
        }
      } catch (error) {
        console.error('데이터 로드 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [activeTab, isAuthReady, isLoggedIn, isAdmin]);

  const fetchUsers = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/users`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setUsers(data);
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

  const fetchLicenses = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/license-list`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setLicenses(data);
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

  // 라이선스 활성화
  const handleActivateLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 활성화하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/activate`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        await fetchLicenses(token);
        alert('라이선스가 활성화되었습니다.');
      } else {
        try {
          const error = await response.json();
          alert(error.message || '활성화에 실패했습니다.');
        } catch {
          alert(`활성화에 실패했습니다. (HTTP ${response.status})`);
        }
      }
    } catch (error) {
      console.error('라이선스 활성화 실패:', error);
      alert('활성화 중 오류가 발생했습니다.');
    }
  };

  // 라이선스 비활성화
  const handleSuspendLicense = async (licenseId: string) => {
    if (!window.confirm('이 라이선스를 비활성화하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/licenses/${licenseId}/suspend`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        await fetchLicenses(token);
        alert('라이선스가 비활성화되었습니다.');
      } else {
        try {
          const error = await response.json();
          alert(error.message || '비활성화에 실패했습니다.');
        } catch {
          alert(`비활성화에 실패했습니다. (HTTP ${response.status})`);
        }
      }
    } catch (error) {
      console.error('라이선스 비활성화 실패:', error);
      alert('비활성화 중 오류가 발생했습니다.');
    }
  };

  const fetchPayments = async (token: string) => {
    const response = await fetch(`${API_URL}/api/admin/payments`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (response.ok) {
      const data = await response.json();
      setPayments(data);
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

  // 프로모션 CRUD 함수들
  const openPromotionModal = (promotion?: Promotion) => {
    if (promotion) {
      setEditingPromotion(promotion);
      setPromotionForm({
        code: promotion.code,
        name: promotion.name,
        discountType: promotion.discountType,
        discountValue: promotion.discountValue,
        productCode: promotion.productCode || '',
        usageLimit: promotion.usageLimit?.toString() || '',
        validFrom: promotion.validFrom ? promotion.validFrom.slice(0, 16) : '',
        validUntil: promotion.validUntil ? promotion.validUntil.slice(0, 16) : '',
        isActive: promotion.isActive,
      });
    } else {
      setEditingPromotion(null);
      setPromotionForm({
        code: '',
        name: '',
        discountType: 10,
        discountValue: 0,
        productCode: '',
        usageLimit: '',
        validFrom: '',
        validUntil: '',
        isActive: true,
      });
    }
    setIsPromotionModalOpen(true);
  };

  const closePromotionModal = () => {
    setIsPromotionModalOpen(false);
    setEditingPromotion(null);
  };

  const handlePromotionSubmit = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const payload = {
      code: promotionForm.code.toUpperCase(),
      name: promotionForm.name,
      discountType: promotionForm.discountType,
      discountValue: promotionForm.discountValue,
      productCode: promotionForm.productCode || null,
      usageLimit: promotionForm.usageLimit ? parseInt(promotionForm.usageLimit) : null,
      validFrom: promotionForm.validFrom ? promotionForm.validFrom + ':00' : null,
      validUntil: promotionForm.validUntil ? promotionForm.validUntil + ':00' : null,
      isActive: promotionForm.isActive,
    };

    try {
      const url = editingPromotion
        ? `${API_URL}/api/promotions/${editingPromotion.id}`
        : `${API_URL}/api/promotions`;
      const method = editingPromotion ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) {
        closePromotionModal();
        await fetchPromotions(token);
      } else {
        const error = await response.json();
        alert(error.message || '저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('프로모션 저장 실패:', error);
      alert('저장 중 오류가 발생했습니다.');
    }
  };

  const handleDeletePromotion = async (id: number) => {
    if (!window.confirm('정말 삭제하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/promotions/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok) {
        await fetchPromotions(token);
      } else {
        const error = await response.json();
        alert(error.message || '삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('프로모션 삭제 실패:', error);
      alert('삭제 중 오류가 발생했습니다.');
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

      if (response.ok) {
        await fetchPromotions(token);
      }
    } catch (error) {
      console.error('프로모션 토글 실패:', error);
    }
  };

  // 상품 CRUD 함수들
  const openProductModal = (product?: Product) => {
    if (product) {
      setEditingProduct(product);
      setProductForm({
        code: product.code,
        name: product.name,
        description: product.description || '',
        isActive: product.isActive,
      });
    } else {
      setEditingProduct(null);
      setProductForm({
        code: '',
        name: '',
        description: '',
        isActive: true,
      });
    }
    setIsProductModalOpen(true);
  };

  const closeProductModal = () => {
    setIsProductModalOpen(false);
    setEditingProduct(null);
  };

  const handleProductSubmit = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const payload = {
      code: productForm.code.toUpperCase(),
      name: productForm.name,
      description: productForm.description || null,
      isActive: productForm.isActive,
    };

    try {
      const url = editingProduct
        ? `${API_URL}/api/admin/products/${editingProduct.code}`
        : `${API_URL}/api/admin/products`;
      const method = editingProduct ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) {
        closeProductModal();
        await fetchProducts(token);
      } else {
        const error = await response.json();
        alert(error.message || '저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('상품 저장 실패:', error);
      alert('저장 중 오류가 발생했습니다.');
    }
  };

  const handleDeleteProduct = async (code: string) => {
    if (!window.confirm('정말 삭제(비활성화)하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/products/${code}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok) {
        await fetchProducts(token);
      } else {
        const error = await response.json();
        alert(error.message || '삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('상품 삭제 실패:', error);
      alert('삭제 중 오류가 발생했습니다.');
    }
  };

  const handleToggleProduct = async (code: string) => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/products/${code}/toggle`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok) {
        await fetchProducts(token);
      }
    } catch (error) {
      console.error('상품 토글 실패:', error);
    }
  };

  // 요금제 CRUD 함수들
  const openPricePlanModal = (plan?: PricePlan) => {
    if (plan) {
      setEditingPricePlan(plan);
      setPricePlanForm({
        productCode: plan.productCode,
        name: plan.name,
        description: plan.description || '',
        price: plan.price,
        currency: plan.currency,
        licensePlanId: plan.licensePlanId || '',
        isActive: plan.isActive,
      });
    } else {
      setEditingPricePlan(null);
      setPricePlanForm({
        productCode: products.length > 0 ? products[0].code : '',
        name: '',
        description: '',
        price: 0,
        currency: 'KRW',
        licensePlanId: '',
        isActive: true,
      });
    }
    setIsPricePlanModalOpen(true);
  };

  const closePricePlanModal = () => {
    setIsPricePlanModalOpen(false);
    setEditingPricePlan(null);
  };

  const handlePricePlanSubmit = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const payload = {
      productCode: pricePlanForm.productCode,
      name: pricePlanForm.name,
      description: pricePlanForm.description || null,
      price: pricePlanForm.price,
      currency: pricePlanForm.currency,
      licensePlanId: pricePlanForm.licensePlanId || null,
      isActive: pricePlanForm.isActive,
    };

    try {
      const url = editingPricePlan
        ? `${API_URL}/api/admin/price-plans/${editingPricePlan.id}`
        : `${API_URL}/api/admin/price-plans`;
      const method = editingPricePlan ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) {
        closePricePlanModal();
        await fetchPricePlans(token);
      } else {
        const error = await response.json();
        alert(error.message || '저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('요금제 저장 실패:', error);
      alert('저장 중 오류가 발생했습니다.');
    }
  };

  const handleDeletePricePlan = async (id: number) => {
    if (!window.confirm('정말 삭제(비활성화)하시겠습니까?')) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    try {
      const response = await fetch(`${API_URL}/api/admin/price-plans/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` },
      });

      if (response.ok) {
        await fetchPricePlans(token);
      } else {
        const error = await response.json();
        alert(error.message || '삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('요금제 삭제 실패:', error);
      alert('삭제 중 오류가 발생했습니다.');
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

      if (response.ok) {
        await fetchPricePlans(token);
      }
    } catch (error) {
      console.error('요금제 토글 실패:', error);
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
      } else {
        const error = await response.json();
        alert(error.message || '저장에 실패했습니다.');
      }
    } catch (error) {
      console.error('라이선스 플랜 저장 실패:', error);
      alert('저장 중 오류가 발생했습니다.');
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
      } else {
        const error = await response.json();
        alert(error.message || '삭제에 실패했습니다.');
      }
    } catch (error) {
      console.error('라이선스 플랜 삭제 실패:', error);
      alert('삭제 중 오류가 발생했습니다.');
    }
  };

  const getProductNameById = (productId: string) => {
    const product = products.find(p => p.id === productId);
    return product ? `${product.code} - ${product.name}` : productId;
  };

  // 사용자 권한 수정 모달 함수들
  const openRoleModal = (userToEdit: User) => {
    setEditingUser(userToEdit);
    setSelectedRole(userToEdit.rolesCode);
    setIsRoleModalOpen(true);
  };

  const closeRoleModal = () => {
    setIsRoleModalOpen(false);
    setEditingUser(null);
    setSelectedRole('');
  };

  const handleUpdateRole = async () => {
    if (!editingUser || !selectedRole) return;

    const token = localStorage.getItem('accessToken');
    if (!token) return;

    setIsUpdatingRole(true);

    try {
      const response = await fetch(`${API_URL}/api/admin/users/${editingUser.id}/role`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({ rolesCode: selectedRole }),
      });

      if (response.ok) {
        // 사용자 목록 새로고침
        await fetchUsers(token);
        closeRoleModal();
        alert('권한이 변경되었습니다.');
      } else {
        const error = await response.json();
        alert(error.message || '권한 변경에 실패했습니다.');
      }
    } catch (error) {
      console.error('권한 변경 실패:', error);
      alert('권한 변경 중 오류가 발생했습니다.');
    } finally {
      setIsUpdatingRole(false);
    }
  };

  const getRoleName = (code: string) => {
    switch (code) {
      case '000': return '관리자';
      case '001': return '매니저';
      case '002': return '일반';
      default: return code;
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatPrice = (price: number, currency: string) => {
    if (currency === 'KRW') {
      return price.toLocaleString('ko-KR') + '원';
    }
    return '$' + price.toLocaleString('en-US');
  };

  const formatPaymentMethod = (method: string | null) => {
    if (!method) return '-';

    // 간편결제 제공자 매핑
    const easyPayProviders: { [key: string]: string } = {
      'EASY_PAY_TOSS': '간편결제(토스)',
      'EASY_PAY_NAVER': '간편결제(네이버)',
      'EASY_PAY_KAKAO': '간편결제(카카오)',
      'EASY_PAY_SAMSUNG': '간편결제(삼성)',
      'EASY_PAY_APPLE': '간편결제(애플)',
      'EASY_PAY_PAYCO': '간편결제(페이코)',
    };

    if (method.startsWith('EASY_PAY_')) {
      return easyPayProviders[method] || `간편결제(${method.replace('EASY_PAY_', '')})`;
    }

    const methodMap: { [key: string]: string } = {
      'CARD': '카드',
      'VIRTUAL_ACCOUNT': '가상계좌',
      'EASY_PAY': '간편결제',
      'TRANSFER': '계좌이체',
      'MOBILE': '휴대폰',
      'GIFT_CARD': '상품권',
      'UNKNOWN': '알 수 없음',
    };

    // 정확한 매칭
    if (methodMap[method]) {
      return methodMap[method];
    }

    // 깨진 한글이나 알 수 없는 값에 대한 fallback 처리
    const lowerMethod = method.toLowerCase();
    if (lowerMethod.includes('가상') || lowerMethod.includes('virtual')) return '가상계좌';
    if (lowerMethod.includes('카드') || lowerMethod.includes('card')) return '카드';
    if (lowerMethod.includes('계좌이체') || lowerMethod.includes('transfer')) return '계좌이체';
    if (lowerMethod.includes('휴대폰') || lowerMethod.includes('mobile')) return '휴대폰';
    if (lowerMethod.includes('상품권') || lowerMethod.includes('gift')) return '상품권';
    if (lowerMethod.includes('간편') || lowerMethod.includes('easy')) return '간편결제';

    // 최종 fallback - 알 수 없는 값은 '기타'로 표시
    return method.length > 20 ? '기타' : method;
  };

  // 페이지네이션 컴포넌트
  const Pagination: React.FC<{ totalItems: number; filteredCount: number }> = ({ totalItems, filteredCount }) => {
    const totalPages = getTotalPages(filteredCount);
    if (totalPages <= 1) return null;

    const pageNumbers: number[] = [];
    const maxVisiblePages = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pageNumbers.push(i);
    }

    return (
      <div className="pagination">
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(1)}
          disabled={currentPage === 1}
        >
          &laquo;
        </button>
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
          disabled={currentPage === 1}
        >
          &lsaquo;
        </button>
        {startPage > 1 && (
          <>
            <button className="pagination-btn" onClick={() => setCurrentPage(1)}>1</button>
            {startPage > 2 && <span className="pagination-ellipsis">...</span>}
          </>
        )}
        {pageNumbers.map(num => (
          <button
            key={num}
            className={`pagination-btn ${currentPage === num ? 'active' : ''}`}
            onClick={() => setCurrentPage(num)}
          >
            {num}
          </button>
        ))}
        {endPage < totalPages && (
          <>
            {endPage < totalPages - 1 && <span className="pagination-ellipsis">...</span>}
            <button className="pagination-btn" onClick={() => setCurrentPage(totalPages)}>{totalPages}</button>
          </>
        )}
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
          disabled={currentPage === totalPages}
        >
          &rsaquo;
        </button>
        <button
          className="pagination-btn"
          onClick={() => setCurrentPage(totalPages)}
          disabled={currentPage === totalPages}
        >
          &raquo;
        </button>
      </div>
    );
  };

  // 검색 바 컴포넌트
  const SearchBar: React.FC<{ placeholder: string }> = ({ placeholder }) => (
    <div className="search-bar">
      <input
        type="text"
        className="search-input"
        placeholder={placeholder}
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onKeyPress={handleSearchKeyPress}
      />
      <button className="search-btn" onClick={handleSearch}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8"/>
          <path d="M21 21l-4.35-4.35"/>
        </svg>
        조회
      </button>
      {appliedSearch && (
        <button className="search-clear-btn" onClick={handleClearSearch}>
          초기화
        </button>
      )}
    </div>
  );

  if (!isAuthReady) {
    return (
      <div className="admin-container">
        <div className="admin-loading">로딩 중...</div>
      </div>
    );
  }

  if (!isLoggedIn || !isAdmin) {
    return null;
  }

  return (
    <>
      <Header logoText="BUL:C" />
      <div className="admin-container">
        <div className="admin-content">
          <h1 className="admin-title">관리자 페이지</h1>

          {/* 탭 메뉴 */}
          <div className="admin-tabs">
            <button
              className={`admin-tab ${activeTab === 'users' ? 'active' : ''}`}
              onClick={() => setActiveTab('users')}
            >
              사용자 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'payments' ? 'active' : ''}`}
              onClick={() => setActiveTab('payments')}
            >
              결제 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'products' ? 'active' : ''}`}
              onClick={() => setActiveTab('products')}
            >
              상품 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'licenses' ? 'active' : ''}`}
              onClick={() => setActiveTab('licenses')}
            >
              라이선스 관리
            </button>
            <button
              className={`admin-tab ${activeTab === 'promotions' ? 'active' : ''}`}
              onClick={() => setActiveTab('promotions')}
            >
              프로모션 관리
            </button>
          </div>

          {/* 탭 컨텐츠 */}
          <div className="admin-tab-content">
            {isLoading ? (
              <div className="admin-loading">데이터 로딩 중...</div>
            ) : (
              <>
                {/* 사용자 관리 */}
                {activeTab === 'users' && (
                  <div className="admin-section">
                    <SearchBar placeholder="이메일, 이름, 전화번호로 검색" />
                    <div className="admin-section-header">
                      <h2>사용자 목록</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredUsers.length}명 / 전체 ${users.length}명` : `${users.length}명`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>이메일</th>
                            <th>이름</th>
                            <th>전화번호</th>
                            <th>권한</th>
                            <th>국가</th>
                            <th>가입일</th>
                            {isSystemAdmin && <th>관리</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {filteredUsers.length > 0 ? (
                            getPaginatedData(filteredUsers).map((userData) => (
                              <tr key={userData.email}>
                                <td>{userData.email}</td>
                                <td>{userData.name || '-'}</td>
                                <td>{formatPhoneNumber(userData.phone) || '-'}</td>
                                <td>
                                  <span className={`role-badge role-${userData.rolesCode}`}>
                                    {getRoleName(userData.rolesCode)}
                                  </span>
                                </td>
                                <td>{userData.countryCode || '-'}</td>
                                <td>{formatDate(userData.createdAt)}</td>
                                {isSystemAdmin && (
                                  <td>
                                    <button
                                      className="action-btn edit"
                                      onClick={() => openRoleModal(userData)}
                                    >
                                      권한수정
                                    </button>
                                  </td>
                                )}
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={isSystemAdmin ? 7 : 6} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '등록된 사용자가 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={users.length} filteredCount={filteredUsers.length} />
                  </div>
                )}

                {/* 라이선스 관리 */}
                {activeTab === 'licenses' && (
                  <>
                  <SearchBar placeholder="라이선스 키, 소유자 ID, 상태로 검색" />
                  <div className="admin-section">
                    <div className="admin-section-header">
                      <h2>라이선스 목록</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredLicenses.length}개 / 전체 ${licenses.length}개` : `${licenses.length}개`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>라이선스 키</th>
                            <th>소유자 유형</th>
                            <th>소유자 이메일</th>
                            <th>상태</th>
                            <th>만료일</th>
                            <th>생성일</th>
                            <th>관리</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredLicenses.length > 0 ? (
                            getPaginatedData(filteredLicenses).map((license) => (
                              <tr key={license.id}>
                                <td>{license.id}</td>
                                <td className="license-key">{license.licenseKey}</td>
                                <td>{license.ownerType}</td>
                                <td>{license.ownerId}</td>
                                <td>
                                  <span className={`status-badge status-${license.status?.toLowerCase()}`}>
                                    {license.status}
                                  </span>
                                </td>
                                <td>{formatDate(license.validUntil)}</td>
                                <td>{formatDate(license.createdAt)}</td>
                                <td>
                                  <div className="action-buttons">
                                    {license.status === 'ACTIVE' ? (
                                      <button
                                        className="action-btn delete"
                                        onClick={() => handleSuspendLicense(license.id)}
                                      >
                                        비활성화
                                      </button>
                                    ) : license.status === 'SUSPENDED' ? (
                                      <button
                                        className="action-btn edit"
                                        onClick={() => handleActivateLicense(license.id)}
                                      >
                                        활성화
                                      </button>
                                    ) : (
                                      <span className="action-disabled">-</span>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={8} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '등록된 라이선스가 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={licenses.length} filteredCount={filteredLicenses.length} />
                  </div>

                    {/* 라이선스 플랜 목록 */}
                    <div className="admin-section">
                      <div className="admin-section-header">
                        <h2>라이선스 플랜 목록</h2>
                        <div className="admin-header-actions">
                          <span className="admin-count">
                            {appliedSearch ? `${filteredLicensePlans.length}개 / 전체 ${licensePlans.length}개` : `${licensePlans.length}개`}
                          </span>
                          {canManagePromotions && (
                            <button className="admin-add-btn" onClick={() => openLicensePlanModal()}>
                              + 플랜 추가
                            </button>
                          )}
                        </div>
                      </div>
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
                              {canManagePromotions && <th>관리</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {filteredLicensePlans.length > 0 ? (
                              getPaginatedData(filteredLicensePlans).map((plan) => (
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
                                    ) : canManagePromotions ? (
                                      <button
                                        className={`status-toggle-btn ${plan.active ? 'active' : 'inactive'}`}
                                        onClick={() => handleToggleLicensePlan(plan.id, plan.active)}
                                      >
                                        {plan.active ? '활성' : '비활성'}
                                      </button>
                                    ) : (
                                      <span className={`status-badge ${plan.active ? 'status-active' : 'status-inactive'}`}>
                                        {plan.active ? '활성' : '비활성'}
                                      </span>
                                    )}
                                  </td>
                                  {canManagePromotions && (
                                    <td>
                                      {!plan.deleted && (
                                        <div className="action-buttons">
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
                                  )}
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={canManagePromotions ? 9 : 8} className="empty-row">
                                  {appliedSearch ? '검색 결과가 없습니다.' : '등록된 라이선스 플랜이 없습니다.'}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                      <Pagination totalItems={licensePlans.length} filteredCount={filteredLicensePlans.length} />
                    </div>
                  </>
                )}

                {/* 결제 관리 */}
                {activeTab === 'payments' && (
                  <div className="admin-section">
                    <SearchBar placeholder="이름, 이메일, 주문번호, 결제수단, 상태로 검색" />
                    <div className="admin-section-header">
                      <h2>결제 내역</h2>
                      <span className="admin-count">
                        {appliedSearch ? `${filteredPayments.length}건 / 전체 ${payments.length}건` : `${payments.length}건`}
                      </span>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
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
                          {filteredPayments.length > 0 ? (
                            getPaginatedData(filteredPayments).map((payment) => (
                              <tr key={payment.id}>
                                <td>{payment.id}</td>
                                <td>{payment.orderId}</td>
                                <td>{payment.userName || '-'}</td>
                                <td>{payment.userEmail}</td>
                                <td>
                                  <div className="payment-method-cell">
                                    <span>{formatPaymentMethod(payment.paymentMethod)}</span>
                                    {/* 카드 결제 상세 정보 */}
                                    {payment.paymentMethod === 'CARD' && payment.cardCompany && (
                                      <div className="payment-detail-info">
                                        <span className="card-info">{payment.cardCompany}</span>
                                        {payment.cardNumber && <span className="card-num">{payment.cardNumber}</span>}
                                        {payment.installmentMonths !== null && payment.installmentMonths > 0 && (
                                          <span className="installment">{payment.installmentMonths}개월</span>
                                        )}
                                        {payment.installmentMonths === 0 && <span className="installment">일시불</span>}
                                        {payment.approveNo && <span className="approve-no">승인: {payment.approveNo}</span>}
                                      </div>
                                    )}
                                    {/* 간편결제 상세 정보 */}
                                    {payment.paymentMethod?.startsWith('EASY_PAY') && (
                                      <div className="payment-detail-info">
                                        {payment.easyPayProvider && <span className="easy-pay-provider">{payment.easyPayProvider}</span>}
                                        {payment.cardCompany && <span className="card-info">{payment.cardCompany}</span>}
                                        {payment.cardNumber && <span className="card-num">{payment.cardNumber}</span>}
                                        {payment.approveNo && <span className="approve-no">승인: {payment.approveNo}</span>}
                                      </div>
                                    )}
                                    {/* 가상계좌 상세 정보 */}
                                    {payment.paymentMethod === 'VIRTUAL_ACCOUNT' && payment.bankName && (
                                      <div className="payment-detail-info">
                                        <span className="bank-info">{payment.bankName}</span>
                                        {payment.accountNumber && <span className="account-num">{payment.accountNumber}</span>}
                                        {payment.dueDate && <span className="due-date">입금기한: {formatDate(payment.dueDate)}</span>}
                                        {payment.depositorName && <span className="depositor">입금자: {payment.depositorName}</span>}
                                      </div>
                                    )}
                                    {/* 계좌이체 상세 정보 */}
                                    {payment.paymentMethod === 'TRANSFER' && payment.bankName && (
                                      <div className="payment-detail-info">
                                        <span className="bank-info">{payment.bankName}</span>
                                        {payment.settlementStatus && <span className="settlement">정산: {payment.settlementStatus}</span>}
                                      </div>
                                    )}
                                  </div>
                                </td>
                                <td>{formatPrice(payment.amount, payment.currency)}</td>
                                <td>
                                  <span className={`status-badge status-${payment.status?.toLowerCase()}`}>
                                    {payment.status}
                                  </span>
                                </td>
                                <td>{formatDate(payment.createdAt)}</td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={8} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '결제 내역이 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={payments.length} filteredCount={filteredPayments.length} />
                  </div>
                )}

                {/* 상품 관리 */}
                {activeTab === 'products' && (
                  <>
                    <SearchBar placeholder="상품 코드, 상품명, 요금제명으로 검색" />
                    <div className="admin-section">
                      <div className="admin-section-header">
                        <h2>상품 목록</h2>
                        <div className="admin-header-actions">
                          <span className="admin-count">
                            {appliedSearch ? `${filteredProducts.length}개 / 전체 ${products.length}개` : `${products.length}개`}
                          </span>
                          {canManagePromotions && (
                            <button className="admin-add-btn" onClick={() => openProductModal()}>
                              + 상품 추가
                            </button>
                          )}
                        </div>
                      </div>
                      <div className="admin-table-wrapper">
                        <table className="admin-table">
                          <thead>
                            <tr>
                              <th>코드</th>
                              <th>상품명</th>
                              <th>설명</th>
                              <th>상태</th>
                              <th>생성일</th>
                              {canManagePromotions && <th>관리</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {filteredProducts.length > 0 ? (
                              filteredProducts.map((product) => (
                                <tr key={product.code}>
                                  <td>{product.code}</td>
                                  <td>{product.name}</td>
                                  <td>{product.description || '-'}</td>
                                  <td>
                                    {canManagePromotions ? (
                                      <button
                                        className={`status-toggle-btn ${product.isActive ? 'active' : 'inactive'}`}
                                        onClick={() => handleToggleProduct(product.code)}
                                      >
                                        {product.isActive ? '활성' : '비활성'}
                                      </button>
                                    ) : (
                                      <span className={`status-badge ${product.isActive ? 'status-active' : 'status-inactive'}`}>
                                        {product.isActive ? '활성' : '비활성'}
                                      </span>
                                    )}
                                  </td>
                                  <td>{formatDate(product.createdAt)}</td>
                                  {canManagePromotions && (
                                    <td>
                                      <div className="action-buttons">
                                        <button
                                          className="action-btn edit"
                                          onClick={() => openProductModal(product)}
                                        >
                                          수정
                                        </button>
                                        <button
                                          className="action-btn delete"
                                          onClick={() => handleDeleteProduct(product.code)}
                                        >
                                          삭제
                                        </button>
                                      </div>
                                    </td>
                                  )}
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={canManagePromotions ? 6 : 5} className="empty-row">
                                  {appliedSearch ? '검색 결과가 없습니다.' : '등록된 상품이 없습니다.'}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <div className="admin-section">
                      <div className="admin-section-header">
                        <h2>요금제 목록</h2>
                        <div className="admin-header-actions">
                          <span className="admin-count">
                            {appliedSearch ? `${filteredPricePlans.length}개 / 전체 ${pricePlans.length}개` : `${pricePlans.length}개`}
                          </span>
                          {canManagePromotions && (
                            <button className="admin-add-btn" onClick={() => openPricePlanModal()}>
                              + 요금제 추가
                            </button>
                          )}
                        </div>
                      </div>
                      <div className="admin-table-wrapper">
                        <table className="admin-table">
                          <thead>
                            <tr>
                              <th>ID</th>
                              <th>상품코드</th>
                              <th>요금제명</th>
                              <th>설명</th>
                              <th>가격</th>
                              <th>상태</th>
                              <th>생성일</th>
                              {canManagePromotions && <th>관리</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {filteredPricePlans.length > 0 ? (
                              getPaginatedData(filteredPricePlans).map((plan) => (
                                <tr key={plan.id}>
                                  <td>{plan.id}</td>
                                  <td>{plan.productCode}</td>
                                  <td>{plan.name}</td>
                                  <td>{plan.description || '-'}</td>
                                  <td>{formatPrice(plan.price, plan.currency)}</td>
                                  <td>
                                    {canManagePromotions ? (
                                      <button
                                        className={`status-toggle-btn ${plan.isActive ? 'active' : 'inactive'}`}
                                        onClick={() => handleTogglePricePlan(plan.id)}
                                      >
                                        {plan.isActive ? '활성' : '비활성'}
                                      </button>
                                    ) : (
                                      <span className={`status-badge ${plan.isActive ? 'status-active' : 'status-inactive'}`}>
                                        {plan.isActive ? '활성' : '비활성'}
                                      </span>
                                    )}
                                  </td>
                                  <td>{formatDate(plan.createdAt)}</td>
                                  {canManagePromotions && (
                                    <td>
                                      <div className="action-buttons">
                                        <button
                                          className="action-btn edit"
                                          onClick={() => openPricePlanModal(plan)}
                                        >
                                          수정
                                        </button>
                                        <button
                                          className="action-btn delete"
                                          onClick={() => handleDeletePricePlan(plan.id)}
                                        >
                                          삭제
                                        </button>
                                      </div>
                                    </td>
                                  )}
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={canManagePromotions ? 8 : 7} className="empty-row">
                                  {appliedSearch ? '검색 결과가 없습니다.' : '등록된 요금제가 없습니다.'}
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                      <Pagination totalItems={pricePlans.length} filteredCount={filteredPricePlans.length} />
                    </div>
                  </>
                )}

                {/* 프로모션 관리 */}
                {activeTab === 'promotions' && (
                  <div className="admin-section">
                    <SearchBar placeholder="쿠폰 코드, 프로모션명으로 검색" />
                    <div className="admin-section-header">
                      <h2>프로모션 목록</h2>
                      <div className="admin-header-actions">
                        <span className="admin-count">
                          {appliedSearch ? `${filteredPromotions.length}개 / 전체 ${promotions.length}개` : `${promotions.length}개`}
                        </span>
                        {canManagePromotions && (
                          <button className="admin-add-btn" onClick={() => openPromotionModal()}>
                            + 프로모션 추가
                          </button>
                        )}
                      </div>
                    </div>
                    <div className="admin-table-wrapper">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>쿠폰 코드</th>
                            <th>프로모션명</th>
                            <th>할인율</th>
                            <th>할인금액</th>
                            <th>상품</th>
                            <th>사용 현황</th>
                            <th>유효 기간</th>
                            <th>상태</th>
                            {canManagePromotions && <th>관리</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {filteredPromotions.length > 0 ? (
                            getPaginatedData(filteredPromotions).map((promo) => (
                              <tr key={promo.id}>
                                <td>{promo.id}</td>
                                <td className="coupon-code">{promo.code}</td>
                                <td>{promo.name}</td>
                                <td>{promo.discountType}%</td>
                                <td>{promo.discountValue.toLocaleString()}원</td>
                                <td>{promo.productCode || '전체'}</td>
                                <td>
                                  {promo.usageCount} / {promo.usageLimit || '∞'}
                                </td>
                                <td>
                                  {promo.validFrom && promo.validUntil ? (
                                    <>
                                      {formatDate(promo.validFrom).split(' ')[0]}<br/>
                                      ~ {formatDate(promo.validUntil).split(' ')[0]}
                                    </>
                                  ) : promo.validFrom ? (
                                    `${formatDate(promo.validFrom).split(' ')[0]} ~`
                                  ) : promo.validUntil ? (
                                    `~ ${formatDate(promo.validUntil).split(' ')[0]}`
                                  ) : (
                                    '제한 없음'
                                  )}
                                </td>
                                <td>
                                  {canManagePromotions ? (
                                    <button
                                      className={`status-toggle-btn ${promo.isActive ? 'active' : 'inactive'}`}
                                      onClick={() => handleTogglePromotion(promo.id)}
                                    >
                                      {promo.isActive ? '활성' : '비활성'}
                                    </button>
                                  ) : (
                                    <span className={`status-badge ${promo.isActive ? 'status-active' : 'status-inactive'}`}>
                                      {promo.isActive ? '활성' : '비활성'}
                                    </span>
                                  )}
                                </td>
                                {canManagePromotions && (
                                  <td>
                                    <div className="action-buttons">
                                      <button
                                        className="action-btn edit"
                                        onClick={() => openPromotionModal(promo)}
                                      >
                                        수정
                                      </button>
                                      <button
                                        className="action-btn delete"
                                        onClick={() => handleDeletePromotion(promo.id)}
                                      >
                                        삭제
                                      </button>
                                    </div>
                                  </td>
                                )}
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={canManagePromotions ? 10 : 9} className="empty-row">
                                {appliedSearch ? '검색 결과가 없습니다.' : '등록된 프로모션이 없습니다.'}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <Pagination totalItems={promotions.length} filteredCount={filteredPromotions.length} />
                  </div>
                )}

              </>
            )}
          </div>
        </div>
      </div>

      {/* 프로모션 모달 */}
      {isPromotionModalOpen && (
        <div className="modal-overlay" onClick={closePromotionModal}>
          <div className="modal-content promotion-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingPromotion ? '프로모션 수정' : '프로모션 추가'}</h3>
              <button className="modal-close-btn" onClick={closePromotionModal}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>쿠폰 코드 <span className="required">*</span></label>
                <input
                  type="text"
                  value={promotionForm.code}
                  onChange={(e) => setPromotionForm({ ...promotionForm, code: e.target.value.toUpperCase() })}
                  placeholder="예: SUMMER2024"
                  maxLength={50}
                />
              </div>
              <div className="form-group">
                <label>프로모션 이름 <span className="required">*</span></label>
                <input
                  type="text"
                  value={promotionForm.name}
                  onChange={(e) => setPromotionForm({ ...promotionForm, name: e.target.value })}
                  placeholder="예: 여름 할인 이벤트"
                />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>할인율 (%) <span className="required">*</span></label>
                  <input
                    type="number"
                    value={promotionForm.discountType}
                    onChange={(e) => setPromotionForm({ ...promotionForm, discountType: parseInt(e.target.value) || 0 })}
                    min={0}
                    max={100}
                  />
                </div>
                <div className="form-group">
                  <label>할인 금액 (원)</label>
                  <input
                    type="number"
                    value={promotionForm.discountValue}
                    onChange={(e) => setPromotionForm({ ...promotionForm, discountValue: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>적용 상품</label>
                  <select
                    value={promotionForm.productCode}
                    onChange={(e) => setPromotionForm({ ...promotionForm, productCode: e.target.value })}
                  >
                    <option value="">전체 상품</option>
                    {products.map((product) => (
                      <option key={product.code} value={product.code}>
                        {product.name} ({product.code})
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label>사용 횟수 제한</label>
                  <input
                    type="number"
                    value={promotionForm.usageLimit}
                    onChange={(e) => setPromotionForm({ ...promotionForm, usageLimit: e.target.value })}
                    placeholder="무제한"
                    min={0}
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>시작일</label>
                  <input
                    type="datetime-local"
                    value={promotionForm.validFrom}
                    onChange={(e) => setPromotionForm({ ...promotionForm, validFrom: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>종료일</label>
                  <input
                    type="datetime-local"
                    value={promotionForm.validUntil}
                    onChange={(e) => setPromotionForm({ ...promotionForm, validUntil: e.target.value })}
                  />
                </div>
              </div>
              <div className="form-group checkbox-group">
                <label>
                  <input
                    type="checkbox"
                    checked={promotionForm.isActive}
                    onChange={(e) => setPromotionForm({ ...promotionForm, isActive: e.target.checked })}
                  />
                  활성화
                </label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closePromotionModal}>취소</button>
              <button className="btn-submit" onClick={handlePromotionSubmit}>
                {editingPromotion ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 사용자 권한 수정 모달 */}
      {isRoleModalOpen && editingUser && (
        <div className="modal-overlay" onClick={closeRoleModal}>
          <div className="modal-content role-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>사용자 권한 수정</h3>
              <button className="modal-close-btn" onClick={closeRoleModal}>✕</button>
            </div>
            <div className="modal-body">
              <div className="user-info-summary">
                <div className="info-item">
                  <label>이메일</label>
                  <span>{editingUser.email}</span>
                </div>
                <div className="info-item">
                  <label>이름</label>
                  <span>{editingUser.name || '-'}</span>
                </div>
                <div className="info-item">
                  <label>현재 권한</label>
                  <span className={`role-badge role-${editingUser.rolesCode}`}>
                    {getRoleName(editingUser.rolesCode)}
                  </span>
                </div>
              </div>
              <div className="form-group">
                <label>변경할 권한 <span className="required">*</span></label>
                <div className="role-options">
                  <button
                    type="button"
                    className={`role-option-btn ${selectedRole === '000' ? 'selected' : ''}`}
                    onClick={() => setSelectedRole('000')}
                  >
                    <span className="role-badge role-000">관리자</span>
                    <span className="role-desc">전체 기능 접근 가능</span>
                  </button>
                  <button
                    type="button"
                    className={`role-option-btn ${selectedRole === '001' ? 'selected' : ''}`}
                    onClick={() => setSelectedRole('001')}
                  >
                    <span className="role-badge role-001">매니저</span>
                    <span className="role-desc">관리자 페이지 조회만 가능</span>
                  </button>
                  <button
                    type="button"
                    className={`role-option-btn ${selectedRole === '002' ? 'selected' : ''}`}
                    onClick={() => setSelectedRole('002')}
                  >
                    <span className="role-badge role-002">일반</span>
                    <span className="role-desc">일반 사용자 권한</span>
                  </button>
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closeRoleModal}>취소</button>
              <button
                className="btn-submit"
                onClick={handleUpdateRole}
                disabled={isUpdatingRole || selectedRole === editingUser.rolesCode}
              >
                {isUpdatingRole ? '변경 중...' : '권한 변경'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 상품 모달 */}
      {isProductModalOpen && (
        <div className="modal-overlay" onClick={closeProductModal}>
          <div className="modal-content product-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingProduct ? '상품 수정' : '상품 추가'}</h3>
              <button className="modal-close-btn" onClick={closeProductModal}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>상품 코드 <span className="required">*</span></label>
                <input
                  type="text"
                  value={productForm.code}
                  onChange={(e) => setProductForm({ ...productForm, code: e.target.value.toUpperCase() })}
                  placeholder="예: BLC"
                  maxLength={3}
                  disabled={!!editingProduct}
                />
                {!editingProduct && <small>영문 대문자 3자 이하</small>}
              </div>
              <div className="form-group">
                <label>상품명 <span className="required">*</span></label>
                <input
                  type="text"
                  value={productForm.name}
                  onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                  placeholder="예: BULC"
                />
              </div>
              <div className="form-group">
                <label>설명</label>
                <textarea
                  value={productForm.description}
                  onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                  placeholder="상품 설명을 입력하세요"
                  rows={3}
                />
              </div>
              <div className="form-group checkbox-group">
                <label>
                  <input
                    type="checkbox"
                    checked={productForm.isActive}
                    onChange={(e) => setProductForm({ ...productForm, isActive: e.target.checked })}
                  />
                  활성화
                </label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closeProductModal}>취소</button>
              <button className="btn-submit" onClick={handleProductSubmit}>
                {editingProduct ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 요금제 모달 */}
      {isPricePlanModalOpen && (
        <div className="modal-overlay" onClick={closePricePlanModal}>
          <div className="modal-content price-plan-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingPricePlan ? '요금제 수정' : '요금제 추가'}</h3>
              <button className="modal-close-btn" onClick={closePricePlanModal}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>상품 <span className="required">*</span></label>
                <select
                  value={pricePlanForm.productCode}
                  onChange={(e) => setPricePlanForm({ ...pricePlanForm, productCode: e.target.value })}
                  disabled={!!editingPricePlan}
                >
                  <option value="">상품을 선택하세요</option>
                  {products.map((product) => (
                    <option key={product.code} value={product.code}>
                      {product.name} ({product.code})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>요금제명 <span className="required">*</span></label>
                <input
                  type="text"
                  value={pricePlanForm.name}
                  onChange={(e) => setPricePlanForm({ ...pricePlanForm, name: e.target.value })}
                  placeholder="예: Basic Plan"
                />
              </div>
              <div className="form-group">
                <label>설명</label>
                <textarea
                  value={pricePlanForm.description}
                  onChange={(e) => setPricePlanForm({ ...pricePlanForm, description: e.target.value })}
                  placeholder="요금제 설명을 입력하세요"
                  rows={2}
                />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>가격 <span className="required">*</span></label>
                  <input
                    type="number"
                    value={pricePlanForm.price}
                    onChange={(e) => setPricePlanForm({ ...pricePlanForm, price: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label>통화</label>
                  <select
                    value={pricePlanForm.currency}
                    onChange={(e) => setPricePlanForm({ ...pricePlanForm, currency: e.target.value })}
                  >
                    <option value="KRW">KRW (원)</option>
                    <option value="USD">USD ($)</option>
                  </select>
                </div>
              </div>
              <div className="form-group checkbox-group">
                <label>
                  <input
                    type="checkbox"
                    checked={pricePlanForm.isActive}
                    onChange={(e) => setPricePlanForm({ ...pricePlanForm, isActive: e.target.checked })}
                  />
                  활성화
                </label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closePricePlanModal}>취소</button>
              <button className="btn-submit" onClick={handlePricePlanSubmit}>
                {editingPricePlan ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 라이선스 플랜 모달 */}
      {isLicensePlanModalOpen && (
        <div className="modal-overlay" onClick={closeLicensePlanModal}>
          <div className="modal-content license-plan-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingLicensePlan ? '라이선스 플랜 수정' : '라이선스 플랜 추가'}</h3>
              <button className="modal-close-btn" onClick={closeLicensePlanModal}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-row">
                <div className="form-group">
                  <label>상품 <span className="required">*</span></label>
                  <select
                    value={licensePlanForm.productId}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, productId: e.target.value })}
                  >
                    <option value="">상품 선택</option>
                    {products.map(p => (
                      <option key={p.id} value={p.id}>{p.code} - {p.name}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label>플랜 코드 <span className="required">*</span></label>
                  <input
                    type="text"
                    value={licensePlanForm.code}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, code: e.target.value })}
                    placeholder="예: BULC-PRO-1Y"
                    maxLength={64}
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>플랜명 <span className="required">*</span></label>
                  <input
                    type="text"
                    value={licensePlanForm.name}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, name: e.target.value })}
                    placeholder="예: BUL:C PRO 1년"
                  />
                </div>
                <div className="form-group">
                  <label>라이선스 유형 <span className="required">*</span></label>
                  <select
                    value={licensePlanForm.licenseType}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, licenseType: e.target.value as 'TRIAL' | 'SUBSCRIPTION' | 'PERPETUAL' })}
                  >
                    <option value="TRIAL">TRIAL (체험판)</option>
                    <option value="SUBSCRIPTION">SUBSCRIPTION (구독)</option>
                    <option value="PERPETUAL">PERPETUAL (영구)</option>
                  </select>
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>유효기간 (일) <span className="required">*</span></label>
                  <input
                    type="number"
                    value={licensePlanForm.durationDays}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, durationDays: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
                <div className="form-group">
                  <label>유예기간 (일)</label>
                  <input
                    type="number"
                    value={licensePlanForm.graceDays}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, graceDays: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>최대 기기 수 <span className="required">*</span></label>
                  <input
                    type="number"
                    value={licensePlanForm.maxActivations}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, maxActivations: parseInt(e.target.value) || 1 })}
                    min={1}
                  />
                </div>
                <div className="form-group">
                  <label>최대 동시 세션 <span className="required">*</span></label>
                  <input
                    type="number"
                    value={licensePlanForm.maxConcurrentSessions}
                    onChange={(e) => setLicensePlanForm({ ...licensePlanForm, maxConcurrentSessions: parseInt(e.target.value) || 1 })}
                    min={1}
                  />
                </div>
              </div>
              <div className="form-group">
                <label>오프라인 허용 일수</label>
                <input
                  type="number"
                  value={licensePlanForm.allowOfflineDays}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, allowOfflineDays: parseInt(e.target.value) || 0 })}
                  min={0}
                />
              </div>
              <div className="form-group">
                <label>설명</label>
                <textarea
                  value={licensePlanForm.description}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, description: e.target.value })}
                  placeholder="플랜 설명을 입력하세요"
                  rows={2}
                />
              </div>
              <div className="form-group">
                <label>Entitlements</label>
                <textarea
                  value={licensePlanForm.entitlements}
                  onChange={(e) => setLicensePlanForm({ ...licensePlanForm, entitlements: e.target.value })}
                  placeholder="쉼표로 구분 (예: core-simulation, advanced-visualization)"
                  rows={2}
                />
                <small>기능 식별자를 쉼표(,)로 구분하여 입력하세요</small>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-cancel" onClick={closeLicensePlanModal}>취소</button>
              <button className="btn-submit" onClick={handleLicensePlanSubmit}>
                {editingLicensePlan ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

    </>
  );
};

export default AdminPage;
