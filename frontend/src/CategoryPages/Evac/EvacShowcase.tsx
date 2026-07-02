import React, { useEffect, useRef, useState } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import 'pretendard/dist/web/static/pretendard.css';
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/500.css';
import '@fontsource/jetbrains-mono/700.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import './evacShowcase.scoped.css';
import contentHtml from './evacShowcase.content.html?raw';
import { initEvacShowcase } from './evacShowcase.appjs';

// 헤더 아래 서브네비 항목 — 본문 섹션 id로 스무스 스크롤 (원본 topbar의 앵커 이동 복원)
const SECTIONS = [
  { id: 'models', label: '모델' },
  { id: 'gallery', label: '예제' },
  { id: 'roadmap', label: '로드맵' },
  { id: 'refs', label: '레퍼런스' },
];

// 본문은 app.js가 명령형으로 DOM을 조작(.in reveal·캔버스·시뮬)한다. 부모가 re-render되면
// React가 dangerouslySetInnerHTML을 다시 커밋해 그 변경을 전부 초기화(내용이 opacity:0으로 리셋)하므로,
// memo로 격리해 마운트 이후 절대 재렌더/재커밋되지 않게 한다.
const EvacContent = React.memo(
  React.forwardRef<HTMLDivElement>((_props, ref) => (
    <div className="evac-showcase-root" ref={ref} dangerouslySetInnerHTML={{ __html: contentHtml }} />
  ))
);
EvacContent.displayName = 'EvacContent';

/**
 * 피난 동역학 시뮬레이션 쇼케이스 (evac-sim) — React 이관 페이지.
 * - 홈페이지 공통 Header/Footer 사용(단일 소스)
 * - 본문은 원본 정적 HTML을 그대로 주입(dangerouslySetInnerHTML), 스타일은 .evac-showcase-root 로 스코핑
 * - 캔버스 시뮬레이션(app.js)은 effect에서 초기화하고, 언마운트 시 rAF/전역 리스너를 정리
 */
const EvacShowcase: React.FC = () => {
  const [activeSection, setActiveSection] = useState('');
  const rootRef = useRef<HTMLDivElement>(null);

  // 고정 헤더+서브네비가 본문 상단(히어로)을 가리지 않도록 그 높이만큼 상단 여백 부여
  useEffect(() => {
    const apply = () => {
      const header = document.querySelector('.header') as HTMLElement | null;
      const subH = (Array.from(document.querySelectorAll('.sub-nav')) as HTMLElement[])
        .reduce((a, el) => a + el.offsetHeight, 0);
      if (rootRef.current) rootRef.current.style.paddingTop = ((header?.offsetHeight || 0) + subH) + 'px';
    };
    apply();
    window.addEventListener('resize', apply);
    return () => window.removeEventListener('resize', apply);
  }, []);

  // 서브네비 클릭 → 해당 섹션으로 스무스 스크롤 (고정 헤더+서브네비 높이만큼 오프셋)
  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (!el) return;
    const header = document.querySelector('.header') as HTMLElement | null;
    const subnav = document.querySelector('.sub-nav') as HTMLElement | null;
    const offset = (header?.offsetHeight || 0) + (subnav?.offsetHeight || 0) + 12;
    window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - offset, behavior: 'smooth' });
    setActiveSection(id);
  };

  useEffect(() => {
    // app.js가 참조하는 전역 KaTeX 주입 (CDN 대신 npm)
    (window as any).katex = katex;

    let destroyed = false;
    const pendingRaf = new Set<number>();
    const origRaf = window.requestAnimationFrame.bind(window);
    const origCaf = window.cancelAnimationFrame.bind(window);
    // 언마운트 후 애니메이션 루프가 계속 도는 것을 막기 위해 rAF를 감싼다.
    (window as any).requestAnimationFrame = (cb: FrameRequestCallback): number => {
      const id = origRaf((t) => {
        pendingRaf.delete(id);
        if (!destroyed) cb(t);
      });
      pendingRaf.add(id);
      return id;
    };

    // init 동안 추가되는 window/document 리스너만 캡처(정리용). 요소 리스너는 DOM 제거 시 함께 사라진다.
    const captured: Array<{ target: EventTarget; type: string; listener: any; opts: any }> = [];
    const wrap = (target: EventTarget) => {
      const orig = target.addEventListener.bind(target);
      (target as any).addEventListener = (type: string, listener: any, opts?: any) => {
        captured.push({ target, type, listener, opts });
        return orig(type, listener, opts);
      };
      return orig;
    };
    const restoreWinAdd = wrap(window);
    const restoreDocAdd = wrap(document);

    try {
      initEvacShowcase(); // 본문이 이미 마운트된 뒤 실행 → 캔버스/갤러리/모달 초기화
    } catch (e) {
      // 초기화 실패가 페이지 렌더를 막지 않도록 무시(캔버스만 비활성)
      // eslint-disable-next-line no-console
      console.error('[EvacShowcase] init 실패:', e);
    }

    // 동기 init이 끝났으니 addEventListener 원복(요소 리스너까지 캡처하지 않도록)
    (window as any).addEventListener = restoreWinAdd;
    (document as any).addEventListener = restoreDocAdd;

    return () => {
      destroyed = true;
      pendingRaf.forEach((id) => origCaf(id));
      pendingRaf.clear();
      (window as any).requestAnimationFrame = origRaf;
      captured.forEach(({ target, type, listener, opts }) => target.removeEventListener(type, listener, opts));
    };
  }, []);

  // 스크롤 위치에 따라 서브네비 활성 섹션 하이라이트 (홈페이지 서브네비와 동일 동작)
  useEffect(() => {
    const header = document.querySelector('.header') as HTMLElement | null;
    const subnav = document.querySelector('.sub-nav') as HTMLElement | null;
    const offset = (header?.offsetHeight || 0) + (subnav?.offsetHeight || 0);
    const els = SECTIONS.map((s) => document.getElementById(s.id)).filter(Boolean) as HTMLElement[];
    if (!els.length) return;
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => { if (e.isIntersecting) setActiveSection((e.target as HTMLElement).id); });
      },
      { rootMargin: `-${offset + 20}px 0px -65% 0px`, threshold: 0 }
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <>
      <Header
        logoText="BUL:C"
        hideAuth
        hideContact
        showSubNav
        subNavItems={SECTIONS}
        activeSubNav={activeSection}
        onSubNavChange={scrollToSection}
      />
      <EvacContent ref={rootRef} />
      <Footer />
    </>
  );
};

export default EvacShowcase;
