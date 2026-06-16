import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './index.css';
import './i18n';
import { AuthProvider } from './context/AuthContext';
import { LanguageProvider } from './context/LanguageContext';
import { AlertProvider } from './components/AlertProvider';
import { getSubdomain, SubdomainType } from './utils/subdomain';
import ErrorBoundary from './components/ErrorBoundary';
import ErrorPage from './components/ErrorPage';
import MeteorPage from './CategoryPages/METEOR/Meteor';
import BulCPage from './CategoryPages/BULC/BulC';
import VRPage from './CategoryPages/VR/VR';
import PaymentPage from './CategoryPages/Payment/Payment';
import PaymentSuccess from './CategoryPages/Payment/PaymentSuccess';
import PaymentFail from './CategoryPages/Payment/PaymentFail';
import BillingSuccess from './CategoryPages/Payment/BillingSuccess';
import BillingFail from './CategoryPages/Payment/BillingFail';
import MyPage from './CategoryPages/MyPage/MyPage';
import DownloadPage from './CategoryPages/Download/Download';
import OAuthCallback from './pages/OAuthCallback';
import OAuthSetupPassword from './pages/OAuthSetupPassword';
import RefundPolicyPage from './CategoryPages/Policy/RefundPolicy';
import BoardPage from './CategoryPages/Board/BoardPage';
import PostDetailPage from './CategoryPages/Board/PostDetailPage';
import PostEditorPage from './CategoryPages/Board/PostEditorPage';
import BoothGiftPage from './CategoryPages/Event/BoothGiftPage';
import FaqPage from './CategoryPages/Docs/FaqPage';
import DocsArticlePage from './CategoryPages/Docs/DocsArticlePage';
import PopupRenderer from './components/PopupRenderer';
import Seo from './components/Seo';

// 404 페이지 래퍼 컴포넌트
const NotFoundPage: React.FC = () => {
  const notFoundError = new Error('요청하신 페이지를 찾을 수 없습니다.');
  (notFoundError as any).code = 404;
  return (
    <>
      {/* SPA 특성상 404도 HTTP 200으로 응답되므로 noindex로 색인을 차단한다 (soft-404 완화) */}
      <Seo title="페이지를 찾을 수 없습니다 | 메테오시뮬레이션" noindex />
      <ErrorPage error={notFoundError} />
    </>
  );
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
      <AlertProvider>
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
            <Route path="/billing/success" element={<BillingSuccess />} />
            <Route path="/billing/fail" element={<BillingFail />} />
            <Route path="/mypage" element={<MyPage />} />
            <Route path="/oauth/callback" element={<OAuthCallback />} />
            <Route path="/oauth/setup-password" element={<OAuthSetupPassword />} />
            <Route path="/download" element={<DownloadPage />} />
            <Route path="/refund-policy" element={<RefundPolicyPage />} />
            <Route path="/board" element={<BoardPage />} />
            <Route path="/board/write" element={<PostEditorPage />} />
            <Route path="/board/edit/:id" element={<PostEditorPage />} />
            <Route path="/board/:id" element={<PostDetailPage />} />
            <Route path="/faq" element={<FaqPage />} />
            <Route path="/docs/:slug" element={<DocsArticlePage />} />
            <Route path="/event/booth-gift" element={<BoothGiftPage />} />
            <Route path="/error" element={<ErrorPage />} />
            {/* 404 - 매칭되지 않는 모든 경로 */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
          <PopupRenderer />
        </BrowserRouter>
        </LanguageProvider>
      </AuthProvider>
      </AlertProvider>
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
