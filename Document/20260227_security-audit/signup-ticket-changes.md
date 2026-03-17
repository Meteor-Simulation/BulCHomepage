# 이메일 인증 우회 방지: signup_ticket 1회용 티켓 방식

**작성일**: 2026-02-27
**목적**: 이메일 인증을 완료하지 않은 사용자의 회원가입 차단 (백엔드 강제)

---

## 1. 변경 사유

### 보안 취약점
- 기존 `/api/auth/signup`은 이메일 인증 여부를 **검증하지 않음**
- 직접 API 호출(`curl`, Postman 등)로 인증 없이 회원가입 가능
- 이메일 인증 상태가 **프론트엔드 상태(isEmailVerified)** 에만 존재 — 서버 측 강제력 없음

### 추가 취약점
- 인증 코드 검증 시 **시도 횟수 제한 없음** — 브루트포스 공격에 노출

---

## 2. 변경 파일 목록

### 신규 파일

| 파일 | 설명 |
|------|------|
| `database/init.sql` 내 `signup_tickets` 테이블 | 1회용 가입 티켓 저장 |
| `database/init.sql` 내 `email_verification_attempts` 테이블 | 인증 시도 횟수 추적 |
| `backend/.../entity/SignupTicket.java` | 가입 티켓 JPA 엔티티 |
| `backend/.../repository/SignupTicketRepository.java` | 비관적 락 조회 포함 |
| `backend/.../service/SignupTicketService.java` | 티켓 생성(createTicket) + 소비(consumeTicket) |
| `backend/.../entity/EmailVerificationAttempt.java` | 인증 시도 횟수 JPA 엔티티 |
| `backend/.../repository/EmailVerificationAttemptRepository.java` | 이메일 기준 조회/삭제 |

### 수정 파일

| 파일 | 변경 내용 |
|------|-----------|
| `database/init.sql` | `users` 테이블에 `email_verified`, `email_verified_at` 컬럼 추가 |
| `backend/.../entity/User.java` | `emailVerified`, `emailVerifiedAt` 필드 추가 |
| `backend/.../dto/request/SignupRequest.java` | `email` 필드 제거 → `signupTicket`(UUID) 필드 추가 |
| `backend/.../service/AuthService.java` | `signup()` — 티켓 소비 후 티켓의 email로 가입, `emailVerified=true` 설정 |
| `backend/.../controller/AuthController.java` | `verifyCode()` — 응답에 `signupTicket` 포함 |
| `backend/.../service/EmailVerificationService.java` | `verifyCode()` — 시도 횟수 제한 로직 추가 (5회/1시간/24시간 락) |
| `frontend/.../SignupModal.tsx` | `signupTicket` 저장 및 signup 요청 시 전송, `email` 제거 |

---

## 3. 보안 설계 요약

### signup_ticket 방식
- 이메일 인증 성공 시 서버가 UUID 티켓 발급 (TTL 10분)
- 회원가입 시 티켓 필수 — 없으면 가입 불가
- 티켓은 1회용 (used_at으로 소비 추적)
- 가입 이메일은 요청 바디가 아닌 **티켓에 바인딩된 email** 사용
- SELECT ... FOR UPDATE (비관적 락)으로 동시성 보호

### 인증 시도 횟수 제한
- 1시간 윈도우 내 최대 5회 실패 허용
- 5회 초과 시 24시간 잠금
- 추적 기준: 이메일 (브라우저/기기 변경으로 우회 불가)
- 잠금 정보 서버 DB 저장 (클라이언트 조작 불가)
- 응답에 실패 횟수/남은 횟수 미노출

---

## 4. 서버 DB 마이그레이션 SQL

```sql
-- 1. signup_tickets 테이블
CREATE TABLE signup_tickets (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email       VARCHAR(255) NOT NULL,
    purpose     VARCHAR(20) NOT NULL DEFAULT 'SIGNUP',
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_signup_tickets_email_purpose ON signup_tickets(email, purpose);
CREATE INDEX idx_signup_tickets_expires_at ON signup_tickets(expires_at);

-- 2. email_verification_attempts 테이블
CREATE TABLE email_verification_attempts (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    attempt_count       INT NOT NULL DEFAULT 0,
    first_attempt_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until        TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_email_verification_attempts_email ON email_verification_attempts(email);
CREATE INDEX idx_email_verification_attempts_locked_until ON email_verification_attempts(locked_until);

-- 3. users 테이블 컬럼 추가
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP NULL;
```
