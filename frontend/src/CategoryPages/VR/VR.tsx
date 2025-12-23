import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../Common/CategoryPages.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';

const SUB_NAV_ITEMS = [
  { id: 'vr-experience', label: 'VR체험' },
  { id: 'curriculum', label: '교육과정' },
  { id: 'safety-diagnosis', label: '안전진단' },
  { id: 'effectiveness', label: '효과검증' },
];

// 각 메뉴별 컨텐츠 컴포넌트
const VRExperienceContent: React.FC = () => (
  <div className="vr-content-section">
    <h2>VR체험</h2>
    <p>VR체험 컨텐츠 영역입니다.</p>
  </div>
);

const CurriculumContent: React.FC = () => (
  <div className="vr-content-section">
    <h2>교육과정</h2>
    <p>교육과정 컨텐츠 영역입니다.</p>
  </div>
);

const SafetyDiagnosisContent: React.FC = () => (
  <div className="vr-content-section">
    <h2>안전진단</h2>
    <p>안전진단 컨텐츠 영역입니다.</p>
  </div>
);

const EffectivenessContent: React.FC = () => (
  <div className="vr-content-section">
    <h2>효과검증</h2>
    <p>효과검증 컨텐츠 영역입니다.</p>
  </div>
);

const VRPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeMenu, setActiveMenu] = useState<string>('vr-experience');

  const handleLogoClick = () => {
    navigate('/'); // 메인 페이지로 이동
  };

  const renderContent = () => {
    switch (activeMenu) {
      case 'vr-experience':
        return <VRExperienceContent />;
      case 'curriculum':
        return <CurriculumContent />;
      case 'safety-diagnosis':
        return <SafetyDiagnosisContent />;
      case 'effectiveness':
        return <EffectivenessContent />;
      default:
        return <VRExperienceContent />;
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
        logoText="VR"
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

export default VRPage;
