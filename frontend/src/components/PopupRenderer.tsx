import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getApiBaseUrl } from '../utils/api';
import './PopupRenderer.css';

export type PopupType = 'IMAGE_TEXT' | 'TEXT_ONLY';
export type PopupTrigger = 'HOME_ENTRY' | 'POST_LOGIN';
export type PopupCloseOption = 'HIDE_TODAY' | 'HIDE_FOREVER' | 'CLOSE';

export interface PopupItem {
  id: number;
  type: PopupType;
  title: string;
  content: string;
  imageUrl?: string | null;
  triggers: PopupTrigger[];
  closeOptions: PopupCloseOption[];
  priority: number;
  isActive: boolean;
  startAt?: string | null;
  endAt?: string | null;
}

const HIDE_UNTIL_KEY = (id: number) => `popup_${id}_hidden_until`;
const HIDE_FOREVER_KEY = (id: number) => `popup_${id}_hidden_forever`;
const IMAGE_HOST = getApiBaseUrl();

const HOME_PATHS = ['/'];

function isPopupHidden(p: PopupItem): boolean {
  try {
    if (localStorage.getItem(HIDE_FOREVER_KEY(p.id)) === 'true') return true;
    const until = localStorage.getItem(HIDE_UNTIL_KEY(p.id));
    if (until) {
      const untilDate = new Date(until);
      if (Number.isFinite(untilDate.getTime()) && untilDate.getTime() > Date.now()) {
        return true;
      }
      localStorage.removeItem(HIDE_UNTIL_KEY(p.id));
    }
  } catch {
    // localStorage 차단 환경에서는 항상 노출
  }
  return false;
}

function renderContent(content: string): React.ReactNode {
  if (!content) return null;
  const lines = content.split('\n');
  return lines.map((line, idx) => (
    <React.Fragment key={idx}>
      {idx > 0 && <br />}
      {line}
    </React.Fragment>
  ));
}

function resolveImageSrc(imageUrl?: string | null): string | undefined {
  if (!imageUrl) return undefined;
  if (imageUrl.startsWith('http://') || imageUrl.startsWith('https://')) return imageUrl;
  return `${IMAGE_HOST}${imageUrl}`;
}

interface PopupCardProps {
  popup: PopupItem;
  cascadeIndex: number;
  onClose: (popup: PopupItem, action: PopupCloseOption) => void;
}

const PopupCard: React.FC<PopupCardProps> = ({ popup, cascadeIndex, onClose }) => {
  const initialOffset = cascadeIndex * 24;
  const [position, setPosition] = useState({ x: initialOffset, y: initialOffset });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef<{ pointerX: number; pointerY: number; startX: number; startY: number } | null>(null);

  const handleDragStart = useCallback((clientX: number, clientY: number) => {
    dragStartRef.current = {
      pointerX: clientX,
      pointerY: clientY,
      startX: position.x,
      startY: position.y,
    };
    setIsDragging(true);
  }, [position.x, position.y]);

  const onMouseDownHeader = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    handleDragStart(e.clientX, e.clientY);
  }, [handleDragStart]);

  const onTouchStartHeader = useCallback((e: React.TouchEvent) => {
    const t = e.touches[0];
    if (t) handleDragStart(t.clientX, t.clientY);
  }, [handleDragStart]);

  useEffect(() => {
    if (!isDragging) return;
    const onMove = (clientX: number, clientY: number) => {
      const start = dragStartRef.current;
      if (!start) return;
      setPosition({
        x: start.startX + (clientX - start.pointerX),
        y: start.startY + (clientY - start.pointerY),
      });
    };
    const handleMouseMove = (e: MouseEvent) => onMove(e.clientX, e.clientY);
    const handleTouchMove = (e: TouchEvent) => {
      const t = e.touches[0];
      if (t) onMove(t.clientX, t.clientY);
    };
    const handleEnd = () => {
      setIsDragging(false);
      dragStartRef.current = null;
    };
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleEnd);
    window.addEventListener('touchmove', handleTouchMove);
    window.addEventListener('touchend', handleEnd);
    window.addEventListener('touchcancel', handleEnd);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleEnd);
      window.removeEventListener('touchmove', handleTouchMove);
      window.removeEventListener('touchend', handleEnd);
      window.removeEventListener('touchcancel', handleEnd);
    };
  }, [isDragging]);

  const style: React.CSSProperties = {
    transform: `translate(${position.x}px, ${position.y}px)`,
    zIndex: 1000 + cascadeIndex,
    userSelect: isDragging ? 'none' : undefined,
  };

  return (
    <div className="popup-card" style={style} role="dialog" aria-modal="false" aria-label={popup.title}>
      <div
        className={`popup-header ${isDragging ? 'is-dragging' : ''}`}
        onMouseDown={onMouseDownHeader}
        onTouchStart={onTouchStartHeader}
      >
        <div className="popup-brand">
          <img src="/logo_transparent.png" alt="" className="popup-brand-logo" draggable={false} />
          <span className="popup-brand-text">BUL:C</span>
        </div>
        <button
          type="button"
          className="popup-close-x"
          onClick={() => onClose(popup, 'CLOSE')}
          onMouseDown={(e) => e.stopPropagation()}
          onTouchStart={(e) => e.stopPropagation()}
          aria-label="닫기"
        >
          ×
        </button>
      </div>

      <div className="popup-subheader">
        <h2 className="popup-title">{popup.title}</h2>
      </div>

      <div className="popup-body">
        {popup.type === 'IMAGE_TEXT' && popup.imageUrl && (
          <div className="popup-image-wrap">
            <img src={resolveImageSrc(popup.imageUrl)} alt={popup.title} draggable={false} />
          </div>
        )}

        <p className="popup-content">{renderContent(popup.content)}</p>
      </div>

      <div className="popup-footer">
        {popup.closeOptions.includes('HIDE_TODAY') && (
          <button
            type="button"
            className="popup-action-btn popup-action-btn-primary"
            onClick={() => onClose(popup, 'HIDE_TODAY')}
          >
            오늘 하루 보지 않기
          </button>
        )}
        {popup.closeOptions.includes('CLOSE') && (
          <button
            type="button"
            className="popup-action-btn popup-action-btn-primary"
            onClick={() => onClose(popup, 'CLOSE')}
          >
            닫기
          </button>
        )}
      </div>
    </div>
  );
};

const PopupRenderer: React.FC = () => {
  const location = useLocation();
  const { isLoggedIn, isAuthReady } = useAuth();
  const [visiblePopups, setVisiblePopups] = useState<PopupItem[]>([]);

  const homeFetchedRef = useRef(false);
  const initializedRef = useRef(false);
  const wasLoggedInRef = useRef(false);

  const fetchTrigger = useCallback(async (trigger: PopupTrigger) => {
    try {
      const url = `${getApiBaseUrl()}/api/popups?trigger=${trigger}`;
      const resp = await fetch(url, { credentials: 'include' });
      if (!resp.ok) return;
      const data: PopupItem[] = await resp.json();
      const survivors = data.filter((p) => !isPopupHidden(p));
      if (survivors.length === 0) return;
      setVisiblePopups((prev) => {
        const existingIds = new Set(prev.map((p) => p.id));
        const additions = survivors.filter((p) => !existingIds.has(p.id));
        return [...prev, ...additions];
      });
    } catch {
      // 네트워크 실패는 silent (팝업은 비필수)
    }
  }, []);

  // HOME_ENTRY: 홈 경로 진입 시 한 번
  useEffect(() => {
    if (homeFetchedRef.current) return;
    if (!HOME_PATHS.includes(location.pathname)) return;
    homeFetchedRef.current = true;
    fetchTrigger('HOME_ENTRY');
  }, [location.pathname, fetchTrigger]);

  // POST_LOGIN: 로그인 false → true 트랜지션
  useEffect(() => {
    if (!isAuthReady) return;
    if (!initializedRef.current) {
      initializedRef.current = true;
      wasLoggedInRef.current = isLoggedIn;
      return;
    }
    if (!wasLoggedInRef.current && isLoggedIn) {
      fetchTrigger('POST_LOGIN');
    }
    wasLoggedInRef.current = isLoggedIn;
  }, [isAuthReady, isLoggedIn, fetchTrigger]);

  const handleClose = useCallback((popup: PopupItem, action: PopupCloseOption) => {
    try {
      if (action === 'HIDE_TODAY') {
        const tomorrow = new Date();
        tomorrow.setHours(24, 0, 0, 0);
        localStorage.setItem(HIDE_UNTIL_KEY(popup.id), tomorrow.toISOString());
      } else if (action === 'HIDE_FOREVER') {
        localStorage.setItem(HIDE_FOREVER_KEY(popup.id), 'true');
      }
    } catch {
      // localStorage 차단 환경은 무시
    }
    setVisiblePopups((prev) => prev.filter((p) => p.id !== popup.id));
  }, []);

  if (visiblePopups.length === 0) return null;

  return (
    <div className="popup-renderer-root">
      {visiblePopups.map((p, idx) => (
        <PopupCard
          key={p.id}
          popup={p}
          cascadeIndex={idx}
          onClose={handleClose}
        />
      ))}
    </div>
  );
};

export default PopupRenderer;
