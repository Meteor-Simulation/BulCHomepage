import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';

interface User {
  id: number;
  email: string;
  name?: string;
}

interface LoginResult {
  success: boolean;
  message?: string;
}

interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<LoginResult>;
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
        refreshAccessToken().then(success => {
          if (success) {
            setUser(JSON.parse(storedUser));
          } else {
            logout();
          }
        });
      }
    }
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

  return (
    <AuthContext.Provider value={{ user, isLoggedIn: !!user, login, logout, sessionTimeLeft }}>
      {children}
    </AuthContext.Provider>
  );
};
