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
const CONFIG_PATH = '/event-config.json';
const CACHE_TTL_MS = 60_000;

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

export const BOOTH_GIFT_PATH = '/event/booth-gift';
