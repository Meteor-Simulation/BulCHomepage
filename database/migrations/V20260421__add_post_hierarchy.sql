ALTER TABLE posts ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES posts(id) ON DELETE SET NULL;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_posts_parent ON posts(parent_id);

COMMENT ON COLUMN posts.parent_id IS '상위 게시글 ID (NULL이면 최상위)';
COMMENT ON COLUMN posts.sort_order IS '같은 부모 내 정렬 순서';
