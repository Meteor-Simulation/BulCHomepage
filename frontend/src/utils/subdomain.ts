export type SubdomainType = 'meteor' | 'bulc' | 'vr';

// 서브도메인이 없을 때 기본 카테고리
const DEFAULT_CATEGORY: SubdomainType = 'bulc';

/**
 * 현재 접속한 서브도메인을 감지합니다.
 * - meteor.localhost, meteor.msimul.com → 'meteor'
 * - bulc.localhost, bulc.msimul.com → 'bulc'
 * - vr.localhost, vr.msimul.com → 'vr'
 * - 그 외 (localhost, IP 등) → 기본값 'meteor'
 */
export const getSubdomain = (): SubdomainType => {
  const hostname = window.location.hostname;
  const subdomain = hostname.split('.')[0].toLowerCase();

  if (subdomain === 'meteor') return 'meteor';
  if (subdomain === 'bulc') return 'bulc';
  if (subdomain === 'vr') return 'vr';

  // 서브도메인이 없는 경우 (localhost, IP, msimul.com 등) 기본 카테고리로
  return DEFAULT_CATEGORY;
};

/**
 * 항상 카테고리 페이지로 접속하므로 true 반환
 * (로고 클릭 시 항상 해당 카테고리의 Intro로 이동)
 */
export const isSubdomainAccess = (): boolean => {
  return true;
};

/**
 * 서브도메인에 해당하는 기본 경로를 반환합니다.
 */
export const getSubdomainPath = (): string => {
  const subdomain = getSubdomain();
  switch (subdomain) {
    case 'meteor': return '/meteor';
    case 'bulc': return '/bulc';
    case 'vr': return '/vr';
    default: return '/';
  }
};
