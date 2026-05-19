import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';
import {
  BOOTH_GIFT_PATH,
  BoothGiftEventConfig,
  fetchBoothGiftConfig,
  isBoothGiftEligible,
} from '../utils/eventConfig';
import './BoothGiftBanner.css';

interface BoothGiftBannerProps {
  className?: string;
}

const BoothGiftBanner: React.FC<BoothGiftBannerProps> = ({ className }) => {
  const navigate = useNavigate();
  const { language, isLanguageReady } = useLanguage();
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

  if (!ready || !isLanguageReady || !isBoothGiftEligible(config, language) || !config) {
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
