/**
 * 기술 문서(아티클) 콘텐츠 레지스트리.
 * 검색·AI 답변 인용을 목표로 한 질문형 지식 콘텐츠 (한국어).
 * 새 문서 추가 시 public/sitemap.xml, public/llms.txt 에도 URL을 추가할 것.
 */

export interface DocsSection {
  heading?: string;
  /** <p>, <ul> 등 HTML 문자열 (policyContent.ts와 동일하게 innerHTML로 렌더링) */
  html: string;
}

export interface DocsArticle {
  slug: string;
  /** 페이지 <h1> */
  title: string;
  /** <title> 태그용 */
  seoTitle: string;
  description: string;
  datePublished: string;
  sections: DocsSection[];
  related: { label: string; to: string }[];
}

export const DOCS_ARTICLES: DocsArticle[] = [
  {
    slug: 'aset-rset',
    title: 'ASET과 RSET이란? 피난 안전성 평가의 핵심 개념',
    seoTitle: 'ASET vs RSET - 피난 안전성 평가 기준 | BUL:C 기술 문서',
    description:
      'ASET(이용 가능 안전 피난 시간)과 RSET(필요 안전 피난 시간)의 정의, 판정 기준, 산정 방법을 설명합니다. 화재·피난 시뮬레이션으로 ASET과 RSET을 산정하는 과정을 소개합니다.',
    datePublished: '2026-06-10',
    sections: [
      {
        html: '<p>건축물의 피난 안전성을 정량적으로 평가할 때 가장 널리 사용되는 개념이 <strong>ASET</strong>과 <strong>RSET</strong>입니다. 성능위주설계(PBD)의 화재·피난 시뮬레이션은 결국 이 두 값을 산정해 비교하는 과정이라고 할 수 있습니다.</p>',
      },
      {
        heading: 'ASET (Available Safe Egress Time, 이용 가능 안전 피난 시간)',
        html: '<p>화재가 발생한 후 재실자가 안전하게 피난할 수 있는 환경이 유지되는 시간입니다. 즉, 화재로 인한 열·연기·독성가스가 인명 안전 기준(허용 한계)에 도달하기까지 걸리는 시간을 의미합니다.</p><p>ASET은 일반적으로 화재 시뮬레이션(CFD)을 통해 다음 항목이 허용 기준을 초과하는 시점으로 산정합니다.</p><ul><li><strong>가시거리</strong>: 연기로 인해 피난 경로의 가시거리가 기준 이하로 감소하는 시점</li><li><strong>온도</strong>: 호흡 한계선 높이에서 공기 온도가 기준을 초과하는 시점</li><li><strong>독성 가스</strong>: 일산화탄소(CO) 등 유해 가스 농도가 기준을 초과하는 시점</li></ul>',
      },
      {
        heading: 'RSET (Required Safe Egress Time, 필요 안전 피난 시간)',
        html: '<p>재실자가 화재를 인지하고 실제로 안전한 장소까지 피난을 완료하는 데 필요한 시간입니다. RSET은 다음 요소의 합으로 구성됩니다.</p><ul><li><strong>감지 시간</strong>: 화재 감지기 또는 재실자가 화재를 감지하기까지의 시간</li><li><strong>통보 시간</strong>: 경보 발령까지의 시간</li><li><strong>반응(지연) 시간</strong>: 재실자가 피난을 결심하고 행동을 시작하기까지의 시간</li><li><strong>이동 시간</strong>: 피난 경로를 따라 안전한 장소까지 이동하는 시간</li></ul><p>이동 시간은 재실자 밀도, 보행 속도, 출구 폭, 병목 구간 등의 영향을 받기 때문에 피난 시뮬레이션(EVAC)을 통해 산정하는 것이 일반적입니다.</p>',
      },
      {
        heading: '피난 안전성 판정 기준: ASET > RSET',
        html: '<p>피난 안전성 평가의 기본 판정 기준은 <strong>ASET이 RSET보다 커야 한다</strong>는 것입니다. 즉, 피난에 필요한 시간보다 안전한 환경이 유지되는 시간이 길어야 재실자가 안전하게 대피할 수 있습니다. 설계 단계에서는 불확실성을 고려해 일정 수준의 안전 여유(margin)를 확보하는 것이 권장됩니다.</p><p>ASET ≤ RSET으로 판정되면 배연 설비 보강, 피난 경로·출구 폭 개선, 방화구획 조정 등의 설계 변경 후 재평가를 수행합니다.</p>',
      },
      {
        heading: 'BUL:C의 ASET/RSET 자동 분석',
        html: '<p>화재 시뮬레이션 소프트웨어 <a href="/">BUL:C</a>는 FDS 물리 엔진 기반 화재 해석과 EVAC 피난 시뮬레이션을 하나의 환경에서 수행하고, ASET/RSET 비교 분석 결과를 성능위주설계(PBD) 보고서로 자동 생성합니다. GPU 가속을 통해 기존 CPU 해석 대비 최대 87배 빠르게 다양한 시나리오를 검토할 수 있습니다.</p>',
      },
    ],
    related: [
      { label: '성능위주설계(PBD)란?', to: '/docs/performance-based-design' },
      { label: 'FDS 기반 화재 시뮬레이션과 GPU 가속', to: '/docs/fds-gpu-acceleration' },
      { label: 'BUL:C 무료 체험 다운로드', to: '/download' },
    ],
  },
  {
    slug: 'performance-based-design',
    title: '성능위주설계(PBD)란? 대상, 절차, 화재·피난 시뮬레이션',
    seoTitle: '성능위주설계(PBD) 개념과 절차 - 화재·피난 시뮬레이션 | BUL:C 기술 문서',
    description:
      '성능위주설계(PBD, Performance-Based Design)의 개념, 적용 대상, 수행 절차를 정리했습니다. PBD에서 화재 시뮬레이션과 피난 시뮬레이션이 수행하는 역할을 설명합니다.',
    datePublished: '2026-06-10',
    sections: [
      {
        html: '<p><strong>성능위주설계(PBD, Performance-Based Design)</strong>는 법령에서 정한 일률적인 사양 기준(사양위주설계)을 그대로 적용하는 대신, 건축물의 용도·구조·재실자 특성을 반영한 화재 시나리오를 설정하고 공학적 분석으로 소방시설의 성능을 입증하는 설계 방식입니다. 초고층·대규모·복합 건축물처럼 표준 기준만으로 안전성을 담보하기 어려운 대상에 적용됩니다.</p>',
      },
      {
        heading: '성능위주설계 적용 대상',
        html: '<p>국내에서는 소방시설 설치 및 관리에 관한 법령에 따라 일정 규모 이상의 특정소방대상물에 대해 성능위주설계가 요구됩니다. 대표적인 대상 유형은 다음과 같습니다(세부 기준은 현행 법령을 확인해야 합니다).</p><ul><li>연면적이 매우 큰 대규모 건축물</li><li>고층·초고층 건축물(아파트 포함, 층수·높이 기준 적용)</li><li>대규모 철도·공항 등 다중이용시설</li><li>영화상영관 등 다수의 상영관이 밀집된 시설</li></ul>',
      },
      {
        heading: '성능위주설계의 일반적인 절차',
        html: '<ol><li><strong>설계 개요 및 시나리오 설정</strong>: 건축물 특성 분석, 화재 시나리오(발화 위치, 화원 크기, 성장 속도 등) 선정</li><li><strong>화재 시뮬레이션</strong>: CFD 해석으로 열·연기·독성가스 확산을 예측하고 ASET 산정</li><li><strong>피난 시뮬레이션</strong>: 재실자 특성과 피난 경로를 반영해 RSET 산정</li><li><strong>피난 안전성 평가</strong>: ASET과 RSET 비교, 안전 여유 검토</li><li><strong>설계 보완 및 보고서 작성</strong>: 미달 시 설계 개선 후 재해석, 심의용 보고서 작성</li></ol>',
      },
      {
        heading: '왜 시뮬레이션이 핵심인가',
        html: '<p>성능위주설계의 핵심 근거 자료는 화재·피난 시뮬레이션 결과입니다. 신뢰성 있는 해석을 위해 국제적으로 검증된 해석 도구(예: NIST의 FDS)가 사용되며, 다양한 시나리오를 검토할수록 설계의 신뢰도가 높아집니다. 그러나 기존 CPU 기반 해석은 시나리오 하나에 수 시간에서 수일이 걸려, 검토 가능한 시나리오 수가 제한되는 것이 현실적인 어려움이었습니다.</p>',
      },
      {
        heading: 'BUL:C로 수행하는 성능위주설계',
        html: '<p><a href="/">BUL:C</a>는 NIST 검증 FDS 물리 엔진 기반의 화재 해석과 EVAC 피난 시뮬레이션, ASET/RSET 자동 분석, 성능위주설계 보고서 자동 생성을 하나의 프로그램에서 제공합니다. GPU 가속으로 해석 시간을 크게 단축해 같은 기간에 더 많은 시나리오를 검토할 수 있고, AI 도면 인식으로 모델링 시간도 줄일 수 있습니다.</p>',
      },
    ],
    related: [
      { label: 'ASET과 RSET이란?', to: '/docs/aset-rset' },
      { label: 'FDS 기반 화재 시뮬레이션과 GPU 가속', to: '/docs/fds-gpu-acceleration' },
      { label: '자주 묻는 질문(FAQ)', to: '/faq' },
    ],
  },
  {
    slug: 'fds-gpu-acceleration',
    title: 'FDS 기반 화재 시뮬레이션과 GPU 가속',
    seoTitle: 'FDS 화재 시뮬레이션이 느린 이유와 GPU 가속 | BUL:C 기술 문서',
    description:
      'NIST FDS(Fire Dynamics Simulator)의 원리와 CPU 해석의 속도 한계, GPU 가속이 화재 시뮬레이션을 빠르게 만드는 원리를 설명합니다.',
    datePublished: '2026-06-10',
    sections: [
      {
        html: '<p><strong>FDS(Fire Dynamics Simulator)</strong>는 미국 국립표준기술연구소(NIST)에서 개발한 화재 전용 전산유체역학(CFD) 해석 도구로, 전 세계 성능위주설계와 화재 연구에서 사실상 표준으로 사용됩니다. 화재로 발생하는 열·연기의 유동을 LES(Large Eddy Simulation) 기반으로 해석하며, 수많은 실험과 비교 검증을 거쳤습니다.</p>',
      },
      {
        heading: '화재 시뮬레이션이 오래 걸리는 이유',
        html: '<p>CFD 해석은 공간을 수백만 개의 격자(셀)로 나누고, 각 셀에 대해 질량·운동량·에너지 보존 방정식을 작은 시간 간격마다 반복 계산합니다. 해석 정확도를 높이려면 격자를 더 잘게 나눠야 하는데, 격자를 절반 크기로 줄이면 계산량은 대략 16배 가까이 증가합니다. 이 때문에 실무에서는 시나리오 하나의 해석에 수 시간에서 수일이 걸리는 경우가 많고, 검토할 수 있는 시나리오 수가 제한됩니다.</p>',
      },
      {
        heading: 'GPU 가속의 원리',
        html: '<p>CFD 계산의 대부분은 수백만 개 격자에 대해 같은 연산을 반복하는 구조입니다. 이런 대규모 병렬 연산은 수십 개의 코어를 가진 CPU보다 수천 개의 연산 코어를 가진 GPU에 훨씬 적합합니다. 격자별 계산을 GPU 코어에 분산하면 동일한 물리 모델을 유지하면서 해석 속도를 비약적으로 높일 수 있습니다.</p>',
      },
      {
        heading: 'BUL:C의 GPU 가속 화재 시뮬레이션',
        html: '<p><a href="/">BUL:C</a>는 FDS의 검증된 물리 모델을 기반으로 NVIDIA CUDA GPU 가속을 적용해, 자사 벤치마크 기준 기존 CPU 해석 대비 최대 87배 빠른 해석 속도를 구현했습니다. 해석 시간이 짧아지면 같은 기간에 더 많은 화재 시나리오와 설계 대안을 검토할 수 있어 설계 품질과 일정 모두에 직접적인 이점이 있습니다.</p><p>GPU 가속 외에도 AI 도면 자동 인식, 자연어 기반 환경 설정, EVAC 피난 시뮬레이션, ASET/RSET 자동 분석과 보고서 생성까지 화재 안전 엔지니어링 워크플로 전체를 지원합니다.</p>',
      },
    ],
    related: [
      { label: 'ASET과 RSET이란?', to: '/docs/aset-rset' },
      { label: '성능위주설계(PBD)란?', to: '/docs/performance-based-design' },
      { label: 'BUL:C 무료 체험 다운로드', to: '/download' },
    ],
  },
];

export const getArticleBySlug = (slug: string | undefined): DocsArticle | undefined =>
  DOCS_ARTICLES.find((a) => a.slug === slug);
