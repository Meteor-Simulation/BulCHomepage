import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './BulC.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import LoginModal from '../../components/LoginModal';
import { useAuth } from '../../context/AuthContext';
import { useScrollNav } from '../../hooks/useScrollNav';
import { isSubdomainAccess } from '../../utils/subdomain';

import {
  HeroSection,
  ComparisonSection,
  CoreValuesSection,
  WorkflowSection,
  ReportSection,
  CTASection,
} from './sections';

const SUB_NAV_ITEMS = [
  { id: 'hero', label: 'Intro' },
  { id: 'comparison', label: 'Comparison' },
  { id: 'core-values', label: 'Core Values' },
  { id: 'workflow', label: 'Workflow' },
  { id: 'report', label: 'Report' },
  { id: 'cta', label: 'Get Started' },
];

const BulCPage: React.FC = () => {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [loginModalOpen, setLoginModalOpen] = useState(false);

  const sectionIds = useMemo(
    () => SUB_NAV_ITEMS.map((item) => item.id),
    []
  );

  const { activeSection, scrollToSection } = useScrollNav({
    sectionIds,
    defaultId: 'hero',
  });

  const handleLogoClick = () => {
    if (isSubdomainAccess()) {
      scrollToSection('hero');
    } else {
      navigate('/');
    }
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

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeSection}
        onSubNavChange={scrollToSection}
        logoLink="/"
        onLogoClick={handleLogoClick}
        logoText="BULC"
      />

      <main className="main-content sub-page">
        <div className="bulc-landing">
          <HeroSection onPurchaseClick={handlePurchaseClick} />
          <ComparisonSection />
          <CoreValuesSection />
          <WorkflowSection />
          <ReportSection />
          <CTASection onPurchaseClick={handlePurchaseClick} />
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
