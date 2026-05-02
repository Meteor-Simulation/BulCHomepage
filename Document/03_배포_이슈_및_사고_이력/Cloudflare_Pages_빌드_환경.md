# Cloudflare Pages 빌드 환경 설정

> 작성일: 2026-04-30
> 관련 PR: #125, #126, #127, #128, #129, #135
> 관련 문서: [배포_가이드.md](../02_배포_가이드/배포_가이드.md), [Cloudflare_Pages_빌드_안정화.md](./Cloudflare_Pages_빌드_안정화.md)

---

## 1. 요약 (한 페이지)

Cloudflare Pages가 BulC 프론트엔드를 정상 빌드/배포하려면 **반드시** 다음 설정이 필요합니다. 빠지면 빌드 실패.

### 필수 설정 (대시보드 → BulCHomepage → Settings)

| 항목 | 값 | 위치 |
|------|-----|------|
| **Build command** | `npm install --legacy-peer-deps && CI=false npm run build` | Builds & deployments → Build configurations |
| **Build output directory** | `build` | 동일 |
| **Root directory** | `frontend` | 동일 |
| **Environment variable** | `SKIP_DEPENDENCY_INSTALL=true` (Production) | Variables and Secrets |
| **Node version** (`.tool-versions`) | `nodejs 18.20.8` | 리포지토리 루트의 `.tool-versions` 파일 |

### 자동 배포 흐름

```
main 브랜치 push/머지
       ↓
Cloudflare Pages 자동 감지
       ↓
[자동 install 단계] ← SKIP_DEPENDENCY_INSTALL=true 면 건너뜀
       ↓
[Build command 실행]
   npm install --legacy-peer-deps  ← 의존성 설치
   CI=false npm run build           ← React 빌드 (warning을 error로 승격 안 함)
       ↓
build/ 디렉토리를 정적 자산으로 배포
       ↓
https://bulc.msimul.com 반영 (보통 2~5분)
```

---

## 2. 각 설정의 이유

### 2.1 `SKIP_DEPENDENCY_INSTALL=true` (가장 중요)

**기본 동작**: Cloudflare Pages는 `package.json`을 발견하면 build command 실행 *전*에 자동으로 `npm clean-install`(=`npm ci`)을 먼저 실행합니다.

**문제**: `npm ci`는 `package-lock.json`과 `package.json`의 sync를 strict하게 검증합니다. 우리 프로젝트는 react-scripts 5의 ajv@6 ↔ ajv-keywords@5 transitive 충돌 때문에 lock에 두 버전이 동시에 깨끗하게 들어가지 않아, npm 10 환경에서 거의 항상 strict 검증에 실패합니다:
```
Invalid: lock file's ajv-keywords@5.1.0 does not satisfy ajv-keywords@3.5.2
Missing: ajv-keywords@5.1.0 from lock file
```

**해결**: `SKIP_DEPENDENCY_INSTALL=true`로 자동 install 단계 자체를 건너뜁니다. 의존성 설치는 build command 안의 `npm install --legacy-peer-deps`가 처리.

**주의**:
- Production 환경에 추가해야 main 배포에 적용됨 (Preview 환경에 추가하면 main에 영향 없음).
- 이 변수가 빠지면 어떤 lock/package.json 조합도 빌드 통과 못 함.

### 2.2 Build command — `npm install --legacy-peer-deps && CI=false npm run build`

| 부분 | 이유 |
|------|------|
| `npm install`(`npm ci` 아님) | strict sync 검증 안 함. lock 미세 차이 자동 조정 |
| `--legacy-peer-deps` | npm 7+의 strict peer 의존성 검사 우회. react 19 + react-scripts 5 + react-i18next 15 등 peer 충돌 회피 |
| `CI=false` | react-scripts가 ESLint warning을 error로 승격하는 동작 비활성화 (CI=true는 Cloudflare 기본값) |
| `npm run build` | `react-scripts build` 실행 |

> 참고: `frontend/.npmrc`에 `legacy-peer-deps=true`도 있어 `--legacy-peer-deps` 옵션은 사실상 중복. 단 명시적으로 둬서 .npmrc가 어떤 이유로 무시되어도 안전.

### 2.3 Root directory — `frontend`

모노레포 구조라 React 앱이 `frontend/` 하위. 이 설정으로 build command가 `frontend/`에서 실행됨.

### 2.4 `.tool-versions` (Node 버전 고정)

```
nodejs 18.20.8
```

Cloudflare가 환경에 따라 임의 Node를 사용하면 빌드 결과가 달라질 수 있음. 18.20.8로 고정 (PR #127). LTS Maintenance 모드라 향후 22로 업그레이드 검토 가능.

---

## 3. 운영 측 직접 작업 (자동 배포로 처리되지 않는 것)

main에 머지되어도 다음은 사용자가 직접 운영 환경에서 실행해야 합니다.

### 3.1 백엔드 / 헬스체크 스크립트 변경 시

운영 서버(Oracle Cloud VM)는 Cloudflare와 별개. 코드 변경분 반영하려면:

```bash
# SSH 접속
ssh -i <private_key> ubuntu@<VM_IP>

# 코드 갱신
cd /home/ubuntu/BulCHomepage
git pull origin main

# 백엔드 변경이면 컨테이너 재시작
docker-compose -f docker-compose.prod.yml up -d --build backend

# 스크립트 변경(예: scripts/health-check.sh)이면 cron이 다음 실행 시 자동 사용 (추가 작업 불필요)
```

### 3.2 DB 스키마 변경 시

`application.yml`이 `ddl-auto: validate`라 Hibernate가 자동 마이그레이션 안 함. 신규 컬럼/테이블이 들어갈 때마다 운영 DB에 직접 SQL 실행:

```bash
# 운영 서버에서 컨테이너 안 psql 진입
docker exec -it bulc-db-prod psql -U bulc_prod_user -d bulc_homepage_db
```

```sql
-- 트랜잭션으로 감싸기
BEGIN;
-- 변경 SQL...
-- 검증 SELECT
COMMIT;  -- 또는 ROLLBACK
```

마이그레이션 SQL은 `database/migrations/V{날짜}__{설명}.sql` 파일에 보관됩니다. 이 파일들은 자동 적용되지 않음 (Flyway 미사용). `database/init.sql`은 신규 환경 부트스트랩용 풀 스키마라 운영 DB에는 적용하지 마세요.

---

## 4. 트러블슈팅

### 4.1 빌드 실패: `npm ci` sync 에러

```
npm error code EUSAGE
npm error `npm ci` can only install packages when your package.json and
          package-lock.json or npm-shrinkwrap.json are in sync.
npm error Invalid: lock file's ajv-keywords@5.1.0 does not satisfy ajv-keywords@3.5.2
npm error Missing: ajv-keywords@5.1.0 from lock file
```

**원인**: `SKIP_DEPENDENCY_INSTALL` 환경 변수가 빠짐. Cloudflare 자동 npm clean-install 단계가 lock strict 검증에서 실패.

**해결**: 환경 변수 `SKIP_DEPENDENCY_INSTALL=true`를 Production 환경에 추가하고 재배포.

> 참고: lock 파일을 손봐도 거의 해결 안 됨. 이번 세션(2026-04-30)에 PR #135로 lock 재생성 시도했지만 실패. 환경 변수가 진짜 fix.

### 4.2 빌드 실패: ERESOLVE peer dep 에러

```
npm error code ERESOLVE
npm error peerOptional typescript@"^5" from react-i18next@15.7.4
npm error Conflicting peer dependency: typescript@5.9.3
```

**원인**: `--legacy-peer-deps` 옵션이 빠짐.

**해결**:
- Build command에 `--legacy-peer-deps` 포함 확인
- `frontend/.npmrc`에 `legacy-peer-deps=true` 확인

### 4.3 빌드 실패: ESLint warning이 error로 승격

```
Treating warnings as errors because process.env.CI = true.
Failed to compile.
[eslint]
  src/.../...tsx
    Line N:M: '...' is defined but never used  @typescript-eslint/no-unused-vars
```

**원인**: Cloudflare가 자동으로 `CI=true` 환경 변수 설정. react-scripts가 이를 보고 warning을 error로 승격.

**해결 (택1)**:
- Build command에 `CI=false` 명시 (현재 적용) — 가장 빠른 해결, 단 ESLint warning이 빌드를 통과시킴
- 또는 ESLint warning을 코드에서 직접 정리 (PR #129가 11건 정리)

### 4.4 자동 배포가 트리거되지 않음

**원인 후보**:
- main 브랜치에 push되지 않음 (다른 브랜치는 Preview 빌드)
- Cloudflare Pages가 Production 브랜치를 다른 이름으로 설정

**확인**:
```
Settings → Builds & deployments → Production branch: main
```

main이 아니면 변경.

### 4.5 환경 변수 추가 후 재빌드가 자동으로 안 됨

Cloudflare Pages는 환경 변수 변경만으로는 재배포를 트리거하지 않습니다.

**재빌드 방법 (택1)**:
- Deployments 탭 → 가장 최근 빌드 → ⋯ → "Retry deployment"
- 또는 더미 커밋 push (예: README 한 줄 변경)

---

## 5. 이번 세션(2026-04-30) 발견 사항 요약

react-scripts 5 + Node 18 + npm 10 조합에서 ajv 다중 버전 충돌이 lock 파일을 일관성 없는 상태로 만듭니다. 코드(lock/package.json/overrides)로 우회하려는 모든 시도가 실패했고, **Cloudflare 환경 변수 `SKIP_DEPENDENCY_INSTALL=true`로 자동 npm ci 단계를 건너뛰는 것이 유일한 안정 fix**였습니다.

| 시도한 방법 | 결과 |
|-------------|------|
| `package.json`에 `ajv@^6.12.6` 직접 의존성 명시 (PR #128) | 어제(4/29)는 통과, 오늘 깨짐 |
| `.npmrc`에 `legacy-peer-deps=true` (PR #126) | install 통과, ci 검증은 못 통과 |
| Node 버전 고정 (PR #127) | 환경 일관성 확보 |
| Lock 파일을 npm 10 도커에서 재생성 (PR #135) | 검증 환경에서는 통과, Cloudflare에서 또 실패 |
| `package.json` overrides로 ajv 강제 | 빌드 단계에서 ajv@6/@8 충돌 |
| **Cloudflare `SKIP_DEPENDENCY_INSTALL=true`** | **✅ 성공** |

근본 원인이 코드가 아니라 빌드 환경의 자동 단계였기 때문에, 환경 설정으로만 해결 가능했습니다.

---

## 6. 향후 개선 검토

### 6.1 단기

- 새 Cloudflare 환경(staging 등) 만들 때 본 문서의 "필수 설정" 4가지 모두 적용 필수
- `frontend/package.json`의 `ajv`, `ajv-keywords` 직접 의존성은 그대로 두기 (제거하면 빌드 단계에서 다른 충돌 발생)

### 6.2 중기

- `react-scripts@5.0.1` 탈출 검토 (Vite 등으로 마이그레이션). React 19 시대 빌드 도구로서 react-scripts 5는 한계 도달.
- TypeScript 5 + Vite 조합으로 옮기면 ajv 호환 문제 자체가 사라짐.

### 6.3 장기

- GitHub Actions로 PR 단계 사전 빌드 검증 추가 (Cloudflare Pages만 빌드 검증처인 현 구조의 한계 보완)
- Flyway 정식 도입으로 DB 스키마 변경 자동화
