import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ko from './locales/ko/common.json';
import en from './locales/en/common.json';

const SUPPORTED_LANGUAGES = ['ko', 'en'] as const;
type SupportedLanguage = typeof SUPPORTED_LANGUAGES[number];

const isSupported = (value: string | null | undefined): value is SupportedLanguage =>
  !!value && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);

// URL의 ?lang= 쿼리가 있으면 첫 페인트부터 해당 언어로 렌더 (마케팅/공유 링크용)
const queryLang = typeof window !== 'undefined'
  ? new URLSearchParams(window.location.search).get('lang')
  : null;

const initialLanguage: SupportedLanguage = isSupported(queryLang)
  ? queryLang
  : isSupported(localStorage.getItem('language'))
    ? (localStorage.getItem('language') as SupportedLanguage)
    : 'ko';

i18n.use(initReactI18next).init({
  resources: {
    ko: { translation: ko },
    en: { translation: en },
  },
  lng: initialLanguage,
  fallbackLng: 'ko',
  interpolation: {
    escapeValue: false,
  },
});

export default i18n;
