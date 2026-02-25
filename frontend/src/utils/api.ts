/**
 * API 통신 유틸리티
 * 모든 API 호출에서 사용하는 공통 함수들
 */

// API Base URL 가져오기
export const getApiBaseUrl = (): string => {
  if (process.env.REACT_APP_API_URL) {
    return process.env.REACT_APP_API_URL;
  }
  const hostname = window.location.hostname;
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname.endsWith('.localhost')
    ? 'http://localhost:8080'
    : `http://${hostname}:8080`;
};

// API_URL 상수 (레거시 호환용)
export const API_URL = getApiBaseUrl();

// 인증 헤더 생성
export const createAuthHeaders = (additionalHeaders?: Record<string, string>): Record<string, string> => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...additionalHeaders,
  };

  const accessToken = localStorage.getItem('accessToken');
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  return headers;
};

// 기본 헤더 생성 (인증 없음)
export const createBaseHeaders = (additionalHeaders?: Record<string, string>): Record<string, string> => {
  return {
    'Content-Type': 'application/json',
    ...additionalHeaders,
  };
};

interface FetchOptions extends Omit<RequestInit, 'headers'> {
  headers?: Record<string, string>;
}

interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  message?: string;
}

// 인증된 fetch 요청
export const fetchWithAuth = async <T = unknown>(
  endpoint: string,
  options: FetchOptions = {}
): Promise<ApiResponse<T>> => {
  const { headers = {}, ...rest } = options;

  const url = endpoint.startsWith('http') ? endpoint : `${getApiBaseUrl()}${endpoint}`;

  const response = await fetch(url, {
    ...rest,
    headers: createAuthHeaders(headers),
    credentials: 'include',
  });

  return response.json();
};

// 인증 없는 fetch 요청
export const fetchWithoutAuth = async <T = unknown>(
  endpoint: string,
  options: FetchOptions = {}
): Promise<ApiResponse<T>> => {
  const { headers = {}, ...rest } = options;

  const url = endpoint.startsWith('http') ? endpoint : `${getApiBaseUrl()}${endpoint}`;

  const response = await fetch(url, {
    ...rest,
    headers: createBaseHeaders(headers),
    credentials: 'include',
  });

  return response.json();
};

// GET 요청 (인증 포함)
export const apiGet = async <T = unknown>(endpoint: string): Promise<ApiResponse<T>> => {
  return fetchWithAuth<T>(endpoint, { method: 'GET' });
};

// POST 요청 (인증 포함)
export const apiPost = async <T = unknown>(
  endpoint: string,
  body?: unknown
): Promise<ApiResponse<T>> => {
  return fetchWithAuth<T>(endpoint, {
    method: 'POST',
    body: body ? JSON.stringify(body) : undefined,
  });
};

// PUT 요청 (인증 포함)
export const apiPut = async <T = unknown>(
  endpoint: string,
  body?: unknown
): Promise<ApiResponse<T>> => {
  return fetchWithAuth<T>(endpoint, {
    method: 'PUT',
    body: body ? JSON.stringify(body) : undefined,
  });
};

// PATCH 요청 (인증 포함)
export const apiPatch = async <T = unknown>(
  endpoint: string,
  body?: unknown
): Promise<ApiResponse<T>> => {
  return fetchWithAuth<T>(endpoint, {
    method: 'PATCH',
    body: body ? JSON.stringify(body) : undefined,
  });
};

// DELETE 요청 (인증 포함)
export const apiDelete = async <T = unknown>(endpoint: string): Promise<ApiResponse<T>> => {
  return fetchWithAuth<T>(endpoint, { method: 'DELETE' });
};
