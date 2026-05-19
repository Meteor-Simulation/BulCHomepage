import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BOOTH_GIFT_PATH,
  BoothGiftEventConfig,
  fetchBoothGiftConfig,
  isBoothGiftEligible,
} from '../utils/eventConfig';
import './BoothGiftBanner.css';

interface BoothGiftBannerProps {
  /** 사용자의 국가 코드. 'KR'인 경우에만 배너 노출 */
  userCountry?: string | null;
  className?: string;
}

const BoothGiftBanner: React.FC<BoothGiftBannerProps> = ({ userCountry, className }) => {
  const navigate = useNavigate();
  const [config, setConfig] = useState<BoothGiftEventConfig | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchBoothGiftConfig().then((value) => {
      if (!cancelled) {
        setConfig(value);
        setReady(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!ready || !isBoothGiftEligible(config, userCountry) || !config) {
    return null;
  }

  return (
    <div className={`booth-gift-banner${className ? ` ${className}` : ''}`}>
      <div className="booth-gift-banner__content">
        <span className="booth-gift-banner__badge">EVENT</span>
        <div className="booth-gift-banner__text">
          <strong className="booth-gift-banner__title">{config.bannerTitle}</strong>
          <span className="booth-gift-banner__desc">{config.bannerDescription}</span>
        </div>
      </div>
      <button
        type="button"
        className="booth-gift-banner__cta"
        onClick={() => navigate(BOOTH_GIFT_PATH)}
      >
        {config.bannerCta}
      </button>
    </div>
  );
};

export default BoothGiftBanner;
