import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import './ErrorPage.css';

interface ExtendedError extends Error {
  code?: number;
}

interface ErrorPageProps {
  error?: ExtendedError;
  resetError?: () => void;
}

interface ErrorLocationState {
  errorCode?: number;
  errorMessage?: string;
}

const ErrorPage: React.FC<ErrorPageProps> = ({ error, resetError }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as ErrorLocationState;

  // 에러 코드와 메시지 결정 (우선순위: error.code > locationState.errorCode > 500)
  const errorCode = error?.code || locationState?.errorCode || 500;
  const errorMessage = error?.message || locationState?.errorMessage || '알 수 없는 오류가 발생했습니다.';

  // 에러 코드별 타이틀
  const getErrorTitle = (code: number): string => {
    switch (code) {
      case 400:
        return '잘못된 요청';
      case 401:
        return '인증이 필요합니다';
      case 403:
        return '접근이 거부되었습니다';
      case 404:
        return '페이지를 찾을 수 없습니다';
      case 500:
        return '서버 오류';
      case 502:
        return '서버 연결 실패';
      case 503:
        return '서비스를 일시적으로 사용할 수 없습니다';
      default:
        return '오류가 발생했습니다';
    }
  };

  // 에러 코드별 설명
  const getErrorDescription = (code: number): string => {
    switch (code) {
      case 400:
        return '요청을 처리할 수 없습니다. 입력 값을 확인해주세요.';
      case 401:
        return '이 페이지에 접근하려면 로그인이 필요합니다.';
      case 403:
        return '이 페이지에 접근할 권한이 없습니다.';
      case 404:
        return '요청하신 페이지가 존재하지 않거나 이동되었습니다.';
      case 500:
        return '서버에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
      case 502:
        return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.';
      case 503:
        return '서비스가 일시적으로 중단되었습니다. 잠시 후 다시 시도해주세요.';
      default:
        return '예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
    }
  };

  const handleGoHome = () => {
    if (resetError) {
      resetError();
    }
    navigate('/');
  };

  const handleGoBack = () => {
    if (resetError) {
      resetError();
    }
    navigate(-1);
  };

  const handleRefresh = () => {
    if (resetError) {
      resetError();
    }
    window.location.reload();
  };

  return (
    <div className="error-page">
      <div className="error-content">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>

        <h1 className="error-code">{errorCode}</h1>
        <h2 className="error-title">{getErrorTitle(errorCode)}</h2>
        <p className="error-description">{getErrorDescription(errorCode)}</p>

        {process.env.NODE_ENV === 'development' && error && (
          <div className="error-details">
            <p className="error-detail-title">상세 정보 (개발 환경)</p>
            <pre className="error-stack">{error.stack || errorMessage}</pre>
          </div>
        )}

        <div className="error-actions">
          <button onClick={handleGoHome} className="error-btn primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            홈으로 이동
          </button>
          <button onClick={handleGoBack} className="error-btn secondary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="19" y1="12" x2="5" y2="12" />
              <polyline points="12 19 5 12 12 5" />
            </svg>
            이전 페이지
          </button>
          <button onClick={handleRefresh} className="error-btn secondary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 4 23 10 17 10" />
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
            새로고침
          </button>
        </div>
      </div>
    </div>
  );
};

export default ErrorPage;
