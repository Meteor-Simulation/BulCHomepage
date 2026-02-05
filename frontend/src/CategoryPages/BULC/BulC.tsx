import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import '../Common/CategoryPages.css';
import './BulC.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import LoginModal from '../../components/LoginModal';
import { useAuth } from '../../context/AuthContext';
import { isSubdomainAccess } from '../../utils/subdomain';

import {
  HeroSection,
  ComparisonSection,
  CoreValuesSection,
  WorkflowSection,
  ReportSection,
  CTASection,
} from './sections';

const BulCPage: React.FC = () => {
  const { t } = useTranslation();

  const SUB_NAV_ITEMS = useMemo(() => [
    { id: 'hero', label: t('bulc.nav.intro') },
    { id: 'comparison', label: t('bulc.nav.comparison') },
    { id: 'core-values', label: t('bulc.nav.coreValues') },
    { id: 'workflow', label: t('bulc.nav.workflow') },
    { id: 'report', label: t('bulc.nav.report') },
    { id: 'cta', label: t('bulc.nav.getStarted') },
  ], [t]);
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('hero');

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

  const renderSection = () => {
    switch (activeSection) {
      case 'hero':
        return <HeroSection onPurchaseClick={handlePurchaseClick} />;
      case 'comparison':
        return <ComparisonSection />;
      case 'core-values':
        return <CoreValuesSection />;
      case 'workflow':
        return <WorkflowSection />;
      case 'report':
        return <ReportSection />;
      case 'cta':
        return <CTASection onPurchaseClick={handlePurchaseClick} />;
      default:
        return <HeroSection onPurchaseClick={handlePurchaseClick} />;
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
        logoText="BULC"
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
    </div>
  );
};

export default BulCPage;
