import React from 'react';
import '../Common/CategoryPages.css';
import CategoryPageLayout from '../../components/CategoryPageLayout';
import { Menu1Content, Menu2Content, Menu3Content, Menu4Content } from './contents';

const SUB_NAV_ITEMS = [
  { id: 'menu1', label: '메뉴1' },
  { id: 'menu2', label: '메뉴2' },
  { id: 'menu3', label: '메뉴3' },
  { id: 'menu4', label: '메뉴4' },
];

const MeteorPage: React.FC = () => {
  return (
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
  );
};

export default MeteorPage;
