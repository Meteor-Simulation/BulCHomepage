import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header';
import { useAuth } from '../../context/AuthContext';
import {
  BoothGiftClaimRecord,
  BoothGiftEventConfig,
  fetchBoothGiftConfig,
  fetchCurrentUserCountry,
  getBoothGiftClaim,
  isBoothGiftActive,
  isCountryEligibleForBoothGift,
  markBoothGiftClaimed,
} from '../../utils/eventConfig';
import './BoothGiftPage.css';

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

const datetimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const formatDateRange = (startAt: string, endAt: string): string => {
  const start = new Date(startAt);
  const end = new Date(endAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return '';
  return `${dateFormatter.format(start)} ~ ${dateFormatter.format(end)}`;
};

const formatDateTime = (iso: string): string => {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return datetimeFormatter.format(date);
};

const BoothGiftPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, isLoggedIn, isAuthReady } = useAuth();
  const [config, setConfig] = useState<BoothGiftEventConfig | null>(null);
  const [country, setCountry] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [claim, setClaim] = useState<BoothGiftClaimRecord | null>(null);
  const [pageEnteredAt] = useState<string>(() => new Date().toISOString());

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [configValue, countryValue] = await Promise.all([
        fetchBoothGiftConfig({ force: true }),
        fetchCurrentUserCountry(),
      ]);
      if (cancelled) return;
      setConfig(configValue);
      setCountry(countryValue);
      setIsLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!isAuthReady) return;
    if (!isLoggedIn) {
      navigate('/error', { state: { errorCode: 401 } });
    }
  }, [isAuthReady, isLoggedIn, navigate]);

  useEffect(() => {
    if (!user?.id || !config) return;
    setClaim(getBoothGiftClaim(user.id, config));
  }, [user?.id, config]);

  const active = useMemo(() => isBoothGiftActive(config), [config]);
  const countryEligible = useMemo(
    () => isCountryEligibleForBoothGift(country),
    [country]
  );
  const dateRange = useMemo(
    () => (config ? formatDateRange(config.startAt, config.endAt) : ''),
    [config]
  );
  const issuedAt = useMemo(() => formatDateTime(pageEnteredAt), [pageEnteredAt]);

  const handleMarkClaimed = () => {
    if (!user?.id || !config) return;
    const confirmed = window.confirm(
      '[부스 직원 확인]\n사은품을 전달하셨습니까?\n\n확인 시 본 화면이 "수령 완료" 상태로 변경되며, 동일 사용자가 같은 기기에서 재방문 시 사은품 페이지가 노출되지 않습니다.\n\n이 작업은 운영자가 직접 수행해야 합니다.'
    );
    if (!confirmed) return;
    const record = markBoothGiftClaimed(user.id, config);
    if (record) {
      setClaim(record);
    }
  };

  if (!isAuthReady || isLoading) {
    return (
      <div className="booth-gift-page">
        <Header />
        <main className="booth-gift-main">
          <div className="booth-gift-loading">로딩 중...</div>
        </main>
      </div>
    );
  }

  if (!isLoggedIn) {
    return null;
  }

  if (!config || !active || !countryEligible) {
    return (
      <div className="booth-gift-page">
        <Header />
        <main className="booth-gift-main">
          <div className="booth-gift-card booth-gift-card--inactive">
            <h1 className="booth-gift-title">이벤트가 진행되고 있지 않습니다</h1>
            <p className="booth-gift-description">
              현재 진행 중인 사은품 이벤트가 없거나, 본 이벤트의 대상이 아닙니다.
            </p>
            <button
              type="button"
              className="booth-gift-btn"
              onClick={() => navigate('/')}
            >
              메인으로 돌아가기
            </button>
          </div>
        </main>
      </div>
    );
  }

  const displayName = user?.name || user?.email?.split('@')[0] || '회원';
  const isClaimed = claim !== null;

  return (
    <div className="booth-gift-page">
      <Header />
      <main className="booth-gift-main">
        <div className={`booth-gift-card${isClaimed ? ' booth-gift-card--claimed' : ''}`}>
          <div className={`booth-gift-badge${isClaimed ? ' booth-gift-badge--claimed' : ''}`}>
            {isClaimed ? 'CLAIMED' : 'EVENT'}
          </div>
          <h1 className="booth-gift-title">{config.title}</h1>
          <p className="booth-gift-subtitle">
            {isClaimed ? '이미 사은품을 수령하셨습니다.' : config.subtitle}
          </p>

          {isClaimed && (
            <div className="booth-gift-claimed-banner" role="status" aria-live="polite">
              <div className="booth-gift-claimed-check" aria-hidden="true">✓</div>
              <div className="booth-gift-claimed-body">
                <strong>수령 완료</strong>
                <span>수령 시각: {formatDateTime(claim!.claimedAt)}</span>
              </div>
            </div>
          )}

          <div className="booth-gift-user-block">
            <div className="booth-gift-user-row">
              <span className="booth-gift-user-label">이름</span>
              <span className="booth-gift-user-value">{displayName}</span>
            </div>
            <div className="booth-gift-user-row">
              <span className="booth-gift-user-label">이메일</span>
              <span className="booth-gift-user-value">{user?.email ?? '-'}</span>
            </div>
            <div className="booth-gift-user-row">
              <span className="booth-gift-user-label">
                {isClaimed ? '수령 시각' : '발급 시각'}
              </span>
              <span className="booth-gift-user-value">
                {isClaimed ? formatDateTime(claim!.claimedAt) : issuedAt}
              </span>
            </div>
          </div>

          <div className="booth-gift-notice">
            <p className="booth-gift-description">{config.description}</p>
            <div className="booth-gift-meta">
              <div>
                <strong>이벤트 기간</strong>
                <span>{dateRange}</span>
              </div>
              <div>
                <strong>부스 위치</strong>
                <span>{config.boothInfo}</span>
              </div>
            </div>
          </div>

          {isClaimed ? (
            <button
              type="button"
              className="booth-gift-btn booth-gift-btn--ghost booth-gift-btn--full"
              onClick={() => navigate('/')}
            >
              메인으로 돌아가기
            </button>
          ) : (
            <div className="booth-gift-operator-section">
              <div className="booth-gift-operator-warning">
                <span className="booth-gift-operator-warning__icon" aria-hidden="true">⚠️</span>
                <div className="booth-gift-operator-warning__text">
                  <strong>아래 버튼은 부스 직원(운영자)이 직접 누릅니다.</strong>
                  <span>사용자께서는 본 화면을 그대로 부스 직원에게 보여주세요.</span>
                </div>
              </div>
              <button
                type="button"
                className="booth-gift-btn booth-gift-btn--operator"
                onClick={handleMarkClaimed}
              >
                <span className="booth-gift-btn__tag">부스 직원 전용</span>
                <span className="booth-gift-btn__main">사은품 수령 완료 처리</span>
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default BoothGiftPage;
