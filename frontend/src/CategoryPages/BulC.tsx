import React, { useState } from 'react';
import './CategoryPages.css';
import Header from '../components/Header';
import HeroSection from './HeroSection';
import QuizSection from './QuizSection';

const SUB_NAV_ITEMS = [
  { id: 'simulator', label: '체험 시뮬레이터' },
  { id: 'education', label: '교육 과정' },
  { id: 'diagnosis', label: '안전 진단' },
  { id: 'verification', label: '효과 검증' },
];

const BulCPage: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState('simulator');

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeMenu}
        onSubNavChange={setActiveMenu}
        contactLabel="체험 문의하기"
      />

      <main className="main-content sub-page">
        {activeMenu === 'simulator' && (
          <>
            <HeroSection />
            <QuizSection />
          </>
        )}
        {activeMenu === 'education' && (
          <div className="page-container">
            <h1 className="page-title">BulC</h1>
            <p className="page-subtitle">교육 과정</p>
          </div>
        )}
        {activeMenu === 'diagnosis' && (
          <div className="page-container">
            <h1 className="page-title">BulC</h1>
            <p className="page-subtitle">안전 진단</p>
          </div>
        )}
        {activeMenu === 'verification' && (
          <div className="page-container">
            <h1 className="page-title">BulC</h1>
            <p className="page-subtitle">효과 검증</p>
          </div>
        )}
      </main>
    </div>
  );
};

export default BulCPage;
