# Session Token 설정 가이드

이 문서는 sessionToken (RS256 JWS) 기능을 설정하는 방법을 설명합니다.

## 1. 키 파일 구조

```
BulCHomepage/
├── keys/
│   ├── session_token_public_key.pem  # 공개키 (Git 커밋됨)
│   └── README.md
└── secrets/
    ├── session_token_private_key.pem       # 개인키 (PEM 형식, Git 무시됨)
    └── README.md
```

## 2. 로컬 개발 환경 설정

### Backend (Spring Boot)

#### 방법 1: 환경변수 (권장)

**Windows (PowerShell):**
```powershell
$env:SESSION_TOKEN_PRIVATE_KEY = Get-Content secrets\session_token_private_key.pem -Raw
.\backend\gradlew.bat bootRun
```

**Linux/Mac:**
```bash
export SESSION_TOKEN_PRIVATE_KEY=$(cat secrets/session_token_private_key.pem)
cd backend && ./gradlew bootRun
```

**중요:** 환경변수에는 PEM 파일의 내용을 **그대로** 설정합니다 (헤더/푸터 포함).
Base64로 재인코딩하지 마세요. SessionTokenService가 내부적으로 처리합니다.

#### 방법 2: application-dev.yml (비권장)

`backend/src/main/resources/application-dev.yml` 생성 (**Git에 커밋 금지**):

```yaml
bulc:
  licensing:
    session-token:
      private-key: |
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCZnC6Cx6Oftfly
        ...
        (전체 PEM 내용)
        ...
        -----END PRIVATE KEY-----
```

### 서버 시작 확인

서버 로그에 다음 메시지가 표시되어야 합니다:

```
SessionTokenService: RS256 개인키 로드 성공 (알고리즘: RS256 전용)
```

#### 오류 발생 시

**개인키 미설정:**
```
[FATAL] SessionTokenService: RS256 개인키가 설정되지 않았습니다.
```
→ `SESSION_TOKEN_PRIVATE_KEY` 환경변수를 설정하세요.

**개인키 형식 오류:**
```
[FATAL] SessionTokenService: RS256 개인키 로드 실패. 키 형식을 확인하세요
```
→ PKCS#8 PEM 형식인지 확인하세요.

## 3. 운영 환경 배포

### Docker Compose

`docker-compose.prod.yml`:

```yaml
services:
  backend:
    environment:
      - SESSION_TOKEN_PRIVATE_KEY=${SESSION_TOKEN_PRIVATE_KEY}
      - SESSION_TOKEN_TTL_MINUTES=15
      - SESSION_TOKEN_ISSUER=bulc-license-server
```

`.env.prod` 파일 (**Git에 커밋 금지**):

```bash
# PEM 파일 내용을 한 줄로 변환 (줄바꿈을 \n으로 치환)
SESSION_TOKEN_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIEvg...(생략)...o74\n-----END PRIVATE KEY-----"
```

**팁:** PEM을 한 줄로 변환하려면:
```bash
awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' secrets/session_token_private_key.pem
```

### Kubernetes

```bash
# Secret 생성 (PEM 파일에서 직접)
kubectl create secret generic session-token-key \
  --from-file=private-key=secrets/session_token_private_key.pem

# Deployment에서 참조
```

`deployment.yaml`:

```yaml
env:
  - name: SESSION_TOKEN_PRIVATE_KEY
    valueFrom:
      secretKeyRef:
        name: session-token-key
        key: private-key
```

### AWS (예시)

**AWS Secrets Manager:**

```bash
# PEM 파일 내용을 직접 저장
aws secretsmanager create-secret \
  --name bulc/session-token/private-key \
  --secret-string file://secrets/session_token_private_key.pem
```

## 4. 검증

### API 테스트

```bash
# 1. 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}'

# 2. 라이선스 검증 (sessionToken 확인)
curl -X POST http://localhost:8080/api/licenses/validate \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceFingerprint": "test-device-123"
  }'
```

**응답 예시:**
```json
{
  "valid": true,
  "licenseId": "...",
  "sessionToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  ...
}
```

### sessionToken 디코딩 검증

https://jwt.io 에서 sessionToken을 붙여넣기:

1. **Header:**
   ```json
   {
     "alg": "RS256",
     "typ": "JWT"
   }
   ```

2. **Payload 클레임 확인:**
   - `iss`: "bulc-license-server"
   - `aud`: 제품 코드 (예: "BULC_EVAC")
   - `sub`: licenseId
   - `dfp`: deviceFingerprint
   - `ent`: entitlements 배열
   - `exp`: 만료 시각

3. **서명 검증:**
   - `keys/session_token_public_key.pem` 내용을 복사
   - jwt.io의 "Verify Signature" 섹션에 붙여넣기
   - "Signature Verified" 확인

## 5. 보안 체크리스트

운영 배포 전 확인:

- [ ] `secrets/` 디렉토리가 Git에 커밋되지 않음
- [ ] `.gitignore`에 `secrets/`, `*.pem`, `*.key` 추가됨
- [ ] 개인키가 환경변수 또는 Secret Manager로 관리됨
- [ ] `application.yml`에 평문 개인키가 없음
- [ ] 공개키가 `keys/` 디렉토리에 커밋됨
- [ ] CLI/앱에 공개키가 내장됨
- [ ] 키 회전 계획 수립됨 (권장: 6개월)

## 6. 트러블슈팅

### sessionToken이 null로 반환됨

**원인:** 개인키 미설정 (dev 환경에서 경고만 표시)

**해결:**
```bash
export SESSION_TOKEN_PRIVATE_KEY=$(cat secrets/session_token_private_key.pem)
```

### 서버 부팅 실패 (prod 환경)

**원인:** prod 환경에서 개인키 필수

**해결:** 환경변수 설정 후 재시작

### 클라이언트에서 서명 검증 실패

**원인:** 공개키 불일치

**해결:**
1. `keys/session_token_public_key.pem`이 최신인지 확인
2. CLI/앱에 내장된 공개키가 일치하는지 확인
3. 키 회전 시 클라이언트도 함께 업데이트

## 7. 관련 문서

- `Document/bulc_auth_module_spec_v0.2.md` - CLI 인증 모듈 스펙
- `Document/licensing_api_v1.1.md` - API 문서 (v1.1.2)
- `keys/README.md` - 공개키 사용 가이드
- `secrets/README.md` - 개인키 보안 가이드

---

작성일: 2025-12-30
관련 이슈: MDP-293
