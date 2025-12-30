import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';

interface User {
  id: string;
  email: string;
  name?: string;
  rolesCode?: string; // 000: admin, 001: manager, 002: user
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
  loginWithToken: (accessToken: string, refreshToken: string) => Promise<LoginResult>;
  logout: () => void;
  sessionTimeLeft: number | null; // 남은 세션 시간 (초)
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// JWT 토큰에서 만료 시간 추출
const getTokenExpiration = (token: string): number | null => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp ? payload.exp * 1000 : null; // 밀리초로 변환
  } catch {
    return null;
  }
};

// API Base URL 가져오기
const getApiBaseUrl = () => {
  return window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : `http://${window.location.hostname}:8080`;
};

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

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isAuthReady, setIsAuthReady] = useState(false); // 인증 상태 초기화 완료 여부
  const [sessionTimeLeft, setSessionTimeLeft] = useState<number | null>(null);

  // 로그아웃 함수
  const logout = useCallback(() => {
    setUser(null);
    setSessionTimeLeft(null);
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('tokenExpiration');
  }, []);

  // 토큰 갱신 함수
  const refreshAccessToken = useCallback(async (): Promise<boolean> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return false;

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/auth/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({ refreshToken }),
      });

      const result = await response.json();

      if (result.success && result.data) {
        const { accessToken, refreshToken: newRefreshToken } = result.data;
        localStorage.setItem('accessToken', accessToken);
        if (newRefreshToken) {
          localStorage.setItem('refreshToken', newRefreshToken);
        }

        const expiration = getTokenExpiration(accessToken);
        if (expiration) {
          localStorage.setItem('tokenExpiration', expiration.toString());
        }

        return true;
      }
      return false;
    } catch (error) {
      console.error('Token refresh error:', error);
      return false;
    }
  }, []);

  // 세션 타이머 및 자동 갱신
  useEffect(() => {
    if (!user) return;

    const checkSession = async () => {
      const expiration = localStorage.getItem('tokenExpiration');
      if (!expiration) return;

      const expirationTime = parseInt(expiration, 10);
      const now = Date.now();
      const timeLeft = Math.floor((expirationTime - now) / 1000);

      if (timeLeft <= 0) {
        // 토큰 만료됨 - 갱신 시도
        const refreshed = await refreshAccessToken();
        if (!refreshed) {
          alert('세션이 만료되었습니다. 다시 로그인해주세요.');
          logout();
        }
      } else if (timeLeft <= 60) {
        // 1분 이하 남았을 때 자동 갱신 시도
        await refreshAccessToken();
      } else {
        setSessionTimeLeft(timeLeft);
      }
    };

    // 초기 체크
    checkSession();

    // 매초 세션 시간 업데이트
    const interval = setInterval(checkSession, 1000);

    return () => clearInterval(interval);
  }, [user, logout, refreshAccessToken]);

  // 페이지 로드 시 로컬 스토리지에서 사용자 정보 복원
  useEffect(() => {
    const initAuth = async () => {
      const storedUser = localStorage.getItem('user');
      const storedToken = localStorage.getItem('accessToken');

      if (storedUser && storedToken) {
        // 토큰 만료 확인
        const expiration = getTokenExpiration(storedToken);
        if (expiration && expiration > Date.now()) {
          setUser(JSON.parse(storedUser));
          localStorage.setItem('tokenExpiration', expiration.toString());
        } else {
          // 토큰이 만료되었으면 갱신 시도
          const success = await refreshAccessToken();
          if (success) {
            setUser(JSON.parse(storedUser));
          } else {
            logout();
          }
        }
      }
      setIsAuthReady(true); // 초기화 완료
    };

    initAuth();
  }, [logout, refreshAccessToken]);

  const login = async (email: string, password: string): Promise<LoginResult> => {
    try {
      console.log('Attempting login for:', email);
      const response = await fetch(`${getApiBaseUrl()}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      });

      console.log('Response status:', response.status);
      const result = await response.json();
      console.log('Response data:', result);

      if (result.success && result.data) {
        const { accessToken, refreshToken, user: userInfo } = result.data;
        const userData: User = {
          id: userInfo.id,
          email: userInfo.email,
          name: userInfo.name || userInfo.email,
          rolesCode: userInfo.rolesCode,
        };

        setUser(userData);
        localStorage.setItem('user', JSON.stringify(userData));
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);

        // 토큰 만료 시간 저장
        const expiration = getTokenExpiration(accessToken);
        if (expiration) {
          localStorage.setItem('tokenExpiration', expiration.toString());
        }

        console.log('Login successful, user:', userData);
        return { success: true };
      }
      console.log('Login failed - success:', result.success, 'data:', result.data);
      return { success: false, message: result.message || '로그인에 실패했습니다.' };
    } catch (error) {
      console.error('Login error:', error);
      return { success: false, message: '로그인 중 오류가 발생했습니다.' };
    }
  };

  // OAuth 토큰으로 로그인 (소셜 로그인용)
  const loginWithToken = async (accessToken: string, refreshToken: string): Promise<LoginResult> => {
    try {
      // 토큰 저장
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);

      // 토큰 만료 시간 저장
      const expiration = getTokenExpiration(accessToken);
      if (expiration) {
        localStorage.setItem('tokenExpiration', expiration.toString());
      }

      // 백엔드에서 사용자 정보 조회
      const response = await fetch(`${getApiBaseUrl()}/api/auth/me`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
        },
      });

      if (!response.ok) {
        throw new Error('사용자 정보를 가져올 수 없습니다.');
      }

      const result = await response.json();

      if (result.success && result.data) {
        const userInfo = result.data;
        const userData: User = {
          id: userInfo.id,
          email: userInfo.email,
          name: userInfo.name || userInfo.email,
          rolesCode: userInfo.rolesCode,
        };

        setUser(userData);
        localStorage.setItem('user', JSON.stringify(userData));

        return { success: true };
      }

      return { success: false, message: result.message || '사용자 정보를 가져올 수 없습니다.' };
    } catch (error) {
      console.error('OAuth login error:', error);
      // 실패 시 토큰 정리
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('tokenExpiration');
      return { success: false, message: '로그인 중 오류가 발생했습니다.' };
    }
  };

  // 관리자 여부 확인 (000: admin, 001: manager)
  const isAdmin = user?.rolesCode === '000' || user?.rolesCode === '001';

  return (
    <AuthContext.Provider value={{ user, isLoggedIn: !!user, isAuthReady, isAdmin, login, loginWithToken, logout, sessionTimeLeft }}>
      {children}
    </AuthContext.Provider>
  );
};
