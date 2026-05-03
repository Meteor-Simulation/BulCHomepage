import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import './Toast.css';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastOptions {
  duration?: number;
  position?: ToastPosition;
}

export type ToastPosition = 'top-right' | 'top-center' | 'top-left' | 'bottom-right' | 'bottom-center' | 'bottom-left';

interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
  duration: number;
  position: ToastPosition;
}

interface ToastContextValue {
  show: (message: string, type?: ToastType, options?: ToastOptions) => void;
  success: (message: string, options?: ToastOptions) => void;
  error: (message: string, options?: ToastOptions) => void;
  info: (message: string, options?: ToastOptions) => void;
  warning: (message: string, options?: ToastOptions) => void;
  dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const DEFAULT_DURATION = 3000;
const DEFAULT_POSITION: ToastPosition = 'top-right';

let nextId = 1;

interface ToastProviderProps {
  children: React.ReactNode;
  defaultDuration?: number;
  defaultPosition?: ToastPosition;
}

export const ToastProvider: React.FC<ToastProviderProps> = ({
  children,
  defaultDuration = DEFAULT_DURATION,
  defaultPosition = DEFAULT_POSITION,
}) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const show = useCallback(
    (message: string, type: ToastType = 'info', options?: ToastOptions) => {
      const id = nextId++;
      const toast: ToastItem = {
        id,
        type,
        message,
        duration: options?.duration ?? defaultDuration,
        position: options?.position ?? defaultPosition,
      };
      setToasts((prev) => [...prev, toast]);
    },
    [defaultDuration, defaultPosition]
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      show,
      success: (msg, opts) => show(msg, 'success', opts),
      error: (msg, opts) => show(msg, 'error', opts),
      info: (msg, opts) => show(msg, 'info', opts),
      warning: (msg, opts) => show(msg, 'warning', opts),
      dismiss,
    }),
    [show, dismiss]
  );

  const groupedByPosition = useMemo(() => {
    const groups: Record<ToastPosition, ToastItem[]> = {
      'top-right': [],
      'top-center': [],
      'top-left': [],
      'bottom-right': [],
      'bottom-center': [],
      'bottom-left': [],
    };
    toasts.forEach((t) => groups[t.position].push(t));
    return groups;
  }, [toasts]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {createPortal(
        <>
          {(Object.entries(groupedByPosition) as [ToastPosition, ToastItem[]][]).map(
            ([position, items]) =>
              items.length > 0 && (
                <div key={position} className={`toast-container toast-${position}`}>
                  {items.map((toast) => (
                    <ToastItemView key={toast.id} toast={toast} onDismiss={dismiss} />
                  ))}
                </div>
              )
          )}
        </>,
        document.body
      )}
    </ToastContext.Provider>
  );
};

interface ToastItemViewProps {
  toast: ToastItem;
  onDismiss: (id: number) => void;
}

const ToastItemView: React.FC<ToastItemViewProps> = ({ toast, onDismiss }) => {
  useEffect(() => {
    if (toast.duration <= 0) return;
    const timer = setTimeout(() => onDismiss(toast.id), toast.duration);
    return () => clearTimeout(timer);
  }, [toast.id, toast.duration, onDismiss]);

  return (
    <div className={`toast toast-${toast.type}`} role="alert">
      <div className="toast-icon">{getIcon(toast.type)}</div>
      <div className="toast-message">{toast.message}</div>
      <button
        type="button"
        className="toast-close-btn"
        onClick={() => onDismiss(toast.id)}
        aria-label="알림 닫기"
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
    </div>
  );
};

function getIcon(type: ToastType): React.ReactElement {
  const common = {
    viewBox: '0 0 24 24',
    fill: 'none',
    xmlns: 'http://www.w3.org/2000/svg',
  } as const;
  switch (type) {
    case 'success':
      return (
        <svg {...common}>
          <path
            d="M22 11.08V12a10 10 0 11-5.93-9.14"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M22 4L12 14.01l-3-3"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    case 'error':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
          <path d="M15 9l-6 6M9 9l6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    case 'warning':
      return (
        <svg {...common}>
          <path
            d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path d="M12 9v4M12 17h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
          <path d="M12 16v-4M12 8h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
  }
}

export const useToast = (): ToastContextValue => {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast()는 <ToastProvider> 안에서만 호출할 수 있습니다.');
  }
  return ctx;
};
