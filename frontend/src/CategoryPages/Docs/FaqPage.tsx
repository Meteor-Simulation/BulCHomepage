import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './Docs.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import Seo from '../../components/Seo';
import { buildFaqJsonLd } from '../../seo/jsonld';
import { FAQ_CONTENT, FaqItem } from './faqContent';

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const highlight = (text: string, query: string): React.ReactNode => {
  const q = query.trim();
  if (!q) return text;
  const parts = text.split(new RegExp(`(${escapeRegExp(q)})`, 'gi'));
  return parts.map((part, i) =>
    part.toLowerCase() === q.toLowerCase() ? (
      <mark className="faq-mark" key={i}>
        {part}
      </mark>
    ) : (
      part
    )
  );
};

const SearchIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
);

const FaqPage: React.FC = () => {
  const { i18n } = useTranslation();
  const lang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const categories = FAQ_CONTENT[lang];
  const allItems = categories.flatMap((c) => c.items);

  const [query, setQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('all');
  const [openSet, setOpenSet] = useState<Set<string>>(new Set());

  const normalizedQuery = query.trim().toLowerCase();

  const matchesQuery = (item: FaqItem) =>
    !normalizedQuery ||
    item.question.toLowerCase().includes(normalizedQuery) ||
    item.answer.toLowerCase().includes(normalizedQuery);

  const visibleCategories = useMemo(
    () =>
      categories
        .filter((c) => activeCategory === 'all' || c.title === activeCategory)
        .map((c) => ({ ...c, items: c.items.filter(matchesQuery) }))
        .filter((c) => c.items.length > 0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [categories, activeCategory, normalizedQuery]
  );

  const visibleCount = visibleCategories.reduce((n, c) => n + c.items.length, 0);
  const totalMatchCount = categories.reduce(
    (n, c) => n + c.items.filter(matchesQuery).length,
    0
  );
  const allVisibleOpen =
    visibleCount > 0 &&
    visibleCategories.every((c) => c.items.every((item) => openSet.has(item.question)));

  const handleQueryChange = (value: string) => {
    setQuery(value);
    const nq = value.trim().toLowerCase();
    if (nq) {
      // 검색 중에는 매칭된 항목을 자동으로 펼친다
      setOpenSet(
        new Set(
          allItems
            .filter(
              (item) =>
                item.question.toLowerCase().includes(nq) ||
                item.answer.toLowerCase().includes(nq)
            )
            .map((item) => item.question)
        )
      );
    }
  };

  const toggleItem = (question: string) => {
    setOpenSet((prev) => {
      const next = new Set(prev);
      if (next.has(question)) {
        next.delete(question);
      } else {
        next.add(question);
      }
      return next;
    });
  };

  const toggleAll = () => {
    if (allVisibleOpen) {
      setOpenSet(new Set());
    } else {
      setOpenSet(new Set(visibleCategories.flatMap((c) => c.items.map((i) => i.question))));
    }
  };

  return (
    <div className="app">
      <Seo
        title={
          lang === 'en'
            ? 'FAQ | BUL:C Fire Simulation Software'
            : '자주 묻는 질문(FAQ) | BUL:C 화재 시뮬레이션'
        }
        description={
          lang === 'en'
            ? 'Frequently asked questions about BUL:C fire simulation software: licensing, pricing, refunds, system requirements, and technical support.'
            : 'BUL:C 화재 시뮬레이션 소프트웨어에 대해 자주 묻는 질문 모음. 라이선스, 요금제, 환불, 시스템 요구사항, 기술 지원 안내.'
        }
        path="/faq"
        jsonLd={buildFaqJsonLd(allItems)}
      />
      <Header logoText="BUL:C" />
      <main className="main-content sub-page">
        <div className="docs-container">
          <h1>{lang === 'en' ? 'Frequently Asked Questions' : '자주 묻는 질문 (FAQ)'}</h1>
          <p className="docs-lead">
            {lang === 'en'
              ? 'Answers to common questions about BUL:C. If you cannot find what you are looking for, contact us at simul@msimul.com.'
              : 'BUL:C에 대해 자주 묻는 질문을 모았습니다. 원하는 답을 찾지 못하셨다면 simul@msimul.com 으로 문의해 주세요.'}
          </p>

          <div className="faq-toolbar">
            <div className="faq-search">
              <SearchIcon className="faq-search-icon" />
              <input
                type="search"
                value={query}
                onChange={(e) => handleQueryChange(e.target.value)}
                placeholder={
                  lang === 'en' ? 'Search questions…' : '궁금한 내용을 검색해 보세요'
                }
                aria-label={lang === 'en' ? 'Search FAQ' : 'FAQ 검색'}
              />
              {query && (
                <button
                  type="button"
                  className="faq-search-clear"
                  onClick={() => handleQueryChange('')}
                  aria-label={lang === 'en' ? 'Clear search' : '검색어 지우기'}
                >
                  ×
                </button>
              )}
            </div>
            <button type="button" className="faq-expand-btn" onClick={toggleAll}>
              {allVisibleOpen
                ? lang === 'en'
                  ? 'Collapse all'
                  : '모두 접기'
                : lang === 'en'
                  ? 'Expand all'
                  : '모두 펼치기'}
            </button>
          </div>

          <div className="faq-chips">
            <button
              type="button"
              className={`faq-chip${activeCategory === 'all' ? ' faq-chip--active' : ''}`}
              aria-pressed={activeCategory === 'all'}
              onClick={() => setActiveCategory('all')}
            >
              {lang === 'en' ? 'All' : '전체'}
              <span className="faq-chip-count">{totalMatchCount}</span>
            </button>
            {categories.map((category) => {
              const count = category.items.filter(matchesQuery).length;
              const active = activeCategory === category.title;
              return (
                <button
                  type="button"
                  key={category.title}
                  className={`faq-chip${active ? ' faq-chip--active' : ''}${
                    count === 0 ? ' faq-chip--muted' : ''
                  }`}
                  aria-pressed={active}
                  onClick={() => setActiveCategory(active ? 'all' : category.title)}
                >
                  {category.title}
                  <span className="faq-chip-count">{count}</span>
                </button>
              );
            })}
          </div>

          {normalizedQuery && visibleCount > 0 && (
            <p className="faq-result-count">
              {lang === 'en'
                ? `${visibleCount} result${visibleCount === 1 ? '' : 's'} for “${query.trim()}”`
                : `“${query.trim()}” 검색 결과 ${visibleCount}건`}
            </p>
          )}

          {visibleCategories.map((category) => (
            <section className="faq-category" key={category.title}>
              <h2>{category.title}</h2>
              {category.items.map((item) => (
                <details
                  className="faq-item"
                  key={item.question}
                  open={openSet.has(item.question)}
                >
                  <summary
                    onClick={(e) => {
                      e.preventDefault();
                      toggleItem(item.question);
                    }}
                  >
                    <h3 className="faq-question">{highlight(item.question, query)}</h3>
                  </summary>
                  <p className="faq-answer">{highlight(item.answer, query)}</p>
                </details>
              ))}
            </section>
          ))}

          {visibleCount === 0 && (
            <div className="faq-empty">
              <p>
                {lang === 'en'
                  ? `No results for “${query.trim()}”.`
                  : `“${query.trim()}”에 대한 검색 결과가 없습니다.`}
              </p>
              <p>
                {lang === 'en' ? (
                  <>
                    Try a different keyword, or{' '}
                    <a href="mailto:simul@msimul.com">email us</a> directly.
                  </>
                ) : (
                  <>
                    다른 키워드로 검색해 보시거나{' '}
                    <a href="mailto:simul@msimul.com">이메일로 문의</a>해 주세요.
                  </>
                )}
              </p>
            </div>
          )}

          <div className="faq-cta">
            <h2>
              {lang === 'en' ? 'Still have questions?' : '원하는 답을 찾지 못하셨나요?'}
            </h2>
            <p>
              {lang === 'en'
                ? 'Send us an email and we will get back to you as soon as possible.'
                : '이메일로 문의하시면 빠르게 답변해 드리겠습니다.'}
            </p>
            <div className="faq-cta-actions">
              <a className="faq-cta-btn" href="mailto:simul@msimul.com">
                simul@msimul.com
              </a>
              <Link className="faq-cta-link" to="/board">
                {lang === 'en' ? 'Visit the board' : '게시판 바로가기'}
              </Link>
            </div>
          </div>

          <aside className="docs-related">
            <h2>{lang === 'en' ? 'See also' : '함께 보기'}</h2>
            <ul>
              <li>
                <Link to="/docs/aset-rset">
                  {lang === 'en' ? 'What are ASET and RSET?' : 'ASET과 RSET이란?'}
                </Link>
              </li>
              <li>
                <Link to="/docs/performance-based-design">
                  {lang === 'en' ? 'What is Performance-Based Design (PBD)?' : '성능위주설계(PBD)란?'}
                </Link>
              </li>
              <li>
                <Link to="/docs/fds-gpu-acceleration">
                  {lang === 'en'
                    ? 'FDS fire simulation and GPU acceleration'
                    : 'FDS 기반 화재 시뮬레이션과 GPU 가속'}
                </Link>
              </li>
              <li>
                <Link to="/download">
                  {lang === 'en' ? 'Download BUL:C free trial' : 'BUL:C 무료 체험 다운로드'}
                </Link>
              </li>
            </ul>
          </aside>
        </div>
      </main>
      <Footer />
    </div>
  );
};

export default FaqPage;
