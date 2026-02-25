import React from 'react';

// 메뉴 타입 정의
export type MenuSection = 'profile' | 'account' | 'subscription' | 'payment' | 'redeem' | 'admin-users' | 'admin-payments' | 'admin-products' | 'admin-licenses' | 'admin-promotions' | 'admin-redeem';

// 관리자용 인터페이스
export interface AdminUser {
  id: string;
  email: string;
  name: string;
  phone: string;
  rolesCode: string;
  countryCode: string;
  createdAt: string;
}

export interface Product {
  id: string;
  code: string;
  name: string;
  description: string;
  isActive: boolean;
  createdAt: string;
}

export interface PricePlan {
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

export interface AdminLicense {
  id: string;
  licenseKey: string;
  ownerType: string;
  ownerId: string;
  status: string;
  validUntil: string;
  createdAt: string;
}

export interface AdminPayment {
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

export interface Promotion {
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

export interface LicensePlan {
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

export interface RedeemCampaign {
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

export interface RedeemCodeItem {
  id: string;
  campaignId: string;
  codeType: 'RANDOM' | 'CUSTOM';
  maxRedemptions: number;
  currentRedemptions: number;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
}

export interface UserInfo {
  email: string;
  name: string;
  phone: string;
  country: string;
  language: string | null;
}

export interface License {
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

export interface Activation {
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

export interface Subscription {
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

export interface BillingKey {
  id: number;
  cardCompany: string;
  cardNumber: string;
  cardType: string;
  ownerType: string;
  isDefault: boolean;
  createdAt: string;
}

// 관리자 패널 공통 검색/페이지네이션 props
export interface AdminSearchProps {
  searchQuery: string;
  appliedSearch: string;
  currentPage: number;
  onSearchQueryChange: (value: string) => void;
  onSearch: () => void;
  onSearchKeyPress: (e: React.KeyboardEvent) => void;
  onClearSearch: () => void;
  onPageChange: (page: number) => void;
  isLoading: boolean;
}
