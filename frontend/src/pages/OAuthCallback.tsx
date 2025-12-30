import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './OAuthCallback.css';

const OAuthCallback: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { loginWithToken } = useAuth();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    const processOAuth = async () => {
      const accessToken = searchParams.get('accessToken');
      const refreshToken = searchParams.get('refreshToken');
      const error = searchParams.get('error');

      if (error) {
        setStatus('error');
        setErrorMessage(error);
        setTimeout(() => navigate('/'), 3000);
        return;
      }

      if (accessToken && refreshToken) {
        // 토큰으로 로그인 처리
        const result = await loginWithToken(accessToken, refreshToken);

        if (result.success) {
          setStatus('success');
          setTimeout(() => navigate('/'), 1500);
        } else {
          setStatus('error');
          setErrorMessage(result.message || '로그인 처리 중 오류가 발생했습니다.');
          setTimeout(() => navigate('/'), 3000);
        }
      } else {
        setStatus('error');
        setErrorMessage('로그인 정보를 받지 못했습니다.');
        setTimeout(() => navigate('/'), 3000);
      }
    };

    processOAuth();
  }, [searchParams, loginWithToken, navigate]);

  return (
    <div className="oauth-callback-container">
      <div className="oauth-callback-card">
        {status === 'loading' && (
          <>
            <div className="oauth-spinner"></div>
            <p className="oauth-message">로그인 처리 중...</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="oauth-success-icon">✓</div>
            <p className="oauth-message">로그인 성공!</p>
            <p className="oauth-sub-message">잠시 후 메인 페이지로 이동합니다.</p>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="oauth-error-icon">✕</div>
            <p className="oauth-message">로그인 실패</p>
            <p className="oauth-sub-message">{errorMessage}</p>
            <p className="oauth-sub-message">잠시 후 메인 페이지로 이동합니다.</p>
          </>
        )}
      </div>
    </div>
  );
};

export default OAuthCallback;
