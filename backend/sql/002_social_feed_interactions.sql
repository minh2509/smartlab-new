-- SmartLab Social Feed Core v1 interactions.
-- Idempotent schema extension; run after 001_smartlab_postgres_schema_seed.sql.

CREATE TABLE IF NOT EXISTS post_reactions (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  reaction_type VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_post_reactions_post_user UNIQUE (post_id, user_id),
  CONSTRAINT chk_post_reactions_type CHECK (reaction_type IN ('LIKE', 'LOVE', 'HAHA', 'SAD', 'ANGRY'))
);

CREATE INDEX IF NOT EXISTS idx_post_reactions_post_type
  ON post_reactions(post_id, reaction_type);

CREATE INDEX IF NOT EXISTS idx_post_reactions_user_post
  ON post_reactions(user_id, post_id);

CREATE TABLE IF NOT EXISTS post_comments (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  author_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  content VARCHAR(5000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_post_comments_content CHECK (length(btrim(content)) BETWEEN 1 AND 5000)
);

CREATE INDEX IF NOT EXISTS idx_post_comments_active_post_order
  ON post_comments(post_id, created_at DESC, id DESC)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_post_comments_author_active
  ON post_comments(author_user_id, post_id)
  WHERE deleted_at IS NULL;

-- 002 is applied after 001, so grant only its own social-feed objects.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT USAGE ON SCHEMA public TO smartlab_user;
    GRANT SELECT, INSERT, UPDATE, DELETE ON post_reactions, post_comments TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE post_reactions_id_seq, post_comments_id_seq TO smartlab_user;
  END IF;
END
$$;
