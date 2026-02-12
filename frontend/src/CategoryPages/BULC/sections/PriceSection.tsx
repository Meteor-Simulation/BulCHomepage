import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useLanguage } from '../../../context/LanguageContext';
import { getApiBaseUrl } from '../../../utils/api';

interface PricePlan {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
}

interface PriceSectionProps {
  onPurchaseClick: () => void;
  onFreeClick?: () => void;
}

const FEATURE_KEYS: Record<string, string> = {
  'BUL:C PRO': 'pro',
  'BUL:C 3D Premium': 'premium',
};

const COMING_SOON_PLANS = ['BUL:C 3D Premium'];

const PriceSection: React.FC<PriceSectionProps> = ({ onPurchaseClick, onFreeClick }) => {
  const { t } = useTranslation();
  const { language } = useLanguage();
  const currency = language === 'ko' ? 'KRW' : 'USD';
  const [plans, setPlans] = useState<PricePlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchPlans = useCallback(async (cur: string) => {
    setLoading(true);
    setError(false);
    try {
      const res = await fetch(`${getApiBaseUrl()}/api/products/001/plans?currency=${cur}`);
      if (!res.ok) throw new Error('Failed to fetch');
      const data: PricePlan[] = await res.json();
      setPlans(data);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPlans(currency);
  }, [currency, fetchPlans]);

  const formatPrice = (price: number, cur: string) => {
    if (cur === 'KRW') {
      return price.toLocaleString() + '원';
    }
    return '$' + price.toLocaleString();
  };

  const getFeatures = (planName: string): string[] => {
    const key = FEATURE_KEYS[planName];
    if (!key) return [];
    return t(`bulc.price.${key}.features`, { returnObjects: true }) as string[];
  };

  const getIncludes = (planName: string): string | null => {
    const key = FEATURE_KEYS[planName];
    if (!key) return null;
    const val = t(`bulc.price.${key}.includes`, { defaultValue: '' });
    return val || null;
  };

  return (
    <section id="price" className="bulc-price">
      <div className="bulc-price__container">
        {loading && (
          <div className="bulc-price__loading">
            <div className="bulc-price__spinner" />
            <p>{t('bulc.price.loading')}</p>
          </div>
        )}

        {error && (
          <div className="bulc-price__error">
            <p>{t('bulc.price.error')}</p>
            <button className="bulc-price__retry-btn" onClick={() => fetchPlans(currency)}>
              {t('bulc.price.retry')}
            </button>
          </div>
        )}

        {!loading && !error && (
          <div className="bulc-price__grid">
            {/* 무료 플랜 */}
            <div className="bulc-price__card bulc-price__card--free">
              <div className="bulc-price__card-header">
                <h3 className="bulc-price__card-name">{t('bulc.price.free.name')}</h3>
                <p className="bulc-price__card-desc">{t('bulc.price.free.desc')}</p>
              </div>
              <div className="bulc-price__card-price">
                <span className="bulc-price__card-amount">{t('bulc.price.free.price')}</span>
                <span className="bulc-price__card-period">{t('bulc.price.free.period')}</span>
              </div>
              <ul className="bulc-price__card-features">
                {(t('bulc.price.free.features', { returnObjects: true }) as string[]).map((f, i) => (
                  <li key={i}>{f}</li>
                ))}
              </ul>
              <button className="bulc-price__card-btn bulc-price__card-btn--free" onClick={onFreeClick || onPurchaseClick}>
                {t('bulc.price.free.button')}
              </button>
            </div>

            {/* DB 유료 플랜 */}
            {plans.map((plan) => {
              const features = getFeatures(plan.name);
              const includes = getIncludes(plan.name);
              const isComingSoon = COMING_SOON_PLANS.includes(plan.name);
              return (
                <div key={plan.id} className={`bulc-price__card${isComingSoon ? ' bulc-price__card--coming-soon' : ''}`}>
                  {isComingSoon && (
                    <div className="bulc-price__card-overlay">
                      <span>{t('bulc.price.comingSoon')}</span>
                    </div>
                  )}
                  <div className="bulc-price__card-header">
                    <h3 className="bulc-price__card-name">{plan.name}</h3>
                    {plan.description && (
                      <p className="bulc-price__card-desc">{plan.description}</p>
                    )}
                  </div>
                  <div className="bulc-price__card-price">
                    <span className="bulc-price__card-amount">
                      {formatPrice(plan.price, plan.currency)}
                    </span>
                    <span className="bulc-price__card-period">{t('bulc.price.perYear')}</span>
                  </div>
                  {(includes || features.length > 0) && (
                    <ul className="bulc-price__card-features">
                      {includes && <li className="bulc-price__card-features-includes">{includes}</li>}
                      {features.map((f, i) => (
                        <li key={i}>{f}</li>
                      ))}
                    </ul>
                  )}
                  <button
                    className="bulc-price__card-btn"
                    onClick={isComingSoon ? undefined : onPurchaseClick}
                    disabled={isComingSoon}
                  >
                    {t('bulc.price.purchase')}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
};

export default PriceSection;
