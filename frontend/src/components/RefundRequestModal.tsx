import React, { useState } from 'react';
import './RefundRequestModal.css';

interface RefundRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  productName: string;
  onSubmit: (reason: string, details: string) => void;
}

const REFUND_REASONS = [
  '서비스 불만족',
  '다른 제품으로 변경',
  '기술적 문제 / 오류',
  '사용하지 않음',
  '가격 부담',
  '기타',
] as const;

const RefundRequestModal: React.FC<RefundRequestModalProps> = ({
  isOpen,
  onClose,
  productName,
  onSubmit,
}) => {
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedReason, setSelectedReason] = useState<string>('');
  const [details, setDetails] = useState('');

  if (!isOpen) return null;

  const handleClose = () => {
    setStep(1);
    setSelectedReason('');
    setDetails('');
    onClose();
  };

  const handleSubmit = () => {
    if (!selectedReason) return;
    onSubmit(selectedReason, details);
    handleClose();
  };

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div className="refund-modal" onClick={(e) => e.stopPropagation()}>
        {/* 헤더 */}
        <div className="refund-modal-header">
          <h2>{step === 1 ? '환불 안내' : '환불 사유 선택'}</h2>
          {step === 2 && (
            <span className="refund-step-badge">2 / 2</span>
          )}
          <button className="modal-close-btn" onClick={handleClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Step 1: 환불 정책 안내 */}
        {step === 1 && (
          <>
            <div className="refund-modal-content">
              <div className="refund-product-info">
                <span className="refund-product-label">환불 대상</span>
                <span className="refund-product-name">{productName}</span>
              </div>

              <div className="refund-policy-summary">
                <h3>환불 가능 조건</h3>
                <ul>
                  <li>결제일로부터 <strong>7일 이내</strong>에 환불을 요청한 경우</li>
                  <li>구매한 라이선스를 <strong>활성화하지 않은</strong> 경우</li>
                  <li>서비스의 중대한 하자로 정상 이용이 불가능한 경우</li>
                </ul>

                <h3>환불 불가 조건</h3>
                <ul>
                  <li>라이선스를 활성화하였거나 서비스를 이용한 이력이 있는 경우</li>
                  <li>결제일로부터 7일이 경과한 경우</li>
                  <li>무료 체험(Trial) 전환 결제의 경우</li>
                </ul>

                <h3>환불 절차</h3>
                <ol>
                  <li>환불 사유 선택 및 신청</li>
                  <li>담당자 검토 (영업일 기준 1~2일)</li>
                  <li>환불 승인 시 원래 결제 수단으로 환불 처리</li>
                  <li>환불 금액 반영까지 영업일 기준 5~7일 소요</li>
                </ol>

                <div className="refund-notice">
                  구독 취소를 원하시는 경우, 자동 갱신을 OFF로 변경하시면 현재 구독 기간 종료 후 자동으로 해지됩니다.
                </div>
              </div>
            </div>
            <div className="refund-modal-footer">
              <button className="refund-cancel-btn" onClick={handleClose}>닫기</button>
              <button className="refund-next-btn" onClick={() => setStep(2)}>
                환불 요청
              </button>
            </div>
          </>
        )}

        {/* Step 2: 환불 사유 선택 */}
        {step === 2 && (
          <>
            <div className="refund-modal-content">
              <p className="refund-reason-desc">환불 사유를 선택해 주세요.</p>

              <div className="refund-reason-list">
                {REFUND_REASONS.map((reason) => (
                  <label key={reason} className={`refund-reason-item ${selectedReason === reason ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="refundReason"
                      value={reason}
                      checked={selectedReason === reason}
                      onChange={() => setSelectedReason(reason)}
                    />
                    <span className="refund-reason-radio" />
                    <span className="refund-reason-text">{reason}</span>
                  </label>
                ))}
              </div>

              <div className="refund-details-group">
                <label className="refund-details-label">
                  상세 사유 {selectedReason === '기타' ? '(필수)' : '(선택)'}
                </label>
                <textarea
                  className="refund-details-textarea"
                  placeholder="환불 사유를 상세히 작성해 주세요."
                  value={details}
                  onChange={(e) => setDetails(e.target.value)}
                  rows={4}
                  maxLength={500}
                />
                <span className="refund-details-count">{details.length} / 500</span>
              </div>
            </div>
            <div className="refund-modal-footer">
              <button className="refund-cancel-btn" onClick={() => setStep(1)}>이전</button>
              <button
                className="refund-submit-btn"
                onClick={handleSubmit}
                disabled={!selectedReason || (selectedReason === '기타' && details.trim() === '')}
              >
                환불 요청 제출
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default RefundRequestModal;
