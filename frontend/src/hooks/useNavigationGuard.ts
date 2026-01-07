import { useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * 네비게이션 가드 훅
 * 뒤로가기 방지 및 인증 상태에 따른 리다이렉트 처리
 */

interface NavigationGuardOptions {
  // 로그인이 필요한 페이지인지 여부
  requireAuth?: boolean;
  // 로그인 상태에서 접근 불가한 페이지인지 (로그인/회원가입 페이지)
  guestOnly?: boolean;
  // 뒤로가기 방지 여부
  preventBack?: boolean;
  // 새로고침 방지 여부
  preventRefresh?: boolean;
  // 새로고침 방지 활성화 조건 (true일 때만 방지)
  shouldPreventRefresh?: boolean;
  // 리다이렉트할 경로
  redirectTo?: string;
}

export const useNavigationGuard = (options: NavigationGuardOptions = {}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { isLoggedIn } = useAuth();

  const {
    requireAuth = false,
    guestOnly = false,
    preventBack = false,
    preventRefresh = false,
    shouldPreventRefresh = true,
    redirectTo = '/',
  } = options;

  // 히스토리 대체 (뒤로가기 방지)
  const replaceHistory = useCallback(() => {
    window.history.replaceState(null, '', location.pathname);
  }, [location.pathname]);

  // 뒤로가기 이벤트 핸들러
  useEffect(() => {
    if (!preventBack) return;

    // 현재 페이지를 히스토리에 추가하여 뒤로가기 시 같은 페이지 유지
    window.history.pushState(null, '', location.pathname);

    const handlePopState = () => {
      // 뒤로가기 시 다시 현재 페이지로
      window.history.pushState(null, '', location.pathname);
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, [preventBack, location.pathname]);

  // 새로고침 방지 이벤트 핸들러
  useEffect(() => {
    if (!preventRefresh || !shouldPreventRefresh) return;

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // 대부분의 브라우저에서 커스텀 메시지는 무시되지만, 표준을 위해 설정
      e.returnValue = '작성 중인 내용이 있습니다. 페이지를 떠나시겠습니까?';
      return e.returnValue;
    };

    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [preventRefresh, shouldPreventRefresh]);

  // 인증 상태에 따른 리다이렉트
  useEffect(() => {
    // 로그인이 필요한 페이지인데 로그인하지 않은 경우
    if (requireAuth && !isLoggedIn) {
      navigate(redirectTo, { replace: true });
      return;
    }

    // 게스트 전용 페이지인데 로그인한 경우 (로그인 페이지 등)
    if (guestOnly && isLoggedIn) {
      navigate(redirectTo, { replace: true });
      return;
    }
  }, [requireAuth, guestOnly, isLoggedIn, navigate, redirectTo]);

  return {
    replaceHistory,
    isLoggedIn,
  };
};

/**
 * 새로고침 방지 전용 훅
 * Router 의존성 없이 단순히 beforeunload 이벤트만 처리
 * 모달 등 Router 컨텍스트 외부에서 사용 가능
 */
export const usePreventRefresh = (shouldPrevent: boolean = true) => {
  useEffect(() => {
    if (!shouldPrevent) return;

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '작성 중인 내용이 있습니다. 페이지를 떠나시겠습니까?';
      return e.returnValue;
    };

    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [shouldPrevent]);
};

export default useNavigationGuard;
