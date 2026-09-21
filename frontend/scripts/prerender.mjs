/**
 * 빌드 후 정적 프리렌더링 스크립트.
 *
 * vite preview로 dist/를 서빙하고, 헤드리스 브라우저로 주요 라우트를 렌더링한
 * HTML 스냅숏을 dist/<route>/index.html 로 저장한다.
 * JS를 실행하지 않는 크롤러(GPTBot, ClaudeBot, PerplexityBot, 네이버 Yeti 등)가
 * 페이지 본문과 메타 태그를 읽을 수 있게 하는 것이 목적이다 (SEO/GEO).
 *
 * - 대상 라우트는 API 의존이 없는 정적 콘텐츠 페이지만 선정한다.
 *   (루트 '/'는 가격 API 의존 + 서브도메인별 분기 때문에 제외 — 모든 서브도메인이
 *    같은 dist를 서빙하므로 루트를 프리렌더하면 meteor/vr 도메인에 bulc 스냅숏이 노출됨)
 * - Cloudflare Pages는 정적 자산을 _redirects(SPA fallback)보다 먼저 매칭하므로
 *   dist/download/index.html 이 /download 요청에 그대로 서빙된다.
 * - 빌드 환경에 Chrome이 없으면 경고 후 건너뛴다(배포는 SPA로 동작).
 *   PRERENDER_STRICT=1 이면 실패 시 빌드를 중단한다.
 * - SKIP_PRERENDER=1 로 명시적으로 건너뛸 수 있다.
 */
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { preview } from 'vite';

const ROUTES = [
  '/download',
  '/refund-policy',
  '/faq',
  '/docs/aset-rset',
  '/docs/performance-based-design',
  '/docs/fds-gpu-acceleration',
];

const PORT = 4173;
const MIN_CONTENT_LENGTH = 500; // 스냅숏 본문 최소 길이 (빈 셸 저장 방지)
const strict = process.env.PRERENDER_STRICT === '1';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const fail = (message, error) => {
  console.error(`[prerender] ${message}`, error ?? '');
  if (strict) process.exit(1);
  console.warn('[prerender] 프리렌더링을 건너뜁니다. 사이트는 SPA로 동작합니다.');
  process.exit(0);
};

if (process.env.SKIP_PRERENDER === '1') {
  console.log('[prerender] SKIP_PRERENDER=1 — 건너뜀');
  process.exit(0);
}

let puppeteer;
try {
  puppeteer = (await import('puppeteer')).default;
} catch (error) {
  fail('puppeteer를 불러올 수 없습니다.', error);
}

let browser;
try {
  browser = await puppeteer.launch({
    headless: 'shell',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
} catch (error) {
  fail('헤드리스 브라우저를 시작할 수 없습니다.', error);
}

const server = await preview({
  root: rootDir,
  preview: { port: PORT, host: '127.0.0.1' },
});

try {
  const page = await browser.newPage();
  // 프리렌더 환경에는 백엔드가 없으므로 API 요청은 즉시 실패시킨다 (대기 시간 단축)
  await page.setRequestInterception(true);
  page.on('request', (req) => {
    const url = req.url();
    if (url.includes(':8080') || url.includes('/api/')) {
      req.abort('connectionrefused').catch(() => {});
    } else {
      req.continue().catch(() => {});
    }
  });

  for (const route of ROUTES) {
    const url = `http://127.0.0.1:${PORT}${route}`;
    await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
    // React 렌더링 + Seo 컴포넌트의 기본 메타 제거가 끝날 때까지 대기
    await page.waitForFunction(
      (min) => {
        const root = document.getElementById('root');
        return (
          root &&
          root.innerHTML.length > min &&
          document.querySelectorAll('[data-seo-default]').length === 0
        );
      },
      { timeout: 15000 },
      MIN_CONTENT_LENGTH,
    );

    const html = await page.evaluate(
      () => '<!DOCTYPE html>\n' + document.documentElement.outerHTML,
    );

    if (!html.includes('rel="canonical"')) {
      throw new Error(`${route}: 스냅숏에 canonical 태그가 없습니다.`);
    }

    const outDir = path.join(rootDir, 'dist', route.replace(/^\//, ''));
    await mkdir(outDir, { recursive: true });
    await writeFile(path.join(outDir, 'index.html'), html, 'utf-8');
    console.log(`[prerender] ✓ ${route} (${(html.length / 1024).toFixed(1)} KB)`);
  }

  console.log(`[prerender] 완료: ${ROUTES.length}개 라우트`);
} catch (error) {
  await browser.close().catch(() => {});
  await server.close().catch(() => {});
  fail('프리렌더링 중 오류가 발생했습니다.', error);
}

await browser.close();
await server.close();
process.exit(0);
