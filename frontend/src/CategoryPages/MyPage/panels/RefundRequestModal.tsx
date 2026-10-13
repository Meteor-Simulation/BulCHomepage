import React, { useEffect, useState } from 'react';
import ProcessingBlocker from '../../../components/ProcessingBlocker';
import './RefundRequestModal.css';

/**
 * 환불 신청 창.
 *
 * window.prompt 를 쓰던 것을 모달로 바꿨다. prompt 는 브라우저마다 모양이 다르고 무엇을
 * 환불하는지 보여 줄 수 없어서, 고객이 금액·주문을 확인하지 못한 채 사유만 적게 됐다.
 *
 * 사유를 자유 입력에서 선택식으로 바꾼 이유: 관리자가 승인·거절을 판단할 때 분류가 있으면
 * 빠르고, "환불해주세요" 같은 내용 없는 사유로 되묻는 왕복이 줄어든다. 분류로 담기지 않는
 * 경우를 위해 '기타' 를 두고, 그때만 직접 입력을 받는다.
 */

/** 선택 항목. value 가 서버로 가는 사유 문구가 된다 — 관리자 화면에 그대로 보인다. */
const PRESET_REASONS = [
  '중복으로 결제했습니다',
  '실수로 결제했습니다',
  '서비스가 기대와 달랐습니다',
  '결제 금액이 잘못되었습니다',
  '서비스를 이용하지 않았습니다',
] as const;

const OTHER = '__other__';

export interface RefundRequestTarget {
  id: number;
  orderName: string | null;
  amount: number;
  currency: string;
}

interface RefundRequestModalProps {
  /** 신청 대상 결제. null 이면 창을 닫는다 */
  target: RefundRequestTarget | null;
  isSubmitting: boolean;
  onSubmit: (reason: string) => void;
  onClose: () => void;
}

const formatPrice = (amount: number, currency: string) =>
  currency === 'KRW' ? `${amount.toLocaleString('ko-KR')}원` : `$${amount.toLocaleString('en-US')}`;

const RefundRequestModal: React.FC<RefundRequestModalProps> = ({
  target, isSubmitting, onSubmit, onClose,
}) => {
  const [selected, setSelected] = useState<string>('');
  const [memo, setMemo] = useState('');

  // 창을 다시 열 때 앞서 고른 값이 남아 있으면 다른 결제에 그 사유가 붙는다
  useEffect(() => {
    if (target) {
      setSelected('');
      setMemo('');
    }
  }, [target]);

  if (!target) return null;

  const isOther = selected === OTHER;
  // 기타를 골랐으면 메모가 있어야 한다 — 빈 '기타' 는 사유가 없는 것과 같다
  const canSubmit = !isSubmitting && (isOther ? memo.trim().length > 0 : selected !== '');

  const handleSubmit = () => {
    if (!canSubmit) return;
    onSubmit(isOther ? `기타: ${memo.trim()}` : selected);
  };

  // 전송 중에는 바깥 클릭으로 닫지 못하게 한다. 닫아 버리면 신청이 들어갔는지 알 수 없어
  // 같은 신청을 다시 하게 된다(서버가 중복을 막지만 사용자는 실패로 받아들인다).
  const handleOverlayClick = () => { if (!isSubmitting) onClose(); };

  return (
    <div className="admin-modal-overlay" onClick={handleOverlayClick}>
      <div className="admin-modal refund-request-modal" onClick={(e) => e.stopPropagation()}>
        <ProcessingBlocker
          isActive={isSubmitting}
          title="환불 요청을 접수하고 있습니다"
          description="잠시만 기다려 주세요."
        />

        <div className="admin-modal-header">
          <h3>환불 요청</h3>
        </div>

        <div className="admin-modal-body">
          {/* 무엇을 환불하는지 먼저 보여 준다 — prompt 로는 할 수 없던 부분이다 */}
          <div className="refund-target">
            <span className="refund-target-name">{target.orderName || `결제 #${target.id}`}</span>
            <strong className="refund-target-amount">{formatPrice(target.amount, target.currency)}</strong>
          </div>

          <p className="refund-modal-lead">환불 사유를 선택해 주세요.</p>

          <div className="refund-reason-list" role="radiogroup" aria-label="환불 사유">
            {PRESET_REASONS.map((reason) => (
              <label className="refund-reason-option" key={reason}>
                <input
                  type="radio"
                  name="refundReason"
                  value={reason}
                  checked={selected === reason}
                  onChange={() => setSelected(reason)}
                  disabled={isSubmitting}
                />
                <span>{reason}</span>
              </label>
            ))}
            <label className="refund-reason-option">
              <input
                type="radio"
                name="refundReason"
                value={OTHER}
                checked={isOther}
                onChange={() => setSelected(OTHER)}
                disabled={isSubmitting}
              />
              <span>기타 (직접 입력)</span>
            </label>
          </div>

          {isOther && (
            <div className="refund-memo">
              <label htmlFor="refundMemo">사유를 적어 주세요</label>
              <textarea
                id="refundMemo"
                className="admin-modal-input"
                rows={3}
                maxLength={500}
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                placeholder="환불을 요청하시는 이유를 적어 주세요."
                disabled={isSubmitting}
                autoFocus
              />
              <span className="refund-memo-count">{memo.length} / 500</span>
            </div>
          )}

          <p className="refund-modal-note">
            {'접수 후 관리자 확인을 거쳐 처리됩니다.\n처리 결과는 결제 내역에서 확인하실 수 있습니다.'}
          </p>
        </div>

        <div className="admin-modal-footer">
          <button type="button" className="cancel-btn" onClick={onClose} disabled={isSubmitting}>
            취소
          </button>
          <button type="button" className="save-btn" onClick={handleSubmit} disabled={!canSubmit}>
            {isSubmitting ? '접수 중…' : '환불 요청'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default RefundRequestModal;
