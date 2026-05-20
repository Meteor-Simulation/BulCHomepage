# Cloudflare Pages 배포 가이드

## 목차
1. [개요](#1-개요)
2. [사전 준비](#2-사전-준비)
3. [Cloudflare 계정 생성](#3-cloudflare-계정-생성)
4. [GitHub 연동 및 프로젝트 배포](#4-github-연동-및-프로젝트-배포)
5. [환경변수 설정](#5-환경변수-설정)
6. [커스텀 도메인 설정](#6-커스텀-도메인-설정)
7. [백엔드 프록시 설정](#7-백엔드-프록시-설정)
8. [배포 확인 및 트러블슈팅](#8-배포-확인-및-트러블슈팅)
9. [무료 티어 제한사항](#9-무료-티어-제한사항)

---

## 1. 개요

### 1.1 Cloudflare Pages란?
Cloudflare Pages는 정적 사이트 및 풀스택 애플리케이션을 위한 JAMstack 플랫폼입니다. Git 저장소와 연동하여 자동 빌드 및 배포를 지원합니다.

### 1.2 무료 티어 혜택

| 항목 | 무료 제공량 |
|------|-------------|
| 빌드 | 월 500회 |
| 대역폭 | **무제한** |
| 요청 수 | **무제한** |
| 사이트 수 | **무제한** |
| 동시 빌드 | 1개 |
| 커스텀 도메인 | 지원 |
| SSL 인증서 | 자동 무료 발급 |
| DDoS 방어 | 기본 포함 |
| CDN | 전 세계 엣지 네트워크 |

### 1.3 배포 아키텍처

```
[사용자 브라우저]
        ↓
[Cloudflare CDN] ─── 전 세계 엣지 서버에서 캐싱
        ↓
[Cloudflare Pages] ─── React 프론트엔드
        ↓ API 요청 (/api/*)
[Cloudflare Proxy] ─── 백엔드 서버 IP 보호 (선택사항)
        ↓
[AWS/기타 클라우드] ─── Spring Boot 백엔드 + PostgreSQL
```

---

## 2. 사전 준비

### 2.1 필수 요구사항
- GitHub 계정 (GitLab도 지원)
- 프로젝트가 Git 저장소에 푸시되어 있어야 함
- Node.js 프로젝트 (React, Vue, Next.js 등)

### 2.2 BulC_Homepage 프로젝트 구조 확인

```
BulC_Homepage/
├── frontend/           ← Cloudflare Pages에 배포할 대상
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── ...
├── backend/            ← AWS 등 별도 서버에 배포
└── database/
```

### 2.3 로컬 빌드 테스트
배포 전 로컬에서 빌드가 정상적으로 되는지 확인합니다:

```bash
cd frontend
npm install
npm run build
```

빌드 성공 시 `dist/` 폴더가 생성됩니다 (Vite 기본 출력 경로).

---

## 3. Cloudflare 계정 생성

### 3.1 회원가입

1. [Cloudflare 공식 사이트](https://dash.cloudflare.com/sign-up) 접속
2. 이메일과 비밀번호 입력
3. 이메일 인증 완료

### 3.2 Cloudflare Pages 접속

1. Cloudflare 대시보드 로그인
2. 좌측 메뉴에서 **"Workers & Pages"** 클릭
3. **"Pages"** 탭 선택

---

## 4. GitHub 연동 및 프로젝트 배포

### 4.1 새 프로젝트 생성

1. **"Create a project"** 버튼 클릭
2. **"Connect to Git"** 선택

### 4.2 GitHub 계정 연결

1. **"Connect GitHub"** 클릭
2. GitHub 로그인 및 권한 승인
3. 연동할 저장소 선택:
   - **"Only select repositories"** 선택
   - `BulC_Homepage` 저장소 체크
4. **"Install & Authorize"** 클릭

### 4.3 빌드 설정

저장소 선택 후 빌드 설정 화면에서 다음과 같이 입력:

| 설정 항목 | 값 |
|-----------|-----|
| **Project name** | `bulc-homepage` (원하는 이름) |
| **Production branch** | `main` 또는 `master` |
| **Framework preset** | `None` 선택 (Cloudflare에 Vite 프리셋이 없으므로 수동 설정) |
| **Root directory** | `frontend` |
| **Build command** | `npm run build` |
| **Build output directory** | `dist` |

### 4.4 빌드 설정 스크린샷 예시

```
┌─────────────────────────────────────────────────────────┐
│ Build settings                                          │
├─────────────────────────────────────────────────────────┤
│ Framework preset:    [None                 ▼]           │
│ Root directory:      [frontend            ]             │
│ Build command:       [npm run build       ]             │
│ Build output:        [dist                ]             │
└─────────────────────────────────────────────────────────┘
```

### 4.5 배포 시작

1. **"Save and Deploy"** 클릭
2. 빌드 로그 확인 (약 1-3분 소요)
3. 배포 완료 시 URL 제공: `https://bulc-homepage.pages.dev`

---

## 5. 환경변수 설정

### 5.1 환경변수 추가 방법

1. Pages 프로젝트 대시보드 접속
2. **"Settings"** → **"Environment variables"** 클릭
3. **"Add variable"** 클릭

### 5.2 필수 환경변수

| 변수명 | 값 예시 | 설명 |
|--------|---------|------|
| `VITE_API_URL` | `https://api.bulc.co.kr` | 백엔드 API 서버 주소 (Vite는 VITE_ prefix 필수) |
| `VITE_TOSS_CLIENT_KEY` | `test_ck_xxx` | Toss 결제 클라이언트 키 |
| `NODE_VERSION` | `20` | Node.js 버전 지정 |

### 5.3 환경별 변수 설정

Cloudflare Pages는 환경별로 다른 변수를 설정할 수 있습니다:

- **Production**: `main` 브랜치 배포 시 적용
- **Preview**: 다른 브랜치 배포 시 적용

```
Production 환경:
  VITE_API_URL = https://api.bulc.co.kr

Preview 환경:
  VITE_API_URL = https://api-dev.bulc.co.kr
```

### 5.4 환경변수 적용

환경변수 변경 후 재배포가 필요합니다:
1. **"Deployments"** 탭 이동
2. 최신 배포의 **"..."** 메뉴 클릭
3. **"Retry deployment"** 선택

---

## 6. 커스텀 도메인 설정

### 6.1 도메인 추가

1. **"Custom domains"** 탭 클릭
2. **"Set up a custom domain"** 클릭
3. 도메인 입력: `www.bulc.co.kr` 또는 `bulc.co.kr`

### 6.2 DNS 설정

#### Cloudflare DNS 사용 시 (권장)
도메인이 Cloudflare에 등록되어 있으면 자동으로 DNS 레코드가 추가됩니다.

#### 외부 DNS 사용 시
DNS 관리 페이지에서 다음 레코드 추가:

```
타입: CNAME
이름: www (또는 @)
값:  bulc-homepage.pages.dev
TTL: Auto
```

### 6.3 SSL 인증서

커스텀 도메인 설정 완료 시 SSL 인증서가 자동으로 발급됩니다 (약 1-5분 소요).

### 6.4 루트 도메인과 www 리다이렉트

```
bulc.co.kr      → www.bulc.co.kr (또는 반대로)
```

Cloudflare 대시보드에서 **Rules** → **Redirect Rules**로 설정 가능합니다.

---

## 7. 백엔드 프록시 설정

### 7.1 _redirects 파일 생성

프론트엔드에서 백엔드 API 호출 시 CORS 문제를 피하기 위해 프록시 설정이 가능합니다.

`frontend/public/_redirects` 파일 생성:

```
/api/*  https://api.bulc.co.kr/api/:splat  200
```

### 7.2 _headers 파일 (선택사항)

보안 헤더 추가를 위해 `frontend/public/_headers` 파일 생성:

```
/*
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
```

### 7.3 백엔드 CORS 설정

백엔드(Spring Boot)에서 Cloudflare 도메인 허용이 필요합니다.

`SecurityConfig.java` 수정:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(
        "https://bulc-homepage.pages.dev",
        "https://www.bulc.co.kr",
        "https://bulc.co.kr"
    ));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

---

## 8. 배포 확인 및 트러블슈팅

### 8.1 배포 상태 확인

1. **"Deployments"** 탭에서 배포 이력 확인
2. 각 배포의 상태:
   - ✅ **Success**: 배포 성공
   - ❌ **Failed**: 빌드 실패
   - 🔄 **Building**: 빌드 중

### 8.2 빌드 로그 확인

빌드 실패 시:
1. 실패한 배포 클릭
2. **"Build log"** 확인
3. 에러 메시지 분석

### 8.3 일반적인 오류 및 해결방법

#### 오류 1: `npm ERR! Could not resolve dependency`
```
해결: package-lock.json 삭제 후 재생성
$ rm package-lock.json
$ npm install
$ git add package-lock.json
$ git commit -m "Regenerate package-lock.json"
$ git push
```

#### 오류 2: `Build output directory not found`
```
해결: 빌드 출력 경로 확인
- Vite (현재 사용): dist
- Create React App (legacy): build
- Next.js: .next 또는 out
```

#### 오류 3: `Node.js version mismatch`
```
해결: 환경변수에 Node 버전 지정
NODE_VERSION = 20
```

#### 오류 4: 라우팅 404 오류 (SPA)
React Router 사용 시 새로고침하면 404 발생하는 경우:

`frontend/public/_redirects` 파일 추가:
```
/*    /index.html   200
```

### 8.4 Preview 배포 활용

Pull Request 생성 시 자동으로 Preview URL이 생성됩니다:
- `https://<commit-hash>.bulc-homepage.pages.dev`

이를 통해 본 배포 전 테스트가 가능합니다.

---

## 9. 무료 티어 제한사항

### 9.1 제한 사항 요약

| 항목 | 무료 티어 | Pro 플랜 ($20/월) |
|------|-----------|-------------------|
| 빌드 횟수 | 월 500회 | 월 5,000회 |
| 동시 빌드 | 1개 | 5개 |
| 빌드 시간 | 최대 20분 | 최대 20분 |
| Functions 요청 | 일 10만 | 일 1,000만 |
| Functions CPU | 요청당 10ms | 요청당 50ms |

### 9.2 빌드 횟수 절약 팁

1. **불필요한 푸시 줄이기**: 커밋을 모아서 푸시
2. **브랜치 필터링**: 특정 브랜치만 빌드하도록 설정
   - Settings → Builds & deployments → Configure Production deployments
3. **Preview 빌드 비활성화**: 필요 없다면 Preview 배포 끄기

### 9.3 Functions 제한 (Workers)

Cloudflare Pages Functions를 사용하는 경우:
- 일일 10만 요청 제한
- CPU 시간 요청당 10ms

> **참고**: 본 프로젝트는 백엔드가 별도(Spring Boot)이므로 Functions 제한은 해당되지 않습니다.

---

## 부록 A: GitHub Actions 연동 (선택사항)

Cloudflare Pages의 기본 빌드 대신 GitHub Actions를 사용할 수 있습니다.

### A.1 Cloudflare API 토큰 생성

1. [Cloudflare API Tokens](https://dash.cloudflare.com/profile/api-tokens) 접속
2. **"Create Token"** 클릭
3. **"Edit Cloudflare Workers"** 템플릿 사용
4. 토큰 복사 및 GitHub Secrets에 저장

### A.2 GitHub Actions 워크플로우

`.github/workflows/deploy-frontend.yml`:

```yaml
name: Deploy Frontend to Cloudflare Pages

on:
  push:
    branches:
      - main
    paths:
      - 'frontend/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Build
        working-directory: frontend
        run: npm run build
        env:
          VITE_API_URL: ${{ secrets.VITE_API_URL }}

      - name: Deploy to Cloudflare Pages
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          command: pages deploy frontend/dist --project-name=bulc-homepage
```

### A.3 GitHub Secrets 설정

| Secret 이름 | 값 |
|-------------|-----|
| `CLOUDFLARE_API_TOKEN` | Cloudflare API 토큰 |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare 계정 ID (대시보드 URL에서 확인) |
| `VITE_API_URL` | 백엔드 API URL |

---

## 부록 B: 체크리스트

### 배포 전 체크리스트

- [ ] 로컬에서 `npm run build` 성공 확인
- [ ] 환경변수 목록 정리
- [ ] `.gitignore`에 `.env` 파일 포함 확인
- [ ] `_redirects` 파일 생성 (SPA 라우팅용)

### 배포 후 체크리스트

- [ ] 배포된 URL 접속 테스트
- [ ] API 연동 테스트 (로그인, 데이터 조회 등)
- [ ] 모바일 브라우저 테스트
- [ ] HTTPS 적용 확인
- [ ] 커스텀 도메인 연결 (선택)

---

## 참고 링크

- [Cloudflare Pages 공식 문서](https://developers.cloudflare.com/pages/)
- [Cloudflare Pages 빌드 설정](https://developers.cloudflare.com/pages/platform/build-configuration/)
- [Vite 프로덕션 빌드 가이드](https://vitejs.dev/guide/build.html)
- [Vite 환경 변수 (`import.meta.env`)](https://vitejs.dev/guide/env-and-mode.html)

---

*문서 작성일: 2026-02-03*
*작성자: Claude Code*
