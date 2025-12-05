import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  // 로그인이 필요한 페이지
  requireAuth?: boolean;
  // 비로그인 사용자만 접근 가능 (로그인/회원가입 페이지)
  guestOnly?: boolean;
  // 리다이렉트 경로
  redirectTo?: string;
}

/**
 * Protected Route 컴포넌트
 * 인증 상태에 따라 접근을 제어하고 리다이렉트 처리
 */
const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requireAuth = false,
  guestOnly = false,
  redirectTo,
}) => {
  const { isLoggedIn } = useAuth();
  const location = useLocation();

  // 로그인이 필요한 페이지인데 로그인하지 않은 경우
  if (requireAuth && !isLoggedIn) {
    // 로그인 후 원래 가려던 페이지로 돌아갈 수 있도록 state에 저장
    return <Navigate to={redirectTo || '/'} state={{ from: location }} replace />;
  }

  // 비로그인 사용자만 접근 가능한데 로그인한 경우
  if (guestOnly && isLoggedIn) {
    return <Navigate to={redirectTo || '/'} replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
