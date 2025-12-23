import React from 'react';
import './MeteorPages.css';

const MeteorSolutions: React.FC = () => {
  return (
    <section className="meteor-section meteor-solutions">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">SOLUTIONS</div>
          <h2 className="section-title">우리가 제공하는 솔루션</h2>
          <p className="section-description">
            시뮬레이션·설계·연구를 하나의 파이프라인으로 통합하여 효율적인 화재 안전 관리를 지원합니다.
          </p>
        </div>

        <div className="solutions-grid">
          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">화재-피난 시뮬레이션</h3>
              <span className="completion-badge complete">100%</span>
            </div>
            <p className="solution-description">
              가시도, 온도, 일산화탄소, 열방출률, 유동 패턴 등 핵심 지표를 다각도로 분석하여 화재 위험을 정확하게 평가합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">초고층</span>
              <span className="tag">터널</span>
              <span className="tag">데이터센터</span>
              <span className="tag">물류창고</span>
              <span className="tag">ESS/배터리</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">성능위주 설계 (PBD)</h3>
              <span className="completion-badge complete">100%</span>
            </div>
            <p className="solution-description">
              RSET/ASET 분석, 피난 안전성 평가, 감지·제연·방화구획 성능 검토를 시각화 리포트로 제공합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">피난/가시도</span>
              <span className="tag">온도/독성</span>
              <span className="tag">제연/배연</span>
              <span className="tag">시나리오 기반</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">소방 안전 AI 솔루션 제작</h3>
              <span className="completion-badge complete">100%</span>
            </div>
            <p className="solution-description">
              AI 기반 화재 예측, 위험도 분석, 최적 대응 전략 수립을 통해 지능형 소방 안전 관리 시스템을 구축합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">화재 예측</span>
              <span className="tag">위험도 분석</span>
              <span className="tag">최적화</span>
              <span className="tag">자동화</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">소방 R&D</h3>
              <span className="completion-badge complete">100%</span>
            </div>
            <p className="solution-description">
              신규 소방 제품 및 시스템 평가, 실규모/모형 실험과 시뮬레이션 상호 검증, 과제 문서화를 지원합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">실험-시뮬 연계</span>
              <span className="tag">데이터 대시보드</span>
              <span className="tag">교육/훈련</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">배터리/전기차 화재 솔루션</h3>
              <span className="completion-badge complete">100%</span>
            </div>
            <p className="solution-description">
              <strong>오프가스 기반 배터리 열폭주 모니터링 및 소화시스템 특허등록완료.</strong> 실험 기반 고정밀 열폭주 연소 모델로 배터리 화재의 특수성을 반영한 정확한 시뮬레이션을 제공합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">ESS</span>
              <span className="tag">전기차</span>
              <span className="tag">열폭주</span>
              <span className="tag">화재 감지 특허</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">실제데이터 VR 화재-피난 교육</h3>
              <span className="completion-badge high">80%</span>
            </div>
            <p className="solution-description">
              실제 화재 시뮬레이션 데이터를 기반으로 한 몰입형 VR 교육 콘텐츠로 효과적인 화재 대응 훈련을 제공합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">VR 교육</span>
              <span className="tag">실시간 체험</span>
              <span className="tag">안전 훈련</span>
              <span className="tag">시뮬레이션 연동</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">Physical Foundation AI 모델 제작</h3>
              <span className="completion-badge medium">40%</span>
            </div>
            <p className="solution-description">
              FDS 빅데이터를 PIDON 등으로 학습해서 다양한 상황에서 실시간급 예측을 제공하는 모델을 제작중입니다. <strong>(포항공대, 화재보험협회, KCL 공동 연구중)</strong>
            </p>
            <div className="solution-tags">
              <span className="tag">실시간 예측</span>
              <span className="tag">FDS 대체</span>
              <span className="tag">고속 연산</span>
              <span className="tag">물리 기반 AI</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">소방·군사 연기상황 로봇 SLAM</h3>
              <span className="completion-badge medium">50%</span>
            </div>
            <p className="solution-description">
              시야거리 제한 상황에서 로봇의 경로 및 행동 추론을 지원하는 SLAM 기술로 극한 환경 탐색을 가능하게 합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">SLAM</span>
              <span className="tag">극한 환경</span>
              <span className="tag">경로 추론</span>
              <span className="tag">소방·군사</span>
            </div>
          </div>

          <div className="solution-card">
            <div className="solution-header">
              <h3 className="solution-title">게임용 Realistic Smoke Engine</h3>
              <span className="completion-badge high">90%</span>
            </div>
            <p className="solution-description">
              고품질 연기 시뮬레이션 엔진으로 게임 및 영상 제작에 사실적인 화재·연기 효과를 제공합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">게임 VFX</span>
              <span className="tag">실시간 렌더링</span>
              <span className="tag">고품질 연기</span>
              <span className="tag">Asset 제공</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default MeteorSolutions;
