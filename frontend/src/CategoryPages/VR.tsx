import React, { useState } from 'react';
import './CategoryPages.css';
import Header from '../components/Header';

const SUB_NAV_ITEMS = [
  { id: 'intro', label: '소개' },
  { id: 'experience', label: '체험 환경' },
  { id: 'content', label: '콘텐츠' },
  { id: 'inquiry', label: '구축 문의' },
];

const VRPage: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState('intro');

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
        {activeMenu === 'intro' && (
          <div className="page-container">
            <h1 className="page-title">VR</h1>
            <p className="page-subtitle">소개</p>
          </div>
        )}
        {activeMenu === 'experience' && (
          <div className="page-container">
            <h1 className="page-title">VR</h1>
            <p className="page-subtitle">체험 환경</p>
          </div>
        )}
        {activeMenu === 'content' && (
          <div className="page-container">
            <h1 className="page-title">VR</h1>
            <p className="page-subtitle">콘텐츠</p>
          </div>
        )}
        {activeMenu === 'inquiry' && (
          <div className="page-container">
            <h1 className="page-title">VR</h1>
            <p className="page-subtitle">구축 문의</p>
          </div>
        )}
      </main>
    </div>
  );
};

export default VRPage;
