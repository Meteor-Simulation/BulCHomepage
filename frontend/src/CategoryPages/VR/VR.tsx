import React from 'react';
import '../Common/CategoryPages.css';
import CategoryPageLayout from '../../components/CategoryPageLayout';
import Seo from '../../components/Seo';
import { ORGANIZATION_JSONLD } from '../../seo/jsonld';
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
    <>
      <Seo
        title="VR 소방 안전 교육 | 메테오시뮬레이션"
        description="VR 기반 화재 대피·소방 안전 체험 교육. 실감형 가상현실 교육과정, 안전진단, 교육 효과 검증까지 제공하는 메테오시뮬레이션의 VR 안전 교육 솔루션입니다."
        path="/"
        origin="https://vr.msimul.com"
        jsonLd={ORGANIZATION_JSONLD}
      />
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
    </>
  );
};

export default VRPage;
