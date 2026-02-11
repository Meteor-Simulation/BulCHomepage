import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import '../Common/CategoryPages.css';
import './BulC.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import LoginModal from '../../components/LoginModal';
import { useAuth } from '../../context/AuthContext';
import { useLanguage } from '../../context/LanguageContext';
import { isSubdomainAccess } from '../../utils/subdomain';

import {
  HeroSection,
  ComparisonSection,
  CoreValuesSection,
  WorkflowSection,
  CTASection,
  PriceSection,
} from './sections';

const BulCPage: React.FC = () => {
  const { t } = useTranslation();
  const { language, changeLanguage } = useLanguage();

  const SUB_NAV_ITEMS = useMemo(() => [
    { id: 'hero', label: t('bulc.nav.intro') },
    { id: 'workflow', label: t('bulc.nav.workflow') },
    { id: 'price', label: t('bulc.nav.price') },
    { id: 'cta', label: t('bulc.nav.getStarted') },
  ], [t]);
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('hero');

  const toggleLanguage = () => {
    changeLanguage(language === 'ko' ? 'en' : 'ko');
  };

  const handleLogoClick = () => {
    if (isSubdomainAccess()) {
      setActiveSection('hero');
    } else {
      navigate('/');
    }
  };

  const handleSectionChange = (sectionId: string) => {
    setActiveSection(sectionId);
  };

  const [loginRedirect, setLoginRedirect] = useState<'payment' | 'cta'>('payment');

  const handlePurchaseClick = () => {
    if (isLoggedIn) {
      navigate('/payment');
    } else {
      setLoginRedirect('payment');
      setLoginModalOpen(true);
    }
  };

  const handleDownloadClick = () => {
    if (isLoggedIn) {
      setActiveSection('cta');
    } else {
      alert(t('bulc.hero.downloadAlert'));
      setLoginRedirect('cta');
      setLoginModalOpen(true);
    }
  };

  const handleLoginSuccess = () => {
    setLoginModalOpen(false);
    if (loginRedirect === 'cta') {
      setActiveSection('cta');
    } else {
      navigate('/payment');
    }
  };

  const renderSection = () => {
    switch (activeSection) {
      case 'hero':
        return (
          <>
            <HeroSection onPurchaseClick={handlePurchaseClick} onDownloadClick={handleDownloadClick} />
            <ComparisonSection />
            <CoreValuesSection />
          </>
        );
      case 'workflow':
        return <WorkflowSection />;
      case 'price':
        return <PriceSection onPurchaseClick={handlePurchaseClick} onFreeClick={handleDownloadClick} />;
      case 'cta':
        return <CTASection onPurchaseClick={handlePurchaseClick} />;
      default:
        return (
          <>
            <HeroSection onPurchaseClick={handlePurchaseClick} onDownloadClick={handleDownloadClick} />
            <ComparisonSection />
            <CoreValuesSection />
          </>
        );
    }
  };

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeSection}
        onSubNavChange={handleSectionChange}
        logoLink="/"
        onLogoClick={handleLogoClick}
        logoText="BUL:C"
        hideUserMenu={false}
      />

      <main className="main-content sub-page">
        <div className="bulc-landing">
          {renderSection()}
        </div>
      </main>

      <Footer />

      <LoginModal
        isOpen={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        onSuccess={handleLoginSuccess}
      />

      {/* 언어 테스트 버튼 */}
      <button
        className="bulc-lang-toggle"
        onClick={toggleLanguage}
        title={language === 'ko' ? 'Switch to English' : '한국어로 전환'}
      >
        {language === 'ko' ? 'EN' : '한'}
      </button>
    </div>
  );
};

export default BulCPage;
