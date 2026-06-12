/**
 * FAQ 페이지 콘텐츠.
 * 내용 변경 시 frontend/public/llms.txt, llms-full.txt 의 FAQ 섹션도 함께 갱신할 것.
 * answer는 JSON-LD(FAQPage)에도 그대로 들어가므로 HTML 태그 없이 작성한다.
 */

export interface FaqItem {
  question: string;
  answer: string;
}

export interface FaqCategory {
  title: string;
  items: FaqItem[];
  /** 카테고리 하단에 노출할 관련 문서 링크 (선택) */
  relatedLinks?: { label: string; to: string }[];
}

export const FAQ_CONTENT: Record<'ko' | 'en', FaqCategory[]> = {
  ko: [
    {
      title: '제품 일반',
      items: [
        {
          question: 'BUL:C는 어떤 소프트웨어인가요?',
          answer:
            'BUL:C는 주식회사 메테오시뮬레이션이 개발한 GPU 가속 기반 화재 시뮬레이션 소프트웨어입니다. NIST에서 검증된 FDS 물리 엔진을 기반으로 화재와 연기의 확산을 분석하고, EVAC 피난 시뮬레이션, AI 자동 환경 설정, 성능위주설계(PBD) 보고서 자동 생성 기능을 제공합니다.',
        },
        {
          question: '기존 FDS와 비교해 어떤 차이가 있나요?',
          answer:
            'BUL:C는 FDS의 검증된 물리 모델을 기반으로 하면서 NVIDIA CUDA GPU 가속을 적용해 기존 CPU 해석 대비 최대 87배 빠른 시뮬레이션 속도를 제공합니다. 또한 텍스트 입력 방식인 FDS와 달리 GUI 기반 모델링, AI 도면 자동 인식, ASET/RSET 자동 분석, 보고서 자동 생성을 지원해 작업 시간을 크게 단축할 수 있습니다.',
        },
        {
          question: 'BUL:C는 주로 어떤 용도로 사용되나요?',
          answer:
            '성능위주설계(PBD)를 위한 화재·피난 시뮬레이션, 건축물 화재 안전성 평가, 피난 시간(ASET/RSET) 분석, 소방 분야 교육·연구 등에 사용됩니다.',
        },
        {
          question: 'AI 기능은 무엇을 해주나요?',
          answer:
            '자연어 입력으로 시뮬레이션 환경을 자동 설정할 수 있고, 건축 도면(CAD)을 AI가 자동으로 인식해 3D 해석 모델을 빠르게 구성할 수 있습니다.',
        },
        {
          question: 'BUL:C는 무료로 사용할 수 있나요?',
          answer:
            '네, 회원가입 시 14일 무료 체험 라이선스가 제공됩니다. 체험판에서는 CPU 기반 분석, 기본 라이브러리, EVAC 피난 분석 등 핵심 기능을 사용할 수 있습니다.',
        },
      ],
    },
    {
      title: '소방성능위주설계(PBD)',
      items: [
        {
          question: '소방성능위주설계(PBD)란 무엇인가요?',
          answer:
            '성능위주설계(PBD, Performance-Based Design)는 법령에서 정한 일률적인 사양 기준(사양위주설계)을 그대로 적용하는 대신, 건축물의 용도·구조·재실자 특성을 반영한 화재 시나리오를 설정하고 화재·피난 시뮬레이션 등 공학적 분석으로 소방시설의 성능을 입증하는 설계 방식입니다. 초고층·대규모·복합 건축물처럼 표준 기준만으로 안전성을 담보하기 어려운 대상에 적용됩니다.',
        },
        {
          question: '성능위주설계는 어떤 건축물에 적용되나요?',
          answer:
            '국내에서는 소방시설 설치 및 관리에 관한 법령에 따라 일정 규모 이상의 특정소방대상물에 성능위주설계가 요구됩니다. 대표적으로 연면적이 매우 큰 대규모 건축물, 고층·초고층 건축물(아파트 포함), 철도·공항 등 대규모 다중이용시설, 다수의 상영관이 밀집된 영화상영관 등이 해당하며, 세부 기준은 현행 법령을 확인해야 합니다.',
        },
        {
          question: '성능위주설계는 어떤 절차로 진행되나요?',
          answer:
            '일반적으로 ① 설계 개요 및 화재 시나리오 설정, ② 화재 시뮬레이션(CFD)으로 열·연기·독성가스 확산을 예측하고 ASET 산정, ③ 피난 시뮬레이션으로 RSET 산정, ④ ASET과 RSET 비교를 통한 피난 안전성 평가, ⑤ 미달 시 설계 보완 후 재해석 및 심의용 보고서 작성의 순서로 진행됩니다.',
        },
        {
          question: '성능위주설계에서 화재·피난 시뮬레이션은 어떤 역할을 하나요?',
          answer:
            '성능위주설계의 핵심 근거 자료가 바로 화재·피난 시뮬레이션 결과입니다. 화재 시뮬레이션(CFD)으로 열·연기·독성가스 확산을 예측해 ASET(이용 가능 안전 피난 시간)을 산정하고, 피난 시뮬레이션(EVAC)으로 RSET(필요 안전 피난 시간)을 산정한 뒤 ASET이 RSET보다 큰지(ASET > RSET)로 피난 안전성을 판정합니다. 신뢰성을 위해 NIST의 FDS처럼 국제적으로 검증된 해석 도구가 사용되며, 다양한 시나리오를 검토할수록 설계 신뢰도가 높아집니다.',
        },
        {
          question: 'BUL:C로 성능위주설계를 수행할 수 있나요?',
          answer:
            '네. BUL:C는 NIST 검증 FDS 물리 엔진 기반 화재 해석과 EVAC 피난 시뮬레이션, ASET/RSET 자동 분석, 성능위주설계(PBD) 보고서 자동 생성을 하나의 프로그램에서 제공합니다. GPU 가속으로 해석 시간을 크게 단축해 같은 기간에 더 많은 시나리오를 검토할 수 있고, AI 도면 자동 인식으로 모델링 시간도 줄일 수 있습니다.',
        },
      ],
      relatedLinks: [
        { label: '성능위주설계(PBD)란? 대상·절차·시뮬레이션', to: '/docs/performance-based-design' },
        { label: 'ASET과 RSET이란? 피난 안전성 평가의 핵심 개념', to: '/docs/aset-rset' },
        { label: 'FDS 기반 화재 시뮬레이션과 GPU 가속', to: '/docs/fds-gpu-acceleration' },
      ],
    },
    {
      title: '라이선스 · 계정',
      items: [
        {
          question: 'BUL:C는 몇 대의 기기에서 사용할 수 있나요?',
          answer: '하나의 계정당 최대 3대의 기기에 등록할 수 있습니다.',
        },
        {
          question: '여러 기기에서 동시에 로그인할 수 있나요?',
          answer:
            '동시 로그인은 1세션으로 제한됩니다. 다른 기기에서 로그인하면 기존 세션은 강제 로그아웃됩니다.',
        },
        {
          question: '기기를 변경할 수 있나요?',
          answer:
            '네, 기기 변경이 가능합니다. 일일 변경 횟수 제한이 있으며, 문제가 있는 경우 고객지원(simul@msimul.com)을 통해 도움받으실 수 있습니다.',
        },
        {
          question: '교육용·연구용 라이선스가 있나요?',
          answer:
            '네, 비상업적 용도의 교육·연구용 라이선스를 별도로 제공합니다. simul@msimul.com 으로 문의해 주세요.',
        },
      ],
    },
    {
      title: '결제 · 환불',
      items: [
        {
          question: '요금제는 어떻게 구성되어 있나요?',
          answer:
            'BUL:C Basic(14일 무료 체험, CPU 기반 분석)과 BUL:C PRO(1년 라이선스, GPU 가속 포함 전체 기능)로 구성됩니다. 교육·연구용은 별도 문의입니다. 자세한 가격은 홈페이지의 가격 안내에서 확인할 수 있습니다.',
        },
        {
          question: '환불은 어떤 경우에 가능한가요?',
          answer:
            '결제일로부터 7일 이내에 환불을 요청한 경우, 구매한 라이선스를 활성화하지 않은 경우, 서비스의 중대한 결함으로 정상적인 이용이 불가능한 경우 등에는 전액 환불이 가능합니다. 자세한 내용은 환불 정책 페이지를 참고해 주세요.',
        },
        {
          question: '어떤 결제 수단을 지원하나요?',
          answer:
            '토스페이먼츠를 통한 카드 결제 등 온라인 결제를 지원합니다. 결제 관련 문의는 simul@msimul.com 으로 연락해 주세요.',
        },
      ],
    },
    {
      title: '시스템 · 기술',
      items: [
        {
          question: 'BUL:C의 시스템 요구사항은 무엇인가요?',
          answer:
            'Windows 10/11 (64-bit), Intel i5 또는 AMD Ryzen 5 이상 CPU, RAM 16GB 이상, NVIDIA GTX 1060 이상(CUDA 지원) GPU, 저장공간 10GB 이상이 필요합니다.',
        },
        {
          question: 'GPU가 반드시 필요한가요?',
          answer:
            '무료 체험판(Basic)은 CPU 기반 분석을 지원하므로 GPU 없이도 사용할 수 있습니다. GPU 가속 시뮬레이션은 PRO 라이선스에서 제공되며, NVIDIA CUDA 지원 그래픽카드가 필요합니다.',
        },
        {
          question: '시뮬레이션 결과는 신뢰할 수 있나요?',
          answer:
            'BUL:C는 NIST(미국 국립표준기술연구소)에서 개발·검증한 FDS(Fire Dynamics Simulator) 물리 엔진을 기반으로 하며, ASET/RSET 분석 등 성능위주설계에 필요한 정량 지표를 제공합니다.',
        },
        {
          question: '보고서는 어떤 내용이 포함되나요?',
          answer:
            '화재 시뮬레이션 결과, 피난 시뮬레이션 결과, ASET/RSET 비교 분석 등 성능위주설계(PBD)에 필요한 항목이 포함된 보고서가 자동으로 생성됩니다.',
        },
        {
          question: '기술 지원은 어떻게 받을 수 있나요?',
          answer:
            '이메일(simul@msimul.com)로 문의하시면 기술 지원을 받을 수 있습니다. 홈페이지의 튜토리얼과 게시판에서도 사용 방법을 확인할 수 있습니다.',
        },
      ],
    },
  ],
  en: [
    {
      title: 'Product',
      items: [
        {
          question: 'What is BUL:C?',
          answer:
            'BUL:C is a GPU-accelerated fire simulation software developed by Meteor Simulation Co., Ltd. Based on the NIST-validated FDS physics engine, it analyzes fire and smoke spread and provides EVAC evacuation simulation, AI-assisted setup, and automatic performance-based design (PBD) report generation.',
        },
        {
          question: 'How is BUL:C different from FDS?',
          answer:
            'BUL:C builds on the validated physics of FDS while adding NVIDIA CUDA GPU acceleration, achieving up to 87x faster simulation compared to CPU-based analysis. Unlike text-input FDS, it offers GUI-based modeling, AI drawing recognition, automatic ASET/RSET analysis, and automatic report generation.',
        },
        {
          question: 'What is BUL:C used for?',
          answer:
            'Fire and evacuation simulation for performance-based design (PBD), building fire safety assessment, egress time (ASET/RSET) analysis, and education and research in fire engineering.',
        },
        {
          question: 'What do the AI features do?',
          answer:
            'You can set up simulation environments with natural-language input, and the AI automatically recognizes architectural drawings (CAD) to quickly build 3D analysis models.',
        },
        {
          question: 'Can I use BUL:C for free?',
          answer:
            'Yes. A 14-day free trial license is provided upon sign-up, including CPU-based analysis, the basic library, and EVAC evacuation analysis.',
        },
      ],
    },
    {
      title: 'Performance-Based Design (PBD)',
      items: [
        {
          question: 'What is Performance-Based Design (PBD)?',
          answer:
            'Performance-Based Design (PBD) is an approach that, instead of simply applying the uniform prescriptive criteria set by code (prescriptive design), defines fire scenarios reflecting a building’s use, structure, and occupant characteristics and demonstrates the performance of fire safety systems through engineering analysis such as fire and evacuation simulation. It is applied to buildings—high-rise, large-scale, or complex—where standard criteria alone cannot guarantee safety.',
        },
        {
          question: 'Which buildings require performance-based design?',
          answer:
            'In Korea, performance-based design is required for certain fire-service target facilities above a defined scale under the relevant fire safety laws. Typical targets include very large buildings by gross floor area, high-rise and super high-rise buildings (including apartments), large multi-use facilities such as railways and airports, and movie theaters with many densely packed auditoriums. Exact thresholds should be checked against current legislation.',
        },
        {
          question: 'What is the procedure for performance-based design?',
          answer:
            'It generally proceeds as follows: (1) define the design overview and fire scenarios, (2) run fire simulation (CFD) to predict heat, smoke, and toxic-gas spread and determine ASET, (3) run evacuation simulation to determine RSET, (4) assess egress safety by comparing ASET and RSET, and (5) if the criteria are not met, revise the design, re-analyze, and prepare the review report.',
        },
        {
          question: 'What role do fire and evacuation simulations play in PBD?',
          answer:
            'Fire and evacuation simulation results are the core supporting evidence for performance-based design. Fire simulation (CFD) predicts heat, smoke, and toxic-gas spread to determine ASET (Available Safe Egress Time), evacuation simulation (EVAC) determines RSET (Required Safe Egress Time), and egress safety is judged by whether ASET exceeds RSET. Internationally validated tools such as NIST’s FDS are used for reliability, and reviewing more scenarios increases design confidence.',
        },
        {
          question: 'Can I perform performance-based design with BUL:C?',
          answer:
            'Yes. BUL:C provides NIST-validated FDS-based fire analysis, EVAC evacuation simulation, automatic ASET/RSET analysis, and automatic PBD report generation in a single program. GPU acceleration greatly shortens analysis time so you can review more scenarios in the same period, and AI drawing recognition reduces modeling time.',
        },
      ],
      relatedLinks: [
        { label: 'What is Performance-Based Design (PBD)?', to: '/docs/performance-based-design' },
        { label: 'What are ASET and RSET?', to: '/docs/aset-rset' },
        { label: 'FDS fire simulation and GPU acceleration', to: '/docs/fds-gpu-acceleration' },
      ],
    },
    {
      title: 'License & Account',
      items: [
        {
          question: 'On how many devices can I use BUL:C?',
          answer: 'You can register up to 3 devices per account.',
        },
        {
          question: 'Can I log in from multiple devices at the same time?',
          answer:
            'Concurrent login is limited to one session. Logging in from another device will force-logout the existing session.',
        },
        {
          question: 'Can I change my device?',
          answer:
            'Yes. Device changes are allowed with a daily limit. Contact support (simul@msimul.com) if you need help.',
        },
        {
          question: 'Is there an education/research license?',
          answer:
            'Yes, a separate license for non-commercial education and research use is available. Please contact simul@msimul.com.',
        },
      ],
    },
    {
      title: 'Payment & Refund',
      items: [
        {
          question: 'What plans are available?',
          answer:
            'BUL:C Basic (14-day free trial, CPU-based analysis) and BUL:C PRO (1-year license with GPU acceleration and full features). Education/research licensing is available on request. See the pricing section on our homepage for details.',
        },
        {
          question: 'When can I get a refund?',
          answer:
            'A full refund is available if requested within 7 days of payment, if the purchased license has not been activated, or if a material defect prevents normal use. See the Refund Policy page for details.',
        },
        {
          question: 'What payment methods are supported?',
          answer:
            'Online payments including card payments via Toss Payments are supported. For payment inquiries, contact simul@msimul.com.',
        },
      ],
    },
    {
      title: 'System & Technical',
      items: [
        {
          question: 'What are the system requirements?',
          answer:
            'Windows 10/11 (64-bit), Intel i5 or AMD Ryzen 5 or higher, 16GB+ RAM, NVIDIA GTX 1060 or higher (CUDA support), and 10GB+ storage.',
        },
        {
          question: 'Is a GPU required?',
          answer:
            'The free trial (Basic) supports CPU-based analysis, so a GPU is not required. GPU-accelerated simulation is available with the PRO license and requires an NVIDIA CUDA-capable graphics card.',
        },
        {
          question: 'Are the simulation results reliable?',
          answer:
            'BUL:C is based on the FDS (Fire Dynamics Simulator) physics engine developed and validated by NIST, and provides quantitative metrics such as ASET/RSET analysis required for performance-based design.',
        },
        {
          question: 'What is included in the generated report?',
          answer:
            'The automatically generated report includes fire simulation results, evacuation simulation results, and ASET/RSET comparison analysis required for performance-based design (PBD).',
        },
        {
          question: 'How can I get technical support?',
          answer:
            'Contact us at simul@msimul.com. Tutorials and the community board on our homepage also cover common usage questions.',
        },
      ],
    },
  ],
};
