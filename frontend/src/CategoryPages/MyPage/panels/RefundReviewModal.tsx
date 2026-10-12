import React, { useEffect, useState } from 'react';
import './RefundReviewModal.css';

/**
 * 환불 요청 확인·처리 창 (관리자).
 *
 * 결제관리 목록에서 바로 띄운다. 종전에는 목록이 "환불 대기" 만 보여 주고 사유는 환불 요청
 * 메뉴로 옮겨 가야 알 수 있었다. 사유를 모르면 승인·거절을 판단할 수 없으므로 같은 자리에서
 * 보고 처리할 수 있어야 한다.
 *
 * 거절 사유도 선택식으로 둔다 — 고객 화면에 그대로 보이는 문구라, 손으로 적으면 응대마다
 * 표현이 달라지고 성의 없는 한 줄이 나가기 쉽다.
 */

/** 거절 사유 선택 항목. value 가 고객에게 보이는 문구가 된다. */
const REJECT_REASONS = [
  '환불 가능 기간이 지났습니다',
  '이미 서비스를 이용하셨습니다',
  '중복 결제가 확인되지 않았습니다',
  '결제 금액에 오류가 없습니다',
] as const;

const OTHER = '__other__';

export interface RefundReviewTarget {
  /** 환불 요청 id — 처리 API 에 쓴다 */
  refundRequestId: number;
  orderId: string | null;
  userEmail: string | null;
  userName: string | null;
  amount: number;
  currency: string;
  reason: string;
  requestedAt: string | null;
}

interface RefundReviewModalProps {
  target: RefundReviewTarget | null;
  isProcessing: boolean;
  onApprove: (note: string) => void;
  onReject: (note: string) => void;
  onClose: () => void;
}

const formatPrice = (amount: number, currency: string) =>
  currency === 'KRW' ? `${amount.toLocaleString('ko-KR')}원` : `$${amount.toLocaleString('en-US')}`;

const formatDate = (s: string | null) => {
  if (!s) return '-';
  return new Date(s).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
};

const RefundReviewModal: React.FC<RefundReviewModalProps> = ({
  target, isProcessing, onApprove, onReject, onClose,
}) => {
  /** null = 아직 고르지 않음, 'approve' = 승인, 'reject' = 거절 */
  const [mode, setMode] = useState<'approve' | 'reject' | null>(null);
  const [rejectReason, setRejectReason] = useState<string>('');
  const [note, setNote] = useState('');

  // 다른 요청에 앞서 입력한 값이 붙지 않게 초기화한다
  useEffect(() => {
    if (target) {
      setMode(null);
      setRejectReason('');
      setNote('');
    }
  }, [target]);

  if (!target) return null;

  const isOtherReject = rejectReason === OTHER;
  const canReject = mode === 'reject'
    && (isOtherReject ? note.trim().length > 0 : rejectReason !== '');

  const handleOverlayClick = () => { if (!isProcessing) onClose(); };

  const submitReject = () => {
    if (!canReject || isProcessing) return;
    onReject(isOtherReject ? note.trim() : rejectReason);
  };

  const submitApprove = () => {
    if (isProcessing) return;
    onApprove(note.trim());
  };

  return (
    <div className="admin-modal-overlay" onClick={handleOverlayClick}>
      <div className="admin-modal refund-review-modal" onClick={(e) => e.stopPropagation()}>
        {/*
          처리 중에는 창 전체를 덮는다. 버튼 disabled 만으로는 부족하다 — 토스 승인취소가
          수 초 걸리는 동안 화면이 그대로면 관리자가 다시 누르거나 닫으려 하고, 닫아 버리면
          환불이 진행 중인지 알 수 없어 같은 건을 또 처리하려 한다.
          (결제 쪽에서 같은 이유로 100원이 두 번 청구된 전례가 있다)
        */}
        {isProcessing && (
          <div className="refund-review-blocker" aria-live="polite" aria-busy="true">
            <div className="refund-review-blocker-spinner" aria-hidden="true" />
            <p>환불을 처리하고 있습니다</p>
            <small>카드사 승인취소까지 수 초가 걸릴 수 있습니다. 창을 닫지 마세요.</small>
          </div>
        )}

        <div className="admin-modal-header">
          <h3>환불 요청 확인</h3>
        </div>

        <div className="admin-modal-body">
          <dl className="refund-review-rows">
            <div className="refund-review-row">
              <dt>주문번호</dt>
              <dd className="refund-review-order">{target.orderId || '-'}</dd>
            </div>
            <div className="refund-review-row">
              <dt>신청자</dt>
              <dd>
                {target.userName || '-'}
                {target.userEmail ? <><br /><small>{target.userEmail}</small></> : null}
              </dd>
            </div>
            <div className="refund-review-row">
              <dt>금액</dt>
              <dd><strong>{formatPrice(target.amount, target.currency)}</strong></dd>
            </div>
            <div className="refund-review-row">
              <dt>신청일</dt>
              <dd>{formatDate(target.requestedAt)}</dd>
            </div>
          </dl>

          {/* 사유 — 이 창의 존재 이유다. 승인·거절 판단의 근거이므로 가장 눈에 띄게 둔다 */}
          <div className="refund-review-reason">
            <span className="refund-review-reason-label">고객이 적은 사유</span>
            <p>{target.reason}</p>
          </div>

          {mode === null && (
            <p className="refund-review-hint">
              {'승인하면 고객 카드로 승인취소가 즉시 요청됩니다.\n거절하면 돈은 움직이지 않고 사유가 고객에게 표시됩니다.'}
            </p>
          )}

          {mode === 'approve' && (
            <div className="refund-review-field">
              <label htmlFor="refundApproveNote">메모 (선택)</label>
              <textarea
                id="refundApproveNote"
                className="admin-modal-input"
                rows={2}
                maxLength={500}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="기록에 남습니다. 비우면 고객 사유가 취소 사유로 쓰입니다."
                disabled={isProcessing}
                autoFocus
              />
              <p className="refund-review-warning">
                고객 카드로 승인취소가 즉시 요청됩니다. 되돌릴 수 없습니다.
              </p>
            </div>
          )}

          {mode === 'reject' && (
            <div className="refund-review-field">
              <label>거절 사유 (고객에게 표시됩니다)</label>
              <div className="refund-review-options" role="radiogroup" aria-label="거절 사유">
                {REJECT_REASONS.map((reason) => (
                  <label className="refund-review-option" key={reason}>
                    <input
                      type="radio"
                      name="rejectReason"
                      checked={rejectReason === reason}
                      onChange={() => setRejectReason(reason)}
                      disabled={isProcessing}
                    />
                    <span>{reason}</span>
                  </label>
                ))}
                <label className="refund-review-option">
                  <input
                    type="radio"
                    name="rejectReason"
                    checked={isOtherReject}
                    onChange={() => setRejectReason(OTHER)}
                    disabled={isProcessing}
                  />
                  <span>기타 (직접 입력)</span>
                </label>
              </div>
              {isOtherReject && (
                <textarea
                  className="admin-modal-input"
                  rows={2}
                  maxLength={500}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="거절 사유를 적어 주세요."
                  disabled={isProcessing}
                  autoFocus
                />
              )}
            </div>
          )}
        </div>

        <div className="admin-modal-footer">
          {mode === null ? (
            <>
              <button type="button" className="cancel-btn" onClick={onClose}>닫기</button>
              <button type="button" className="cancel-btn" onClick={() => setMode('reject')}>거절</button>
              <button type="button" className="save-btn" onClick={() => setMode('approve')}>환불 승인</button>
            </>
          ) : (
            <>
              <button
                type="button"
                className="cancel-btn"
                onClick={() => { setMode(null); setNote(''); setRejectReason(''); }}
                disabled={isProcessing}
              >
                뒤로
              </button>
              {mode === 'approve' ? (
                <button type="button" className="save-btn" onClick={submitApprove} disabled={isProcessing}>
                  {isProcessing ? '처리 중…' : '환불 실행'}
                </button>
              ) : (
                <button type="button" className="save-btn" onClick={submitReject} disabled={!canReject || isProcessing}>
                  {isProcessing ? '처리 중…' : '거절 확정'}
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default RefundReviewModal;
