import React from 'react';

interface BulCIntroProps {
  onNavigate: (menu: string) => void;
}

const BulCIntro: React.FC<BulCIntroProps> = ({ onNavigate }) => (
  <div className="bulc-intro-section">
    <h1 className="intro-headline">
      <span className="highlight">화재 시뮬레이션</span>의<br />
      압도적 <span className="highlight">속도</span>와<br />
      정확한 <span className="highlight">예측</span>
    </h1>
    <div className="intro-description">
      <p>Fire-AmgX GPU 가속으로 기존 FDS 대비 10배 이상 빠른 데이터 생성.</p>
      <p>Physical AI PINN/PIDON 기반으로 1초 내 실시간 화재 확산 예측을 실현합니다.</p>
    </div>
    <div className="intro-buttons-grid">
      <button className="intro-btn" onClick={() => onNavigate('bulc')}>
        BULC
      </button>
      <button className="intro-btn" onClick={() => onNavigate('ai-agent')}>
        AI Agent
      </button>
      <button className="intro-btn" onClick={() => onNavigate('tutorial')}>
        Tutorial
      </button>
      <button className="intro-btn" onClick={() => onNavigate('download')}>
        Download
      </button>
    </div>
  </div>
);

export default BulCIntro;
