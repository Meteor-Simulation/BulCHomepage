# 라이선스 중복 구매 차단 + owner_id 정합성 복구

> 작성일: 2026-05-28
> 관련 브랜치: `fix/MDP-409-MDP-504-license-status-after-expiry`
> 마이그레이션: `database/migrations/V20260528__fix_orphan_license_owners_and_add_unique_index.sql`

---

## 1. 배경

### 1.1 도입 동기
개인(USER) 사용자가 동일 제품을 중복 구매할 수 있는 상태였습니다. 기관(ORG) 사용자는 다중 라이선스 정책이 의도된 설계이지만, 결제 경로는 현재 `OwnerType.USER`로 하드코딩되어 있어 모든 결제 사용자가 무방비로 중복 구매 가능.

프론트엔드만 막을 경우 뒤로가기/더블 클릭/직접 호출 등으로 우회 가능 → 백엔드 강제력 필요.

### 1.2 추가로 발견된 owner_id 정합성 버그
중복 차단 구현을 위해 PaymentService를 분석하던 중, 라이선스 발급 시 `owner_id`를 다음과 같이 계산하던 버그를 발견했습니다.

```java
UUID userId = UUID.nameUUIDFromBytes(userEmail.getBytes(StandardCharsets.UTF_8));
```

PaymentController에서 넘기는 `userEmail` 파라미터는 사실 `authentication.getName()` = **UUID 문자열**입니다 ([CustomUserDetailsService](../../backend/src/main/java/com/bulc/homepage/security/CustomUserDetailsService.java) `buildUserDetails`가 `user.getId().toString()`을 username으로 설정). 즉 위 코드는 "UUID 문자열의 바이트를 다시 해시"하여 가짜 v3 UUID를 만들어 `owner_id`로 저장 → `licenses.owner_id`가 실제 `users.id`와 불일치하는 레코드가 다수 존재할 가능성.

영향 범위:
- `/api/v1/me/licenses` 호출 시 본인 라이선스가 조회되지 않음
- 본 PR의 중복 차단 쿼리(`existsActiveOrPendingPaidLicense`)가 매칭 실패 → 중복 차단 무력화
- `payments.user_id`도 같은 흐름에서 NULL로 저장됨 (`findByEmail(uuidString)` → empty)

---

## 2. 변경 내역

### 2.1 코드 변경 (3중 방어 + 버그 fix)

| 계층 | 파일 | 내용 |
|------|------|------|
| ① 결제 진입 차단 | [PaymentService.confirmPayment](../../backend/src/main/java/com/bulc/homepage/service/PaymentService.java) | 토스 API 호출 전 `licenseService.requireUserCanPurchasePlan()` 호출 |
| ② 발급 시 재검증 | [LicenseService.issueLicenseWithPlanForBilling](../../backend/src/main/java/com/bulc/homepage/licensing/service/LicenseService.java) | `OwnerType.USER`인 경우 동일 product의 ACTIVE/PENDING 비-TRIAL 라이선스 존재 시 `LICENSE_ALREADY_EXISTS` |
| ③ DB partial unique index | [init.sql](../../database/init.sql) + 마이그레이션 | `uk_licenses_user_product_active_paid` |
| 부수 - userId 일관성 | PaymentService 즉시/웹훅 두 경로 | `UUID.nameUUIDFromBytes(userEmail)` → `userRepository.findById(UUID.fromString(userEmail))` |
| 프론트 | [Payment.tsx](../../frontend/src/CategoryPages/Payment/Payment.tsx) | `/me/licenses` 조회 → "구독 중" 배지 + "현재 구독 중" 버튼 + 결제 시도 차단 |

### 2.2 데이터 마이그레이션
파일: `database/migrations/V20260528__fix_orphan_license_owners_and_add_unique_index.sql`

수행 작업:
1. **임시 함수** `pg_temp.name_uuid_v3()` 정의 — Java `UUID.nameUUIDFromBytes` 동등 (MD5 + 버전 3 + IETF variant bits)
2. **license.owner_id 보정**: `license.source_order_id = name_uuid_v3(payment_details.order_id)` 매칭으로 orphan 레코드를 진짜 user.id로 UPDATE. 매칭 기준은 `payments.user_email`(버그로 UUID 문자열 저장됨) = `users.id::text`.
3. **payments.user_id 보정**: NULL이고 user_email이 valid UUID인 경우 진짜 user.id로 UPDATE
4. **남은 orphan / 중복 그룹 진단**: RAISE NOTICE / WARNING
5. **partial unique index 생성**: 중복 그룹이 없을 때만 생성, 있으면 skip + WARNING

마이그레이션은 idempotent하며 재실행 안전.

---

## 3. 운영 배포 절차

### 3.1 순서
```
1) 백엔드 코드 배포 (PaymentService, LicenseService 변경 포함)
   - 이 시점부터 신규 결제는 정상 user.id로 발급됨
   - 기존 orphan 데이터는 그대로 (마이그레이션 전)
2) 마이그레이션 실행
   psql -h <host> -U <user> -d <db> -f V20260528__fix_orphan_license_owners_and_add_unique_index.sql
3) 마이그레이션 출력 검토
   - 보정 건수 NOTICE 확인
   - 남은 orphan WARNING 있으면 (a) 쿼리로 상세 확인 + 수동 처리
   - 중복 그룹 WARNING 있으면 (b) 쿼리로 상세 확인 + 수동 정리 후 재실행
4) 프론트엔드 배포
```

### 3.2 마이그레이션 출력 예상 시나리오

**케이스 A — 모든 orphan 자동 복구 성공:**
```
NOTICE:  [license-migration] license.owner_id 보정: 42 건
NOTICE:  [license-migration] payments.user_id 보정: 38 건
NOTICE:  [license-migration] orphan license 없음 OK
NOTICE:  [license-migration] partial unique index 생성 완료 OK
```

**케이스 B — 일부 orphan 복구 실패 (source_order_id 매칭 실패):**
```
WARNING: [license-migration] 보정되지 않은 orphan license 3 건 — source_order_id 매칭 실패. 수동 검토 필요.
```
→ 진단 쿼리 (a) 실행, payment_details 누락/payment.user_email 형식 비정상 등 사례별 처리.

**케이스 C — 중복 그룹 발견 (인덱스 생성 미실행):**
```
WARNING: [license-migration] USER 중복 ACTIVE/PENDING 유상 라이선스 2 그룹 발견. partial unique index 생성을 건너뜁니다. 운영팀이 수동 정리 후 본 마이그레이션 재실행 필요.
```
→ 진단 쿼리 (b) 실행, 정책에 따라 가장 늦은 valid_until 1개만 ACTIVE로 두고 나머지는 SUSPENDED/REVOKED. 정리 후 마이그레이션 재실행 시 인덱스 자동 생성.

### 3.3 진단 쿼리

```sql
-- (a) 보정되지 않은 orphan license 상세
SELECT l.id, l.owner_id, l.product_id, l.source_order_id,
       l.license_key, l.created_at
FROM licenses l
WHERE l.owner_type = 'USER'
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = l.owner_id);

-- (b) USER 중복 ACTIVE/PENDING 유상 라이선스 상세
SELECT owner_id, product_id,
       array_agg(id ORDER BY created_at) AS license_ids,
       array_agg(status ORDER BY created_at) AS statuses,
       array_agg(created_at ORDER BY created_at) AS created_ats,
       array_agg(valid_until ORDER BY created_at) AS valid_untils
FROM licenses
WHERE owner_type = 'USER'
  AND license_type <> 'TRIAL'
  AND status IN ('ACTIVE','PENDING')
GROUP BY owner_id, product_id
HAVING COUNT(*) > 1;

-- (c) 마이그레이션 후 정상성 검증
SELECT
  (SELECT COUNT(*) FROM licenses l WHERE l.owner_type='USER'
   AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = l.owner_id)) AS orphan_remaining,
  (SELECT COUNT(*) FROM (
     SELECT owner_id, product_id FROM licenses
     WHERE owner_type='USER' AND license_type<>'TRIAL'
     AND status IN ('ACTIVE','PENDING')
     GROUP BY owner_id, product_id HAVING COUNT(*) > 1
   ) d) AS duplicate_groups,
  (SELECT COUNT(*) FROM pg_indexes
   WHERE indexname = 'uk_licenses_user_product_active_paid') AS unique_index_exists;
```

기대 결과:
- `orphan_remaining = 0`
- `duplicate_groups = 0`
- `unique_index_exists = 1`

---

## 4. 정책

### 4.1 중복 차단 범위
- **차단 기준**: 동일 `users.id` + 동일 `products.id`에 ACTIVE/PENDING **비-TRIAL** 라이선스 존재 시
- **TRIAL 제외**: 회원가입 시 자동 발급되는 14일 체험 라이선스는 유상 구매를 막지 않음
- **EXPIRED_GRACE 제외**: 유예기간 중에는 갱신 구매 가능 (자연스러운 갱신 UX)
- **ORGANIZATION 제외**: 기관 다중 라이선스 정책 유지 (현재 결제 경로는 USER만 노출되어 있어 실제로 트리거되지 않음)

### 4.2 업그레이드/다운그레이드는 별도 작업
"동일 product에 ACTIVE 라이선스가 있을 때 다른 플랜(상위/하위)으로 변경 구매"는 본 PR 범위 외. 별도 옵션 구매 형식으로 추후 구현.

---

## 5. 검증 체크리스트

코드 PR 머지 전:
- [ ] `./gradlew test` (JDK 17 환경) — `DuplicatePurchaseBlock` 테스트 4건 통과
- [ ] `npx tsc --noEmit` (frontend) — 통과
- [ ] 신규 사용자로 회원가입 → TRIAL 자동 발급 → 결제 가능 확인
- [ ] 결제 완료 후 같은 제품 재결제 시 토스 호출 전 차단 + 한글 메시지 표시
- [ ] 결제 페이지에서 보유 중인 제품 카드에 "구독 중" 배지 + 결제 버튼 "현재 구독 중"

운영 마이그레이션 후:
- [ ] §3.3 검증 쿼리로 3개 카운터 모두 기대값
- [ ] 무작위 사용자 1명 picking → `/me/licenses` 호출 → 라이선스 정상 조회
- [ ] 운영 PostgreSQL 로그에 partial unique index 위반 에러 없음 (며칠 모니터링)
