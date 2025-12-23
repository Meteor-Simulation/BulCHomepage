import React from 'react';
import './MeteorPages.css';

const MeteorAbout: React.FC = () => {
  return (
    <section className="meteor-section meteor-about">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">METEOR SIMULATION</div>
          <h2 className="section-title">Meteor Simulation 소개</h2>
          <p className="section-description">
            화재-피난 그리고 소방전문 연구를 통해<br />
            보다 안전한 사회를 만들어 갑니다.
          </p>
        </div>

        <div className="value-grid">
          <div className="value-card">
            <span className="value-number">01</span>
            <h3 className="value-title">소방 전문 지식</h3>
            <p className="value-description">
              화재 피난 소방 전문 지식을 바탕으로 실질적이고 효과적인 안전 솔루션을 제공합니다. 오랜 연구와 경험을 통해 축적된 전문성으로 최적의 화재 안전 환경을 구축합니다.
            </p>
          </div>
          <div className="value-card">
            <span className="value-number">02</span>
            <h3 className="value-title">소방 온톨로지 전문지식</h3>
            <p className="value-description">
              AI, 법, S/W, 공학적 지식은 아주 복잡한 연관 관계를 전문적으로 정립하여 연구 개발에 가속도를 높입니다. 체계적인 지식 구조화로 혁신적인 소방 기술을 개발합니다.
            </p>
          </div>
          <div className="value-card">
            <span className="value-number">03</span>
            <h3 className="value-title">S/W, AI 기술 융합</h3>
            <p className="value-description">
              정확한 소방지식에 AI를 이용해서 자동화 최적화 미래 기술을 소방 화재 피난 분야에 접목하였습니다. 첨단 기술과 전문 지식의 융합으로 차세대 안전 솔루션을 실현합니다.
            </p>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="stats-section">
          <div className="stats-grid">
            <div className="stat-item">
              <div className="stat-number">15+</div>
              <div className="stat-label">연구 개발 과제</div>
              <div className="stat-description">정부 및 민간 R&D 프로젝트 수행</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">100+</div>
              <div className="stat-label">용역 프로젝트</div>
              <div className="stat-description">화재 안전 컨설팅 및 시뮬레이션</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">20+</div>
              <div className="stat-label">연구 논문</div>
              <div className="stat-description">국제 저널 및 학회 발표 실적</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">10+</div>
              <div className="stat-label">대형 프로젝트</div>
              <div className="stat-description">대기업 공기업 검증된 레퍼런스</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default MeteorAbout;
