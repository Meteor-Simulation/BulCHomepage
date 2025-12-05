import React from 'react';
import './HeroSection.css';

const HeroSection: React.FC = () => {
  return (
    <section className="hero-section" /* vr-section */>
      {/* Background Decorations */}
      <div className="hero-blur hero-blur-1"></div>
      <div className="hero-blur hero-blur-2"></div>
      <div className="hero-blur hero-blur-3"></div>

      <div className="hero-content">
        {/* Left Content */}
        <div className="hero-left">
          <span className="hero-badge">
            <svg className="badge-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M13 10V3L4 14H11V21L20 10H13Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            Next Gen Safety Training
          </span>

          <h1 className="hero-title">
            화재 발생 시<br />
            당신의 <span className="text-highlight">생존 확률</span>은<br />
            얼마나 될까요?
          </h1>

          <p className="hero-description">
          실제 화재 데이터(BULC) 기반의 90% 이상 정밀한 연기 시뮬레이션. 
          책으로 배우는 안전이 아닌, 몸이 기억하는 생존 본능을 깨우세요.
          </p>

          <div className="hero-buttons">
            <button className="btn-primary">
              <svg className="btn-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M8 17L12 21L16 17M8 7L12 3L16 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M12 3V21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              연기 시뮬레이션 체험
            </button>
            <button className="btn-secondary">
              <svg className="btn-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <polygon points="5,3 19,12 5,21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
              </svg>
              데모 영상 보기
            </button>
          </div>
        </div>

        {/* Right Content - Hero Image */}
        <div className="hero-right">
          <div className="hero-image-container">
            <div className="hero-image-glow"></div>
            <img
              src="/img/VR1.png"
              alt="VR 화재 시뮬레이션 체험"
              className="hero-image"
            />

            {/* Floating Stats Card */}
            <div className="stats-card">
              <div className="stats-icon-wrapper">
                <svg className="stats-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M23 6L13.5 15.5L8.5 10.5L1 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M17 6H23V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <div className="stats-content">
                <div className="stats-value">275%</div>
                <div className="stats-label">자신감 상승 효과</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default HeroSection;
