import React, { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from './Header';
import Footer from './Footer';
import { useHashMenu } from '../hooks/useHashMenu';
import { isSubdomainAccess } from '../utils/subdomain';

interface SubNavItem {
  id: string;
  label: string;
}

interface ContentMap {
  [key: string]: ReactNode;
}

interface CategoryPageLayoutProps {
  /** 로고 텍스트 (ex: "BULC", "METEOR", "VR") */
  logoText: string;
  /** 서브 네비게이션 메뉴 아이템들 */
  menuItems: SubNavItem[];
  /** 기본 선택 메뉴 ID */
  defaultMenuId: string;
  /** 메뉴 ID별 컨텐츠 매핑 */
  contentMap: ContentMap;
  /** 컨테이너 클래스명 (선택사항) */
  containerClassName?: string;
}

/**
 * 카테고리 페이지 공통 레이아웃 컴포넌트
 *
 * @example
 * <CategoryPageLayout
 *   logoText="BULC"
 *   menuItems={[
 *     { id: 'intro', label: 'Intro' },
 *     { id: 'tutorial', label: 'Tutorial' },
 *   ]}
 *   defaultMenuId="intro"
 *   contentMap={{
 *     intro: <IntroContent />,
 *     tutorial: <TutorialContent />,
 *   }}
 * />
 */
const CategoryPageLayout: React.FC<CategoryPageLayoutProps> = ({
  logoText,
  menuItems,
  defaultMenuId,
  contentMap,
  containerClassName = 'category-content-container',
}) => {
  const navigate = useNavigate();
  const { activeMenu, setActiveMenu } = useHashMenu({
    items: menuItems,
    defaultId: defaultMenuId,
  });

  const handleLogoClick = () => {
    // 서브도메인 접속 시 첫 메뉴(Intro)로 이동, 일반 접속 시 메인 페이지로
    if (isSubdomainAccess()) {
      setActiveMenu(defaultMenuId);
      window.scrollTo(0, 0);
    } else {
      navigate('/');
    }
  };

  // 현재 메뉴에 해당하는 컨텐츠 렌더링
  const currentContent = contentMap[activeMenu] || contentMap[defaultMenuId];

  return (
    <div className="app">
      <Header
        showSubNav={true}
        subNavItems={menuItems}
        activeSubNav={activeMenu}
        onSubNavChange={setActiveMenu}
        logoLink="/"
        onLogoClick={handleLogoClick}
        logoText={logoText}
      />

      <main className="main-content sub-page">
        <div className={containerClassName}>
          {currentContent}
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default CategoryPageLayout;
