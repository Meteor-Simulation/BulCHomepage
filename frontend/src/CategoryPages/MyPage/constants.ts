import { MenuSection } from './types';

export const COUNTRIES = [
  { code: 'KR', name: '대한민국', currency: 'KRW' },
  { code: 'CN', name: '중국', currency: 'USD' },
  { code: 'JP', name: '일본', currency: 'USD' },
  { code: 'US', name: '미국', currency: 'USD' },
  { code: 'EU', name: '유럽', currency: 'USD' },
  { code: 'RU', name: '러시아', currency: 'USD' },
];

export const LANGUAGES = [
  { code: 'ko', name: '한국어' },
  { code: 'en', name: 'English' },
];

export const VALID_MENU_SECTIONS: MenuSection[] = [
  'profile', 'account', 'subscription', 'payment', 'redeem',
  'admin-users', 'admin-contacts',
  'admin-mailing', 'admin-popups',
  'admin-products', 'admin-promotions', 'admin-payments',
  'admin-licenses', 'admin-redeem',
];

export const ADMIN_ITEMS_PER_PAGE = 10;

/**
 * 관리자 메뉴 항목 → i18n 라벨 키 (MDP-908).
 *
 * 메뉴를 JSX 에 하드코딩하지 않고 데이터로 둔다. 모듈이 하나 늘 때 고칠 곳이
 * 이 파일 한 곳이 되고, 항목이 어느 묶음에 속하는지 한눈에 보인다.
 */
export const MENU_LABEL_KEYS: Record<MenuSection, string> = {
  'profile': 'profile',
  'account': 'account',
  'subscription': 'subscription',
  'payment': 'payment',
  'redeem': 'redeem',
  'admin-users': 'adminUsers',
  'admin-contacts': 'adminContacts',
  'admin-mailing': 'adminMailing',
  'admin-popups': 'adminPopups',
  'admin-products': 'adminProducts',
  'admin-promotions': 'adminPromotions',
  'admin-payments': 'adminPayments',
  'admin-licenses': 'adminLicenses',
  'admin-redeem': 'adminRedeem',
};

/**
 * 관리자 하위 메뉴 묶음 — 백엔드 모듈 경계와 같은 선이다.
 *
 * - 회원: 회원 계정(account) + 비회원 컨택(lead 모듈)
 * - 소통: 메일 발송(mail 모듈) + 팝업 공지(콘텐츠 모듈)
 * - 상품·결제: 카탈로그(상품·프로모션) + 결제(payment 모듈).
 *   프로모션은 가격·할인이라 라이선스가 아니라 여기 속한다
 * - 라이선스: 발급·조회(licensing 모듈) + 리딤 캠페인(라이선스 발급 수단)
 */
export const ADMIN_MENU_GROUPS: { labelKey: string; items: MenuSection[] }[] = [
  { labelKey: 'myPage.menu.adminGroupMembers', items: ['admin-users', 'admin-contacts'] },
  { labelKey: 'myPage.menu.adminGroupComms', items: ['admin-mailing', 'admin-popups'] },
  { labelKey: 'myPage.menu.adminGroupCommerce', items: ['admin-products', 'admin-promotions', 'admin-payments'] },
  { labelKey: 'myPage.menu.adminGroupLicense', items: ['admin-licenses', 'admin-redeem'] },
];
