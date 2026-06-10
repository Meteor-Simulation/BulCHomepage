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
