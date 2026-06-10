/**
 * 사이트 공통 JSON-LD 구조화 데이터.
 * index.html의 기본(data-seo-default) 스크립트와 같은 내용을 React 렌더링에서도 제공한다.
 * 내용 변경 시 frontend/index.html, frontend/public/llms.txt 도 함께 갱신할 것.
 */

export const ORGANIZATION_JSONLD = {
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: '주식회사 메테오시뮬레이션',
  alternateName: ['Meteor Simulation', '메테오시뮬레이션'],
  url: 'https://bulc.msimul.com',
  logo: 'https://bulc.msimul.com/logo.png',
  description: 'AI 기반 유체 역학 시뮬레이션으로 대한민국 안전 교육의 새로운 표준을 만듭니다.',
  address: {
    '@type': 'PostalAddress',
    addressLocality: '원주시',
    addressRegion: '강원특별자치도',
    addressCountry: 'KR',
    streetAddress: '마재2로 10, 305호(원주미래산업진흥원)',
  },
  contactPoint: {
    '@type': 'ContactPoint',
    telephone: '+82-10-2747-2056',
    email: 'simul@msimul.com',
    contactType: 'customer service',
    availableLanguage: ['Korean', 'English'],
  },
  sameAs: [
    'https://www.youtube.com/@SimulationMeteor',
    'https://www.threads.com/@meteorsimulation',
  ],
};

export const BULC_SOFTWARE_JSONLD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'BUL:C',
  alternateName: 'BUL:C Fire Simulator',
  applicationCategory: 'EngineeringApplication',
  operatingSystem: 'Windows 10/11 (64-bit)',
  description:
    'GPU 가속 기반 차세대 화재 시뮬레이션 소프트웨어. FDS 물리 엔진으로 정확한 화재·연기 확산 분석, EVAC 피난 시뮬레이션, AI 자동 설정, 성능위주설계(PBD) 보고서 자동 생성을 지원합니다.',
  url: 'https://bulc.msimul.com',
  featureList: [
    'GPU 가속 CFD 시뮬레이션 (CPU 대비 87배 빠름)',
    'NIST 검증 FDS 물리 엔진 기반',
    'EVAC 피난 시뮬레이션 (충돌, 사회력, 다층)',
    'AI 기반 자동 환경 설정',
    'ASET/RSET 자동 분석',
    '성능위주설계(PBD) 보고서 자동 생성',
    '기본 라이브러리 (아파트, 오피스텔 등)',
    'AI 도면 자동 인식',
  ],
  offers: [
    {
      '@type': 'Offer',
      name: 'BUL:C Basic (Free Trial)',
      price: '0',
      priceCurrency: 'KRW',
      description: '14일 무료 체험판. CPU 기반 분석, 기본 라이브러리, EVAC 분석 포함.',
      availability: 'https://schema.org/InStock',
    },
    {
      '@type': 'Offer',
      name: 'BUL:C PRO',
      priceCurrency: 'KRW',
      description: '1년 라이선스. GPU 가속, 전체 기능 포함.',
      availability: 'https://schema.org/InStock',
    },
  ],
  softwareRequirements:
    'Intel i5 / AMD Ryzen 5 이상, RAM 16GB 이상, NVIDIA GTX 1060 이상 (CUDA 지원), 저장공간 10GB 이상',
  author: {
    '@type': 'Organization',
    name: '주식회사 메테오시뮬레이션',
  },
};

/** FAQ 항목 배열로 FAQPage JSON-LD 생성 */
export const buildFaqJsonLd = (items: { question: string; answer: string }[]) => ({
  '@context': 'https://schema.org',
  '@type': 'FAQPage',
  mainEntity: items.map(({ question, answer }) => ({
    '@type': 'Question',
    name: question,
    acceptedAnswer: { '@type': 'Answer', text: answer },
  })),
});

/** 기술 문서(아티클) JSON-LD 생성 */
export const buildArticleJsonLd = (params: {
  title: string;
  description: string;
  path: string;
  datePublished: string;
  dateModified?: string;
}) => ({
  '@context': 'https://schema.org',
  '@type': 'TechArticle',
  headline: params.title,
  description: params.description,
  url: `https://bulc.msimul.com${params.path}`,
  datePublished: params.datePublished,
  dateModified: params.dateModified ?? params.datePublished,
  inLanguage: 'ko',
  author: {
    '@type': 'Organization',
    name: '주식회사 메테오시뮬레이션',
    url: 'https://bulc.msimul.com',
  },
  publisher: {
    '@type': 'Organization',
    name: '주식회사 메테오시뮬레이션',
    logo: { '@type': 'ImageObject', url: 'https://bulc.msimul.com/logo.png' },
  },
});
