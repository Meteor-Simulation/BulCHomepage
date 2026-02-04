import { useState, useEffect, useCallback, useRef } from 'react';

interface UseScrollNavOptions {
  sectionIds: string[];
  defaultId?: string;
}

/**
 * 스크롤 기반 앵커 네비게이션 훅
 *
 * IntersectionObserver로 현재 보이는 섹션을 추적하고,
 * scrollToSection으로 부드럽게 스크롤합니다.
 */
export function useScrollNav({ sectionIds, defaultId }: UseScrollNavOptions) {
  const fallbackId = defaultId || sectionIds[0] || '';
  const [activeSection, setActiveSection] = useState<string>(fallbackId);
  const isScrollingRef = useRef(false);

  // 섹션으로 스크롤
  const scrollToSection = useCallback((id: string) => {
    const el = document.getElementById(id);
    if (!el) return;

    isScrollingRef.current = true;
    setActiveSection(id);

    el.scrollIntoView({ behavior: 'smooth' });

    // URL 해시 업데이트 (React Router 충돌 방지)
    window.history.replaceState(null, '', `#${id}`);

    // 스크롤 완료 후 플래그 해제
    setTimeout(() => {
      isScrollingRef.current = false;
    }, 1000);
  }, []);

  // IntersectionObserver로 현재 보이는 섹션 추적
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (isScrollingRef.current) return;

        for (const entry of entries) {
          if (entry.isIntersecting) {
            const id = entry.target.id;
            if (sectionIds.includes(id)) {
              setActiveSection(id);
              window.history.replaceState(null, '', `#${id}`);
            }
          }
        }
      },
      {
        rootMargin: '-140px 0px -40% 0px',
        threshold: 0.1,
      }
    );

    const elements = sectionIds
      .map((id) => document.getElementById(id))
      .filter(Boolean) as HTMLElement[];

    elements.forEach((el) => observer.observe(el));

    return () => observer.disconnect();
  }, [sectionIds]);

  // 마운트 시 URL 해시 확인하여 초기 스크롤
  useEffect(() => {
    const hash = window.location.hash.replace('#', '');
    if (hash && sectionIds.includes(hash)) {
      // DOM이 렌더링된 후 스크롤
      requestAnimationFrame(() => {
        scrollToSection(hash);
      });
    }
    // 마운트 시 1회만 실행
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { activeSection, scrollToSection };
}

export default useScrollNav;
