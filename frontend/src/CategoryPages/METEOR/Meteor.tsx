import React from 'react';
import '../Common/CategoryPages.css';
import CategoryPageLayout from '../../components/CategoryPageLayout';
import Seo from '../../components/Seo';
import { ORGANIZATION_JSONLD } from '../../seo/jsonld';
import { Menu1Content, Menu2Content, Menu3Content, Menu4Content } from './contents';

const SUB_NAV_ITEMS = [
  { id: 'menu1', label: '메뉴1' },
  { id: 'menu2', label: '메뉴2' },
  { id: 'menu3', label: '메뉴3' },
  { id: 'menu4', label: '메뉴4' },
];

const MeteorPage: React.FC = () => {
  return (
    <>
      <Seo
        title="메테오시뮬레이션 | AI 기반 화재·유체 시뮬레이션 전문 기업"
        description="주식회사 메테오시뮬레이션은 AI 기반 유체 역학 시뮬레이션 기술로 화재 안전 소프트웨어 BUL:C와 VR 안전 교육 솔루션을 개발합니다."
        path="/"
        origin="https://meteor.msimul.com"
        jsonLd={ORGANIZATION_JSONLD}
      />
      <CategoryPageLayout
        logoText="METEOR"
        menuItems={SUB_NAV_ITEMS}
        defaultMenuId="menu1"
        contentMap={{
          menu1: <Menu1Content />,
          menu2: <Menu2Content />,
          menu3: <Menu3Content />,
          menu4: <Menu4Content />,
        }}
      />
    </>
  );
};

export default MeteorPage;
