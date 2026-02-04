import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './index.css';
import './i18n';
import { AuthProvider } from './context/AuthContext';
import { LanguageProvider } from './context/LanguageContext';
import { getSubdomain, SubdomainType } from './utils/subdomain';
import ErrorBoundary from './components/ErrorBoundary';
import ErrorPage from './components/ErrorPage';
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

// 404 페이지 래퍼 컴포넌트
const NotFoundPage: React.FC = () => {
  const notFoundError = new Error('요청하신 페이지를 찾을 수 없습니다.');
  (notFoundError as any).code = 404;
  return <ErrorPage error={notFoundError} />;
};

// 서브도메인에 따른 카테고리 페이지 컴포넌트
const CategoryRouter: React.FC<{ subdomain: SubdomainType }> = ({ subdomain }) => {
  switch (subdomain) {
    case 'meteor':
      return <MeteorPage />;
    case 'bulc':
      return <BulCPage />;
    case 'vr':
      return <VRPage />;
    default:
      return <MeteorPage />; // 기본값
  }
};

// App 라우터
const App: React.FC = () => {
  const subdomain = getSubdomain();

  return (
    <ErrorBoundary>
      <AuthProvider>
        <LanguageProvider>
        <BrowserRouter>
          <Routes>
            {/* 루트 경로: 서브도메인에 따른 카테고리 페이지 */}
            <Route path="/" element={<CategoryRouter subdomain={subdomain} />} />

            {/* 공통 라우트 */}
            <Route path="/payment" element={<PaymentPage />} />
            <Route path="/payment/success" element={<PaymentSuccess />} />
            <Route path="/payment/fail" element={<PaymentFail />} />
            <Route path="/mypage" element={<MyPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/oauth/callback" element={<OAuthCallback />} />
            <Route path="/oauth/setup-password" element={<OAuthSetupPassword />} />
            <Route path="/error" element={<ErrorPage />} />
            {/* 404 - 매칭되지 않는 모든 경로 */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </BrowserRouter>
        </LanguageProvider>
      </AuthProvider>
    </ErrorBoundary>
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
