import React from 'react';

interface BulCDownloadSectionProps {
  onPurchaseClick: () => void;
}

const BulCDownloadSection: React.FC<BulCDownloadSectionProps> = ({ onPurchaseClick }) => (
  <div className="bulc-download-section">
    <h1 className="download-title">지금 시작하세요</h1>
    <div className="download-buttons-grid">
      <a
        href="https://msimul.sharepoint.com/:f:/s/msteams_8c91f3-2/EtNitiqwxNhEv4gcjBVUaWMBGqIY1zxNdNOwl4IUMSGxwg?e=ENEjWr"
        target="_blank"
        rel="noopener noreferrer"
        className="download-btn"
      >
        무료 다운로드
      </a>
      <a href="#" className="download-btn">
        AI Agent Download
      </a>
      <button onClick={onPurchaseClick} className="download-btn">
        라이센스 구입
      </button>
      <a href="#" className="download-btn">
        Q&A
      </a>
    </div>
  </div>
);

export default BulCDownloadSection;
