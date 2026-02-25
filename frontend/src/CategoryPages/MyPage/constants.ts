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
  'admin-users', 'admin-payments', 'admin-products', 'admin-licenses', 'admin-promotions', 'admin-redeem',
];

export const ADMIN_ITEMS_PER_PAGE = 10;
