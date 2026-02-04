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
    const stored = localStorage.getItem('language');
    if (stored) {
      i18n.changeLanguage(stored);
      setLanguage(stored);
      setIsLanguageReady(true);
      return;
    }

    // localStorage에 없으면 IP 감지 API 호출
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
