import React, { useEffect, useState } from 'react';
import './MeteorPages.css';

interface CompanyInfo {
  company: {
    name: string;
    nameEn: string;
    description: string;
  };
  address: {
    full: string;
    zipCode: string;
  };
  contact: {
    tel: string;
    email: string;
  };
}

const BulCDownload: React.FC = () => {
  const currentYear = new Date().getFullYear();
  const [companyInfo, setCompanyInfo] = useState<CompanyInfo | null>(null);

  useEffect(() => {
    fetch('/config/company.json')
      .then(res => res.json())
      .then(data => setCompanyInfo(data))
      .catch(err => console.error('회사 정보 로드 실패:', err));
  }, []);

  return (
    <>
      <section className="bulc-cta-section">
        <div className="cta-container">
          <h2 className="cta-title">지금 시작하세요</h2>
          <div className="cta-buttons">
            <a
              href="https://msimul.sharepoint.com/:f:/s/msteams_8c91f3-2/EtNitiqwxNhEv4gcjBVUaWMBGqIY1zxNdNOwl4IUMSGxwg?e=ENEjWr"
              className="btn-light"
              target="_blank"
              rel="noopener noreferrer"
            >
              무료 다운로드
            </a>
            <a
              href="mailto:simul@msimul.com?subject=%5B문의%5D%20프로젝트%20상담"
              className="btn-outline-light"
            >
              Q&A
            </a>
          </div>
          <div className="cta-buttons" style={{ marginTop: '24px' }}>
            <a
              href="https://github.com/using76/BULC_MCP"
              className="btn-outline-light"
              target="_blank"
              rel="noopener noreferrer"
            >
              AI AGENT download
            </a>
            <button className="btn-outline-light">
              BULC 구입
            </button>
          </div>
        </div>
      </section>

      <footer className="bulc-footer">
        <div className="footer-container">
          <div>
            <div className="footer-brand">{companyInfo?.company.nameEn || 'METEOR SIMULATION'}</div>
            <p className="footer-description">
              {companyInfo?.company.description || 'AI 기반 화재 시뮬레이션으로 더 안전한 미래를 만들어갑니다.'}
            </p>
          </div>

          <div className="footer-section">
            <h4>Company</h4>
            <ul className="footer-links">
              <li><a href="#intro">Intro</a></li>
              <li><a href="#bulc">BULC</a></li>
              <li><a href="#tutorial">Tutorial</a></li>
              <li><a href="#download">Download</a></li>
              <li><a href="https://openfire.tech" target="_blank" rel="noopener noreferrer">OpenFire</a></li>
            </ul>
          </div>

          <div className="footer-section">
            <h4>Contact</h4>
            <ul className="footer-links">
              <li><a href={`mailto:${companyInfo?.contact.email || 'simul@msimul.com'}`}>{companyInfo?.contact.email || 'simul@msimul.com'}</a></li>
              <li><a href={`tel:${companyInfo?.contact.tel || '010-2747-2056'}`}>{companyInfo?.contact.tel || '010-2747-2056'}</a></li>
              <li>{companyInfo?.address.full || '강원도 원주시 마재2로 10, 강원미래산업진흥원 3층'}</li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom">
          © {currentYear} {companyInfo?.company.nameEn || 'Meteor Simulation'}. All rights reserved.
        </div>
      </footer>
    </>
  );
};

export default BulCDownload;
