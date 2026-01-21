import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './BulC.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import LoginModal from '../../components/LoginModal';
import { useAuth } from '../../context/AuthContext';
import { useHashMenu } from '../../hooks/useHashMenu';

// 컨텐츠 컴포넌트들
import {
  BulCIntro,
  BulCBULC,
  BulCAIAgent,
  BulCTutorial,
  BulCDownload,
} from './contents';

const SUB_NAV_ITEMS = [
  { id: 'intro', label: 'Intro' },
  { id: 'bulc', label: 'BULC' },
  { id: 'ai-agent', label: 'AI Agent' },
  { id: 'tutorial', label: 'Tutorial' },
  { id: 'download', label: 'Download' },
];

const BulCPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const { activeMenu, setActiveMenu } = useHashMenu({
    items: SUB_NAV_ITEMS,
    defaultId: 'intro',
  });
  const [loginModalOpen, setLoginModalOpen] = useState(false);

  const handleLogoClick = () => {
    navigate('/');
  };

  const handleNavigate = (menu: string) => {
    setActiveMenu(menu);
  };

  const handlePurchaseClick = () => {
    if (isLoggedIn) {
      navigate('/payment');
    } else {
      setLoginModalOpen(true);
    }
  };

  const handleLoginSuccess = () => {
    setLoginModalOpen(false);
    navigate('/payment');
  };

  // 메뉴별 컨텐츠 매핑
  const contentMap: Record<string, React.ReactNode> = {
    intro: <BulCIntro onNavigate={handleNavigate} />,
    bulc: <BulCBULC />,
    'ai-agent': <BulCAIAgent />,
    tutorial: <BulCTutorial />,
    download: <BulCDownload onPurchaseClick={handlePurchaseClick} />,
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
        <div className="category-content-container">
          {contentMap[activeMenu] || contentMap['intro']}
        </div>
      </main>

      <Footer />

      <LoginModal
        isOpen={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        onSuccess={handleLoginSuccess}
      />
    </div>
  );
};

export default BulCPage;
