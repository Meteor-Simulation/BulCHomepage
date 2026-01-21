import { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

interface SubNavItem {
  id: string;
  label: string;
}

interface UseHashMenuOptions {
  items: SubNavItem[];
  defaultId?: string;
}

/**
 * URL 해시 기반 메뉴 상태 관리 훅
 *
 * @example
 * // URL: /bulc#tutorial
 * const { activeMenu, setActiveMenu, items } = useHashMenu({
 *   items: [
 *     { id: 'intro', label: 'Intro' },
 *     { id: 'tutorial', label: 'Tutorial' },
 *   ],
 *   defaultId: 'intro'
 * });
 * // activeMenu = 'tutorial'
 */
export function useHashMenu({ items, defaultId }: UseHashMenuOptions) {
  const location = useLocation();
  const navigate = useNavigate();

  // 기본값: defaultId가 있으면 사용, 없으면 첫 번째 아이템
  const fallbackId = defaultId || items[0]?.id || '';

  // 현재 해시에서 메뉴 ID 추출
  const getMenuFromHash = useCallback(() => {
    const hash = location.hash.replace('#', '');
    // 해시가 있고, items에 해당 id가 있는 경우에만 사용
    if (hash && items.some(item => item.id === hash)) {
      return hash;
    }
    return fallbackId;
  }, [location.hash, items, fallbackId]);

  const [activeMenu, setActiveMenuState] = useState<string>(getMenuFromHash);

  // 해시 변경 시 메뉴 상태 업데이트
  useEffect(() => {
    const menuFromHash = getMenuFromHash();
    setActiveMenuState(menuFromHash);
  }, [getMenuFromHash]);

  // location.state에서 activeTab 처리 (기존 호환성 유지)
  useEffect(() => {
    const state = location.state as { activeTab?: string } | null;
    if (state?.activeTab && items.some(item => item.id === state.activeTab)) {
      setActiveMenuState(state.activeTab);
      // 해시로 변환하고 state 초기화
      navigate(`${location.pathname}#${state.activeTab}`, { replace: true });
    }
  }, [location.state, location.pathname, items, navigate]);

  // 메뉴 변경 시 해시 업데이트
  const setActiveMenu = useCallback((menuId: string) => {
    if (items.some(item => item.id === menuId)) {
      setActiveMenuState(menuId);
      // 해시 변경 (히스토리에 추가)
      navigate(`${location.pathname}#${menuId}`, { replace: false });
    }
  }, [items, location.pathname, navigate]);

  return {
    activeMenu,
    setActiveMenu,
    items,
  };
}

export default useHashMenu;
