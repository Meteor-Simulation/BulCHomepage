import React, { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { getApiBaseUrl } from '../utils/api';

interface User {
  id: string;
  email: string;
  name?: string;
  rolesCode?: string; // 000: admin, 001: manager, 002: user
  language?: string;  // 사용자 언어 설정 (ko, en)
}

interface LoginResult {
  success: boolean;
  message?: string;
}

interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  isAuthReady: boolean; // 인증 상태 초기화 완료 여부
  isAdmin: boolean; // 관리자 여부 (admin 또는 manager)
  login: (email: string, password: string) => Promise<LoginResult>;
  loginWithToken: () => Promise<LoginResult>;
  logout: () => Promise<void>;
  sessionTimeLeft: number | null; // 남은 세션 시간 (초)
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

// Access Token 만료 시간 (초) — 백엔드 기본값과 동일
const ACCESS_TOKEN_LIFETIME_SEC = 600; // 10분

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const { i18n } = useTranslation();
  const [user, setUser] = useState<User | null>(null);
  const [isAuthReady, setIsAuthReady] = useState(false);
  const [sessionTimeLeft, setSessionTimeLeft] = useState<number | null>(null);
  const tokenExpiresAtRef = useRef<number | null>(null);

  // 사용자 언어 설정 적용 함수
  const applyUserLanguage = useCallback((language: string | undefined) => {
    if (language) {
      localStorage.setItem('language', language);
      i18n.changeLanguage(language);
    }
  }, [i18n]);

  // 로그아웃 함수
  const logout = useCallback(async () => {
    try {
      await fetch(`${getApiBaseUrl()}/api/auth/logout`, {
        method: 'POST',
        credentials: 'include',
      });
    } catch (error) {
      // logout API error
    }

    setUser(null);
    setSessionTimeLeft(null);
    tokenExpiresAtRef.current = null;
  }, []);

  // 토큰 갱신 함수 (쿠키 기반 — REFRESH_TOKEN 쿠키가 자동 전송됨)
  const refreshAccessToken = useCallback(async (): Promise<boolean> => {
    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({}),
      });

      const result = await response.json();

      if (result.success && result.data) {
        // 토큰 만료 시간 갱신 (쿠키는 백엔드가 자동 설정)
        const expiresIn = result.data.expiresIn;
        if (expiresIn) {
          tokenExpiresAtRef.current = Date.now() + expiresIn * 1000;
        }
        return true;
      }
      return false;
    } catch (error) {
      return false;
    }
  }, []);

  // /api/auth/me로 현재 로그인 상태 확인
  const fetchCurrentUser = useCallback(async (): Promise<User | null> => {
    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/me`, {
        method: 'GET',
        credentials: 'include',
      });

      if (!response.ok) return null;

      const result = await response.json();
      if (result.success && result.data) {
        return {
          id: result.data.id,
          email: result.data.email,
          name: result.data.name || result.data.email,
          rolesCode: result.data.rolesCode,
          language: result.data.language,
        };
      }
      return null;
    } catch {
      return null;
    }
  }, []);

  // 세션 타이머 및 자동 갱신
  useEffect(() => {
    if (!user) return;

    const checkSession = async () => {
      if (!tokenExpiresAtRef.current) return;

      const timeLeft = Math.floor((tokenExpiresAtRef.current - Date.now()) / 1000);

      if (timeLeft <= 0) {
        // 토큰 만료됨 — 갱신 시도
        const refreshed = await refreshAccessToken();
        if (!refreshed) {
          alert('세션이 만료되었습니다. 다시 로그인해주세요.');
          logout();
        }
      } else if (timeLeft <= 60) {
        // 1분 이하 남았을 때 자동 갱신
        await refreshAccessToken();
      } else {
        setSessionTimeLeft(timeLeft);
      }
    };

    checkSession();
    const interval = setInterval(checkSession, 1000);
    return () => clearInterval(interval);
  }, [user, logout, refreshAccessToken]);

  // 페이지 로드 시 쿠키 기반으로 로그인 상태 복원
  useEffect(() => {
    const initAuth = async () => {
      const userData = await fetchCurrentUser();
      if (userData) {
        setUser(userData);
        tokenExpiresAtRef.current = Date.now() + ACCESS_TOKEN_LIFETIME_SEC * 1000;
        applyUserLanguage(userData.language);
      } else {
        // 쿠키 만료 — refresh 시도
        const refreshed = await refreshAccessToken();
        if (refreshed) {
          const refreshedUser = await fetchCurrentUser();
          if (refreshedUser) {
            setUser(refreshedUser);
            applyUserLanguage(refreshedUser.language);
          }
        }
      }
      setIsAuthReady(true);
    };

    initAuth();
  }, [fetchCurrentUser, refreshAccessToken, applyUserLanguage]);

  const login = async (email: string, password: string): Promise<LoginResult> => {
    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      });

      const result = await response.json();

      if (result.success && result.data) {
        const userInfo = result.data.user;
        const userData: User = {
          id: userInfo.id,
          email: userInfo.email,
          name: userInfo.name || userInfo.email,
          rolesCode: userInfo.rolesCode,
          language: userInfo.language,
        };

        setUser(userData);

        // 토큰 만료 시간 추적 (쿠키는 백엔드가 설정, 여기선 타이머용)
        const expiresIn = result.data.expiresIn;
        tokenExpiresAtRef.current = Date.now() + (expiresIn ? expiresIn * 1000 : ACCESS_TOKEN_LIFETIME_SEC * 1000);

        applyUserLanguage(userInfo.language);

        return { success: true };
      }
      return { success: false, message: result.message || '로그인에 실패했습니다.' };
    } catch (error) {
      return { success: false, message: '로그인 중 오류가 발생했습니다.' };
    }
  };

  // OAuth 소셜 로그인용 — 쿠키가 이미 설정된 상태에서 사용자 정보 조회
  const loginWithToken = async (): Promise<LoginResult> => {
    try {
      const userData = await fetchCurrentUser();
      if (userData) {
        setUser(userData);
        tokenExpiresAtRef.current = Date.now() + ACCESS_TOKEN_LIFETIME_SEC * 1000;
        applyUserLanguage(userData.language);
        return { success: true };
      }
      return { success: false, message: '사용자 정보를 가져올 수 없습니다.' };
    } catch (error) {
      return { success: false, message: '로그인 중 오류가 발생했습니다.' };
    }
  };

  const isAdmin = user?.rolesCode === '000' || user?.rolesCode === '001';

  return (
    <AuthContext.Provider value={{ user, isLoggedIn: !!user, isAuthReady, isAdmin, login, loginWithToken, logout, sessionTimeLeft }}>
      {children}
    </AuthContext.Provider>
  );
};
