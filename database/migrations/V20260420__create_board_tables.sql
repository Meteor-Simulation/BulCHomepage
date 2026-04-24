-- 게시판 테이블
CREATE TABLE IF NOT EXISTS posts (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    author_id       UUID NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content_html    TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    view_count      INT NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_visibility ON posts(visibility);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX idx_posts_is_deleted ON posts(is_deleted);

COMMENT ON TABLE posts IS '게시판 게시글';
COMMENT ON COLUMN posts.visibility IS '공개 범위: PUBLIC(전체), MEMBER(회원), STAFF(매니저/관리자)';
COMMENT ON COLUMN posts.is_deleted IS '소프트 삭제 여부';

-- 게시글 이미지 테이블
CREATE TABLE IF NOT EXISTS post_images (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    post_id         BIGINT,
    image_url       VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255),
    file_size       BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE SET NULL
);

CREATE INDEX idx_post_images_post ON post_images(post_id);

COMMENT ON TABLE post_images IS '게시글 이미지';
COMMENT ON COLUMN post_images.post_id IS '게시글 ID (NULL이면 아직 게시글에 연결되지 않은 임시 이미지)';
