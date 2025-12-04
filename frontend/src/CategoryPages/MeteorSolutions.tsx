import React, { useState } from 'react';
import './MeteorPages.css';
import ReportModal from '../components/ReportModal';

const MeteorSolutions: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [reportType, setReportType] = useState<'ASET' | 'RSET' | null>(null);

  const handleOpenReport = (type: 'ASET' | 'RSET') => {
    setReportType(type);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setReportType(null);
  };

  return (
    <>
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
            <h3 className="solution-title">화재 시뮬레이션</h3>
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
            <h3 className="solution-title">성능위주 설계 (PBD)</h3>
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
            <h3 className="solution-title">소방 R&D</h3>
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
            <h3 className="solution-title">배터리/전기차 화재</h3>
            <p className="solution-description">
              실험 기반 고정밀 열폭주 연소 모델로 배터리 화재의 특수성을 반영한 정확한 시뮬레이션을 제공합니다.
            </p>
            <div className="solution-tags">
              <span className="tag">ESS</span>
              <span className="tag">전기차</span>
              <span className="tag">열폭주</span>
              <span className="tag">PBD</span>
            </div>
          </div>

          <div className="solution-card full-width">
            <h3 className="solution-title">ASET+RSET 자동 보고서</h3>
            <p className="solution-description">
              Available Safe Egress Time과 Required Safe Egress Time을 자동으로 분석하여 화재 시 안전한 대피 가능 시간과 필요 시간을 정확하게 계산하고 보고서를 생성합니다.
            </p>
            <div className="solution-video">
              <iframe
                src="https://www.youtube.com/embed/vmM57d6DpcU"
                title="ASET+RSET 소개"
                allow="autoplay; encrypted-media"
                allowFullScreen
              ></iframe>
            </div>
            <div className="sample-buttons">
              <button className="btn-sample" onClick={() => handleOpenReport('ASET')}>
                ASET SAMPLE
              </button>
              <button className="btn-sample" onClick={() => handleOpenReport('RSET')}>
                RSET SAMPLE
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <ReportModal
      isOpen={isModalOpen}
      onClose={handleCloseModal}
      reportType={reportType}
    />
    </>
  );
};

export default MeteorSolutions;
