import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './BulC.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';

const SUB_NAV_ITEMS = [
  { id: 'intro', label: 'Intro' },
  { id: 'bulc', label: 'BULC' },
  { id: 'ai-agent', label: 'AI Agent' },
  { id: 'tutorial', label: 'Tutorial' },
  { id: 'download', label: 'Download' },
];

// Intro 컨텐츠
interface IntroContentProps {
  onNavigate: (menu: string) => void;
}

const IntroContent: React.FC<IntroContentProps> = ({ onNavigate }) => (
  <div className="bulc-intro-section">
    <h1 className="intro-headline">
      <span className="highlight">화재 시뮬레이션</span>의<br />
      압도적 <span className="highlight">속도</span>와<br />
      정확한 <span className="highlight">예측</span>
    </h1>
    <div className="intro-description">
      <p>Fire-AmgX GPU 가속으로 기존 FDS 대비 10배 이상 빠른 데이터 생성.</p>
      <p>Physical AI PINN/PIDON 기반으로 1초 내 실시간 화재 확산 예측을 실현합니다.</p>
    </div>
    <div className="intro-buttons-grid">
      <button className="intro-btn" onClick={() => onNavigate('bulc')}>
        BULC
      </button>
      <button className="intro-btn" onClick={() => onNavigate('ai-agent')}>
        AI Agent
      </button>
      <button className="intro-btn" onClick={() => onNavigate('tutorial')}>
        Tutorial
      </button>
      <button className="intro-btn" onClick={() => onNavigate('download')}>
        Download
      </button>
    </div>
  </div>
);

// BULC 컨텐츠
const BulCFeatureContent: React.FC = () => (
  <div className="bulc-content-section">
    <h2>BULC</h2>
    <p>BULC 기능 소개 컨텐츠 영역입니다.</p>
  </div>
);

// AI Agent 컨텐츠
const AIAgentContent: React.FC = () => (
  <div className="bulc-content-section">
    <h2>AI Agent</h2>
    <p>AI Agent 기능 소개 컨텐츠 영역입니다.</p>
  </div>
);

// Tutorial 컨텐츠
const TutorialContent: React.FC = () => (
  <div className="bulc-content-section">
    <h2>Tutorial</h2>
    <p>튜토리얼 컨텐츠 영역입니다.</p>
  </div>
);

// Download 컨텐츠
const DownloadContent: React.FC = () => (
  <div className="bulc-download-section">
    <h1 className="download-title">지금 시작하세요</h1>
    <div className="download-buttons-grid">
      <a
        href="https://msimul.sharepoint.com/:f:/s/msteams_8c91f3-2/EtNitiqwxNhEv4gcjBVUaWMBGqIY1zxNdNOwl4IUMSGxwg?e=ENEjWr"
        target="_blank"
        rel="noopener noreferrer"
        className="download-btn"
      >
        무료 다운로드
      </a>
      <a href="#" className="download-btn">
        AI Agent Download
      </a>
      <a href="/payment" className="download-btn">
        라이센스 구입
      </a>
      <a href="#" className="download-btn">
        Q&A
      </a>
    </div>
  </div>
);

const BulCPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [activeMenu, setActiveMenu] = useState<string>('intro');

  // location state에서 activeTab 읽어서 설정
  useEffect(() => {
    const state = location.state as { activeTab?: string } | null;
    if (state?.activeTab) {
      setActiveMenu(state.activeTab);
      // state 초기화 (뒤로가기 시 다시 적용되지 않도록)
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleLogoClick = () => {
    navigate('/'); // 메인 페이지로 이동
  };

  const handleNavigate = (menu: string) => {
    setActiveMenu(menu);
  };

  const renderContent = () => {
    switch (activeMenu) {
      case 'intro':
        return <IntroContent onNavigate={handleNavigate} />;
      case 'bulc':
        return <BulCFeatureContent />;
      case 'ai-agent':
        return <AIAgentContent />;
      case 'tutorial':
        return <TutorialContent />;
      case 'download':
        return <DownloadContent />;
      default:
        return <IntroContent onNavigate={handleNavigate} />;
    }
  };

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeMenu}
        onSubNavChange={setActiveMenu}
        logoLink="/"
        onLogoClick={handleLogoClick}
        logoText="BUL:C"
      />

      <main className="main-content sub-page">
        <div className="bulc-content-container">
          {renderContent()}
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default BulCPage;
