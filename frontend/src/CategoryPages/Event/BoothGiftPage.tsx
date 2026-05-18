import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header';
import { useAuth } from '../../context/AuthContext';
import {
  BoothGiftClaimRecord,
  BoothGiftEventConfig,
  clearBoothGiftClaim,
  fetchBoothGiftConfig,
  getBoothGiftClaim,
  isBoothGiftActive,
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
  const [isLoading, setIsLoading] = useState(true);
  const [claim, setClaim] = useState<BoothGiftClaimRecord | null>(null);
  const [pageEnteredAt] = useState<string>(() => new Date().toISOString());

  useEffect(() => {
    let cancelled = false;
    fetchBoothGiftConfig({ force: true }).then((value) => {
      if (!cancelled) {
        setConfig(value);
        setIsLoading(false);
      }
    });
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
  const dateRange = useMemo(
    () => (config ? formatDateRange(config.startAt, config.endAt) : ''),
    [config]
  );
  const issuedAt = useMemo(() => formatDateTime(pageEnteredAt), [pageEnteredAt]);

  const handleMarkClaimed = () => {
    if (!user?.id || !config) return;
    const confirmed = window.confirm(
      '사은품을 받으셨나요? "확인"을 누르면 수령 완료로 표시되며, 이후 동일 기기에서는 "수령 완료" 화면으로 표시됩니다.'
    );
    if (!confirmed) return;
    const record = markBoothGiftClaimed(user.id, config);
    if (record) {
      setClaim(record);
    }
  };

  const handleUndoClaim = () => {
    if (!user?.id || !config) return;
    const confirmed = window.confirm(
      '수령 표시를 취소합니다. (실수로 눌렀거나 부스 직원이 사은품을 전달하지 않은 경우에만 사용하세요)'
    );
    if (!confirmed) return;
    clearBoothGiftClaim(user.id, config);
    setClaim(null);
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

  if (!config || !active) {
    return (
      <div className="booth-gift-page">
        <Header />
        <main className="booth-gift-main">
          <div className="booth-gift-card booth-gift-card--inactive">
            <h1 className="booth-gift-title">이벤트가 진행되고 있지 않습니다</h1>
            <p className="booth-gift-description">
              현재 진행 중인 사은품 이벤트가 없습니다. 추후 이벤트 일정은 홈페이지를 통해 안내드립니다.
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

          {!isClaimed && (
            <div className="booth-gift-staff-callout">
              <strong>부스 직원 안내</strong>
              <p>본 화면 확인 후 사은품을 전달하시고, 아래 <em>"사은품 수령 완료"</em> 버튼을 눌러주세요.</p>
            </div>
          )}

          {isClaimed ? (
            <div className="booth-gift-action-row">
              <button
                type="button"
                className="booth-gift-btn booth-gift-btn--ghost"
                onClick={() => navigate('/')}
              >
                메인으로 돌아가기
              </button>
              <button
                type="button"
                className="booth-gift-undo-link"
                onClick={handleUndoClaim}
              >
                실수로 누르셨나요? 수령 표시 취소
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="booth-gift-btn booth-gift-btn--claim"
              onClick={handleMarkClaimed}
            >
              사은품 수령 완료
            </button>
          )}
        </div>
      </main>
    </div>
  );
};

export default BoothGiftPage;
