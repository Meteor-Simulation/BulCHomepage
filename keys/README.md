# Session Token 공개키

이 디렉토리에는 sessionToken (RS256 JWS) 검증에 사용되는 **공개키**가 포함되어 있습니다.

## 파일

- `session_token_public_key.pem` - RS256 공개키 (PEM 형식)

## 용도

이 공개키는 다음에서 사용됩니다:

1. **CLI 도구 (bulc-lic.exe)**
   - sessionToken 서명 검증
   - 클라이언트측 기능 unlock 결정

2. **클라이언트 앱 (METEOR, BulC 등)**
   - session.json의 sessionToken 검증
   - 앱 내장용 (하드코딩 또는 리소스 파일)

## 보안 참고사항

- ✅ 이 파일은 **공개키**이므로 Git에 커밋해도 안전합니다
- ✅ 클라이언트 앱에 내장되어 배포됩니다
- ⚠️ 대응되는 개인키는 `secrets/` 디렉토리에 있으며 **절대 공개하지 마세요**

## 키 쌍 재생성

키 쌍을 재생성해야 하는 경우:

```bash
# 1. 개인키 생성 (secrets/에 저장)
openssl genrsa -out secrets/session_token_private_key.pem 2048

# 2. 공개키 추출 (keys/에 저장)
openssl rsa -in secrets/session_token_private_key.pem -pubout -out keys/session_token_public_key.pem

# 3. 개인키를 Base64로 인코딩 (백엔드 환경변수용)
cat secrets/session_token_private_key.pem | base64 -w 0

# 4. 백엔드 환경변수 설정
export SESSION_TOKEN_PRIVATE_KEY="<base64-encoded-key>"
```

## 클라이언트 통합

### .NET (CLI)

```csharp
var publicKeyPem = File.ReadAllText("session_token_public_key.pem");
var rsa = RSA.Create();
rsa.ImportFromPem(publicKeyPem);
```

### Java (앱)

```java
String publicKeyPem = Resources.toString(
    Resources.getResource("session_token_public_key.pem"),
    StandardCharsets.UTF_8
);
```

---

생성일: 2025-12-30
관련 이슈: MDP-293
