import React from 'react';
import './CategoryPages.css';
import Header from '../components/Header';
import Footer from '../components/Footer';

const SUB_NAV_ITEMS = [
  { id: 'menu1', label: '메뉴1' },
  { id: 'menu2', label: '메뉴2' },
  { id: 'menu3', label: '메뉴3' },
  { id: 'menu4', label: '메뉴4' },
];

const MorePage: React.FC = () => {
  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        logoLink="/more"
      />

      <main className="main-content sub-page">
      </main>

      <Footer />
    </div>
  );
};

export default MorePage;
