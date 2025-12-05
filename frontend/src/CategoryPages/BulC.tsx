import React, { useState, useRef, useEffect } from 'react';
import './CategoryPages.css';
import Header from '../components/Header';
import Footer from '../components/Footer';
import BulCHero from './BulCHero';
import BulCSolutions from './BulCSolutions';
import BulCAIAgent from './BulCAIAgent';
import BulCTutorial from './BulCTutorial';
import BulCDownload from './BulCDownload';

const SUB_NAV_ITEMS = [
  { id: 'menu1', label: 'Intro' },
  { id: 'menu2', label: 'BULC' },
  { id: 'menu3', label: 'AI AGENT' },
  { id: 'menu4', label: 'Tutorial' },
  { id: 'menu5', label: 'Download' },
];

const BulCPage: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState<string>('menu1');
  const menu1Ref = useRef<HTMLDivElement>(null);
  const menu2Ref = useRef<HTMLDivElement>(null);
  const menu3Ref = useRef<HTMLDivElement>(null);
  const menu4Ref = useRef<HTMLDivElement>(null);
  const menu5Ref = useRef<HTMLDivElement>(null);

  // Handle sub-navigation clicks
  const handleSubNavClick = (menuId: string) => {
    setActiveMenu(menuId);

    // Scroll to the corresponding section
    let targetRef = null;
    if (menuId === 'menu1') targetRef = menu1Ref;
    else if (menuId === 'menu2') targetRef = menu2Ref;
    else if (menuId === 'menu3') targetRef = menu3Ref;
    else if (menuId === 'menu4') targetRef = menu4Ref;
    else if (menuId === 'menu5') targetRef = menu5Ref;

    if (targetRef?.current) {
      const headerOffset = 140; // Account for fixed header height
      const elementPosition = targetRef.current.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

      window.scrollTo({
        top: offsetPosition,
        behavior: 'smooth'
      });
    }
  };

  // Update active menu based on scroll position
  useEffect(() => {
    const handleScroll = () => {
      const scrollPosition = window.scrollY + 200; // Offset for better UX

      if (menu5Ref.current && scrollPosition >= menu5Ref.current.offsetTop) {
        setActiveMenu('menu5');
      } else if (menu4Ref.current && scrollPosition >= menu4Ref.current.offsetTop) {
        setActiveMenu('menu4');
      } else if (menu3Ref.current && scrollPosition >= menu3Ref.current.offsetTop) {
        setActiveMenu('menu3');
      } else if (menu2Ref.current && scrollPosition >= menu2Ref.current.offsetTop) {
        setActiveMenu('menu2');
      } else if (menu1Ref.current && scrollPosition >= menu1Ref.current.offsetTop) {
        setActiveMenu('menu1');
      }
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={SUB_NAV_ITEMS}
        activeSubNav={activeMenu}
        onSubNavChange={handleSubNavClick}
      />

      <main className="main-content sub-page">
        <div ref={menu1Ref}>
          <BulCHero />
        </div>
        <div ref={menu2Ref}>
          <BulCSolutions />
        </div>
        <div ref={menu3Ref}>
          <BulCAIAgent />
        </div>
        <div ref={menu4Ref}>
          <BulCTutorial />
        </div>
        <div ref={menu5Ref}>
          <BulCDownload />
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default BulCPage;
