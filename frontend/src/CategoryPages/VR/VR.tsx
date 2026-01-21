import React from 'react';
import '../Common/CategoryPages.css';
import CategoryPageLayout from '../../components/CategoryPageLayout';
import {
  VRExperienceContent,
  CurriculumContent,
  SafetyDiagnosisContent,
  EffectivenessContent,
} from './contents';

const SUB_NAV_ITEMS = [
  { id: 'vr-experience', label: 'VR체험' },
  { id: 'curriculum', label: '교육과정' },
  { id: 'safety-diagnosis', label: '안전진단' },
  { id: 'effectiveness', label: '효과검증' },
];

const VRPage: React.FC = () => {
  return (
    <CategoryPageLayout
      logoText="VR"
      menuItems={SUB_NAV_ITEMS}
      defaultMenuId="vr-experience"
      contentMap={{
        'vr-experience': <VRExperienceContent />,
        curriculum: <CurriculumContent />,
        'safety-diagnosis': <SafetyDiagnosisContent />,
        effectiveness: <EffectivenessContent />,
      }}
    />
  );
};

export default VRPage;
