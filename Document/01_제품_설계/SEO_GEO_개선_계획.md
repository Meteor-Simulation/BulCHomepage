# SEO / GEO 개선 계획

> 작성일: 2026-06-10
> 대상: bulc.msimul.com (+ meteor / vr 서브도메인)
> 목적: 구글·네이버 검색 노출(SEO)과 ChatGPT·Claude·Perplexity 등 AI 검색 인용(GEO) 개선

---

## 1. 현재 상태 진단

### 이미 잘 되어 있는 것 ✅

| 항목 | 위치 | 비고 |
|------|------|------|
| meta description / keywords / canonical | `frontend/index.html` | 루트 페이지 기준 |
| Open Graph / Twitter Card | `frontend/index.html` | og:image가 로고(정사각형) |
| JSON-LD 구조화 데이터 | `frontend/index.html` | Organization, SoftwareApplication(+Offers), FAQPage |
| robots.txt | `frontend/public/robots.txt` | GPTBot, ClaudeBot, PerplexityBot 등 AI 크롤러 명시 허용 |
| sitemap.xml | `frontend/public/sitemap.xml` | 4개 URL (정적) |
| llms.txt | `frontend/public/llms.txt` | 회사·제품·요금·FAQ 요약 (GEO 기본기) |
| 이미지 최적화 | `frontend/public/images/` | 대부분 webp |

기초 공사는 평균 이상으로 되어 있습니다. 문제는 아래 구조적 한계입니다.

### 핵심 문제점 ❌

**P1. 순수 CSR(클라이언트 렌더링) SPA — 가장 큰 문제**
- 모든 본문 콘텐츠가 JavaScript 실행 후에만 생성됨 (`<div id="root">`가 빈 채로 서빙).
- **AI 크롤러(GPTBot, ClaudeBot, PerplexityBot)는 JS를 실행하지 않음** → robots.txt로 허용해 줘도 실제로 읽어가는 것은 `index.html`의 head와 `llms.txt`뿐. 페이지 본문(기능 설명, 가격, 튜토리얼 등)은 AI에게 보이지 않음.
- 네이버 크롤러(Yeti)도 JS 렌더링이 약해서 같은 문제 발생. 구글은 렌더링하지만 색인이 느리고 불리함.

**P2. 모든 라우트가 동일한 title / description / canonical**
- `/payment`, `/download`, `/board`, `/refund-policy` 전부 같은 `index.html`을 받음.
- 특히 canonical이 전 페이지에서 `https://bulc.msimul.com`(루트)로 고정 → 검색엔진에 "이 페이지들은 전부 루트의 중복본"이라고 선언하는 셈. 하위 페이지 색인을 스스로 막고 있음.
- 라우트별 meta 관리 코드 없음 (react-helmet 미사용, `document.title` 변경도 없음).

**P3. 서브도메인 3개가 같은 메타 공유**
- meteor.msimul.com, vr.msimul.com 접속 시에도 BUL:C 중심의 title/OG/JSON-LD가 노출됨.

**P4. sitemap 빈약**
- `lastmod` 없음, `/board`와 게시글 URL 누락, 서브도메인 페이지 누락.

**P5. 다국어(ko/en) 처리가 검색에 안 보임**
- i18n이 클라이언트 토글 방식이라 URL이 분리되지 않음 → hreflang 불가, 영어 콘텐츠는 검색·AI 양쪽에서 존재하지 않는 것과 같음.

**P6. 기타**
- soft-404: `_redirects`의 `/* /index.html 200` 때문에 존재하지 않는 URL도 200 응답 → 검색 품질 점수에 불리.
- og:image가 로고 — 1200×630 전용 이미지 권장.
- Google Search Console / 네이버 서치어드바이저 verification 흔적이 코드에 없음 (DNS 인증했는지 확인 필요).
- Google Fonts가 렌더 블로킹 (LCP에 영향).

---

## 2. 개선 계획 (단계별)

### Phase 0 — 코드 변경 없이 당장 (반나절)

1. **검색엔진 등록 확인/등록**
   - [ ] Google Search Console: bulc/meteor/vr 3개 속성 등록, sitemap 제출, 주요 URL 색인 요청
   - [ ] **네이버 서치어드바이저** (searchadvisor.naver.com): 사이트 등록 + 소유 확인 + sitemap/RSS 제출 — *네이버 노출의 필수 전제*
   - [ ] Bing Webmaster Tools 등록 (ChatGPT 검색이 Bing 색인을 사용하므로 GEO에도 직결)
2. **현 상태 측정 (개선 전 베이스라인)**
   - [ ] `site:msimul.com` 으로 구글/네이버 색인 현황 캡처
   - [ ] PageSpeed Insights 점수 기록
   - [ ] ChatGPT/Perplexity에 "화재 시뮬레이션 소프트웨어 추천", "BUL:C" 질문해서 현재 인용 여부 기록

### Phase 1 — 빠른 코드 개선 (약 1주)

1. **라우트별 title / description / canonical / OG 동적 적용**
   - React 19는 컴포넌트 안의 `<title>`, `<meta>`, `<link>`를 자동으로 `<head>`로 호이스팅하므로 **라이브러리 없이 구현 가능**.
   - 공용 `<Seo title description canonical />` 컴포넌트를 만들어 모든 페이지에 적용.
   - canonical은 페이지 자기 자신의 URL로 (현재의 루트 고정 제거).
2. **서브도메인별 메타 분기**
   - `getSubdomain()` 결과에 따라 METEOR/VR 페이지에 각자의 title/description/JSON-LD 적용.
   - 장기적으로는 서브도메인별 `index.html` 분리 빌드(또는 Cloudflare Pages 프로젝트 분리) 검토.
3. **sitemap 정비**
   - `/board` 추가, `lastmod` 추가, 서브도메인 URL 포함 (또는 서브도메인별 sitemap + sitemap index).
4. **og:image 제작** — 1200×630 전용 이미지 (제품 스크린샷 + 카피).
5. **404 개선** — NotFound 페이지에 `noindex` meta 추가 (soft-404 완화. 진짜 404 응답은 Phase 2 프리렌더와 함께 해결).
6. **폰트 최적화** — DM Sans를 self-host하거나 `media="print" onload` 패턴으로 비차단 로드.

### Phase 2 — 프리렌더링 도입 (핵심, 약 2~3주)

CSR 문제(P1)를 해결해야 SEO/GEO 모두 실질 효과가 납니다. 선택지:

| 옵션 | 내용 | 장점 | 단점 |
|------|------|------|------|
| **A. 빌드타임 프리렌더 (권장)** | 빌드 후 Puppeteer/vite 플러그인으로 주요 라우트를 정적 HTML 스냅샷 → Cloudflare Pages에 그대로 배포 | 현 구조 유지, 마이그레이션 비용 최소, 마케팅 페이지가 대부분 정적이라 적합 | 게시판 등 동적 페이지는 커버 못 함 |
| B. Vike(vite-plugin-ssr)/Next.js 마이그레이션 | 프레임워크 차원 SSG/SSR | 가장 완전한 해결 | 공수 큼, 결제·인증 플로우 재검증 필요 |
| C. 봇 대상 동적 렌더링 (Cloudflare Worker + 프리렌더 서비스) | UA가 봇일 때만 렌더된 HTML 제공 | 코드 변경 거의 없음 | 구글이 임시방편으로 규정, 유지비, 클로킹 오인 리스크 |

**권장: 옵션 A.** 프리렌더 대상 라우트:
- `/` (서브도메인 3종 각각), `/download`, `/payment`, `/refund-policy`, `/board`(목록 첫 페이지)
- 결과: AI 크롤러와 네이버가 본문 텍스트를 그대로 읽을 수 있게 됨. 이것이 GEO의 최대 지렛대.

수용 기준: `curl -A "GPTBot" https://bulc.msimul.com/download` 결과에 본문 텍스트가 포함될 것.

### Phase 3 — 콘텐츠 / GEO 강화 (지속)

1. **llms.txt 확장**
   - `llms-full.txt` 추가: 기술 문서, 튜토리얼, 가격, 검증 자료(NIST FDS 검증 등)를 마크다운 전문으로 제공.
   - llms.txt에 주요 페이지 링크 목록 추가 (AI가 후속 크롤링할 수 있도록).
2. **AI/검색이 읽을 수 있는 정적 콘텐츠 페이지 구축**
   - 현재 JSON-LD FAQ 5개 → 실제 FAQ 페이지(HTML)로 확장 (라이선스, 설치, FDS 비교, PBD 보고서 등 20+ 항목).
   - 기술 용어/지식 페이지: "ASET/RSET이란", "성능위주설계(PBD)란", "FDS vs BUL:C" 같은 질문형 콘텐츠 — AI 답변에 인용되는 콘텐츠의 전형.
   - `tech-docs/technical-doc.html`이 이미 정적 HTML이므로 sitemap에 추가하고 내부 링크 연결.
3. **게시판(Board) SEO화**
   - 백엔드에 동적 sitemap 엔드포인트 추가 (`/api/sitemap-board.xml` → 게시글 URL + lastmod).
   - 게시글 상세를 프리렌더 또는 백엔드 OG 태그 주입으로 크롤러에 노출.
4. **영어 콘텐츠 URL 분리 + hreflang**
   - `/en/...` 경로(또는 `?lang=` 대신 경로 기반)로 영어 페이지 분리 → hreflang 쌍 선언. 해외 노출이 목표일 때 진행.
5. **네이버 특화 전략** (네이버는 자사 콘텐츠를 우선 노출)
   - 네이버 블로그/포스트 채널 운영: 화재 시뮬레이션·PBD 키워드 콘텐츠 → 홈페이지 링크.
   - 네이버 지식백과/지식iN 등 보조 채널 검토.
   - 사이트 대표 키워드: "화재 시뮬레이션", "성능위주설계 프로그램", "FDS GPU", "피난 시뮬레이션" 등으로 서치어드바이저 수집 현황 모니터링.

### Phase 4 — 측정·운영 (상시)

- Google Search Console / 네이버 서치어드바이저 주간 점검 (색인 수, 노출 키워드, CTR).
- Cloudflare 로그에서 AI 크롤러(GPTBot, ClaudeBot 등) 방문 추이 확인.
- 분기마다 ChatGPT/Perplexity/Claude에 타깃 질문을 던져 인용 여부 추적 (Phase 0 베이스라인과 비교).
- Core Web Vitals (PageSpeed) 모니터링.

---

## 3. 우선순위 요약

| 순위 | 작업 | 효과 | 공수 |
|------|------|------|------|
| 1 | 검색엔진 3사 등록 + sitemap 제출 (Phase 0) | 즉시 | 반나절 |
| 2 | 라우트별 meta/canonical (Phase 1-1) | 높음 — 현재 canonical이 색인을 막는 중 | 1~2일 |
| 3 | 프리렌더링 (Phase 2-A) | **매우 높음 — GEO의 전제조건** | 1~2주 |
| 4 | llms-full.txt + FAQ/지식 콘텐츠 (Phase 3) | 높음 (AI 인용) | 지속 |
| 5 | 서브도메인 메타 분기, sitemap 정비, og:image | 중간 | 2~3일 |
| 6 | 영어 URL 분리 + hreflang | 해외 타깃 시 | 1주+ |

## 4. 검증 방법

| 검증 | 도구/명령 |
|------|-----------|
| 구조화 데이터 | Google Rich Results Test (FAQ·SoftwareApplication 리치 결과 확인) |
| 크롤러가 보는 화면 | `curl -A "GPTBot" <url>`, 서치어드바이저 "수집 테스트" |
| OG 미리보기 | 카카오톡 공유 디버거, opengraph.xyz |
| 색인 현황 | GSC URL 검사, `site:` 검색 |
| AI 인용 | ChatGPT(검색 모드)/Perplexity에 타깃 질문 |
