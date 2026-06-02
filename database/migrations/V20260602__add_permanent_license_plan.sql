-- MDP-538: 영구 라이선스 플랜 추가 + 기간(description) 포맷 정리
-- 회의 결정사항(2026-05-26): BUL:C 3D Premium 영구 라이선스 3000만원
-- 멱등 적용 가능. USD 가격 미확정으로 KRW 행만 추가.

-- 1) 구독형 description 포맷 변경: '1년/365일' → '1년(365일)'
UPDATE price_plans
SET description = '1년(365일)'
WHERE name = 'BUL:C 3D Premium'
  AND description = '1년/365일';

-- 2) 영구 라이선스 행 추가 (이름은 구독형과 동일, description으로 구분)
INSERT INTO price_plans (product_code, name, description, price, currency, is_active)
SELECT '001', 'BUL:C 3D Premium', '영구 라이선스', 30000000, 'KRW', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM price_plans
    WHERE product_code = '001'
      AND name = 'BUL:C 3D Premium'
      AND description = '영구 라이선스'
      AND currency = 'KRW'
);
