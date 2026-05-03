import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import BaseModal from './BaseModal';
import './ConfirmModal.css';

export type ConfirmVariant = 'default' | 'danger' | 'warning';

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: ConfirmVariant;
}

export interface ConfirmModalProps extends ConfirmOptions {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  onConfirm,
  onCancel,
  title,
  message,
  confirmText = '확인',
  cancelText = '취소',
  variant = 'default',
}) => {
  return (
    <BaseModal
      isOpen={isOpen}
      onClose={onCancel}
      title={title}
      size="sm"
      showCloseButton={false}
      footer={
        <>
          <button type="button" className="confirm-cancel-btn" onClick={onCancel}>
            {cancelText}
          </button>
          <button
            type="button"
            className={`confirm-confirm-btn confirm-${variant}`}
            onClick={onConfirm}
          >
            {confirmText}
          </button>
        </>
      }
    >
      <p className="confirm-message">{message}</p>
    </BaseModal>
  );
};

export default ConfirmModal;

/* ========================================
   useConfirm 훅 — Promise 기반 confirm 다이얼로그
======================================== */

interface PendingConfirm extends ConfirmOptions {
  resolve: (value: boolean) => void;
}

interface ConfirmContextValue {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const ConfirmContext = createContext<ConfirmContextValue | null>(null);

export const ConfirmProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [pending, setPending] = useState<PendingConfirm | null>(null);

  const confirm = useCallback((options: ConfirmOptions): Promise<boolean> => {
    return new Promise<boolean>((resolve) => {
      setPending({ ...options, resolve });
    });
  }, []);

  const handleConfirm = useCallback(() => {
    if (pending) {
      pending.resolve(true);
      setPending(null);
    }
  }, [pending]);

  const handleCancel = useCallback(() => {
    if (pending) {
      pending.resolve(false);
      setPending(null);
    }
  }, [pending]);

  const value = useMemo<ConfirmContextValue>(() => ({ confirm }), [confirm]);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {pending && (
        <ConfirmModal
          isOpen={true}
          onConfirm={handleConfirm}
          onCancel={handleCancel}
          title={pending.title}
          message={pending.message}
          confirmText={pending.confirmText}
          cancelText={pending.cancelText}
          variant={pending.variant}
        />
      )}
    </ConfirmContext.Provider>
  );
};

export const useConfirm = (): ((options: ConfirmOptions) => Promise<boolean>) => {
  const ctx = useContext(ConfirmContext);
  if (!ctx) {
    throw new Error('useConfirm()은 <ConfirmProvider> 안에서만 호출할 수 있습니다.');
  }
  return ctx.confirm;
};
