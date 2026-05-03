import React, { useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import './BaseModal.css';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';

export interface BaseModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  size?: ModalSize;
  showCloseButton?: boolean;
  closeOnOverlayClick?: boolean;
  closeOnEsc?: boolean;
  preventBodyScroll?: boolean;
  footer?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}

const BaseModal: React.FC<BaseModalProps> = ({
  isOpen,
  onClose,
  title,
  size = 'md',
  showCloseButton = true,
  closeOnOverlayClick = true,
  closeOnEsc = true,
  preventBodyScroll = true,
  footer,
  className = '',
  children,
}) => {
  useEffect(() => {
    if (!isOpen || !closeOnEsc) return;
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [isOpen, closeOnEsc, onClose]);

  useEffect(() => {
    if (!isOpen || !preventBodyScroll) return;
    const original = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = original;
    };
  }, [isOpen, preventBodyScroll]);

  const handleOverlayMouseDown = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (closeOnOverlayClick && e.target === e.currentTarget) {
        onClose();
      }
    },
    [closeOnOverlayClick, onClose]
  );

  if (!isOpen) return null;

  const modal = (
    <div className="base-modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className={`base-modal base-modal-${size} ${className}`} role="dialog" aria-modal="true">
        {(title || showCloseButton) && (
          <div className="base-modal-header">
            {title && <h2 className="base-modal-title">{title}</h2>}
            {showCloseButton && (
              <button
                type="button"
                className="base-modal-close-btn"
                onClick={onClose}
                aria-label="닫기"
              >
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path
                    d="M18 6L6 18M6 6L18 18"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            )}
          </div>
        )}

        <div className="base-modal-body">{children}</div>

        {footer && <div className="base-modal-footer">{footer}</div>}
      </div>
    </div>
  );

  return createPortal(modal, document.body);
};

export default BaseModal;
