import React, { useState } from 'react';
import './CategoryPages.css';
import Header from '../components/Header';

const SUB_NAV_ITEMS = [
  { id: 'company', label: '회사 소개' },
  { id: 'news', label: '뉴스' },
  { id: 'careers', label: '채용' },
  { id: 'contact', label: '문의하기' },
];

const MorePage: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState('company');

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeMenu}
        onSubNavChange={setActiveMenu}
        contactLabel="문의하기"
      />

      <main className="main-content sub-page">
        {activeMenu === 'company' && (
          <div className="page-container">
            <h1 className="page-title">More</h1>
            <p className="page-subtitle">회사 소개</p>
          </div>
        )}
        {activeMenu === 'news' && (
          <div className="page-container">
            <h1 className="page-title">More</h1>
            <p className="page-subtitle">뉴스</p>
          </div>
        )}
        {activeMenu === 'careers' && (
          <div className="page-container">
            <h1 className="page-title">More</h1>
            <p className="page-subtitle">채용</p>
          </div>
        )}
        {activeMenu === 'contact' && (
          <div className="page-container">
            <h1 className="page-title">More</h1>
            <p className="page-subtitle">문의하기</p>
          </div>
        )}
      </main>
    </div>
  );
};

export default MorePage;
