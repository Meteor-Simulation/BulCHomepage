import React, { useState } from 'react';
import './CategoryPages.css';
import Header from '../components/Header';

const SUB_NAV_ITEMS = [
  { id: 'overview', label: '개요' },
  { id: 'features', label: '주요 기능' },
  { id: 'cases', label: '적용 사례' },
  { id: 'pricing', label: '요금제' },
];

const MeteorPage: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState('overview');

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeMenu}
        onSubNavChange={setActiveMenu}
        contactLabel="도입 문의하기"
      />

      <main className="main-content sub-page">
        {activeMenu === 'overview' && (
          <div className="page-container">
            <h1 className="page-title">Meteor Simulation</h1>
            <p className="page-subtitle">개요</p>
          </div>
        )}
        {activeMenu === 'features' && (
          <div className="page-container">
            <h1 className="page-title">Meteor Simulation</h1>
            <p className="page-subtitle">주요 기능</p>
          </div>
        )}
        {activeMenu === 'cases' && (
          <div className="page-container">
            <h1 className="page-title">Meteor Simulation</h1>
            <p className="page-subtitle">적용 사례</p>
          </div>
        )}
        {activeMenu === 'pricing' && (
          <div className="page-container">
            <h1 className="page-title">Meteor Simulation</h1>
            <p className="page-subtitle">요금제</p>
          </div>
        )}
      </main>
    </div>
  );
};

export default MeteorPage;
