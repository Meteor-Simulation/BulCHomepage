-- MDP-538: 영구 라이선스 플랜 추가 (3000만원 KRW)
-- 회의 결정사항(2026-05-26): BUL:C 3D Premium 영구 라이선스 3000만원
-- USD 가격 미확정으로 KRW 행만 추가

INSERT INTO price_plans (product_code, name, description, price, currency, is_active)
SELECT '001', 'BUL:C 3D Premium 영구', '영구', 30000000, 'KRW', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM price_plans
    WHERE product_code = '001'
      AND name = 'BUL:C 3D Premium 영구'
      AND currency = 'KRW'
);
