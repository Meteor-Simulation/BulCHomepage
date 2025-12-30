import React, { useState, useEffect } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom';
import './index.css';
import { AuthProvider } from './context/AuthContext';
import Header from './components/Header';
import MeteorPage from './CategoryPages/METEOR/Meteor';
import BulCPage from './CategoryPages/BULC/BulC';
import VRPage from './CategoryPages/VR/VR';
import PaymentPage from './CategoryPages/Payment/Payment';
import PaymentSuccess from './CategoryPages/Payment/PaymentSuccess';
import PaymentFail from './CategoryPages/Payment/PaymentFail';
import MyPage from './CategoryPages/MyPage/MyPage';
import AdminPage from './CategoryPages/Admin/AdminPage';
import OAuthCallback from './pages/OAuthCallback';
import OAuthSetupPassword from './pages/OAuthSetupPassword';

// 메인 페이지 컴포넌트
const MainPage: React.FC = () => {
  const [phase, setPhase] = useState<'slogan' | 'complete'>('slogan');
  const navigate = useNavigate();

  useEffect(() => {
    const completeTimer = setTimeout(() => {
      setPhase('complete');
    }, 2000);

    return () => {
      clearTimeout(completeTimer);
    };
  }, []);

  // 클릭으로 애니메이션 스킵
  const handleSkipAnimation = () => {
    if (phase === 'slogan') {
      setPhase('complete');
    }
  };

  return (
    <div className="app main-page" onClick={handleSkipAnimation}>
      <Header />

      {/* 문구 (2-4초: 페이드인 후 페이드아웃) - 클릭 시 스킵 */}
      <div className={`slogan-overlay ${phase === 'slogan' ? 'visible' : ''}`}>
        <div className="slogan-content">
          <p><span className="text-accent">화재</span>를 예측하고</p>
          <p><span className="text-accent">생명</span>을 구합니다</p>
        </div>
        {phase === 'slogan' && (
          <p className="skip-hint">클릭하여 건너뛰기</p>
        )}
      </div>

      {/* 메인 콘텐츠 영역 */}
      <main className="main-content">
        <div className={`category-container ${phase === 'complete' ? 'visible' : ''}`}>
          <div className="category-card" onClick={() => navigate('/meteor')}>
            <span className="category-name">Meteor<br/>Simulation</span>
          </div>
          <div className="category-card" onClick={() => navigate('/bulc')}>
            <span className="category-name">BUL:C</span>
          </div>
          <div className="category-card" onClick={() => navigate('/vr')}>
            <span className="category-name">VR</span>
          </div>
        </div>
      </main>
    </div>
  );
};

// App 라우터
const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<MainPage />} />
          <Route path="/meteor" element={<MeteorPage />} />
          <Route path="/bulc" element={<BulCPage />} />
          <Route path="/vr" element={<VRPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/payment/success" element={<PaymentSuccess />} />
          <Route path="/payment/fail" element={<PaymentFail />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/oauth/callback" element={<OAuthCallback />} />
          <Route path="/oauth/setup-password" element={<OAuthSetupPassword />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement
);

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
