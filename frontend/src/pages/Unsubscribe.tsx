import React, { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { API_URL } from '../utils/api';
import Header from '../components/Header';
import Seo from '../components/Seo';
import '../CategoryPages/Payment/PaymentResult.css';

/**
 * 광고성 메일 수신거부 페이지 (MDP-607).
 *
 * 광고성 메일 footer 의 /unsubscribe?token=... 링크로 진입한다.
 * 봇/메일 스캐너의 링크 프리페치로 인한 오발동을 막기 위해 버튼 클릭 시에만 처리한다.
 */
const Unsubscribe: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<'idle' | 'processing' | 'done' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState('');

  const handleUnsubscribe = async () => {
    if (!token) {
      setErrorMsg('유효하지 않은 수신거부 링크입니다.');
      setStatus('error');
      return;
    }
    setStatus('processing');
    try {
      const res = await fetch(`${API_URL}/api/unsubscribe`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      });
      if (res.ok) {
        setStatus('done');
      } else {
        const data = await res.json().catch(() => ({}));
        setErrorMsg(data.error || '수신거부 처리에 실패했습니다.');
        setStatus('error');
      }
    } catch {
      setErrorMsg('수신거부 처리 중 오류가 발생했습니다.');
      setStatus('error');
    }
  };

  return (
    <div className="payment-result-page">
      <Seo title="광고성 메일 수신거부 | BUL:C" noindex />
      <Header hideUserMenu={true} />
      <div className="payment-result-container">
        {status === 'processing' ? (
          <div className="payment-result-card processing">
            <div className="spinner"></div>
            <h2>처리 중입니다...</h2>
            <p>잠시만 기다려주세요.</p>
          </div>
        ) : status === 'done' ? (
          <div className="payment-result-card">
            <div className="result-icon success">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M8 12l3 3 5-6" />
              </svg>
            </div>
            <h2>수신거부 완료</h2>
            <p>광고성 메일 수신이 거부되었습니다.<br />앞으로 마케팅·홍보 메일을 보내드리지 않습니다.</p>
            <p className="result-notice">
              서비스 운영상 필수 안내(계정·결제·보안 등)는 수신거부와 무관하게 계속 발송됩니다.
            </p>
            <div className="result-actions">
              <button className="btn-primary" onClick={() => navigate('/')}>홈으로</button>
            </div>
          </div>
        ) : status === 'error' ? (
          <div className="payment-result-card error">
            <div className="result-icon error">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M15 9l-6 6M9 9l6 6" />
              </svg>
            </div>
            <h2>수신거부에 실패했습니다</h2>
            <p>{errorMsg}</p>
            <p className="result-notice">
              문제가 계속되면 고객센터로 문의해주세요.<br />simul@msimul.com
            </p>
            <div className="result-actions">
              <button className="btn-primary" onClick={() => navigate('/')}>홈으로</button>
            </div>
          </div>
        ) : (
          <div className="payment-result-card">
            <div className="result-icon waiting">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 4h16v16H4z" />
                <path d="M4 7l8 6 8-6" />
              </svg>
            </div>
            <h2>광고성 메일 수신거부</h2>
            <p>광고성·마케팅 메일 수신을 거부하시겠습니까?</p>
            <p className="result-notice">
              계정·결제·보안 등 서비스 운영상 필수 안내 메일은 수신거부 후에도 계속 발송됩니다.
            </p>
            <div className="result-actions">
              <button className="btn-secondary" onClick={() => navigate('/')}>취소</button>
              <button className="btn-primary" onClick={handleUnsubscribe}>수신거부</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Unsubscribe;
