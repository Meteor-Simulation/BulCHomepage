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
 * 로그인/로그아웃 후 히스토리 정리 훅
 * 로그인 성공 또는 로그아웃 후 호출하여 이전 히스토리를 정리
 */
export const useAuthNavigation = () => {
  const navigate = useNavigate();

  // 로그인 성공 후 호출 - 히스토리를 대체하여 뒤로가기 시 로그인 페이지로 가지 않도록
  const navigateAfterLogin = useCallback((path: string = '/') => {
    navigate(path, { replace: true });
  }, [navigate]);

  // 로그아웃 후 호출 - 히스토리를 대체하여 뒤로가기 시 인증 페이지로 가지 않도록
  const navigateAfterLogout = useCallback((path: string = '/') => {
    navigate(path, { replace: true });
  }, [navigate]);

  // 회원가입 완료 후 호출
  const navigateAfterSignup = useCallback((path: string = '/') => {
    navigate(path, { replace: true });
  }, [navigate]);

  // 결제 완료 후 호출 - 결제 페이지로 뒤로가기 방지
  const navigateAfterPayment = useCallback((path: string = '/') => {
    // 결제 완료 페이지로 이동하면서 히스토리 대체
    navigate(path, { replace: true });
  }, [navigate]);

  return {
    navigateAfterLogin,
    navigateAfterLogout,
    navigateAfterSignup,
    navigateAfterPayment,
  };
};

export default useNavigationGuard;
