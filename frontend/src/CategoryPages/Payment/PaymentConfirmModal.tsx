import React from 'react';
import { useTranslation } from 'react-i18next';
import './PaymentConfirmModal.css';

/**
 * 결제 직전 확인 창.
 *
 * 왜 필요한가 — 종전에는 버튼을 누르면 화면이 그대로 있다가 몇 초 뒤 완료 페이지로 넘어갔다.
 * 그 침묵 구간에 사용자가 "안 눌렸다" 고 판단해 다시 눌러 결제가 두 번 됐다
 * (2026-10-07 · 10-12, 각 100원 2건). 무엇을 결제하는지 한 번 보여 주고,
 * 전송이 시작되면 진행 상태를 이 창 안에서 그대로 보여 준다 — 화면이 바뀌지 않는 구간을 없앤다.
 *
 * 중복 클릭 자체는 서버의 멱등키가 막는다. 이 창은 사용자가 다시 누를 이유를 없애는 쪽이다.
 */

export type ConfirmRow = { label: string; value: string };

interface PaymentConfirmModalProps {
  isOpen: boolean;
  /** 상품·요금제·결제수단·구매자 정보 등 확인용 항목 */
  rows: ConfirmRow[];
  /** 최종 결제 금액 (표시용 문자열) */
  totalLabel: string;
  /** 전송 중이면 true — 버튼을 잠그고 진행 표시로 바꾼다 */
  isSubmitting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

const PaymentConfirmModal: React.FC<PaymentConfirmModalProps> = ({
  isOpen, rows, totalLabel, isSubmitting, onConfirm, onClose,
}) => {
  const { t } = useTranslation();
  if (!isOpen) return null;

  // 전송 중에는 바깥 클릭·닫기로 창을 닫지 못하게 한다. 닫아 버리면 결제가 진행 중인지
  // 알 수 없게 되고, 사용자가 처음부터 다시 시도해 이중 결제로 이어진다.
  const handleOverlayClick = () => { if (!isSubmitting) onClose(); };

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className="payment-confirm-modal" onClick={(e) => e.stopPropagation()}>
        {isSubmitting ? (
          <div className="payment-confirm-progress">
            <div className="payment-confirm-spinner" aria-hidden="true" />
            <h3>{t('payment.confirmModal.processingTitle')}</h3>
            {/* "창을 닫지 말라" 는 당부를 본문에 합쳤다 — 줄이 셋이면 읽히지 않고, 정작 중요한
                한 줄이 묻힌다. 결제창 경로는 토스 화면으로 넘어가 잠깐만 보이지만, 등록 카드
                결제는 응답까지 7~9초 걸려 이 구간이 길다. */}
            <p className="payment-confirm-warning">{t('payment.confirmModal.processingBody')}</p>
          </div>
        ) : (
          <>
            <div className="payment-confirm-header">
              <h3>{t('payment.confirmModal.title')}</h3>
              <button type="button" className="modal-close-btn" onClick={onClose} aria-label={t('payment.modal.cancel')}>
                ×
              </button>
            </div>
            <p className="payment-confirm-lead">{t('payment.confirmModal.lead')}</p>
            <dl className="payment-confirm-rows">
              {rows.map((row) => (
                <div className="payment-confirm-row" key={row.label}>
                  <dt>{row.label}</dt>
                  <dd>{row.value}</dd>
                </div>
              ))}
            </dl>
            <div className="payment-confirm-total">
              <span>{t('payment.confirmModal.total')}</span>
              <strong>{totalLabel}</strong>
            </div>
            <div className="payment-confirm-footer">
              <button type="button" className="terms-cancel-btn" onClick={onClose}>
                {t('payment.confirmModal.back')}
              </button>
              <button type="button" className="terms-agree-btn" onClick={onConfirm}>
                {t('payment.confirmModal.proceed')}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PaymentConfirmModal;
