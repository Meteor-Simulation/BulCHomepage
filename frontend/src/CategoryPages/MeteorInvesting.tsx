import React from 'react';
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
  // Revenue data (in millions KRW)
  const revenueData = {
    labels: ['2023', '2024', '2025', '2026', '2027', '2028'],
    datasets: [
      {
        label: '실제 매출',
        data: [450, 680, 920, null, null, null],
        borderColor: '#C4320A',
        backgroundColor: 'rgba(196, 50, 10, 0.1)',
        borderWidth: 3,
        pointRadius: 6,
        pointHoverRadius: 8,
        tension: 0.4,
        fill: true,
      },
      {
        label: 'AI 매출 예측',
        data: [null, null, 920, 1250, 1680, 2200],
        borderColor: '#2c5aa0',
        backgroundColor: 'rgba(44, 90, 160, 0.1)',
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
        text: 'Revenue Growth & AI Forecast (단위: 백만원)',
        font: {
          size: 18,
          weight: 'bold',
        },
        padding: 20,
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        titleFont: {
          size: 14,
          weight: 'bold',
        },
        bodyFont: {
          size: 13,
        },
        callbacks: {
          label: function(context: any) {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            if (context.parsed.y !== null) {
              label += context.parsed.y.toLocaleString() + '백만원';
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
            size: 12,
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
            size: 12,
            weight: 'bold',
          }
        }
      }
    }
  };

  // Key Expected Clients - Tier system
  const expectedClients = {
    tier1: {
      title: 'Tier 1 - Strategic Partners',
      clients: ['S전자', 'G건설', 'K기술원', 'H현대'],
      targets: {
        projects: 8,
        bulcLicenses: 15,
        expectedRevenue: 650
      }
    },
    tier2: {
      title: 'Tier 2 - Key Clients',
      clients: ['I교통공사', 'L화학', 'D데이터센터', 'P물류'],
      targets: {
        projects: 12,
        bulcLicenses: 25,
        expectedRevenue: 480
      }
    },
    tier3: {
      title: 'Tier 3 - Growing Accounts',
      clients: ['J중공업', 'N네트웍스', 'A안전', 'B방재'],
      targets: {
        projects: 20,
        bulcLicenses: 40,
        expectedRevenue: 320
      }
    }
  };

  // AI Marketing Strategies
  const aiStrategies = [
    {
      icon: '🎯',
      title: 'AI 기반 타겟 분석',
      description: '머신러닝을 활용한 잠재 고객 예측 및 맞춤형 접근 전략 수립'
    },
    {
      icon: '🤖',
      title: '자동화된 콘텐츠 생성',
      description: 'AI가 생성하는 기술 백서, 케이스 스터디 및 마케팅 자료'
    },
    {
      icon: '📊',
      title: '실시간 시장 인사이트',
      description: '빅데이터 분석을 통한 화재 안전 시장 트렌드 모니터링'
    },
    {
      icon: '💡',
      title: '지능형 리드 스코어링',
      description: 'AI 알고리즘으로 고가치 영업 기회 우선순위 선정'
    },
    {
      icon: '🔄',
      title: '챗봇 기반 고객 응대',
      description: '24/7 AI 챗봇을 통한 즉각적인 기술 문의 대응'
    },
    {
      icon: '📈',
      title: '예측 마케팅 캠페인',
      description: 'AI 예측 모델링을 통한 최적 마케팅 타이밍 및 채널 선정'
    }
  ];

  return (
    <div className="investing-container">
      {/* Revenue Chart Section */}
      <section className="chart-section">
        <div className="chart-wrapper">
          <Line data={revenueData} options={chartOptions} />
        </div>
      </section>

      {/* Major Deliveries Section */}
      <section className="deliveries-section">
        <h3 className="section-subtitle">주요 납품 실적</h3>
        <div className="deliveries-grid">
          {majorDeliveries.map((delivery, index) => (
            <div key={index} className="delivery-card">
              <div className="delivery-rank">#{index + 1}</div>
              <div className="delivery-info">
                <h4 className="delivery-name">{delivery.name}</h4>
                <div className="delivery-revenue">
                  <span className="revenue-amount">{delivery.revenue.toLocaleString()}</span>
                  <span className="revenue-unit">백만원</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Future Goals Section */}
      <section className="goals-section">
        <h3 className="section-subtitle">향후 목표</h3>

        <div className="tier-container">
          {/* Tier 1 */}
          <div className="tier-card tier-1">
            <div className="tier-header">
              <span className="tier-badge">TIER 1</span>
              <h4 className="tier-title">{futureGoals.tier1.title}</h4>
            </div>
            <div className="tier-clients">
              {futureGoals.tier1.clients.map((client, idx) => (
                <span key={idx} className="client-tag">{client}</span>
              ))}
            </div>
            <div className="tier-targets">
              <div className="target-item">
                <span className="target-label">용역 프로젝트</span>
                <span className="target-value">{futureGoals.tier1.targets.projects} 건</span>
              </div>
              <div className="target-item">
                <span className="target-label">BULC 라이센스</span>
                <span className="target-value">{futureGoals.tier1.targets.bulcLicenses} Copy</span>
              </div>
              <div className="target-item highlight">
                <span className="target-label">예상 매출</span>
                <span className="target-value">{futureGoals.tier1.targets.expectedRevenue.toLocaleString()}M</span>
              </div>
            </div>
          </div>

          {/* Tier 2 */}
          <div className="tier-card tier-2">
            <div className="tier-header">
              <span className="tier-badge">TIER 2</span>
              <h4 className="tier-title">{futureGoals.tier2.title}</h4>
            </div>
            <div className="tier-clients">
              {futureGoals.tier2.clients.map((client, idx) => (
                <span key={idx} className="client-tag">{client}</span>
              ))}
            </div>
            <div className="tier-targets">
              <div className="target-item">
                <span className="target-label">용역 프로젝트</span>
                <span className="target-value">{futureGoals.tier2.targets.projects} 건</span>
              </div>
              <div className="target-item">
                <span className="target-label">BULC 라이센스</span>
                <span className="target-value">{futureGoals.tier2.targets.bulcLicenses} Copy</span>
              </div>
              <div className="target-item highlight">
                <span className="target-label">예상 매출</span>
                <span className="target-value">{futureGoals.tier2.targets.expectedRevenue.toLocaleString()}M</span>
              </div>
            </div>
          </div>

          {/* Tier 3 */}
          <div className="tier-card tier-3">
            <div className="tier-header">
              <span className="tier-badge">TIER 3</span>
              <h4 className="tier-title">{futureGoals.tier3.title}</h4>
            </div>
            <div className="tier-clients">
              {futureGoals.tier3.clients.map((client, idx) => (
                <span key={idx} className="client-tag">{client}</span>
              ))}
            </div>
            <div className="tier-targets">
              <div className="target-item">
                <span className="target-label">용역 프로젝트</span>
                <span className="target-value">{futureGoals.tier3.targets.projects} 건</span>
              </div>
              <div className="target-item">
                <span className="target-label">BULC 라이센스</span>
                <span className="target-value">{futureGoals.tier3.targets.bulcLicenses} Copy</span>
              </div>
              <div className="target-item highlight">
                <span className="target-label">예상 매출</span>
                <span className="target-value">{futureGoals.tier3.targets.expectedRevenue.toLocaleString()}M</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default MeteorInvesting;
