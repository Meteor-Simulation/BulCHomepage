import React from 'react';
import { Link, useParams } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './Docs.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import ErrorPage from '../../components/ErrorPage';
import Seo from '../../components/Seo';
import { buildArticleJsonLd } from '../../seo/jsonld';
import { getArticleBySlug } from './articles';

const DocsArticlePage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const article = getArticleBySlug(slug);

  if (!article) {
    const notFoundError = new Error('요청하신 문서를 찾을 수 없습니다.');
    (notFoundError as any).code = 404;
    return (
      <>
        <Seo title="문서를 찾을 수 없습니다 | BUL:C" noindex />
        <ErrorPage error={notFoundError} />
      </>
    );
  }

  const path = `/docs/${article.slug}`;

  return (
    <div className="app">
      <Seo
        title={article.seoTitle}
        description={article.description}
        path={path}
        jsonLd={buildArticleJsonLd({
          title: article.title,
          description: article.description,
          path,
          datePublished: article.datePublished,
        })}
      />
      <Header logoText="BUL:C" />
      <main className="main-content sub-page">
        <article className="docs-container docs-article" lang="ko">
          <h1>{article.title}</h1>
          <p className="docs-meta">
            메테오시뮬레이션 기술 문서 · {article.datePublished}
          </p>

          {article.sections.map((section, i) => (
            <section key={i}>
              {section.heading && <h2>{section.heading}</h2>}
              <div dangerouslySetInnerHTML={{ __html: section.html }} />
            </section>
          ))}

          <aside className="docs-related">
            <h2>함께 보기</h2>
            <ul>
              {article.related.map((link) => (
                <li key={link.to}>
                  <Link to={link.to}>{link.label}</Link>
                </li>
              ))}
            </ul>
          </aside>
        </article>
      </main>
      <Footer />
    </div>
  );
};

export default DocsArticlePage;
