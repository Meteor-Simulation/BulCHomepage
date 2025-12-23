import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../Common/CategoryPages.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';

const SUB_NAV_ITEMS = [
  { id: 'menu1', label: '메뉴1' },
  { id: 'menu2', label: '메뉴2' },
  { id: 'menu3', label: '메뉴3' },
  { id: 'menu4', label: '메뉴4' },
];

// 각 메뉴별 컨텐츠 컴포넌트
const Menu1Content: React.FC = () => (
  <div className="vr-content-section">
    <h2>메뉴1</h2>
    <p>메뉴1 컨텐츠 영역입니다.</p>
  </div>
);

const Menu2Content: React.FC = () => (
  <div className="vr-content-section">
    <h2>메뉴2</h2>
    <p>메뉴2 컨텐츠 영역입니다.</p>
  </div>
);

const Menu3Content: React.FC = () => (
  <div className="vr-content-section">
    <h2>메뉴3</h2>
    <p>메뉴3 컨텐츠 영역입니다.</p>
  </div>
);

const Menu4Content: React.FC = () => (
  <div className="vr-content-section">
    <h2>메뉴4</h2>
    <p>메뉴4 컨텐츠 영역입니다.</p>
  </div>
);

const MeteorPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeMenu, setActiveMenu] = useState<string>('menu1');

  const handleLogoClick = () => {
    navigate('/'); // 메인 페이지로 이동
  };

  const renderContent = () => {
    switch (activeMenu) {
      case 'menu1':
        return <Menu1Content />;
      case 'menu2':
        return <Menu2Content />;
      case 'menu3':
        return <Menu3Content />;
      case 'menu4':
        return <Menu4Content />;
      default:
        return <Menu1Content />;
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
        logoText="METEOR"
      />

      <main className="main-content sub-page">
        <div className="vr-content-container">
          {renderContent()}
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default MeteorPage;
