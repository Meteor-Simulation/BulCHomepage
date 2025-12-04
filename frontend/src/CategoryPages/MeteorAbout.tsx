import React from 'react';
import './MeteorPages.css';

const MeteorAbout: React.FC = () => {
  return (
    <section className="meteor-section meteor-about">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">WHY BULC</div>
          <h2 className="section-title">왜 BULC인가</h2>
          <p className="section-description">
            화재 시뮬레이션을 누구나 쉽고 정확하게 할 수 있도록 만들어,<br />
            더 많은 생명과 재산을 보호합니다.
          </p>
        </div>

        <div className="value-grid">
          <div className="value-card">
            <span className="value-number">01</span>
            <h3 className="value-title">저렴한 비용</h3>
            <p className="value-description">
              기존 고가의 시뮬레이션 서비스 대비 90% 이상 비용을 절감하여, 중소기업과 공공기관도 부담 없이 화재 안전 검토를 수행할 수 있습니다.
            </p>
          </div>
          <div className="value-card">
            <span className="value-number">02</span>
            <h3 className="value-title">쉬운 사용</h3>
            <p className="value-description">
              복잡한 CFD 지식 없이도 Drag & Drop 방식과 AI 자동화로 누구나 쉽게 화재 시뮬레이션을 실행할 수 있습니다.
            </p>
          </div>
          <div className="value-card">
            <span className="value-number">03</span>
            <h3 className="value-title">생명 보호</h3>
            <p className="value-description">
              정확한 화재 예측으로 피난 계획을 수립하고 제연 시스템을 최적화하여, 화재 발생 시 인명 피해를 최소화합니다.
            </p>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="stats-section">
          <div className="stats-grid">
            <div className="stat-item">
              <div className="stat-number">50%</div>
              <div className="stat-label">비용 절감</div>
              <div className="stat-description">기존 대비 시뮬레이션 비용 대폭 절감</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">10×</div>
              <div className="stat-label">빠른 작업</div>
              <div className="stat-description">AI 기반 자동화로 작업 시간 단축</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">95%</div>
              <div className="stat-label">정확도</div>
              <div className="stat-description">FDS 기반 검증된 시뮬레이션</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">1000+</div>
              <div className="stat-label">프로젝트</div>
              <div className="stat-description">시뮬레이션으로 보호된 시설</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default MeteorAbout;
