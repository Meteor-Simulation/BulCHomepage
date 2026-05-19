import { getApiBaseUrl } from './api';

export interface BoothGiftEventConfig {
  enabled: boolean;
  startAt: string;
  endAt: string;
  title: string;
  subtitle: string;
  description: string;
  boothInfo: string;
  bannerTitle: string;
  bannerCta: string;
  bannerDescription: string;
}

interface EventConfigPayload {
  boothGift: BoothGiftEventConfig;
}

const PENDING_REDIRECT_KEY = 'pendingEventRedirect';
const CLAIM_KEY_PREFIX = 'boothGiftClaimed:';
const CONFIG_PATH = '/event-config.json';
const CACHE_TTL_MS = 60_000;

/** 사은품 이벤트 대상 국가 (현재 한국 전용) */
export const BOOTH_GIFT_TARGET_COUNTRY = 'KR';

export interface BoothGiftClaimRecord {
  claimedAt: string;
  eventStartAt: string;
  eventEndAt: string;
}

let cache: { value: BoothGiftEventConfig | null; fetchedAt: number } | null = null;
let inflight: Promise<BoothGiftEventConfig | null> | null = null;

export const fetchBoothGiftConfig = async (
  options: { force?: boolean } = {}
): Promise<BoothGiftEventConfig | null> => {
  const now = Date.now();
  if (!options.force && cache && now - cache.fetchedAt < CACHE_TTL_MS) {
    return cache.value;
  }
  if (inflight) {
    return inflight;
  }

  inflight = (async () => {
    try {
      const response = await fetch(`${CONFIG_PATH}?t=${now}`, { cache: 'no-store' });
      if (!response.ok) {
        cache = { value: null, fetchedAt: Date.now() };
        return null;
      }
      const payload = (await response.json()) as EventConfigPayload;
      const value = payload?.boothGift ?? null;
      cache = { value, fetchedAt: Date.now() };
      return value;
    } catch (error) {
      console.warn('[eventConfig] failed to load event config', error);
      cache = { value: null, fetchedAt: Date.now() };
      return null;
    } finally {
      inflight = null;
    }
  })();

  return inflight;
};

export const isBoothGiftActive = (config: BoothGiftEventConfig | null): boolean => {
  if (!config || !config.enabled) return false;
  const now = Date.now();
  const start = Date.parse(config.startAt);
  const end = Date.parse(config.endAt);
  if (Number.isNaN(start) || Number.isNaN(end)) return false;
  return now >= start && now <= end;
};

/** 국가 코드가 사은품 이벤트 대상인지 판정 (한국만 허용, null/undefined는 제외) */
export const isCountryEligibleForBoothGift = (
  country: string | null | undefined
): boolean => country === BOOTH_GIFT_TARGET_COUNTRY;

/** 활성 상태 + 사용자 국가 적격성 종합 판정 */
export const isBoothGiftEligible = (
  config: BoothGiftEventConfig | null,
  country: string | null | undefined
): boolean => isBoothGiftActive(config) && isCountryEligibleForBoothGift(country);

/** 현재 로그인한 사용자의 국가 코드 조회 (이벤트 게이팅 전용) */
export const fetchCurrentUserCountry = async (): Promise<string | null> => {
  const token = localStorage.getItem('accessToken');
  if (!token) return null;
  try {
    const response = await fetch(`${getApiBaseUrl()}/api/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) return null;
    const data = await response.json();
    return (data?.country as string | undefined) ?? null;
  } catch (error) {
    console.warn('[eventConfig] failed to fetch user country', error);
    return null;
  }
};

export const markPendingEventRedirect = (): void => {
  try {
    sessionStorage.setItem(PENDING_REDIRECT_KEY, '1');
  } catch {
    /* sessionStorage unavailable - ignore */
  }
};

export const consumePendingEventRedirect = (): boolean => {
  try {
    const value = sessionStorage.getItem(PENDING_REDIRECT_KEY);
    if (value) {
      sessionStorage.removeItem(PENDING_REDIRECT_KEY);
      return true;
    }
  } catch {
    /* ignore */
  }
  return false;
};

const buildClaimKey = (userId: string, config: BoothGiftEventConfig): string =>
  `${CLAIM_KEY_PREFIX}${userId}:${config.startAt}`;

export const getBoothGiftClaim = (
  userId: string | undefined | null,
  config: BoothGiftEventConfig | null
): BoothGiftClaimRecord | null => {
  if (!userId || !config) return null;
  try {
    const raw = localStorage.getItem(buildClaimKey(userId, config));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as BoothGiftClaimRecord;
    if (parsed?.eventStartAt !== config.startAt) return null;
    return parsed;
  } catch {
    return null;
  }
};

export const markBoothGiftClaimed = (
  userId: string | undefined | null,
  config: BoothGiftEventConfig | null
): BoothGiftClaimRecord | null => {
  if (!userId || !config) return null;
  const record: BoothGiftClaimRecord = {
    claimedAt: new Date().toISOString(),
    eventStartAt: config.startAt,
    eventEndAt: config.endAt,
  };
  try {
    localStorage.setItem(buildClaimKey(userId, config), JSON.stringify(record));
    return record;
  } catch {
    return null;
  }
};

export const clearBoothGiftClaim = (
  userId: string | undefined | null,
  config: BoothGiftEventConfig | null
): void => {
  if (!userId || !config) return;
  try {
    localStorage.removeItem(buildClaimKey(userId, config));
  } catch {
    /* ignore */
  }
};

export const BOOTH_GIFT_PATH = '/event/booth-gift';
