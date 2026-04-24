ALTER TABLE posts ADD COLUMN IF NOT EXISTS annotated_images_json TEXT;
COMMENT ON COLUMN posts.annotated_images_json IS '주석 이미지 데이터 (JSON)';
