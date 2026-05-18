import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header';
import { useAuth } from '../../context/AuthContext';
import {
  BoothGiftEventConfig,
  fetchBoothGiftConfig,
  isBoothGiftActive,
} from '../../utils/eventConfig';
import './BoothGiftPage.css';

const formatDateRange = (startAt: string, endAt: string): string => {
  const formatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
  const start = new Date(startAt);
  const end = new Date(endAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return '';
  return `${formatter.format(start)} ~ ${formatter.format(end)}`;
};

const BoothGiftPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, isLoggedIn, isAuthReady } = useAuth();
  const [config, setConfig] = useState<BoothGiftEventConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);

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

  const active = useMemo(() => isBoothGiftActive(config), [config]);
  const dateRange = useMemo(
    () => (config ? formatDateRange(config.startAt, config.endAt) : ''),
    [config]
  );
  const issuedAt = useMemo(
    () =>
      new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date()),
    []
  );

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

  return (
    <div className="booth-gift-page">
      <Header />
      <main className="booth-gift-main">
        <div className="booth-gift-card">
          <div className="booth-gift-badge">EVENT</div>
          <h1 className="booth-gift-title">{config.title}</h1>
          <p className="booth-gift-subtitle">{config.subtitle}</p>

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
              <span className="booth-gift-user-label">발급 시각</span>
              <span className="booth-gift-user-value">{issuedAt}</span>
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

          <div className="booth-gift-staff-callout">
            <strong>부스 직원 안내</strong>
            <p>본 화면 확인 후 사은품을 전달해주세요. (사용자 이름/이메일 + 발급 시각 확인)</p>
          </div>
        </div>
      </main>
    </div>
  );
};

export default BoothGiftPage;
