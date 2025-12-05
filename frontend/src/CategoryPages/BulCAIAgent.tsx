import React, { useState } from 'react';
import './MeteorPages.css';
import ReportModal from '../components/ReportModal';

const BulCAIAgent: React.FC = () => {
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
      <section className="meteor-section ai-agent-section">
        <div className="meteor-container">
          <div className="section-header">
            <div className="section-eyebrow">AI POWERED</div>
            <h2 className="section-title">AI Agent 기능</h2>
            <p className="section-description">
              최첨단 AI 기술을 활용하여 복잡한 화재 시뮬레이션과 분석을 자동화합니다.
            </p>
          </div>

          <div className="ai-agent-grid">
            <div className="ai-agent-card">
              <h3 className="solution-title">AI 자동 화재-피난 시뮬레이션</h3>
              <p className="solution-description">
                AI를 이용해서 사용자의 요구에 맞춰 자동으로 설정하고 시뮬레이션을 합니다. Claude, Chat GPT 등 MCP 커넥터를 지원합니다.
              </p>
              <div className="solution-video">
                <iframe
                  src="https://www.youtube.com/embed/Q1mjYTyukr4"
                  title="AI 자동 화재-피난 시뮬레이션"
                  allow="autoplay; encrypted-media"
                  allowFullScreen
                ></iframe>
              </div>
              <div className="solution-tags">
                <span className="tag">Claude AI</span>
                <span className="tag">ChatGPT</span>
                <span className="tag">MCP</span>
                <span className="tag">자동화</span>
              </div>
              <div className="opensource-link">
                Open source available: <a href="https://github.com/using76/BULC_MCP" target="_blank" rel="noopener noreferrer">https://github.com/using76/BULC_MCP</a>
              </div>
            </div>

            <div className="ai-agent-card">
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

export default BulCAIAgent;
