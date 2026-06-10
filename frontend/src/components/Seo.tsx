import React, { useEffect } from 'react';

/** 대표(canonical) 도메인. 서브도메인 페이지는 origin prop으로 재정의한다. */
export const CANONICAL_BASE = 'https://bulc.msimul.com';

const SITE_NAME = '메테오시뮬레이션';
const DEFAULT_OG_IMAGE = '/og-image.png';

export interface SeoProps {
  title: string;
  description?: string;
  /** canonical 경로 (예: '/download'). origin과 합쳐 canonical URL이 된다. */
  path?: string;
  /** canonical origin 재정의 (meteor/vr 서브도메인 페이지용) */
  origin?: string;
  /** OG 이미지. '/'로 시작하면 origin이 붙는다. */
  ogImage?: string;
  /** 검색 색인 제외 (마이페이지, 결제 결과, 404 등) */
  noindex?: boolean;
  /** 페이지별 JSON-LD 구조화 데이터 */
  jsonLd?: object | object[];
}

/**
 * 페이지별 SEO 메타 태그 컴포넌트.
 *
 * React 19의 문서 메타데이터 호이스팅을 사용하므로 <title>/<meta>/<link>가
 * 자동으로 <head>로 이동한다 (별도 라이브러리 불필요).
 *
 * index.html에 박혀 있는 기본 메타 태그(data-seo-default 표시)는 마운트 시점에
 * 제거하여, JS 실행 후 또는 프리렌더 스냅숏에 canonical/title이 중복 노출되지
 * 않도록 한다. JS를 실행하지 않는 크롤러는 index.html의 기본 태그를 그대로 본다.
 */
const Seo: React.FC<SeoProps> = ({
  title,
  description,
  path = '/',
  origin = CANONICAL_BASE,
  ogImage = DEFAULT_OG_IMAGE,
  noindex = false,
  jsonLd,
}) => {
  useEffect(() => {
    document.querySelectorAll('[data-seo-default]').forEach((el) => el.remove());
  }, []);

  const canonical = path === '/' ? origin : `${origin}${path}`;
  const image = ogImage.startsWith('/') ? `${origin}${ogImage}` : ogImage;
  const jsonLdList = jsonLd ? (Array.isArray(jsonLd) ? jsonLd : [jsonLd]) : [];

  return (
    <>
      <title>{title}</title>
      {description && <meta name="description" content={description} />}
      {noindex ? (
        <meta name="robots" content="noindex, nofollow" />
      ) : (
        <link rel="canonical" href={canonical} />
      )}
      <meta property="og:type" content="website" />
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:title" content={title} />
      {description && <meta property="og:description" content={description} />}
      <meta property="og:url" content={canonical} />
      <meta property="og:image" content={image} />
      <meta property="og:locale" content="ko_KR" />
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={title} />
      {description && <meta name="twitter:description" content={description} />}
      <meta name="twitter:image" content={image} />
      {jsonLdList.map((schema, i) => (
        <script
          key={i}
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(schema) }}
        />
      ))}
    </>
  );
};

export default Seo;
