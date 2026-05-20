# Cloudflare Pages 빌드 안정화 (TypeScript 5 업그레이드 + ESLint 정리)

> 작성일: 2026-04-30
> 관련 PR: [#129](https://github.com/Meteor-Simulation/BulCHomepage/pull/129)
> 관련 브랜치: `fix/ajv-dependency`

> ✅ **상태: RESOLVED (2026-05-03)** — 본 문서가 다루는 react-scripts@5.0.1의 호환성 문제는 **Vite로의 마이그레이션 (`chore/vite-migration`)으로 근본 해소**되었습니다. 본 문서는 사고 이력으로 보존됩니다.

---

## 1. 배경

### 1.1 문제 상황

Cloudflare Pages에서 프론트엔드 자동 배포가 연쇄적으로 실패했습니다. 다음 3개의 별개 원인이 순차적으로 드러났고, 매번 같은 빌드 환경에서 다음 단계로 넘어가야 다음 오류가 노출되는 구조였습니다.

| 단계 | 발생 시점 | 원인 | 해결 커밋 |
|------|-----------|------|-----------|
| 1단계 | 2026-04-29 | Node 버전 미고정으로 Cloudflare가 임의 Node를 사용 | `8193e806` |
| 2단계 | 2026-04-29 | `ajv` ↔ `ajv-keywords` 의존성 충돌 | `b29c7a8a` |
| 3단계 | 2026-04-30 | `react-i18next@15` ↔ `typescript@4` peer 충돌 + ESLint 경고가 CI에서 에러로 승격 | `21b10d9e` (이번 작업) |

본 문서는 **3단계 작업**을 다룹니다. 1, 2단계는 같은 PR 시리즈 내에서 선행된 단발성 fix이고, 3단계가 가장 변경 폭이 크며 운영 안전성 관점의 검토가 필요했습니다.

### 1.2 3단계 빌드 실패 원인

Cloudflare Pages가 `npm clean-install`을 실행할 때 다음 오류가 발생했습니다.

```
npm error code ERESOLVE
npm error While resolving: react-i18next@15.7.4
npm error Found: typescript@4.9.5
npm error Could not resolve dependency:
npm error peerOptional typescript@"^5" from react-i18next@15.7.4
npm error Conflicting peer dependency: typescript@5.9.3
```

- 프로젝트의 `typescript`는 `^4.9.5`로 고정되어 있었음
- 그러나 `react-i18next@15.7.4`는 `typescript@^5`를 **peerOptional**로 요구함
- npm 7+ 의 `npm ci`는 peerOptional 충돌도 strict하게 검사하여 ERESOLVE 발생
- 로컬 `package-lock.json`은 `--legacy-peer-deps` 모드로 생성된 상태였기에 로컬에서는 문제없이 install됐고, 이 차이가 Cloudflare에서만 깨지는 원인이었음

---

## 2. 해결 방향 검토

### 2.1 방안 비교

빌드를 통과시키는 방법으로 세 가지 선택지가 있었습니다.

| 방안 | 적용 범위 | 향후 충돌 재발 가능성 | 변경 폭 | 트레이드오프 |
|------|-----------|----------------------|---------|--------------|
| A. 충돌 패키지를 `package.json`에 직접 고정 | 1건 한정 | 새 충돌 시마다 반복 필요 | 작음 | 의존성 트리에 잡음 누적 |
| B. `.npmrc`에 `legacy-peer-deps=true` | 모든 peer 충돌 | 없음 | 가장 작음 | peer 경고가 묵살될 수 있음 |
| C. TypeScript 5로 업그레이드 | 근본 해결 | 없음 | 보통 | TS 5의 strict 타입 체크로 인한 잠재 에러 |

### 2.2 채택한 방향

**B + C 동시 적용**.

- B만 적용 시 빌드는 통과하지만 React 19 + react-scripts 5 + TS 4 같은 부조화한 의존성 조합이 그대로 남음 (장기적으로 다른 패키지 추가 시 또 깨질 위험)
- C만 적용 시 npm peer 검사 모드 차이로 인한 lock 파일 드리프트가 그대로 남음 (`legacy-peer-deps` 없이 새 패키지 추가 시 다시 깨짐)
- 동시에 적용하면 단기 fix와 근본 fix를 한 번의 변경으로 정리 가능

---

## 3. 변경 내역

### 3.1 의존성/설정

#### `frontend/.npmrc` (신규)

```
legacy-peer-deps=true
```

- npm의 peer 의존성 해결 모드를 npm 6 시절 동작(경고만 표시, 에러 없음)으로 고정
- `npm ci`/`npm install` 모두 일관된 동작
- 로컬 환경과 Cloudflare 환경의 npm 동작 일치 → lock 파일 드리프트 방지

#### `frontend/package.json`

```diff
- "typescript": "^4.9.5",
+ "typescript": "^5.4.5",
```

- TS 5.4.5는 `react-scripts@5.0.1` 및 React 19와 호환성이 검증된 안정 버전
- `^5.4.5`로 두면 npm이 5.x 범위 내에서 patch 자동 적용

#### `frontend/package-lock.json`

- 변경 1개 패키지(typescript)만 갱신
- `npm install` 실행 결과: `changed 1 package, and audited 1393 packages in 3s`
- 의존성 트리 자체에는 영향 없음

### 3.2 ESLint 경고 정리 (11건)

TS 5 업그레이드만으로는 충분하지 않았습니다. Cloudflare Pages는 빌드 시 `CI=true` 환경 변수를 자동 설정하는데, `react-scripts`는 이 값을 보면 **ESLint 경고를 컴파일 에러로 승격**시킵니다. 이전까지는 npm install 단계에서 빌드가 멈춰서 컴파일까지 가본 적이 없었기에 잠복해 있던 11건의 경고가 이제 한꺼번에 드러났습니다.

| 파일 | 변경 내용 | 위험도 |
|------|-----------|--------|
| `Board/BoardPage.tsx` | 미사용 import `useCallback` 제거, `useAuth()`에서 미사용 `user` 제거 | 낮음 |
| `Board/PostDetailPage.tsx` | `useAuth()`에서 미사용 `user` 제거 | 낮음 |
| `Board/PostEditorPage.tsx` | `useAuth()`에서 미사용 `isLoggedIn` 제거 | 낮음 |
| `Board/components/MathNodeView.tsx` | 호출처 없는 `setIsEditing` 제거 (`useState` 결과를 `[isEditing]` 단일 destructure로) | 낮음 |
| `MyPage/MyPage.tsx` | 미사용 import/destructure 정리, `useEffect` deps에 `eslint-disable-next-line` 추가, regex `\[` → `[` 정리 | 보통 (3.3 절 참조) |
| `Payment/Payment.tsx` | 죽은 코드 `companyInfo` state/effect/interface 통째 제거 | 보통 (3.3 절 참조) |
| `components/Header.tsx` | 미사용 `isAdmin`, `logout` destructure 제거, 호출처 없는 `handleLogout` 함수 제거 | 낮음 |

### 3.3 운영 안전성이 걸렸던 변경 두 건

#### MyPage.tsx의 useEffect deps

ESLint는 다음 effect의 deps에 `changeGlobalLanguage`가 빠졌다고 경고했습니다.

```tsx
useEffect(() => {
  const fetchUserInfo = async () => {
    // ... fetch 후 setUserInfo, setEditName, setSelectedLanguage,
    //     setTempLanguage 호출
    if (data.language) {
      changeGlobalLanguage(data.language);  // ← 여기서 호출
    }
  };
  if (isLoggedIn) fetchUserInfo();
}, [isLoggedIn]);  // ESLint: 'changeGlobalLanguage'가 deps에 빠짐
```

**deps에 추가하지 않은 이유:**

`changeGlobalLanguage`는 `LanguageContext`에서 제공되는 함수입니다. 이를 deps에 추가하면 함수 참조가 바뀔 때마다 effect가 재실행되어 `/api/users/me` API가 추가로 호출됩니다. 만약 `LanguageContext`가 `useCallback`으로 메모이제이션되어 있지 않으면 매 렌더마다 fetch가 발생할 수도 있습니다.

이 effect는 사용자 정보를 로드하는 핵심 로직이므로, **현재 동작(`isLoggedIn` 변경 시에만 1회 fetch)을 보존하는 것이 안전**합니다. 따라서 다음과 같이 명시적 무시 주석을 추가했습니다.

```tsx
if (isLoggedIn) fetchUserInfo();
// changeGlobalLanguage는 LanguageContext에서 제공되며 deps에 추가 시 effect 재실행 빈도 변경으로
// 사용자 정보 API 재호출 패턴이 바뀔 수 있어 의도적으로 제외함
// eslint-disable-next-line react-hooks/exhaustive-deps
}, [isLoggedIn]);
```

**효과:** API 호출 패턴 변동 없음. 운영 DB의 `users` 테이블 부하 변동 없음.

#### Payment.tsx의 companyInfo 죽은 코드

다음 코드가 있었습니다.

```tsx
const [companyInfo, setCompanyInfo] = useState<CompanyInfo | null>(null);
// ...
useEffect(() => {
  fetch('/config/company.json')
    .then(res => res.json())
    .then(data => setCompanyInfo(data))
    .catch(() => { /* 회사 정보 로드 실패 */ });
}, []);
```

`companyInfo` 값은 컴포넌트 어디에서도 읽히지 않았고(JSX 미반영), 따라서 `setCompanyInfo`도 의미 없는 호출이었습니다. ESLint는 `companyInfo`만 unused로 잡았지만, 실질적으로는 `useState` + `useEffect` + `interface CompanyInfo` 전체가 죽은 코드였습니다.

**제거 결정 근거:**

- `/config/company.json`은 정적 파일 (frontend/public 또는 Cloudflare Pages 정적 자산) → 백엔드 API 미호출 → DB 무관
- 향후 회사 정보 표시 기능을 추가하게 되면 git history에서 복원 가능
- 미사용 fetch는 클라이언트의 불필요한 네트워크 요청을 줄이는 부수 효과도 있음

**효과:** 백엔드 호출 변동 없음. 클라이언트 측 정적 파일 1건 fetch 제거.

---

## 4. 작업 진행 과정과 발생 오류

작업은 시간 순으로 다음과 같이 진행됐고, 두 차례 실수로 인한 빌드 실패가 있었습니다.

### 4.1 1차 시도: TS 5 + ESLint 11건 일괄 수정

처음 ESLint 출력을 보고 7개 파일을 수정했습니다. 그러나 **두 군데에서 ESLint 경고에 명시되지 않은 변수까지 함께 제거**하는 실수를 했습니다.

#### 오류 ①: `PostDetailPage.tsx`에서 `isLoggedIn` 잘못 제거

ESLint 경고:
```
src/CategoryPages/Board/PostDetailPage.tsx
  Line 26:23:  'user' is assigned a value but never used
```

`user`만 unused였으나, destructure 전체를 다시 쓸 때 `isLoggedIn`까지 함께 제거했습니다.

```tsx
// 변경 전: const { isLoggedIn, user, isAdmin } = useAuth();
// 잘못된 변경: const { isAdmin } = useAuth();   ← isLoggedIn도 빼버림
```

빌드 결과:
```
TS2304: Cannot find name 'isLoggedIn'.
  src/CategoryPages/Board/PostDetailPage.tsx:130
        {!isLoggedIn && (
          ^^^^^^^^^^
```

조치: `isLoggedIn` 복원.

```tsx
const { isLoggedIn, isAdmin } = useAuth();
```

#### 오류 ②: `Payment.tsx`의 `formatPhoneNumberOnInput` 잘못 제거

ESLint 경고는 `MyPage.tsx`의 `formatPhoneNumberOnInput` import가 unused라는 내용이었으나, 같은 import 줄을 가진 `Payment.tsx`도 함께 처리하다가 그쪽은 경고에 없었음에도 import에서 제거했습니다.

빌드 결과:
```
TS2552: Cannot find name 'formatPhoneNumberOnInput'. Did you mean 'formatPhoneNumber'?
  src/CategoryPages/Payment/Payment.tsx:194
       setPaymentInfo(prev => ({ ...prev, [name]: formatPhoneNumberOnInput(value) }));
                                                  ^^^^^^^^^^^^^^^^^^^^^^^^
```

조치: import 복원.

```tsx
import { formatPhoneNumber, formatPhoneNumberOnInput, cleanPhoneNumber } from '../../utils/phoneUtils';
```

#### 오류 ③: `Header.tsx`의 `logout` 신규 unused 발생

`handleLogout` 함수를 제거하면서 `logout()`이 호출되던 유일한 위치가 함께 사라졌고, 그 결과 `logout` destructure 자체가 새로 unused가 됐습니다. 이는 1차 수정 시점에는 인지하지 못했고, 빌드 결과로 드러났습니다.

```
src/components/Header.tsx
  Line 41:23:  'logout' is assigned a value but never used
```

조치: `logout` 제거.

```tsx
// 변경 전: const { isLoggedIn, logout } = useAuth();
// 변경 후: const { isLoggedIn } = useAuth();
```

### 4.2 교훈

ESLint 출력의 정확한 파일/줄/변수명만 손대고, 같은 줄에 있는 다른 변수는 절대 함께 제거하지 않는다는 원칙을 더 엄격히 지켰어야 합니다. 함수 단위 제거 시에는 그 함수가 사용하던 변수의 새 unused 발생 여부도 같은 단계에서 확인해야 합니다.

---

## 5. 검증 방법

로컬 환경(Node 24)에서 react-scripts 5의 webpack 빌드가 silent failure를 일으키는 별개 환경 이슈가 있어, 직접 확인이 불가능했습니다. 따라서 **Cloudflare Pages와 동일한 환경(Node 18.20.8 + `CI=true`)을 Docker 컨테이너로 재현**하여 검증했습니다.

```bash
docker run --rm -v "/c/.../frontend:/src:ro" node:18.20.8-alpine sh -c "
  cp -r /src/. /build/
  cd /build
  rm -rf node_modules
  npm ci
  CI=true npm run build
"
```

최종 빌드 결과:

```
Compiled successfully.

File sizes after gzip:
  579.3 kB (-111 B)  build/static/js/main.e9825c24.js
  27.58 kB           build/static/css/main.3d052313.css

The build folder is ready to be deployed.
```

---

## 6. 운영 영향 점검

| 항목 | 결과 |
|------|------|
| 백엔드 API 호출 코드 | **변경 없음** |
| 사용자 정보 fetch (`/api/users/me`) 호출 빈도 | **변경 없음** (useEffect deps 의도적 보존) |
| 라이선스/구독/리딤 API | **변경 없음** |
| DB 스키마 | **변경 없음** |
| 인증/세션 동작 | **변경 없음** (`Header.tsx` 죽은 함수만 제거) |
| 정적 파일 호출 | `/config/company.json` fetch 1건 제거 (UI 미반영 죽은 코드) |
| 번들 크기 | -111 B (미세 감소) |

---

## 7. 향후 권장사항

### 7.1 단기

- Cloudflare Pages가 새 커밋(`21b10d9e`)을 빌드하면 통과 여부 확인
- 통과 후 PR #129 머지

### 7.2 중기

- **`react-scripts` 탈출 검토**: `react-scripts@5.0.1`은 React 18 시대 빌드 도구이며 현재 프로젝트는 React 19 사용. Vite 등으로의 마이그레이션이 장기적으로 필요. 같이 진행하면 다음 효과:
  - Node 24 등 최신 환경에서도 안정 빌드
  - 빌드 속도 대폭 개선
  - peer dep 트리 정리 가능

- **ESLint 룰 재검토**: 현재 `react-app/jest` 기본 설정 사용. `no-unused-vars`가 워닝 레벨이어서 CI=false에서는 잠복하는 구조. unused 코드는 IDE에서 가시화되지만, PR 리뷰 단계에서 CI가 강제 검출하도록 룰을 명시적으로 정의하는 것이 좋습니다.

### 7.3 장기

- **프론트엔드 빌드 CI 추가**: 현재는 Cloudflare Pages 자체가 유일한 빌드 검증처. PR 단계에서 GitHub Actions 등으로 사전 빌드 검증 워크플로를 추가하면, 본 문서 같은 연쇄 실패 디버깅을 한 PR 안에서 마무리할 수 있습니다.

---

## 부록: 변경된 파일 목록 (커밋 `21b10d9e`)

```
frontend/.npmrc                                              (신규)
frontend/package.json                                        (typescript ^4.9.5 → ^5.4.5)
frontend/package-lock.json                                   (lock 갱신)
frontend/src/CategoryPages/Board/BoardPage.tsx               (ESLint 정리)
frontend/src/CategoryPages/Board/PostDetailPage.tsx          (ESLint 정리)
frontend/src/CategoryPages/Board/PostEditorPage.tsx          (ESLint 정리)
frontend/src/CategoryPages/Board/components/MathNodeView.tsx (ESLint 정리)
frontend/src/CategoryPages/MyPage/MyPage.tsx                 (ESLint 정리 + eslint-disable + regex)
frontend/src/CategoryPages/Payment/Payment.tsx               (죽은 코드 제거)
frontend/src/components/Header.tsx                           (ESLint 정리)
```
