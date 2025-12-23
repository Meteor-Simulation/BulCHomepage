import React from 'react';
import './MeteorPages.css';

const BulCSolutions: React.FC = () => {
  return (
    <>
    <section className="meteor-section meteor-solutions">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">CORE FEATURES</div>
          <h2 className="section-title">BULC 핵심 기능</h2>
          <p className="section-description">
            AI 기반 자동화와 직관적인 인터페이스로 누구나 쉽게 전문적인 화재 시뮬레이션을 수행할 수 있습니다.
          </p>
        </div>

        <div className="solutions-grid">
          <div className="solution-card">
            <h3 className="solution-title">화재-피난 시뮬레이션</h3>
            <p className="solution-description">
              가시도, 온도, 일산화탄소, 열방출률, 유동 패턴 등 핵심 지표를 다각도로 분석하여 화재 위험을 정확하게 평가합니다. 다양한 EVAC 모델을 이용해서 다층 건물의 대피 경로와 탈출 시간을 정확하게 계산합니다.
            </p>
            <div className="solution-video">
              <iframe
                src="https://www.youtube.com/embed/SUwtGhU9mbc"
                title="화재-피난 시뮬레이션"
                allow="autoplay; encrypted-media"
                allowFullScreen
              ></iframe>
            </div>
            <div className="solution-tags">
              <span className="tag">초고층</span>
              <span className="tag">터널</span>
              <span className="tag">데이터센터</span>
              <span className="tag">물류창고</span>
              <span className="tag">ESS/배터리</span>
              <span className="tag">EVAC</span>
            </div>
          </div>

          <div className="solution-card">
            <h3 className="solution-title">Drag & Drop</h3>
            <p className="solution-description">
              복잡한 코딩 없이 마우스 드래그만으로 건물 구조, 화재원, 센서 등을 직관적으로 배치하고 시뮬레이션을 설정할 수 있습니다.
            </p>
            <div className="solution-video">
              <iframe
                src="https://www.youtube.com/embed/KwZUWgnw540"
                title="Drag & Drop"
                allow="autoplay; encrypted-media"
                allowFullScreen
              ></iframe>
            </div>
            <div className="solution-tags">
              <span className="tag">직관적 UI</span>
              <span className="tag">빠른 설정</span>
              <span className="tag">3D 미리보기</span>
              <span className="tag">실시간 편집</span>
            </div>
          </div>

          <div className="solution-card">
            <h3 className="solution-title">Fire DB</h3>
            <p className="solution-description">
              실험 기반 검증된 화재 데이터베이스를 통해 다양한 가연물의 연소 특성을 정확하게 반영한 시뮬레이션을 수행합니다.
            </p>
            <div className="solution-video">
              <iframe
                src="https://www.youtube.com/embed/KwZUWgnw540"
                title="Fire DB"
                allow="autoplay; encrypted-media"
                allowFullScreen
              ></iframe>
            </div>
            <div className="solution-tags">
              <span className="tag">실험 데이터</span>
              <span className="tag">배터리 화재</span>
              <span className="tag">전기차</span>
              <span className="tag">ESS</span>
            </div>
          </div>

          <div className="solution-card">
            <h3 className="solution-title">FDS-AmgX GPU 엔진(Beta)</h3>
            <p className="solution-description">
              GPU를 이용해서 대형 건축물과 공간을 실시간으로 계산하는 엔진입니다. 현재 데이터 전송 최적화 중입니다.
            </p>
            <div className="architecture-diagram">
              <pre>
{`┌─────────────────────────────────────────────────────────────┐
│                    FDS Simulation (Fortran)                 │
│                         main.f90                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                 Pressure Solver (pres.f90)                  │
│                                                             │
│    ┌─────────────┐                    ┌─────────────┐       │
│    │  CPU Path   │                    │  GPU Path   │       │
│    │ (FFT/ULMAT) │                    │   (AmgX)    │       │
│    └─────────────┘                    └──────┬──────┘       │
└──────────────────────────────────────────────┼──────────────┘
                                               │
                          ┌────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Fortran Interface (amgx_fortran.f90)           │
│                      ISO_C_BINDING                          │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                C Wrapper (amgx_c_wrapper.c)                 │
│           Zone Management, Matrix Conversion                │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    NVIDIA AmgX Library                      │
│              FGMRES Solver + AMG Preconditioner             │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      NVIDIA GPU (CUDA)                      │
└─────────────────────────────────────────────────────────────┘`}
              </pre>
            </div>
            <div className="solution-tags">
              <span className="tag">GPU</span>
              <span className="tag">실시간</span>
              <span className="tag">고성능</span>
              <span className="tag">Beta</span>
            </div>
            <div className="opensource-link">
              Open source available: <a href="https://github.com/using76/GPU-FDS-AMGX" target="_blank" rel="noopener noreferrer">https://github.com/using76/GPU-FDS-AMGX</a>
            </div>
          </div>

        </div>
      </div>
    </section>
    </>
  );
};

export default BulCSolutions;
