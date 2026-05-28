import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { API_URL } from '../utils/api';

interface LanguageContextType {
  language: string;
  changeLanguage: (lang: string) => void;
  isLanguageReady: boolean;
}

const LanguageContext = createContext<LanguageContextType>({
  language: 'ko',
  changeLanguage: () => {},
  isLanguageReady: false,
});

export const useLanguage = () => useContext(LanguageContext);

const SUPPORTED_LANGUAGES = ['ko', 'en'];

// URL에서 ?lang= 쿼리만 제거 (다른 쿼리/해시는 유지)
const stripLangQueryFromUrl = () => {
  const url = new URL(window.location.href);
  if (!url.searchParams.has('lang')) return;
  url.searchParams.delete('lang');
  const cleaned = url.pathname + (url.searchParams.toString() ? `?${url.searchParams}` : '') + url.hash;
  window.history.replaceState(null, '', cleaned);
};

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { i18n } = useTranslation();
  const [language, setLanguage] = useState(() => localStorage.getItem('language') || '');
  const [isLanguageReady, setIsLanguageReady] = useState(false);

  const changeLanguage = useCallback((lang: string) => {
    setLanguage(lang);
    localStorage.setItem('language', lang);
    i18n.changeLanguage(lang);
  }, [i18n]);

  useEffect(() => {
    // 1순위: URL ?lang= 쿼리 (마케팅/공유 링크용, 적용 후 URL 정리)
    // 로그인 사용자의 DB 언어가 있으면 AuthContext에서 이후에 다시 덮어쓴다.
    const queryLang = new URLSearchParams(window.location.search).get('lang');
    if (queryLang && SUPPORTED_LANGUAGES.includes(queryLang)) {
      changeLanguage(queryLang);
      stripLangQueryFromUrl();
      setIsLanguageReady(true);
      return;
    }

    // 2순위: localStorage 캐시
    const stored = localStorage.getItem('language');
    if (stored) {
      i18n.changeLanguage(stored);
      setLanguage(stored);
      setIsLanguageReady(true);
      return;
    }

    // 3순위: IP 감지 API
    const detect = async () => {
      try {
        const response = await fetch(`${API_URL}/api/language/detect`);
        if (response.ok) {
          const data = await response.json();
          changeLanguage(data.language || 'ko');
        } else {
          changeLanguage('ko');
        }
      } catch {
        changeLanguage('ko');
      } finally {
        setIsLanguageReady(true);
      }
    };

    detect();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <LanguageContext.Provider value={{ language, changeLanguage, isLanguageReady }}>
      {children}
    </LanguageContext.Provider>
  );
};
