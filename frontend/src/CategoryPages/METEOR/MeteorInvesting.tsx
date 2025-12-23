import React, { useState, useEffect } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import './MeteorInvesting.css';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

const MeteorInvesting: React.FC = () => {
  const [expandedActivity, setExpandedActivity] = useState<string | null>(null);
  const [isMobile, setIsMobile] = useState(window.innerWidth <= 480);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 480);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const toggleActivity = (activityId: string) => {
    setExpandedActivity(expandedActivity === activityId ? null : activityId);
  };
  // Revenue data (in millions KRW)
  const revenueData = {
    labels: ['2023', '2024', '2025', '2026', '2027', '2028', '2029', '2030'],
    datasets: [
      {
        label: '실제 매출',
        data: [100, 270, 430, null, null, null, null, null],
        borderColor: '#C4320A',
        backgroundColor: 'rgba(196, 50, 10, 0.1)',
        borderWidth: 3,
        pointRadius: 6,
        pointHoverRadius: 8,
        tension: 0.4,
        fill: true,
      },
      {
        label: 'AI 예측 - 현재 상승세 유지',
        data: [null, null, 430, 590, 750, 910, 1070, 1230],
        borderColor: '#666666',
        backgroundColor: 'rgba(102, 102, 102, 0.05)',
        borderWidth: 2.5,
        borderDash: [8, 4],
        pointRadius: 5,
        pointHoverRadius: 7,
        tension: 0.4,
        fill: false,
      },
      {
        label: 'AI 예측 - 좋을 경우',
        data: [null, null, 430, 850, 1400, 2100, 2950, 4000],
        borderColor: '#2c5aa0',
        backgroundColor: 'rgba(44, 90, 160, 0.1)',
        borderWidth: 3,
        borderDash: [10, 5],
        pointRadius: 6,
        pointHoverRadius: 8,
        tension: 0.4,
        fill: true,
      },
      {
        label: 'AI 예측 - 아주 좋을 경우',
        data: [null, null, 430, 1200, 2300, 3800, 5800, 8500],
        borderColor: '#27ae60',
        backgroundColor: 'rgba(39, 174, 96, 0.1)',
        borderWidth: 3,
        borderDash: [10, 5],
        pointRadius: 6,
        pointHoverRadius: 8,
        tension: 0.4,
        fill: true,
      }
    ]
  };

  const chartOptions: any = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          font: {
            size: 14,
            weight: 'bold',
          },
          padding: 20,
          usePointStyle: true,
        }
      },
      title: {
        display: true,
        text: isMobile ? '매출 성장 & AI 예측' : 'Revenue Growth & AI Forecast (단위: 백만원)',
        font: {
          size: isMobile ? 14 : 18,
          weight: 'bold',
        },
        padding: isMobile ? 12 : 20,
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: isMobile ? 8 : 12,
        titleFont: {
          size: isMobile ? 11 : 14,
          weight: 'bold',
        },
        bodyFont: {
          size: isMobile ? 10 : 13,
        },
        callbacks: {
          label: function(context: any) {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            if (context.parsed.y !== null) {
              label += context.parsed.y.toLocaleString() + (isMobile ? 'M' : '백만원');
            }
            return label;
          }
        }
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: function(value: any) {
            return value.toLocaleString() + 'M';
          },
          font: {
            size: isMobile ? 9 : 12,
          }
        },
        grid: {
          color: 'rgba(0, 0, 0, 0.05)',
        }
      },
      x: {
        grid: {
          display: false,
        },
        ticks: {
          font: {
            size: isMobile ? 9 : 12,
            weight: 'bold',
          }
        }
      }
    }
  };

  // Key Expected Clients - Tier system
  const expectedClients = {
    tier1: {
      title: 'Tier 1 - Strategic Partners (2026년 확정)',
      clients: ['S전자', 'G건설', 'K기술원', 'L전자', '일본 M사', 'S교통공사', 'I교통공사'],
      targets: {
        projects: 8,
        bulcLicenses: 15,
        expectedRevenue: 650
      }
    },
    tier2: {
      title: 'Tier 2 - Key Clients',
      clients: ['I교통공사', 'K공단', 'D데이터센터', 'S계열사', '물류회사 3사', '배터리사 3사'],
      targets: {
        projects: 12,
        bulcLicenses: 25,
        expectedRevenue: 480
      }
    },
    tier3: {
      title: 'Tier 3 - Growing Accounts',
      clients: ['H엔지니어링', 'A안전', 'B방재', '추가예정'],
      targets: {
        projects: 20,
        bulcLicenses: 40,
        expectedRevenue: 320
      }
    },
    tierEdu: {
      title: 'Tier EDU - Education',
      description: '교육용 무료 라이센스 배포',
      targets: {
        freeLicenses: 400,
        vrEducation: {
          educationOffice: { projects: 1, revenue: 100 },
          corporate: { clients: 'S전자, G건설', revenue: 400 }
        },
        totalRevenue: 500
      }
    }
  };

  // Marketing Strategy Timeline (2026 Plan) - Grouped by month with details
  const marketingTimelineByMonth = [
    {
      month: '2026.01',
      activities: [
        {
          category: 'SEO',
          activity: 'Google Search Console 최적화 및 키워드 전략 수립',
          type: 'digital',
          details: '화재 시뮬레이션, 성능위주설계, ESS 화재 등 핵심 키워드 분석 및 검색 순위 개선 전략 수립. 기술 블로그 SEO 최적화 작업 진행.\n\n💰 예상 소요비용: 8백만원 (SEO 전문가 인건비, 키워드 분석 툴, 콘텐츠 제작)\n📈 예상 매출 증가: 분기 10~15백만원 - 유기적 검색 유입 증가로 문의 2~3건 추가 발생'
        },
        {
          category: 'GEO',
          activity: 'ChatGPT/Claude 검색 최적화 콘텐츠 제작',
          type: 'digital',
          details: 'AI 검색엔진에 최적화된 FAQ 형식 콘텐츠 제작. ChatGPT, Claude, Gemini에서 BULC 정보가 정확하게 표시되도록 구조화된 데이터 생성.\n\n💰 예상 소요비용: 6백만원 (AI 콘텐츠 전략가, 데이터 구조화 작업)\n📈 예상 매출 증가: 분기 10백만원 - AI 검색 유입 신규 채널 확보, 브랜드 인지도 향상'
        },
        {
          category: '전시회',
          activity: 'Korea Fire Safety Expo 2026 부스 기획',
          type: 'exhibition',
          details: '국내 최대 소방안전 전시회 참가 준비. BULC 시뮬레이션 데모 부스 설계 및 전시 자료 제작.\n\n💰 예상 소요비용: 12백만원 (부스 디자인, 전시 자료 제작, 사전 마케팅)\n📈 예상 매출 증가: 3월 전시회 후 10~20백만원 - 국내 전시회 계약 1건 예상 (프로젝트당 1,000~2,000만원)'
        },
        {
          category: '학회',
          activity: 'Fire Safety Science 논문 초안 작성',
          type: 'academic',
          details: 'AI 기반 화재 시뮬레이션 자동화 기술 관련 논문 작성. 국제 저널 투고를 위한 초안 준비.\n\n💰 예상 소요비용: 10백만원 (연구원 인건비, 실험 데이터 분석, 논문 교정)\n📈 예상 매출 증가: 연간 20~30백만원 - 기술 신뢰도 향상으로 대학·연구소 문의 증가'
        },
        {
          category: '전문가 모임',
          activity: 'Fire Safety Expert Roundtable 기획회의',
          type: 'expert',
          details: '국내 화재안전 전문가 네트워크 구축. 연 4회 라운드테이블 개최를 위한 기획 및 연사 섭외.\n\n💰 예상 소요비용: 5백만원 (회의 장소, 연사료, 네트워킹 이벤트)\n📈 예상 매출 증가: 연간 10~20백만원 - 전문가 추천 계약 1건, B2B 네트워크 확대'
        },
        {
          category: '교육용',
          activity: '한국 대학 BULC 라이센스 계약 (30 Copy)',
          type: 'education',
          details: '국내 대학 소방공학과 및 건축학과 대상 BULC 교육용 라이센스 제공. 학생 교육 및 연구 지원.\n\n💰 예상 소요비용: 8백만원 (교육 자료, 기술 지원, 세미나 개최)\n📈 예상 매출 증가: 직접 45백만원 (30 Copy × 1.5백만원), 간접효과 장기적 브랜드 구축'
        },
      ]
    },
    {
      month: '2026.02',
      activities: [
        {
          category: 'SEO',
          activity: '기술 블로그 SEO 최적화 및 백링크 구축',
          type: 'digital',
          details: '화재안전 관련 기술 블로그 포스팅 및 업계 웹사이트와의 백링크 교환. 도메인 권위도 향상 작업.\n\n💰 예상 소요비용: 10백만원 (콘텐츠 작성, 백링크 구축 대행, 도메인 분석 툴)\n📈 예상 매출 증가: 분기 10~15백만원 - 검색 유입 증가로 추가 문의 2~3건'
        },
        {
          category: 'GEO',
          activity: 'AI 검색엔진 크롤링 최적화 및 스키마 마크업',
          type: 'digital',
          details: 'Schema.org 마크업 적용으로 구조화된 데이터 제공. AI가 BULC 정보를 정확하게 이해하고 요약할 수 있도록 최적화.\n\n💰 예상 소요비용: 7백만원 (스키마 개발, 데이터 구조화, 테스트 및 검증)\n📈 예상 매출 증가: 분기 8~12백만원 - AI 검색 브랜드 노출 강화'
        },
        {
          category: '전시회',
          activity: 'Intersec Dubai 2026 참가 등록 및 준비',
          type: 'exhibition',
          details: '중동 최대 보안 및 소방 전시회 참가 등록. 영문 마케팅 자료 및 해외 영업 전략 수립.\n\n💰 예상 소요비용: 18백만원 (부스 등록, 영문 자료 제작, 해외 마케팅 컨설팅)\n📈 예상 매출 증가: 5월 전시 후 100백만원 - 국제 전시회 계약 1건 예상'
        },
        {
          category: '학회',
          activity: 'Fire Safety Science 논문 투고 및 피어리뷰',
          type: 'academic',
          details: '국제 학술지 Fire Safety Science에 논문 투고. 피어리뷰 피드백 반영 및 수정 작업 진행.\n\n💰 예상 소요비용: 8백만원 (피어리뷰 대응, 추가 실험, 영문 교정)\n📈 예상 매출 증가: 연간 20~30백만원 - 논문 게재 후 기술 권위 인정'
        },
        {
          category: '전문가 모임',
          activity: 'AI Fire Safety Innovation Forum 연사 섭외',
          type: 'expert',
          details: '국내외 AI 및 화재안전 분야 전문가 섭외. 포럼 주제 및 세션 구성 기획.\n\n💰 예상 소요비용: 6백만원 (연사료 선지급, 기획 회의, 마케팅 자료)\n📈 예상 매출 증가: 5월 포럼 후 10~15백만원 - 전문가 추천 계약 기대'
        },
        {
          category: '교육용',
          activity: '한국 대학 BULC 라이센스 계약 (30 Copy)',
          type: 'education',
          details: '2월 학기 시작에 맞춰 국내 대학 교육용 라이센스 추가 제공. 교수진 대상 사용법 교육 진행.\n\n💰 예상 소요비용: 7백만원 (교육 자료, 교수진 세미나, 기술 지원)\n📈 예상 매출 증가: 직접 45백만원 (30 Copy × 1.5백만원), 간접효과 브랜드 구축'
        },
      ]
    },
    {
      month: '2026.03',
      activities: [
        {
          category: 'SEO',
          activity: 'YouTube 기술 채널 컨텐츠 전략 수립',
          type: 'digital',
          details: 'BULC 사용법, 화재 시뮬레이션 케이스 스터디 등 YouTube 콘텐츠 기획. 영상 SEO 최적화 전략 수립.\n\n💰 예상 소요비용: 15백만원 (영상 제작, 편집, YouTube SEO 최적화, 썸네일 디자인)\n📈 예상 매출 증가: 분기 12~18백만원 - YouTube 유입 증가로 문의 2~3건'
        },
        {
          category: 'GEO',
          activity: 'AI 검색 결과 품질 모니터링 시스템 구축',
          type: 'digital',
          details: 'ChatGPT, Claude, Gemini에서 BULC 관련 쿼리 응답 품질 모니터링. 부정확한 정보 발견 시 즉시 수정.\n\n💰 예상 소요비용: 9백만원 (모니터링 시스템 개발, AI 쿼리 테스트, 데이터 수집)\n📈 예상 매출 증가: 분기 10백만원 - AI 검색 브랜드 신뢰도 강화'
        },
        {
          category: '전시회',
          activity: 'Korea Fire Safety Expo 2026 현장 참가',
          type: 'exhibition',
          details: '국내 최대 소방안전 전시회 현장 부스 운영. BULC 실시간 데모 및 잠재 고객 상담 진행.\n\n💰 예상 소요비용: 20백만원 (부스 운영, 인력 배치, 현장 마케팅 자료, 데모 장비)\n📈 예상 매출 증가: 10~20백만원 - 국내 전시회 계약 1건 (1,000~2,000만원/건)'
        },
        {
          category: '학회',
          activity: 'SFPE Conference 논문 발표 준비',
          type: 'academic',
          details: '미국 SFPE(Society of Fire Protection Engineers) 컨퍼런스 발표 자료 준비. 영문 발표 연습.\n\n💰 예상 소요비용: 12백만원 (발표 자료 제작, 영문 스크립트, 발표 코칭)\n📈 예상 매출 증가: 연간 30~50백만원 - SFPE 발표 후 국제 인지도 상승'
        },
        {
          category: '전문가 모임',
          activity: 'Fire Safety Expert Roundtable 개최',
          type: 'expert',
          details: '제1회 화재안전 전문가 라운드테이블 개최. 업계 이슈 토론 및 네트워킹.\n\n💰 예상 소요비용: 8백만원 (회의 장소, 케이터링, 연사료, 행사 운영)\n📈 예상 매출 증가: 분기 10~15백만원 - 전문가 추천 계약 1건'
        },
        {
          category: '교육용',
          activity: '일본 대학 BULC 라이센스 계약 (40 Copy)',
          type: 'education',
          details: '일본 주요 대학 소방공학과 대상 BULC 교육용 라이센스 제공. 일본어 매뉴얼 및 기술 지원.\n\n💰 예상 소요비용: 12백만원 (일본어 매뉴얼, 현지 기술 지원, 마케팅)\n📈 예상 매출 증가: 직접 80백만원 (40 Copy × 2백만원), 간접효과 일본 시장 진출'
        },
      ]
    },
    {
      month: '2026.04',
      activities: [
        {
          category: 'SEO',
          activity: '국제 웹사이트 영문 SEO 최적화',
          type: 'digital',
          details: '글로벌 시장 진출을 위한 영문 웹사이트 SEO 최적화. Google.com 검색 순위 개선 작업.\n\n💰 예상 소요비용: 14백만원 (영문 콘텐츠 제작, 글로벌 SEO 컨설팅, 해외 키워드 분석)\n📈 예상 매출 증가: 분기 15~20백만원 - 글로벌 문의 증가'
        },
        {
          category: 'GEO',
          activity: 'AI 검색 결과 Featured Snippet 전략',
          type: 'digital',
          details: 'AI 검색 결과 상단 노출을 위한 Featured Snippet 최적화. 질문-답변 형식 콘텐츠 강화.\n\n💰 예상 소요비용: 8백만원 (Q&A 콘텐츠 제작, Featured Snippet 최적화, A/B 테스트)\n📈 예상 매출 증가: 분기 10~15백만원 - Featured Snippet 노출 강화'
        },
        {
          category: '전시회',
          activity: 'SFPE PBD 싱가포르 국제전시회 참가',
          type: 'exhibition',
          details: 'SFPE Performance-Based Design 싱가포르 컨퍼런스 및 전시회 참가. 아시아-태평양 시장 개척.\n\n💰 예상 소요비용: 35백만원 (항공료, 숙박, 부스 운영, 영문 마케팅 자료, 현지 통역)\n📈 예상 매출 증가: 100백만원 - 국제 전시회 계약 1건 (프로젝트당 약 1억원)'
        },
        {
          category: '학회',
          activity: 'SFPE Conference 현장 발표 / 방재학회·소방학회 참가',
          type: 'academic',
          details: 'SFPE Conference (미국) 현장 논문 발표. 국내 방재학회 및 소방학회 춘계학술대회 논문 발표.\n\n💰 예상 소요비용: 22백만원 (SFPE 출장비, 국내 학회 등록비, 발표 자료 제작)\n📈 예상 매출 증가: 연간 40~60백만원 - 학회 발표 후 대학·연구소 계약 증가'
        },
        {
          category: '전문가 모임',
          activity: 'Global Fire Safety Leaders Summit 기획',
          type: 'expert',
          details: '글로벌 화재안전 리더 서밋 기획. 해외 전문가 초청 및 국제 협력 방안 논의.\n\n💰 예상 소요비용: 15백만원 (기획 회의, 해외 연사 섭외, 사전 마케팅)\n📈 예상 매출 증가: 7월 서밋 후 20~30백만원 - 글로벌 파트너십 계약 기대'
        },
        {
          category: '교육용',
          activity: '한국 대학 BULC 라이센스 계약 (30 Copy)',
          type: 'education',
          details: '봄 학기 추가 대학 라이센스 계약. 학생 프로젝트 지원 및 우수 사례 발굴.\n\n💰 예상 소요비용: 9백만원 (교육 자료, 학생 프로젝트 멘토링, 우수 사례 시상)\n📈 예상 매출 증가: 직접 45백만원 (30 Copy × 1.5백만원), 간접효과 브랜드 구축'
        },
      ]
    },
    {
      month: '2026.05',
      activities: [
        {
          category: 'SEO',
          activity: '기술 백서 다운로드 랜딩페이지 SEO 강화',
          type: 'digital',
          details: 'BULC 기술 백서 다운로드 랜딩페이지 제작 및 SEO 최적화. 리드 생성 및 전환율 개선.\n\n💰 예상 소요비용: 11백만원 (랜딩페이지 디자인, 백서 제작, 전환율 최적화 테스트)\n📈 예상 매출 증가: 분기 15~20백만원 - 리드 생성 및 전환율 개선'
        },
        {
          category: 'GEO',
          activity: 'Gemini 검색 최적화 및 음성 검색 대응',
          type: 'digital',
          details: 'Google Gemini 최적화 작업. 음성 검색 쿼리에 대한 자연어 답변 콘텐츠 생성.\n\n💰 예상 소요비용: 10백만원 (음성 검색 콘텐츠 제작, Gemini 최적화, 자연어 처리 분석)\n📈 예상 매출 증가: 분기 10백만원 - 음성 검색 신규 채널'
        },
        {
          category: '전시회',
          activity: 'Intersec Dubai 2026 현장 참가 및 네트워킹',
          type: 'exhibition',
          details: '두바이 현장 부스 운영 및 중동 지역 파트너사 발굴. 현지 소방안전 규제 조사.\n\n💰 예상 소요비용: 38백만원 (항공료, 숙박, 부스 운영, 통역, 현지 규제 조사)\n📈 예상 매출 증가: 100백만원 - 국제 전시회 계약 1건 (프로젝트당 약 1억원)'
        },
        {
          category: '학회',
          activity: 'Journal of Fire Sciences 논문 투고',
          type: 'academic',
          details: 'SCI급 국제 학술지 Journal of Fire Sciences에 신규 논문 투고. 연구 성과 확산.\n\n💰 예상 소요비용: 15백만원 (연구 실험, 데이터 분석, 논문 작성, 영문 교정)\n📈 예상 매출 증가: 연간 40~60백만원 - SCI 논문 게재 후 기술 권위 확보'
        },
        {
          category: '전문가 모임',
          activity: 'AI Fire Safety Innovation Forum 개최',
          type: 'expert',
          details: 'AI와 화재안전 융합 기술 포럼 개최. 업계 전문가 및 학계 연사 초청.\n\n💰 예상 소요비용: 20백만원 (행사장 대관, 연사료, 케이터링, 행사 운영, 마케팅)\n📈 예상 매출 증가: 분기 15~20백만원 - 포럼 현장 계약 1~2건'
        },
        {
          category: '교육용',
          activity: '한국 대학 BULC 라이센스 계약 (30 Copy)',
          type: 'education',
          details: '5월 학생 프로젝트 시즌 대비 추가 라이센스 제공. 캡스톤 디자인 프로젝트 지원.\n\n💰 예상 소요비용: 10백만원 (교육 자료, 캡스톤 프로젝트 멘토링, 학생 경진대회 후원)\n📈 예상 매출 증가: 직접 45백만원 (30 Copy × 1.5백만원), 간접효과 브랜드 구축'
        },
      ]
    },
    {
      month: '2026.06',
      activities: [
        {
          category: 'Under Construction',
          activity: '마케팅 계획 수립 중',
          type: 'construction',
          details: '6월 이후 마케팅 전략 및 세부 실행 계획 수립 중입니다.\n\n💰 예상 소요비용: 미정 (전략 수립 후 확정)\n📈 예상 매출 증가: 미정 (1~5월 성과 분석 후 재조정)'
        }
      ]
    }
  ];

  // Competitive Analysis Data - Fire Simulation
  const fireSimCompetitors = [
    {
      company: 'Thunderhead Engineering',
      products: 'PyroSim, Pathfinder',
      country: '미국',
      annualRevenue: '추정 $20M (약 260억원)',
      marketShare: '글로벌 1위',
      strengths: 'FDS 기반 GUI, 피난 시뮬레이션 통합',
      sector: '화재 시뮬레이션'
    },
    {
      company: 'FSEG (University of Greenwich)',
      products: 'SMARTFIRE',
      country: '영국',
      annualRevenue: '추정 $5M (약 65억원)',
      marketShare: '유럽 시장 강세',
      strengths: 'CFD 기반 고급 해석, 복잡 구조 분석',
      sector: '화재 시뮬레이션'
    },
    {
      company: 'NIST',
      products: 'FDS, CFAST',
      country: '미국',
      annualRevenue: '오픈소스 (무료)',
      marketShare: '학술/연구 분야 독점',
      strengths: '무료 오픈소스, 검증된 물리 모델',
      sector: '화재 시뮬레이션'
    },
    {
      company: 'FireFOAM',
      products: 'FireFOAM',
      country: '글로벌',
      annualRevenue: '오픈소스 (무료)',
      marketShare: '연구/학술 분야',
      strengths: 'OpenFOAM 기반, 고도화 분석',
      sector: '화재 시뮬레이션'
    }
  ];

  // Competitive Analysis Data - Game VFX
  const gameVFXCompetitors = [
    {
      company: 'JangaFX',
      products: 'EmberGen',
      country: '미국',
      annualRevenue: '$3M (약 40억원, 2024)',
      marketShare: '200+ 게임 스튜디오',
      strengths: '실시간 볼류메트릭 시뮬레이터, 기존 대비 6배 빠른 제작',
      sector: '게임 VFX',
      employees: '20명',
      pricing: 'Indie $1,400 / Studio $2,300 / Enterprise 협의'
    },
    {
      company: 'SideFX',
      products: 'Houdini',
      country: '캐나다',
      annualRevenue: '추정 $50M+ (약 650억원)',
      marketShare: '영화/게임 VFX 표준',
      strengths: '절차적 3D 모델링, Epic Games 투자 유치',
      sector: '게임 VFX',
      employees: '200명+',
      pricing: 'Indie $269/년 / FX $4,495 / 노드락 $6,995'
    },
    {
      company: 'Chaos Group',
      products: 'Phoenix',
      country: '불가리아',
      annualRevenue: '추정 $30M (약 390억원)',
      marketShare: '건축 시각화 및 VFX',
      strengths: 'V-Ray 통합, 유체 역학 시뮬레이션',
      sector: '게임 VFX',
      employees: '100명+',
      pricing: 'Phoenix $480 / 연간 구독 $240'
    },
    {
      company: 'Afterworks (Sitni Sati)',
      products: 'FumeFX',
      country: '크로아티아',
      annualRevenue: '추정 $5M (약 65억원)',
      marketShare: '3ds Max 플러그인',
      strengths: '12년+ 영화/게임 산업 표준, 연기/화재 특화',
      sector: '게임 VFX',
      employees: '20명',
      pricing: '$750 (3ds Max) / $950 (Maya)'
    }
  ];

  const competitorsData = [...fireSimCompetitors, ...gameVFXCompetitors];

  // Market Analysis Data
  const marketAnalysis = {
    globalConstruction: '전세계 건축 시장 규모: $10조 (약 13,000조원)',
    designCost: '설계 비용 비율: 건축비의 5%',
    fireSimulationRatio: '화재 시뮬레이션 비율: 설계비의 10~30%',
    marketSize: '글로벌 화재 시뮬레이션 시장: 연간 650억원 ~ 1,950억원',
    pdbMandatory: [
      { country: '미국', status: 'NFPA 101, IBC - 고층 건물 의무화' },
      { country: '영국', status: 'BS 9999 - 성능설계 의무화' },
      { country: '한국', status: '소방법 - 대형 건축물 의무화 추진' },
      { country: '일본', status: '건축기준법 - 특정 건축물 의무화' },
      { country: '싱가포르', status: 'SCDF Code - 고층 건물 의무화' },
      { country: 'UAE', status: 'Dubai Civil Defence - 의무화' }
    ],
    growthTrend: '연평균 성장률 (CAGR): 8~12% (2024~2030)'
  };

  // VR Safety Education Market Data
  const vrMarketAnalysis = {
    title: 'Pivot 시장: VR 안전 교육 시장',
    description: '화재 피난 교육은 전세계 모든 사업장과 학교에서 의무적으로 실시',
    businessPrice: '사업장당 연간 약 300만원',
    schoolPrice: '학교 학급당 약 60만원',
    marketCharacteristics: [
      '선진국 중심 의무 교육 시장',
      '블루오션 신규 시장',
      '기존 대면 교육 대비 VR의 몰입도 및 효과성 우수',
      '반복 교육 가능 및 위험 상황 체험 가능'
    ],
    estimatedMarket: '선진국 VR 안전교육 시장: 연간 약 5,000억원 ~ 15,000억원',
    targetShare: 'BULC 목표 시장 점유율: 25%',
    targetRevenue: '목표 매출: 연간 1,250억원 ~ 3,750억원',
    marketPotential: '화재 시뮬레이션 PBD 시장 대비 약 8~10배 규모'
  };

  // Game VFX Asset Market Data
  const gameMarketAnalysis = {
    title: 'Pivot 시장: 게임 특수효과(VFX) Asset 시장',
    description: '화재·연기·폭발 실시간 시뮬레이션 Asset 및 프로그램 제공',
    competitors: [
      'EmberGen (JangaFX): 200+ 게임 스튜디오 사용 중, 실시간 볼류메트릭 시뮬레이터',
      'Chaos Phoenix: 건축 시각화 및 VFX용 유체 역학 시뮬레이터',
      'Bifrost (Autodesk): 폭발, 연기, 화재 등 Maya 통합 시뮬레이션',
      'FumeFX: 12년 이상 영화·게임 산업 표준 플러그인'
    ],
    marketData: {
      title: '글로벌 시장 규모',
      globalVFX: '글로벌 VFX 시장: $10.12B (2023) → $18.72B (2032)',
      gameVFXShare: '게임 VFX 비중: 전체 VFX 시장의 29%',
      animationGame: '애니메이션·VFX·게임 통합 시장: $259.3B (2023) → $563.6B (2032)',
      simulationFX: 'Simulation FX 세그먼트: 2023년 최대 시장 점유율',
      realTimeGrowth: '실시간 VFX 툴 수요: 27% 증가 (2024)',
      growthRate: '연평균 성장률 (CAGR): 7.07~8.8%'
    },
    gameEngineAdoption: {
      title: '게임 엔진 통합',
      unityUnreal: 'Unity & Unreal Engine: 52% 게임 개발사 실시간 엔진 사용',
      unrealEngine5: 'Unreal Engine 5: 차세대 게임의 50% 이상 개발 중',
      assetQuality: 'VFX Asset 품질: 게임 설치 및 인앱 구매 15~20% 증가 효과',
      productionSpeed: 'EmberGen 생산성: 기존 방식 대비 6배 이상 빠른 제작'
    },
    bulcAdvantage: [
      '실제 화재 시뮬레이션 물리엔진 보유 (검증된 기술)',
      '연기·화재 확산 패턴의 정확도 (소방·건축 분야 실적)',
      'Real-time 최적화 경험 (VR/AR 플랫폼)',
      'Unity/Unreal Plugin 개발 가능'
    ],
    pricing: {
      assetPack: 'VFX Asset Pack: $50~$300 (Unity/Unreal Marketplace)',
      pluginLicense: 'Plugin 라이센스: $200~$1,000 (스튜디오용)',
      enterpriseLicense: 'Enterprise 라이센스: $5,000~$20,000 (대형 스튜디오)'
    },
    totalMarket: '게임 VFX Asset 시장: 연간 약 3조원 ~ 5조원 (전체 VFX의 29%)',
    targetRevenue: 'BULC 목표 시장 진입: 2026~2028년, 연간 50억원 ~ 300억원',
    marketPotential: '화재 시뮬레이션 PBD 시장과 유사 규모, 글로벌 확장 용이'
  };

  // Extreme Environment Robot Market Data
  const robotMarketAnalysis = {
    title: 'Pivot 시장: 극한 환경 로봇 시장',
    description: '소방·군사용 로봇의 실내 연기 환경에서 필수적인 SLAM 기술 및 빅데이터',
    applications: [
      '소방 로봇: 연기로 시야가 제한된 실내 공간 경로 탐색 및 의사결정',
      '군사용 로봇: 자율 드론 및 지상 로봇의 실내 작전 수행',
      'SLAM 교육용 빅데이터: 극한 환경 시뮬레이션 학습 데이터',
      '경로 계획 및 장애물 회피 알고리즘 개발'
    ],
    firefightingMarket: {
      title: '소방 로봇 시장',
      globalSize: '글로벌 소방 로봇 시장: $1.5B (2024) → $2.8B (2030)',
      budgetRatio: '전세계 소방청 재정의 약 10% 이상',
      slamMarket: 'SLAM 로봇 시장: $0.29B (2023) → $5.79B (2030)',
      growthRate: '연평균 성장률 (CAGR): 9.6~14.5%'
    },
    militaryMarket: {
      title: '군사용 SLAM 시장',
      pentagonBudget: '미국 국방부 자율시스템 예산: $13.4B (2026)',
      droneBudget: '무인항공기(UAV) 예산: $9.4B (2026)',
      rdBudget: '한국 R&D 예산: 2026년 15~42억원 (향후 대폭 확대 예정)',
      marketProjection: '글로벌 군사 드론 시장: $47B (2032 예상)',
      slamCostRatio: 'SLAM 기술 비용: 군사 드론 개발비의 약 10%'
    },
    totalMarket: '소방·군사 극한환경 로봇 시장: 연간 약 2조원 ~ 6조원 (SLAM 기술 기준)',
    targetRevenue: 'BULC 목표 시장 진입: 2027~2030년 본격화, 연간 200억원 ~ 1,000억원',
    marketPotential: '화재 시뮬레이션 PBD 시장 대비 약 3~5배 규모'
  };

  return (
    <>
      {/* Revenue Chart Section */}
      <section className="meteor-section chart-section">
        <div className="meteor-container">
          <div className="section-header">
            <div className="section-eyebrow">FINANCIAL OVERVIEW</div>
            <h2 className="section-title">투자 정보</h2>
            <p className="section-description">
              매출 현황과 미래 전망을 확인하실 수 있습니다.
            </p>
          </div>

          <div className="chart-wrapper">
            <Line data={revenueData} options={chartOptions} />
          </div>

        {/* AI Prediction Models Explanation */}
        <div className="ai-models-section">
          <h3 className="models-title">AI 예측 모델 설명</h3>

          <div className="models-grid">
            <div className="model-card">
              <div className="model-header">
                <span className="model-number">모델 1</span>
                <h4 className="model-name">선형 트렌드 분석 (현재 상승세 유지)</h4>
              </div>
              <p className="model-description">
                <strong>방법론:</strong> 2023-2025년 실제 매출 데이터의 연평균 성장률(CAGR)을 기반으로 선형 추세를 연장합니다.
              </p>
              <p className="model-description">
                <strong>계산:</strong> CAGR = ((430/100)^(1/2) - 1) ≈ 107% → 2026년 이후 동일한 성장률 160M/년 유지
                <br/>• 2026년: 590M / 2027년: 750M / 2028년: 910M
                <br/>• 2029년: 1,070M / 2030년: 1,230M
              </p>
              <p className="model-description">
                <strong>가정:</strong> 외부 환경 변화 없이 현재의 성장 모멘텀이 그대로 유지되는 보수적 시나리오
              </p>
            </div>

            <div className="model-card highlight">
              <div className="model-header">
                <span className="model-number">모델 2</span>
                <h4 className="model-name">시장 점유율 확대 모델 (좋을 경우)</h4>
              </div>
              <p className="model-description">
                <strong>방법론:</strong> 주요 예상 고객(Tier 1-3) 확보 시나리오 + 경쟁사 대비 기술적 우위를 반영한 시장 점유율 모델
              </p>
              <p className="model-description">
                <strong>계산:</strong>
                <br/>• 2026년: Tier 1 고객 2개 확보 (S전자, G건설) + PBD 시장 5% 점유 = 850M
                <br/>• 2027년: Tier 2 확대 + 국내 PBD 시장 12% 점유 + 국제 전시회 성과 = 1,400M
                <br/>• 2028년: Tier 1-2 안정화 + 글로벌 시장 진출 + VR/게임 시장 초기 진입 = 2,100M
                <br/>• 2029년: 글로벌 확장 + VR 교육 본격화 + 게임 VFX 성장 = 2,950M
                <br/>• 2030년: 다각화 완성 + 글로벌 PBD 시장 20% 점유 + Pivot 시장 안정화 = 4,000M
              </p>
              <p className="model-description">
                <strong>가정:</strong> 마케팅 전략이 성공하고, Thunderhead Engineering 대비 20-30% 낮은 가격 경쟁력 + 한국/일본 시장 선점
              </p>
            </div>

            <div className="model-card highlight-green">
              <div className="model-header">
                <span className="model-number">모델 3</span>
                <h4 className="model-name">멀티 피벗 시장 진입 모델 (아주 좋을 경우)</h4>
              </div>
              <p className="model-description">
                <strong>방법론:</strong> 화재 시뮬레이션 + 3대 Pivot 시장(VR 교육, 로봇 SLAM, 게임 VFX) 동시 진출 시나리오
              </p>
              <p className="model-description">
                <strong>계산:</strong>
                <br/>• 2026년: PBD 시장 점유 + VR 교육 시장 초기 진입(50M) + Physical AI 상용화(100M) = 1,200M
                <br/>• 2027년: 게임 VFX 시장 진출(EmberGen 수준 200M) + 로봇 SLAM R&D(150M) + PBD 시장 확대 = 2,300M
                <br/>• 2028년: VR 교육 본격화(800M) + 게임 VFX 성장(500M) + PBD 글로벌 확대(1,500M) + SLAM 상용화(1,000M) = 3,800M
                <br/>• 2029년: VR 교육 확산(1,500M) + 게임 VFX 도약(1,000M) + SLAM 군사/소방 시장(1,500M) + PBD 글로벌(1,800M) = 5,800M
                <br/>• 2030년: 전 시장 성숙기 - VR(2,500M) + 게임 VFX(2,000M) + SLAM(2,500M) + PBD 글로벌 리더(1,500M) = 8,500M
              </p>
              <p className="model-description">
                <strong>가정:</strong> 모든 Pivot 시장에서 성공적 진입 + Physical AI/SLAM 기술 완성 + 글로벌 파트너십 확보 + EmberGen 수준의 게임 시장 점유 + 글로벌 VR 교육 시장 25% 점유
              </p>
            </div>
          </div>

          {/* AI Model References */}
          <div className="ai-references-section">
            <h4 className="references-title">AI 예측 모델 레퍼런스</h4>
            <div className="references-content">
              <div className="reference-item">
                <span className="reference-label">모델 1:</span>
                <span className="reference-text">
                  선형 회귀 분석 (Linear Regression) - 과거 데이터의 추세를 기반으로 미래 값을 예측하는 통계적 기법
                </span>
              </div>
              <div className="reference-item">
                <span className="reference-label">모델 2:</span>
                <span className="reference-text">
                  시장 점유율 기반 예측 모델 (Market Share Growth Model) - Porter's Five Forces 경쟁 분석 + TAM/SAM/SOM 시장 규모 분석 + 고객 획득 시나리오 기반 예측
                </span>
              </div>
              <div className="reference-item">
                <span className="reference-label">모델 3:</span>
                <span className="reference-text">
                  멀티 시장 확장 모델 (Multi-Market Expansion Model) - Ansoff Matrix 다각화 전략 + McKinsey 3 Horizons of Growth + 시장별 성장률 가중 합산
                </span>
              </div>
              <div className="reference-item">
                <span className="reference-label">데이터 출처:</span>
                <span className="reference-text">
                  2023-2025 실제 매출 데이터, 글로벌 VFX 시장 조사 (Markets and Markets, 2024), 건축 시장 PBD 의무화 현황 (NFPA, ICC), 게임 VFX 시장 분석 (EmberGen/JangaFX 벤치마크), 국방 R&D 예산 (DARPA, 2026), 소방 로봇 시장 조사 (MarketsandMarkets, 2024)
                </span>
              </div>
              <div className="reference-note">
                <strong>※ 주의:</strong> 본 예측 모델은 현재 시장 데이터와 합리적 가정을 기반으로 한 시뮬레이션이며, 실제 매출은 시장 환경, 경쟁 상황, 기술 개발 속도 등 다양한 변수에 따라 달라질 수 있습니다.
              </div>
            </div>
          </div>
        </div>
        </div>
      </section>

      {/* Financial Metrics Section */}
      <section className="meteor-section financial-metrics-section">
        <div className="meteor-container">
          <h3 className="section-subtitle">재무 핵심 지표</h3>

          <div className="metrics-grid">
          {/* Unit Economics */}
          <div className="metric-card unit-economics">
            <div className="metric-card-header">
              <h4 className="metric-card-title">Unit Economics</h4>
              <p className="metric-card-subtitle">사업 부문별 수익성</p>
            </div>
            <div className="metric-card-body">
              <div className="unit-items-grid">
                <div className="unit-item primary">
                  <div className="unit-content">
                    <span className="unit-label">시뮬레이션 용역</span>
                    <span className="unit-value">43%</span>
                  </div>
                </div>
                <div className="unit-item secondary">
                  <div className="unit-content">
                    <span className="unit-label">S/W 판매</span>
                    <span className="unit-value">40%</span>
                  </div>
                </div>
                <div className="unit-item tertiary">
                  <div className="unit-content">
                    <span className="unit-label">R&D 용역</span>
                    <span className="unit-value">15%</span>
                  </div>
                </div>
              </div>
              <p className="metric-description">
                평균 프로젝트 규모 80백만원 기준, 직접비(인건비, 컴퓨팅) 제외 후 이익률입니다.
                시뮬레이션 용역과 소프트웨어 판매가 주력 수익원입니다.
              </p>
            </div>
          </div>

          {/* Gross Margin */}
          <div className="metric-card gross-margin">
            <div className="metric-card-header">
              <h4 className="metric-card-title">Gross Margin</h4>
              <p className="metric-card-subtitle">매출총이익률 추이</p>
            </div>
            <div className="metric-card-body">
              <div className="gross-margin-timeline">
                <div className="margin-item past">
                  <span className="margin-year">2023</span>
                  <span className="margin-value negative">-11%</span>
                </div>
                <div className="margin-item past">
                  <span className="margin-year">2024</span>
                  <span className="margin-value">2%</span>
                </div>
                <div className="margin-item current">
                  <span className="margin-year">2025</span>
                  <span className="margin-value positive">20%</span>
                </div>
                <div className="margin-item future">
                  <span className="margin-year">2026</span>
                  <span className="margin-value positive">42%</span>
                </div>
                <div className="margin-item future">
                  <span className="margin-year">2027</span>
                  <span className="margin-value positive">54%</span>
                </div>
                <div className="margin-item future">
                  <span className="margin-year">2028~</span>
                  <span className="margin-value positive">50%+</span>
                </div>
              </div>
              <p className="metric-description">
                <strong>고정비:</strong> 3.4억원/년 (인건비, 사무실, 소프트웨어 라이센스 등)
                <br/>
                <strong>전략:</strong> 2027년 54% 달성 후 50%대 유지를 목표로 합니다.
              </p>
            </div>
          </div>
        </div>

        {/* Market Size - TAM/SAM/SOM */}
        <div className="market-size-section">
          <h4 className="market-size-title">시장 규모 분석 (TAM/SAM/SOM)</h4>
          <div className="market-research-placeholder">
            <div className="research-icon-img">
              <img src="/img/bulc-bg.png" alt="Research" />
            </div>
            <p className="research-text">Researching Now.</p>
          </div>
        </div>

        {/* Exit Strategy */}
        <div className="exit-strategy-section">
          <h4 className="exit-strategy-title">Exit 전략</h4>
          <div className="exit-cards">
            <div className="exit-card secondary">
              <h5 className="exit-card-title">M&A (전략적 인수합병)</h5>
              <p className="exit-timeline"><strong>유력 후보:</strong> G건설 등</p>
              <ul className="exit-milestones">
                <li>국내 대형 건설사 (G건설, S물산, H건설 등)</li>
                <li>글로벌 시뮬레이션 기업 (Autodesk, Ansys 등)</li>
                <li>방재 솔루션 기업 (Honeywell, Siemens 등)</li>
                <li>게임 엔진 기업 (Unity, Epic Games)</li>
              </ul>
              <p className="exit-description">
                독보적인 AI-Physical 시뮬레이션 기술과 검증된 고객 기반을 바탕으로 전략적 M&A 추진. 화재 시뮬레이션 및 PBD 분야에서 전략적 협력 관계를 통해 우선 협상 가능성 보유.
              </p>
            </div>

            <div className="exit-card primary">
              <h5 className="exit-card-title">IPO (기업공개)</h5>
              <p className="exit-timeline"><strong>목표 시기:</strong> 10년 이내 (2033-2035)</p>
              <ul className="exit-milestones">
                <li>2026-2028년: 매출 10억원 달성, 흑자 전환 안정화</li>
                <li>2029-2030년: 매출 30억원, 영업이익률 40%+</li>
                <li>2031-2032년: Pre-IPO 투자 유치, 매출 60억원+</li>
                <li>2033-2035년: KOSDAQ 상장 (목표 시가총액 800억원+)</li>
              </ul>
              <p className="exit-description">
                장기적인 매출 성장과 높은 이익률을 바탕으로 KOSDAQ 상장을 통해 글로벌 시장 확장 자금 확보. 10년의 충분한 성장 기간을 통해 안정적 상장 추진.
              </p>
            </div>
          </div>
          <div className="exit-note">
            <strong>※ 전략:</strong> M&A를 우선 목표로 하며 전략적 협력을 통한 인수합병을 추진합니다. 장기적으로는 10년 내 IPO를 목표로 하되, 시장 상황과 전략적 제휴 기회에 따라 유연하게 대응합니다.
          </div>
        </div>
        </div>
      </section>

      {/* Key Expected Clients Section */}
      <section className="meteor-section clients-section">
        <div className="meteor-container">
          <h3 className="section-subtitle">주요 예상 고객</h3>

          {/* Tier 1 */}
        <div className="tier-row tier-1">
          <div className="tier-label-box">
            <span className="tier-badge">TIER 1</span>
            <h4 className="tier-title">{expectedClients.tier1.title}</h4>
          </div>
          <div className="tier-content">
            <div className="clients-list">
              {expectedClients.tier1.clients.map((client, idx) => (
                <span key={idx} className="client-chip">{client}</span>
              ))}
            </div>
            <div className="tier-metrics">
              <div className="metric-item">
                <span className="metric-label">용역</span>
                <span className="metric-value">{expectedClients.tier1.targets.projects}건</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item">
                <span className="metric-label">BULC 라이센스</span>
                <span className="metric-value">{expectedClients.tier1.targets.bulcLicenses}Copy</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item highlight">
                <span className="metric-label">예상 매출</span>
                <span className="metric-value">{expectedClients.tier1.targets.expectedRevenue}백만원</span>
              </div>
            </div>
          </div>
        </div>

        {/* Tier 2 */}
        <div className="tier-row tier-2">
          <div className="tier-label-box">
            <span className="tier-badge">TIER 2</span>
            <h4 className="tier-title">{expectedClients.tier2.title}</h4>
          </div>
          <div className="tier-content">
            <div className="clients-list">
              {expectedClients.tier2.clients.map((client, idx) => (
                <span key={idx} className="client-chip">{client}</span>
              ))}
            </div>
            <div className="tier-metrics">
              <div className="metric-item">
                <span className="metric-label">용역</span>
                <span className="metric-value">{expectedClients.tier2.targets.projects}건</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item">
                <span className="metric-label">BULC 라이센스</span>
                <span className="metric-value">{expectedClients.tier2.targets.bulcLicenses}Copy</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item highlight">
                <span className="metric-label">예상 매출</span>
                <span className="metric-value">{expectedClients.tier2.targets.expectedRevenue}백만원</span>
              </div>
            </div>
          </div>
        </div>

        {/* Tier 3 */}
        <div className="tier-row tier-3">
          <div className="tier-label-box">
            <span className="tier-badge">TIER 3</span>
            <h4 className="tier-title">{expectedClients.tier3.title}</h4>
          </div>
          <div className="tier-content">
            <div className="clients-list">
              {expectedClients.tier3.clients.map((client, idx) => (
                <span key={idx} className="client-chip">{client}</span>
              ))}
            </div>
            <div className="tier-metrics">
              <div className="metric-item">
                <span className="metric-label">용역</span>
                <span className="metric-value">{expectedClients.tier3.targets.projects}건</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item">
                <span className="metric-label">BULC 라이센스</span>
                <span className="metric-value">{expectedClients.tier3.targets.bulcLicenses}Copy</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item highlight">
                <span className="metric-label">예상 매출</span>
                <span className="metric-value">{expectedClients.tier3.targets.expectedRevenue}백만원</span>
              </div>
            </div>
          </div>
        </div>

        {/* Tier EDU */}
        <div className="tier-row tier-edu">
          <div className="tier-label-box">
            <span className="tier-badge">TIER EDU</span>
            <h4 className="tier-title">{expectedClients.tierEdu.title}</h4>
          </div>
          <div className="tier-content">
            <div className="edu-description">
              <p className="edu-subtitle">{expectedClients.tierEdu.description}</p>
            </div>
            <div className="tier-metrics">
              <div className="metric-item">
                <span className="metric-label">무료 라이센스</span>
                <span className="metric-value">{expectedClients.tierEdu.targets.freeLicenses}Copy</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item">
                <span className="metric-label">VR 교육 - 교육청 과제</span>
                <span className="metric-value">{expectedClients.tierEdu.targets.vrEducation.educationOffice.projects}건 ({expectedClients.tierEdu.targets.vrEducation.educationOffice.revenue}백만원)</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item">
                <span className="metric-label">{expectedClients.tierEdu.targets.vrEducation.corporate.clients}</span>
                <span className="metric-value">{expectedClients.tierEdu.targets.vrEducation.corporate.revenue}백만원</span>
              </div>
              <div className="metric-divider">|</div>
              <div className="metric-item highlight">
                <span className="metric-label">총 예상 매출</span>
                <span className="metric-value">{expectedClients.tierEdu.targets.totalRevenue}백만원</span>
              </div>
            </div>
          </div>
        </div>
        </div>
      </section>

      {/* Marketing Strategy Timeline Section */}
      <section className="meteor-section marketing-section">
        <div className="meteor-container">
          <h3 className="section-subtitle">2026 마케팅 전략 타임라인</h3>
          <p className="section-intro">
            체계적인 마케팅 계획을 통해 글로벌 시장을 공략합니다
          </p>

        {/* Category Legend */}
        <div className="timeline-legend">
          <div className="legend-item">
            <span className="legend-badge digital">SEO / GEO</span>
            <span className="legend-desc">디지털 마케팅</span>
          </div>
          <div className="legend-item">
            <span className="legend-badge exhibition">전시회</span>
            <span className="legend-desc">국제 전시 참가</span>
          </div>
          <div className="legend-item">
            <span className="legend-badge academic">학회</span>
            <span className="legend-desc">논문 투고/발표</span>
          </div>
          <div className="legend-item">
            <span className="legend-badge expert">전문가 모임</span>
            <span className="legend-desc">네트워킹</span>
          </div>
          <div className="legend-item">
            <span className="legend-badge education">교육용</span>
            <span className="legend-desc">대학 라이센스</span>
          </div>
        </div>

        {/* Timeline Cards */}
        <div className="timeline-cards">
          {marketingTimelineByMonth.map((monthData, monthIndex) => (
            <div key={monthIndex} className="month-card">
              <div className="month-header">
                <h4 className="month-title">{monthData.month}</h4>
              </div>
              <div className="activities-grid">
                {monthData.activities.map((activity, actIndex) => {
                  const activityId = `${monthData.month}-${actIndex}`;
                  const isExpanded = expandedActivity === activityId;

                  return (
                    <div key={actIndex} className={`activity-item ${activity.type}`}>
                      <div
                        className="activity-clickable"
                        onClick={() => toggleActivity(activityId)}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="activity-header">
                          <div className="activity-category">
                            <span className={`category-badge ${activity.type}`}>{activity.category}</span>
                          </div>
                          <div className="activity-content">{activity.activity}</div>
                          {activity.details && (
                            <div className="activity-toggle">
                              <svg
                                className={`toggle-icon ${isExpanded ? 'expanded' : ''}`}
                                width="20"
                                height="20"
                                viewBox="0 0 24 24"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                              >
                                <path
                                  d="M6 9L12 15L18 9"
                                  stroke="currentColor"
                                  strokeWidth="2"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                              </svg>
                            </div>
                          )}
                        </div>
                      </div>
                      {isExpanded && activity.details && (
                        <div className="activity-details">
                          {activity.details}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
        </div>
      </section>

      {/* Competitive Analysis Section */}
      <section className="meteor-section marketing-section">
        <div className="meteor-container">
          <h3 className="section-subtitle">경쟁사 분석</h3>
        <p className="section-intro">
          글로벌 화재 시뮬레이션 시장의 주요 경쟁사 현황
        </p>

        {/* Fire Simulation Competitors */}
        <div className="competitor-section">
          <h5 className="competitor-category-title">화재 피난 시뮬레이션 경쟁사</h5>
          <div className="competitors-table-wrapper">
            <table className="competitors-table">
              <thead>
                <tr>
                  <th>기업명</th>
                  <th>제품명</th>
                  <th>국가</th>
                  <th>연간 매출</th>
                  <th>시장 위치</th>
                  <th>강점</th>
                </tr>
              </thead>
              <tbody>
                {fireSimCompetitors.map((competitor, idx) => (
                  <tr key={idx}>
                    <td className="company-name">{competitor.company}</td>
                    <td>{competitor.products}</td>
                    <td>{competitor.country}</td>
                    <td>{competitor.annualRevenue}</td>
                    <td className="market-share">{competitor.marketShare}</td>
                    <td>{competitor.strengths}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Game VFX Competitors */}
        <div className="competitor-section">
          <h5 className="competitor-category-title">게임 특수효과(VFX) 경쟁사</h5>
          <div className="competitors-table-wrapper">
            <table className="competitors-table">
              <thead>
                <tr>
                  <th>기업명</th>
                  <th>제품명</th>
                  <th>국가</th>
                  <th>인원</th>
                  <th>연간 매출</th>
                  <th>시장 위치</th>
                  <th>강점</th>
                  <th>가격대</th>
                </tr>
              </thead>
              <tbody>
                {gameVFXCompetitors.map((competitor, idx) => (
                  <tr key={idx}>
                    <td className="company-name">{competitor.company}</td>
                    <td>{competitor.products}</td>
                    <td>{competitor.country}</td>
                    <td>{competitor.employees}</td>
                    <td>{competitor.annualRevenue}</td>
                    <td className="market-share">{competitor.marketShare}</td>
                    <td>{competitor.strengths}</td>
                    <td>{competitor.pricing}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="market-analysis-section">
          <h4 className="market-analysis-title">시장 분석 및 전망 (화재 시뮬레이션 PBD 기준)</h4>

          <div className="market-grid">
            <div className="market-card">
              <div className="market-card-header">
                <span className="market-icon">🏗️</span>
                <h5>글로벌 건축 시장</h5>
              </div>
              <p className="market-value">{marketAnalysis.globalConstruction}</p>
              <p className="market-desc">전세계 건축 산업 규모</p>
            </div>

            <div className="market-card">
              <div className="market-card-header">
                <span className="market-icon">📐</span>
                <h5>설계 비용 비율</h5>
              </div>
              <p className="market-value">{marketAnalysis.designCost}</p>
              <p className="market-desc">건축비 대비 설계비</p>
            </div>

            <div className="market-card">
              <div className="market-card-header">
                <span className="market-icon">🔥</span>
                <h5>화재 시뮬레이션</h5>
              </div>
              <p className="market-value">{marketAnalysis.fireSimulationRatio}</p>
              <p className="market-desc">설계비 대비 비율</p>
            </div>

            <div className="market-card highlight">
              <div className="market-card-header">
                <span className="market-icon">💰</span>
                <h5>시장 규모</h5>
              </div>
              <p className="market-value">{marketAnalysis.marketSize}</p>
              <p className="market-desc">글로벌 화재 시뮬레이션 시장</p>
            </div>
          </div>

          <div className="pdb-mandatory-section">
            <h5 className="pdb-title">PBD (Performance-Based Design) 의무화 현황</h5>
            <div className="pdb-grid">
              {marketAnalysis.pdbMandatory.map((item, idx) => (
                <div key={idx} className="pdb-card">
                  <div className="pdb-country">{item.country}</div>
                  <div className="pdb-status">{item.status}</div>
                </div>
              ))}
            </div>
            <div className="growth-trend">
              <strong>📈 {marketAnalysis.growthTrend}</strong>
            </div>
          </div>

          {/* Pivot Markets Section */}
          <div className="pivot-markets-section">
            <h4 className="pivot-section-title">Pivot 시장 분석</h4>

            {/* Game VFX Asset Market */}
            <div className="pivot-market-card">
              <div className="pivot-header">
                <h5 className="pivot-title">{gameMarketAnalysis.title}</h5>
                <p className="pivot-description">{gameMarketAnalysis.description}</p>
              </div>

              <div className="pivot-content-grid">
                <div className="pivot-info-section">
                  <div className="info-label">주요 경쟁 제품</div>
                  <div className="info-items">
                    {gameMarketAnalysis.competitors.map((item, idx) => (
                      <div key={idx} className="characteristic-item">{item}</div>
                    ))}
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">{gameMarketAnalysis.marketData.title}</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">글로벌 VFX</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.globalVFX}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">게임 비중</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.gameVFXShare}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">통합 시장</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.animationGame}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">Simulation FX</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.simulationFX}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">실시간 툴 성장</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.realTimeGrowth}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">성장률</span>
                      <span className="info-value">{gameMarketAnalysis.marketData.growthRate}</span>
                    </div>
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">{gameMarketAnalysis.gameEngineAdoption.title}</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">엔진 점유율</span>
                      <span className="info-value">{gameMarketAnalysis.gameEngineAdoption.unityUnreal}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">UE5 채택</span>
                      <span className="info-value">{gameMarketAnalysis.gameEngineAdoption.unrealEngine5}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">매출 증대 효과</span>
                      <span className="info-value">{gameMarketAnalysis.gameEngineAdoption.assetQuality}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">제작 속도</span>
                      <span className="info-value">{gameMarketAnalysis.gameEngineAdoption.productionSpeed}</span>
                    </div>
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">BULC 경쟁 우위</div>
                  <div className="info-items">
                    {gameMarketAnalysis.bulcAdvantage.map((item, idx) => (
                      <div key={idx} className="characteristic-item">{item}</div>
                    ))}
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">가격 구조</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">Asset Pack</span>
                      <span className="info-value">{gameMarketAnalysis.pricing.assetPack}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">Plugin</span>
                      <span className="info-value">{gameMarketAnalysis.pricing.pluginLicense}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">Enterprise</span>
                      <span className="info-value">{gameMarketAnalysis.pricing.enterpriseLicense}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="pivot-metrics-row">
                <div className="pivot-metric">
                  <div className="metric-label-text">총 시장 규모</div>
                  <div className="metric-value-text">{gameMarketAnalysis.totalMarket}</div>
                </div>
                <div className="pivot-metric highlight-metric">
                  <div className="metric-label-text">BULC 목표</div>
                  <div className="metric-value-text">{gameMarketAnalysis.targetRevenue}</div>
                </div>
              </div>

              <div className="pivot-conclusion">
                {gameMarketAnalysis.marketPotential}
              </div>
            </div>

            {/* VR Safety Education Market */}
            <div className="pivot-market-card">
              <div className="pivot-header">
                <h5 className="pivot-title">{vrMarketAnalysis.title}</h5>
                <p className="pivot-description">{vrMarketAnalysis.description}</p>
              </div>

              <div className="pivot-content-grid">
                <div className="pivot-info-section">
                  <div className="info-label">가격 구조</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">사업장 교육</span>
                      <span className="info-value">{vrMarketAnalysis.businessPrice}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">학교 교육</span>
                      <span className="info-value">{vrMarketAnalysis.schoolPrice}</span>
                    </div>
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">시장 특성</div>
                  <div className="info-items">
                    {vrMarketAnalysis.marketCharacteristics.map((item, idx) => (
                      <div key={idx} className="characteristic-item">{item}</div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="pivot-metrics-row">
                <div className="pivot-metric">
                  <div className="metric-label-text">시장 규모 추정</div>
                  <div className="metric-value-text">{vrMarketAnalysis.estimatedMarket}</div>
                </div>
                <div className="pivot-metric">
                  <div className="metric-label-text">목표 점유율</div>
                  <div className="metric-value-text">{vrMarketAnalysis.targetShare}</div>
                </div>
                <div className="pivot-metric highlight-metric">
                  <div className="metric-label-text">목표 매출</div>
                  <div className="metric-value-text">{vrMarketAnalysis.targetRevenue}</div>
                </div>
              </div>

              <div className="pivot-conclusion">
                {vrMarketAnalysis.marketPotential}
              </div>
            </div>

            {/* Extreme Environment Robot Market */}
            <div className="pivot-market-card">
              <div className="pivot-header">
                <h5 className="pivot-title">{robotMarketAnalysis.title}</h5>
                <p className="pivot-description">{robotMarketAnalysis.description}</p>
              </div>

              <div className="pivot-content-grid">
                <div className="pivot-info-section">
                  <div className="info-label">적용 분야</div>
                  <div className="info-items">
                    {robotMarketAnalysis.applications.map((item, idx) => (
                      <div key={idx} className="characteristic-item">{item}</div>
                    ))}
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">{robotMarketAnalysis.firefightingMarket.title}</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">글로벌 시장</span>
                      <span className="info-value">{robotMarketAnalysis.firefightingMarket.globalSize}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">예산 비중</span>
                      <span className="info-value">{robotMarketAnalysis.firefightingMarket.budgetRatio}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">SLAM 시장</span>
                      <span className="info-value">{robotMarketAnalysis.firefightingMarket.slamMarket}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">성장률</span>
                      <span className="info-value">{robotMarketAnalysis.firefightingMarket.growthRate}</span>
                    </div>
                  </div>
                </div>

                <div className="pivot-info-section">
                  <div className="info-label">{robotMarketAnalysis.militaryMarket.title}</div>
                  <div className="info-items">
                    <div className="info-item">
                      <span className="info-key">미국 국방 예산</span>
                      <span className="info-value">{robotMarketAnalysis.militaryMarket.pentagonBudget}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">UAV 예산</span>
                      <span className="info-value">{robotMarketAnalysis.militaryMarket.droneBudget}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">한국 R&D</span>
                      <span className="info-value">{robotMarketAnalysis.militaryMarket.rdBudget}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">시장 전망</span>
                      <span className="info-value">{robotMarketAnalysis.militaryMarket.marketProjection}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-key">SLAM 비용 비율</span>
                      <span className="info-value">{robotMarketAnalysis.militaryMarket.slamCostRatio}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="pivot-metrics-row">
                <div className="pivot-metric">
                  <div className="metric-label-text">총 시장 규모</div>
                  <div className="metric-value-text">{robotMarketAnalysis.totalMarket}</div>
                </div>
                <div className="pivot-metric highlight-metric">
                  <div className="metric-label-text">BULC 목표</div>
                  <div className="metric-value-text">{robotMarketAnalysis.targetRevenue}</div>
                </div>
              </div>

              <div className="pivot-conclusion">
                {robotMarketAnalysis.marketPotential}
              </div>
            </div>
          </div>
        </div>
        </div>
      </section>
    </>
  );
};

export default MeteorInvesting;
