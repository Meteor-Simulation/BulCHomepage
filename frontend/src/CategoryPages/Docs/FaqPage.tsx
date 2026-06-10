import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './Docs.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import Seo from '../../components/Seo';
import { buildFaqJsonLd } from '../../seo/jsonld';
import { FAQ_CONTENT } from './faqContent';

const FaqPage: React.FC = () => {
  const { i18n } = useTranslation();
  const lang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const categories = FAQ_CONTENT[lang];
  const allItems = categories.flatMap((c) => c.items);

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

          {categories.map((category) => (
            <section className="faq-category" key={category.title}>
              <h2>{category.title}</h2>
              {category.items.map((item) => (
                <details className="faq-item" key={item.question} open>
                  <summary>
                    <h3 style={{ fontSize: 'inherit', margin: 0, fontWeight: 'inherit' }}>
                      {item.question}
                    </h3>
                  </summary>
                  <p className="faq-answer">{item.answer}</p>
                </details>
              ))}
            </section>
          ))}

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
