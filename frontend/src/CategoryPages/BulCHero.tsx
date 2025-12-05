import React from 'react';
import './MeteorPages.css';

const BulCHero: React.FC = () => {
  return (
    <>
      {/* HERO SECTION */}
      <section className="bulc-hero">
        <div className="bulc-hero-container">
          <div className="hero-eyebrow">AI FOR GOOD · SIMULATION FOR SAFETY</div>
          <h1 className="hero-title">
            화재를 예측하고<br/>
            <span className="highlight">생명을 구합니다</span>
          </h1>
          <p className="hero-subtitle">
            BULC는 AI 기반 화재 시뮬레이션 프로그램입니다. 복잡한 전문 지식 없이도 누구나 쉽고 정확하게 화재를 예측하여 안전을 확보할 수 있습니다.
          </p>
          <div className="hero-actions">
            <div className="btn-wrapper">
              <a
                href="https://msimul.sharepoint.com/:f:/s/msteams_8c91f3-2/EtNitiqwxNhEv4gcjBVUaWMBGqIY1zxNdNOwl4IUMSGxwg?e=ENEjWr"
                className="btn-primary"
                target="_blank"
                rel="noopener noreferrer"
              >
                무료 다운로드
              </a>
              <span className="btn-subtitle">30 Days Free</span>
            </div>
            <button className="btn-secondary">구매 및 상담</button>
          </div>
        </div>
      </section>

      {/* VIDEO SECTION */}
      <section className="bulc-video-section">
        <div className="video-wrapper">
          <iframe
            src="https://www.youtube.com/embed/TuIETV5L_IE?autoplay=1&mute=1&controls=1&loop=1&playlist=TuIETV5L_IE&rel=0"
            title="BULC Intro Video"
            allow="autoplay; encrypted-media"
            allowFullScreen
          ></iframe>
        </div>
      </section>

      {/* STATS SECTION */}
      <section className="bulc-stats-section">
        <div className="meteor-container">
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
      </section>
    </>
  );
};

export default BulCHero;
