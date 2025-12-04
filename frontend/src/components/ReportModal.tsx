import React, { useEffect } from 'react';
import './ReportModal.css';

interface ReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  reportType: 'ASET' | 'RSET' | null;
}

const ReportModal: React.FC<ReportModalProps> = ({ isOpen, onClose, reportType }) => {
  // Close modal on Escape key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen || !reportType) return null;

  const reportUrl = reportType === 'ASET'
    ? '/reports/ASET_Report/ASET_Report.html'
    : '/reports/RSET_Report/RSET_Report.html';

  const reportTitle = reportType === 'ASET'
    ? 'ASET Analysis Report - Available Safe Egress Time'
    : 'RSET Analysis Report - Required Safe Egress Time';

  return (
    <div className="report-modal-overlay" onClick={onClose}>
      <div className="report-modal-container" onClick={(e) => e.stopPropagation()}>
        <div className="report-modal-header">
          <h2 className="report-modal-title">{reportTitle}</h2>
          <button className="report-modal-close" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        </div>
        <div className="report-modal-body">
          <iframe
            src={reportUrl}
            title={reportTitle}
            className="report-iframe"
          />
        </div>
      </div>
    </div>
  );
};

export default ReportModal;
